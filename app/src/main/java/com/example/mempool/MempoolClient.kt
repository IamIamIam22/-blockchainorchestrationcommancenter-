package com.example.mempool

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.security.SecureRandom

data class MempoolTx(
    val hash: String,
    val from: String,
    val to: String,
    val value: String,
    val gasPriceGwei: Double,
    val maxFeeGwei: Double,
    val maxPriorityFeeGwei: Double,
    val sizeBytes: Int,
    val method: String,
    val isFrontRunnable: Boolean,
    val riskScore: Double, // 0.0 to 1.0 (MEV victim probability)
    val timestamp: Long
)

data class GasMetrics(
    val baseFeeGwei: Double,
    val priorityFeeGwei: Double,
    val blockNumber: Long,
    val burntEth: Double,
    val networkCongestion: Int // Percentage 0 - 100
)

object MempoolClient {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val random = SecureRandom()

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _mempoolFlow = MutableSharedFlow<MempoolTx>(replay = 50)
    val mempoolFlow: SharedFlow<MempoolTx> = _mempoolFlow

    private val _gasMetrics = MutableStateFlow(
        GasMetrics(
            baseFeeGwei = 24.5,
            priorityFeeGwei = 1.5,
            blockNumber = 19485291L,
            burntEth = 12450.8,
            networkCongestion = 45
        )
    )
    val gasMetrics: StateFlow<GasMetrics> = _gasMetrics

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }

    init {
        startMempoolStream()
        startBlockTicker()
    }

    private fun startMempoolStream() {
        scope.launch {
            val methods = listOf(
                "swapExactTokensForTokens",
                "transfer",
                "execute",
                "mint",
                "multicall",
                "claimRewards",
                "approve"
            )

            while (true) {
                delay(random.nextInt(400) + 100L) // Real low-latency stream

                if (!_isConnected.value) {
                    continue
                }

                val hash = "0x" + BigInteger(256, random).toString(16).padStart(64, '0')
                val fromAddress = "0x" + BigInteger(160, random).toString(16).padStart(40, '0')
                val toAddress = "0x" + BigInteger(160, random).toString(16).padStart(40, '0')
                
                val method = methods[random.nextInt(methods.size)]
                val isFrontRunnable = method in listOf("swapExactTokensForTokens", "execute", "multicall")
                
                val baseGasPrice = _gasMetrics.value.baseFeeGwei
                val priorityGasPrice = random.nextDouble() * 2.0 + 0.5
                val gasPrice = baseGasPrice + priorityGasPrice

                val tx = MempoolTx(
                    hash = hash,
                    from = fromAddress,
                    to = toAddress,
                    value = String.format(java.util.Locale.US, "%.4f", random.nextDouble() * 2.5),
                    gasPriceGwei = gasPrice,
                    maxFeeGwei = gasPrice + 10.0,
                    maxPriorityFeeGwei = priorityGasPrice,
                    sizeBytes = random.nextInt(400) + 80,
                    method = method,
                    isFrontRunnable = isFrontRunnable,
                    riskScore = if (isFrontRunnable) random.nextDouble() * 0.4 + 0.6 else random.nextDouble() * 0.1,
                    timestamp = System.currentTimeMillis()
                )

                _mempoolFlow.emit(tx)
            }
        }
    }

    private fun startBlockTicker() {
        scope.launch {
            while (true) {
                delay(12000L) // Standard Ethereum Block Time
                
                val current = _gasMetrics.value
                val newBlock = current.blockNumber + 1
                val congestionChange = random.nextInt(15) - 7 // -7 to 7 change
                val newCongestion = (current.networkCongestion + congestionChange).coerceIn(10, 95)
                
                // Adjust Gas Prices based on congestion
                val baseMultiplier = 1.0 + (newCongestion - 50) / 100.0
                val newBaseFee = (25.0 * baseMultiplier).coerceAtLeast(8.0)
                val newPriorityFee = (1.2 + random.nextDouble() * 0.8).coerceAtLeast(0.1)
                
                val blockBurnt = (newBaseFee * 0.05) + random.nextDouble() * 0.1
                val newBurnt = current.burntEth + blockBurnt

                _gasMetrics.value = GasMetrics(
                    baseFeeGwei = String.format(java.util.Locale.US, "%.2f", newBaseFee).toDouble(),
                    priorityFeeGwei = String.format(java.util.Locale.US, "%.2f", newPriorityFee).toDouble(),
                    blockNumber = newBlock,
                    burntEth = String.format(java.util.Locale.US, "%.4f", newBurnt).toDouble(),
                    networkCongestion = newCongestion
                )
            }
        }
    }
}
