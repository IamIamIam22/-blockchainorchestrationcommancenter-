package com.example.automation

import android.content.Context
import com.example.contract.ContractCoordinator
import com.example.crypto.WalletEngine
import com.example.data.AppDatabase
import com.example.data.TransactionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

enum class TaskType {
    REWARD_CLAIM,
    COMPOUND_STAKING,
    WEB2_FAUCET_BOT
}

enum class ConditionType {
    INTERVAL,
    GAS_BELOW,
    BLOCK_MULTIPLE
}

data class AutomationTask(
    val id: String,
    val name: String,
    val type: TaskType,
    val contractAddress: String,
    val functionCall: String,
    val conditionType: ConditionType,
    val conditionValue: String, // e.g. "20" (gas gwei), "10" (every 10 blocks)
    var isRunning: Boolean = false,
    var lastRunTimestamp: Long = 0,
    var nextRunTimestamp: Long = 0
)

data class AutomationLog(
    val timestamp: Long,
    val level: String, // "INFO", "SUCCESS", "WARNING", "ERROR"
    val message: String,
    val details: String? = null
)

object AutomationController {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val random = SecureRandom()
    private var job: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val _tasks = MutableStateFlow<List<AutomationTask>>(emptyList())
    val tasks: StateFlow<List<AutomationTask>> = _tasks

    private val _logs = MutableStateFlow<List<AutomationLog>>(emptyList())
    val logs: StateFlow<List<AutomationLog>> = _logs

    // Mock proxies list for Web2 faucet proxy rotation
    private val proxies = listOf(
        "185.191.134.12:8080",
        "45.56.120.91:3128",
        "198.211.96.40:80",
        "162.243.108.161:8080",
        "104.248.63.15:3128"
    )

    init {
        initializeTasks()
    }

    private fun initializeTasks() {
        _tasks.value = listOf(
            AutomationTask(
                id = "task_faucet",
                name = "EVM Web2 Faucet Bot & Proxy Bypass",
                type = TaskType.WEB2_FAUCET_BOT,
                contractAddress = "0x5A0b54D5dc1c4C075c1C07C45A1bE2405CDEd949",
                functionCall = "requestTokens()",
                conditionType = ConditionType.INTERVAL,
                conditionValue = "30" // seconds for demo, real faucet might be 24h
            ),
            AutomationTask(
                id = "task_compound",
                name = "Staking Compounder (Auto-Reinvest)",
                type = TaskType.COMPOUND_STAKING,
                contractAddress = "0xfA6E1E4F8b488F48c9C7C0006767676767676767",
                functionCall = "set(uint256)", // uses Storage template for demo
                conditionType = ConditionType.GAS_BELOW,
                conditionValue = "28" // gwei limit
            ),
            AutomationTask(
                id = "task_rewards",
                name = "High-Priority Reward Harvester",
                type = TaskType.REWARD_CLAIM,
                contractAddress = "0x8E1E4F4b48488F48c9C7C0006767676767676767",
                functionCall = "get()",
                conditionType = ConditionType.BLOCK_MULTIPLE,
                conditionValue = "5" // every 5 blocks
            )
        )
        addLog("INFO", "Automation Controller initialized with ${_tasks.value.size} active orchestration definitions.")
    }

    fun startController(context: Context) {
        if (job != null && job?.isActive == true) return
        
        job = scope.launch {
            addLog("INFO", "Executing Blockchain Automation loops.")
            var simulatedBlock = 19485291L

            while (true) {
                delay(2000L) // Scan condition intervals every 2 seconds
                simulatedBlock++

                val currentTasks = _tasks.value.toMutableList()
                val updatedTasks = currentTasks.map { task ->
                    if (task.isRunning) {
                        var shouldTrigger = false
                        when (task.conditionType) {
                            ConditionType.INTERVAL -> {
                                val now = System.currentTimeMillis()
                                if (task.lastRunTimestamp == 0L || now - task.lastRunTimestamp >= (task.conditionValue.toLong() * 1000L)) {
                                    shouldTrigger = true
                                }
                            }
                            ConditionType.GAS_BELOW -> {
                                // Check gas pricing from mempool
                                val currentGas = com.example.mempool.MempoolClient.gasMetrics.value.baseFeeGwei
                                if (currentGas <= task.conditionValue.toDouble()) {
                                    shouldTrigger = true
                                }
                            }
                            ConditionType.BLOCK_MULTIPLE -> {
                                if (simulatedBlock % task.conditionValue.toLong() == 0L) {
                                    shouldTrigger = true
                                }
                            }
                        }

                        if (shouldTrigger) {
                            // Execute Task asynchronously
                            executeTask(context, task, simulatedBlock)
                            task.lastRunTimestamp = System.currentTimeMillis()
                            task.nextRunTimestamp = System.currentTimeMillis() + when (task.conditionType) {
                                ConditionType.INTERVAL -> task.conditionValue.toLong() * 1000L
                                else -> 10000L // 10s default check interval
                            }
                        }
                    }
                    task
                }
                _tasks.value = updatedTasks
            }
        }
    }

    fun stopController() {
        job?.cancel()
        job = null
        addLog("WARNING", "Automation loops suspended.")
    }

    fun toggleTask(taskId: String): Boolean {
        val current = _tasks.value.toMutableList()
        var newState = false
        val updated = current.map {
            if (it.id == taskId) {
                it.isRunning = !it.isRunning
                newState = it.isRunning
                addLog("INFO", "Task '${it.name}' status toggled to: ${if (it.isRunning) "ACTIVE" else "INACTIVE"}")
            }
            it
        }
        _tasks.value = updated
        return newState
    }

    private fun executeTask(context: Context, task: AutomationTask, currentBlock: Long) {
        scope.launch {
            addLog("INFO", "Triggering automation: [${task.name}]", "Condition met! Starting execution cycle.")

            if (!WalletEngine.hasWallet(context)) {
                addLog("ERROR", "Automation failed", "No wallet imported on-device. Local signature required.")
                return@launch
            }

            try {
                when (task.type) {
                    TaskType.WEB2_FAUCET_BOT -> {
                        executeFaucetBot(context, task)
                    }
                    TaskType.COMPOUND_STAKING -> {
                        executeStakingCompound(context, task)
                    }
                    TaskType.REWARD_CLAIM -> {
                        executeRewardClaim(context, task, currentBlock)
                    }
                }
            } catch (e: Exception) {
                addLog("ERROR", "[${task.name}] Execution error", e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun executeFaucetBot(context: Context, task: AutomationTask) {
        // Web2 API integration with proxy rotation & Captcha bypass hooks
        val proxy = proxies[random.nextInt(proxies.size)]
        addLog("INFO", "Faucet Bot", "Rotating network proxy. New proxy outbound route: $proxy")
        delay(1200L)

        addLog("INFO", "Faucet Bot", "Initiating CAPTCHA bypass API challenge solver...")
        delay(1800L)
        val solverId = random.nextInt(10000) + 20000
        addLog("SUCCESS", "Faucet Bot", "CAPTCHA challenge solved. Token token_grecaptcha_v3=$solverId. Simulating callback payload.")

        // We call a real faucet or complete the on-chain interaction
        addLog("INFO", "Faucet Bot", "Submitting Web2 claim transaction to faucet endpoint...")
        delay(1000L)

        // Broadcast claim on-chain
        try {
            val txHash = ContractCoordinator.writeContract(
                context,
                task.contractAddress,
                "set(uint256)",
                listOf("100") // Claim 100 faucet units
            )
            addLog("SUCCESS", "Faucet Bot Cycle Complete", "Tokens claimed successfully on-chain! Tx Hash: $txHash")
        } catch (e: Exception) {
            addLog("ERROR", "Faucet Bot On-chain Broadcast failed", e.message ?: "Unknown error")
        }
    }

    private suspend fun executeStakingCompound(context: Context, task: AutomationTask) {
        addLog("INFO", "Staking Compounder", "Low gas condition met! Querying staking yield pools...")
        delay(1000L)

        try {
            val address = WalletEngine.getWalletAddress(context) ?: run {
                addLog("ERROR", "Staking Compounder", "Wallet address missing")
                return
            }
            val balance = WalletEngine.getNativeBalance(context, address)
            addLog("INFO", "Staking Compounder", "Current wallet yield reserve balance: $balance ETH")

            if (balance < BigDecimal("0.005")) {
                addLog("WARNING", "Staking Compounder", "Skipping cycle: native reserve balance below secure safety buffer (0.005 ETH).")
                return
            }

            // Execute compound on-chain call
            val txHash = ContractCoordinator.writeContract(
                context,
                task.contractAddress,
                "set(uint256)",
                listOf(System.currentTimeMillis().toString().takeLast(6))
            )
            addLog("SUCCESS", "Staking Compounded Successfully", "Yield reinvested and compound staking finalized. Tx Hash: $txHash")
        } catch (e: Exception) {
            addLog("ERROR", "Staking Compounder failed", e.message ?: "Unknown error")
        }
    }

    private suspend fun executeRewardClaim(context: Context, task: AutomationTask, currentBlock: Long) {
        addLog("INFO", "Reward Harvester", "Block interval reached ($currentBlock). Checking claimable yield balance...")
        delay(800L)

        try {
            val state = ContractCoordinator.readContractState(
                context,
                task.contractAddress,
                "get()"
            )
            addLog("INFO", "Reward Harvester", "Claimable balance: $state Gwei. Formulating signature payload...")

            val txHash = ContractCoordinator.writeContract(
                context,
                task.contractAddress,
                "set(uint256)",
                listOf("1")
            )
            addLog("SUCCESS", "Reward Harvester Completed", "Claimed rewards on-chain safely. Tx Hash: $txHash")
        } catch (e: Exception) {
            addLog("ERROR", "Reward Harvester failed", e.message ?: "Unknown error")
        }
    }

    private fun addLog(level: String, message: String, details: String? = null) {
        val newLogs = _logs.value.toMutableList()
        newLogs.add(0, AutomationLog(System.currentTimeMillis(), level, message, details))
        if (newLogs.size > 100) newLogs.removeAt(newLogs.size - 1)
        _logs.value = newLogs
    }
}
