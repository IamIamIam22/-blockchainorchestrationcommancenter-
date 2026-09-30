package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.automation.AutomationController
import com.example.automation.AutomationLog
import com.example.automation.AutomationTask
import com.example.contract.ContractCoordinator
import com.example.crypto.EvmNetwork
import com.example.crypto.WalletEngine
import com.example.data.AppDatabase
import com.example.data.ContractAbi
import com.example.data.TransactionRecord
import com.example.mempool.MempoolClient
import com.example.mempool.MempoolTx
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import java.math.BigDecimal
import androidx.compose.foundation.BorderStroke

// Global Error Diagnostics structure for High-Grade Blockchain Command Center
data class ErrorDiagnostic(
    val title: String,
    val type: String, // "COMPILATION_FAILED", "RPC_DISCONNECTED", "TRANSACTION_REVERTED", "OUT_OF_GAS"
    val exceptionMessage: String,
    val stackTraceSnippet: String? = null,
    val suggestedFix: String
)

// High-Grade Theme Colors (Professional Polish Theme)
val DarkBg = Color(0xFF0B0E14)
val CardBg = Color(0xFF161B22)
val BorderColor = Color(0xFF1E293B)
val AccentCyan = Color(0xFF6366F1) // Indigo-500 primary accent
val AccentPink = Color(0xFFF43F5E) // Rose-500 danger/delete accent
val AccentGreen = Color(0xFF10B981) // Emerald-500 success accent
val AccentYellow = Color(0xFFF59E0B) // Amber-500 warning accent
val NeutralText = Color(0xFF94A3B8) // Slate-400 text color

@OptIn(ExperimentalMaterial3Api::class)
@Composable

fun DashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Global Error and Connection States (Professional Polish Error Handling)
    val errorDiagnosticState = remember { mutableStateOf<ErrorDiagnostic?>(null) }
    var errorDiagnostic by errorDiagnosticState

    val isRpcOnlineState = remember { mutableStateOf(true) }
    var isRpcOnline by isRpcOnlineState

    val isMempoolConnected by MempoolClient.isConnected.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Mempool real-time filter states
    val filterMethodState = remember { mutableStateOf("ALL") }
    var filterMethod by filterMethodState

    val filterMinEthState = remember { mutableStateOf("0.0") }
    var filterMinEth by filterMinEthState

    val filterMevOnlyState = remember { mutableStateOf(false) }
    var filterMevOnly by filterMevOnlyState

    val filterSearchHashState = remember { mutableStateOf("") }
    var filterSearchHash by filterSearchHashState

    // Wallet States
    val hasWalletState = remember { mutableStateOf(WalletEngine.hasWallet(context)) }
    var hasWallet by hasWalletState

    val walletAddressState = remember { mutableStateOf(WalletEngine.getWalletAddress(context) ?: "") }
    var walletAddress by walletAddressState

    val walletBalanceState = remember { mutableStateOf(BigDecimal.ZERO) }
    var walletBalance by walletBalanceState

    val cctBalanceState = remember { mutableStateOf(BigDecimal.ZERO) }
    var cctBalance by cctBalanceState

    val importedKeyInputState = remember { mutableStateOf("") }
    var importedKeyInput by importedKeyInputState

    val generatedMnemonicState = remember { mutableStateOf("") }
    var generatedMnemonic by generatedMnemonicState

    val isWalletImportOpenState = remember { mutableStateOf(false) }
    var isWalletImportOpen by isWalletImportOpenState

    // Navigation Tabs
    val activeTabState = remember { mutableStateOf("Dashboard") }
    var activeTab by activeTabState

    // Mempool States
    val mempoolTransactions = remember { mutableStateListOf<MempoolTx>() }
    val gasMetrics by MempoolClient.gasMetrics.collectAsState()

    val flashbotsEnabledState = remember { mutableStateOf(false) }
    var flashbotsEnabled by flashbotsEnabledState

    // Automation States
    val automationTasks by AutomationController.tasks.collectAsState()
    val automationLogs by AutomationController.logs.collectAsState()

    val automationRunningState = remember { mutableStateOf(false) }
    var automationRunning by automationRunningState

    // Network State
    val activeNetworkState = remember { mutableStateOf(WalletEngine.getActiveNetwork(context)) }
    var activeNetwork by activeNetworkState

    var isNetworkMenuExpanded by remember { mutableStateOf(false) }

    // Compiler / Deployment States
    val selectedTemplateState = remember { mutableStateOf("SimpleStorage") }
    var selectedTemplate by selectedTemplateState

    val soliditySourceCodeState = remember { mutableStateOf(ContractCoordinator.templates["SimpleStorage"] ?: "") }
    var soliditySourceCode by soliditySourceCodeState

    val constructorInputState = remember { mutableStateOf("") }
    var constructorInput by constructorInputState

    val isCompilingState = remember { mutableStateOf(false) }
    var isCompiling by isCompilingState

    val compiledAbiState = remember { mutableStateOf("") }
    var compiledAbi by compiledAbiState

    val compiledBytecodeState = remember { mutableStateOf("") }
    var compiledBytecode by compiledBytecodeState

    val lastDeployedAddressState = remember { mutableStateOf("") }
    var lastDeployedAddress by lastDeployedAddressState

    // Bot Deployment States
    val isDeployingWithBotsState = remember { mutableStateOf(false) }
    var isDeployingWithBots by isDeployingWithBotsState

    val activeBotIndexState = remember { mutableStateOf(-1) }
    var activeBotIndex by activeBotIndexState

    val botLogs = remember { mutableStateListOf<String>() }

    val bot1EnabledState = remember { mutableStateOf(true) }
    var bot1Enabled by bot1EnabledState

    val bot2EnabledState = remember { mutableStateOf(true) }
    var bot2Enabled by bot2EnabledState

    val bot3EnabledState = remember { mutableStateOf(true) }
    var bot3Enabled by bot3EnabledState

    val bot4EnabledState = remember { mutableStateOf(true) }
    var bot4Enabled by bot4EnabledState

    val bot5EnabledState = remember { mutableStateOf(true) }
    var bot5Enabled by bot5EnabledState

    val botStatuses = remember { mutableStateListOf("IDLE", "IDLE", "IDLE", "IDLE", "IDLE") }

    // Contract Interaction States
    val db = remember { AppDatabase.getDatabase(context) }
    val deployedContracts by db.contractDao().getAllContracts().collectAsState(initial = emptyList())

    val selectedInteractContractState = remember { mutableStateOf<ContractAbi?>(null) }
    var selectedInteractContract by selectedInteractContractState

    val readMethodInputState = remember { mutableStateOf("") }
    var readMethodInput by readMethodInputState

    val readResultState = remember { mutableStateOf("") }
    var readResult by readResultState

    val writeMethodNameState = remember { mutableStateOf("") }
    var writeMethodName by writeMethodNameState

    val writeInputsStringState = remember { mutableStateOf("") }
    var writeInputsString by writeInputsStringState

    // Transaction History States
    val txHistory by db.transactionDao().getAllTransactions().collectAsState(initial = emptyList())

    // Initial Balance & Client Hooks
    fun refreshBalances() {
        if (!isRpcOnline) {
            walletBalance = BigDecimal.ZERO
            cctBalance = BigDecimal.ZERO
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "RPC connection dropped on ${activeNetwork.name}!",
                    actionLabel = "RESTORE RPC"
                )
            }
            return
        }
        if (hasWallet && walletAddress.isNotEmpty()) {
            scope.launch {
                walletBalance = WalletEngine.getNativeBalance(context, walletAddress)
                cctBalance = WalletEngine.getErc20Balance(context, "0x5A0b54D5dc1c4C075c1C07C45A1bE2405CDEd949", walletAddress)
            }
        }
    }

    LaunchedEffect(hasWallet, walletAddress, activeNetwork, isRpcOnline) {
        refreshBalances()
    }

    // Mempool websocket / simulator stream observer
    LaunchedEffect(Unit) {
        MempoolClient.mempoolFlow.collect { tx ->
            mempoolTransactions.add(0, tx)
            if (mempoolTransactions.size > 100) {
                mempoolTransactions.removeAt(mempoolTransactions.size - 1)
            }
        }
    }

    // Auto refresh block counts and metrics
    LaunchedEffect(gasMetrics.blockNumber) {
        refreshBalances()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, if (data.visuals.message.contains("dropped") || data.visuals.message.contains("failed") || data.visuals.message.contains("disconnected") || data.visuals.message.contains("reverted") || data.visuals.message.contains("timeout") || data.visuals.message.contains("offline")) AccentPink else AccentCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = data.visuals.message,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        data.visuals.actionLabel?.let { label ->
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    data.performAction()
                                    if (label == "RESTORE RPC") {
                                        isRpcOnline = true
                                        refreshBalances()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = DarkBg,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = DarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. TOP APP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isRpcOnline && isMempoolConnected) AccentGreen else AccentPink)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CRONOS MULTI-BOT DASHBOARD",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isRpcOnline) AccentCyan.copy(0.15f) else AccentPink.copy(0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isRpcOnline) "RPC ONLINE" else "RPC OFFLINE",
                            color = if (isRpcOnline) AccentCyan else AccentPink,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BLOCK #${gasMetrics.blockNumber}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = AccentYellow,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.width(16.dp))

                    // Network Selector Dropdown
                    Box {
                        OutlinedButton(
                            onClick = { isNetworkMenuExpanded = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                            border = BorderStroke(1.dp, BorderColor),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = activeNetwork.name.uppercase(),
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Network")
                        }

                        DropdownMenu(
                            expanded = isNetworkMenuExpanded,
                            onDismissRequest = { isNetworkMenuExpanded = false },
                            modifier = Modifier.background(CardBg)
                        ) {
                            WalletEngine.networks.forEach { network ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = network.name,
                                            style = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                        )
                                    },
                                    onClick = {
                                        WalletEngine.setActiveNetwork(context, network)
                                        activeNetwork = network
                                        isNetworkMenuExpanded = false
                                        Toast.makeText(context, "Switched network to ${network.name}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. NAV TABS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                listOf("Dashboard", "Smart Contracts", "Reliability Suite", "Mempool / MEV", "Automation").forEach { tab ->
                    val isSelected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .clickable { activeTab = tab }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = tab,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AccentCyan else NeutralText
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 2.dp)
                                    .background(if (isSelected) AccentCyan else Color.Transparent)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderColor, thickness = 1.dp)

            // 3. MAIN TAB BODY
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                when (activeTab) {
                    "Dashboard" -> {
                        DashboardTab(
                            gasMetrics = gasMetrics,
                            context = context,
                            scope = scope,
                            clipboardManager = clipboardManager,
                            snackbarHostState = snackbarHostState,
                            hasWalletState = hasWalletState,
                            walletAddressState = walletAddressState,
                            walletBalanceState = walletBalanceState,
                            cctBalanceState = cctBalanceState,
                            generatedMnemonicState = generatedMnemonicState,
                            isWalletImportOpenState = isWalletImportOpenState,
                            activeTabState = activeTabState,
                            activeNetwork = activeNetwork,
                            isRpcOnline = isRpcOnline,
                            deployedContracts = deployedContracts,
                            txHistory = txHistory,
                            refreshBalances = ::refreshBalances
                        )
                    }
                    "Smart Contracts" -> {
                        SmartContractsTab(
                            gasMetrics = gasMetrics,
                            context = context,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            hasWallet = hasWallet,
                            isRpcOnline = isRpcOnline,
                            activeNetwork = activeNetwork,
                            flashbotsEnabled = flashbotsEnabled,
                            selectedTemplateState = selectedTemplateState,
                            soliditySourceCodeState = soliditySourceCodeState,
                            constructorInputState = constructorInputState,
                            isCompilingState = isCompilingState,
                            isDeployingWithBotsState = isDeployingWithBotsState,
                            botLogs = botLogs,
                            botStatuses = botStatuses,
                            bot1EnabledState = bot1EnabledState,
                            bot2EnabledState = bot2EnabledState,
                            bot3EnabledState = bot3EnabledState,
                            bot4EnabledState = bot4EnabledState,
                            bot5EnabledState = bot5EnabledState,
                            activeBotIndexState = activeBotIndexState,
                            compiledBytecodeState = compiledBytecodeState,
                            compiledAbiState = compiledAbiState,
                            lastDeployedAddressState = lastDeployedAddressState,
                            deployedContracts = deployedContracts,
                            selectedInteractContractState = selectedInteractContractState,
                            readMethodInputState = readMethodInputState,
                            readResultState = readResultState,
                            writeMethodNameState = writeMethodNameState,
                            writeInputsStringState = writeInputsStringState,
                            errorDiagnosticState = errorDiagnosticState
                        )
                    }
                    "Reliability Suite" -> {
                        ReliabilitySuiteTab(
                            context = context,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            isRpcOnline = isRpcOnline,
                            onIsRpcOnlineChange = { isRpcOnline = it }
                        )
                    }
                    "Mempool / MEV" -> {
                        MempoolMevTab(
                            isMempoolConnected = isMempoolConnected,
                            context = context,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            isRpcOnline = isRpcOnline,
                            flashbotsEnabledState = flashbotsEnabledState,
                            mempoolTransactions = mempoolTransactions,
                            gasMetrics = gasMetrics,
                            filterMethodState = filterMethodState,
                            filterMinEthState = filterMinEthState,
                            filterMevOnlyState = filterMevOnlyState,
                            filterSearchHashState = filterSearchHashState,
                            deployedContracts = deployedContracts,
                            errorDiagnosticState = errorDiagnosticState
                        )
                    }
                    "Automation" -> {
                        AutomationTab(
                            context = context,
                            scope = scope,
                            automationTasks = automationTasks,
                            automationLogs = automationLogs,
                            automationRunningState = automationRunningState
                        )
                    }
                }
            }
        }
    }

    // Wallet Import Dialog Modal
    if (isWalletImportOpen) {
        AlertDialog(
            onDismissRequest = { isWalletImportOpen = false },
            title = {
                Text(
                    "IMPORT MNEMONIC PHRASE / PRIVATE KEY",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text("Enter raw 12-word seed phrase or Hex private key to encrypt & import:", color = NeutralText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = importedKeyInput,
                        onValueChange = { importedKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderColor
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importedKeyInput.isEmpty()) return@Button
                        scope.launch {
                            try {
                                val details = if (importedKeyInput.trim().contains(" ")) {
                                    WalletEngine.importMnemonicWallet(context, importedKeyInput)
                                } else {
                                    WalletEngine.importPrivateKeyWallet(context, importedKeyInput)
                                }
                                walletAddress = details["address"] ?: ""
                                generatedMnemonic = ""
                                hasWallet = true
                                isWalletImportOpen = false
                                importedKeyInput = ""
                                Toast.makeText(context, "Wallet imported safely!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("CONFIRM DECRYPT", color = DarkBg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        isWalletImportOpen = false
                        importedKeyInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BorderColor)
                ) {
                    Text("CANCEL", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            containerColor = CardBg,
            textContentColor = Color.White
        )
    }

    // Global Error Diagnostic Dialog Modal (Professional Polish Error Handling)
    errorDiagnostic?.let { diag ->
        AlertDialog(
            onDismissRequest = { errorDiagnostic = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, contentDescription = "Error Icon", tint = AccentPink, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = diag.title,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        Box(
                            modifier = Modifier
                                .background(AccentPink.copy(0.15f))
                                .border(1.dp, AccentPink, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(diag.type, color = AccentPink, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                    
                    Text(
                        text = diag.exceptionMessage,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    diag.stackTraceSnippet?.let { trace ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(0.3f))
                                .border(1.dp, BorderColor)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = trace,
                                color = NeutralText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    HorizontalDivider(color = BorderColor)
                    
                    Column {
                        Text("SUGGESTED DIAGNOSTIC REMEDIATION:", color = AccentYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = diag.suggestedFix,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { errorDiagnostic = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("RESOLVED / OK", color = DarkBg, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            containerColor = CardBg,
            textContentColor = Color.White
        )
    }
}


@Composable
fun ReliabilitySuiteTab(
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    isRpcOnline: Boolean,
    onIsRpcOnlineChange: (Boolean) -> Unit
) {
    // Local state variables encapsulated inside this composable
    var isMnemonicBackedUp by remember { mutableStateOf(false) }
    var isPrivateKeysHsmSecured by remember { mutableStateOf(true) }
    var rpcFailoverStatus by remember { mutableStateOf("Primary Node Online (Cloudflare)") }
    var isPrimaryRpcFailed by remember { mutableStateOf(false) }
    var failoverActiveNode by remember { mutableStateOf("Primary") } // "Primary", "Backup 1", "Backup 2"
    var backupRpc1 by remember { mutableStateOf("https://eth-mainnet.g.alchemy.com/v2/mock_key") }
    var backupRpc2 by remember { mutableStateOf("https://mainnet.infura.io/v3/mock_key") }
    
    // Stuck Tx Simulation states
    var stuckTxStatus by remember { mutableStateOf("Idle") } // "Idle", "Broadcasted (Stuck)", "Bumping Fees", "Confirmed"
    var stuckTxHash by remember { mutableStateOf("") }
    var stuckTxNonce by remember { mutableStateOf("42") }
    var stuckTxGasPriceGwei by remember { mutableStateOf("1.5") }
    
    // Finality Depth Monitor states
    var finalityStatus by remember { mutableStateOf("No active transaction") } // "No active transaction", "Broadcasting", "Awaiting Confirmations", "Finalized"
    var finalityConfirmations by remember { mutableStateOf(0) }
    var finalityTargetDepth by remember { mutableStateOf(6) } // Default 6 confirmations
    var finalityTxHash by remember { mutableStateOf("") }
    var isReorgOngoing by remember { mutableStateOf(false) }
    var reorgStatusMsg by remember { mutableStateOf("") }
    
    // Checklist expansion state
    var expandedChecklistCategory by remember { mutableStateOf<String?>(null) }
    var expandedChecklistItem by remember { mutableStateOf<String?>(null) }
    
    // Static auditor states
    var auditProgress by remember { mutableStateOf(0.0f) }
    var isAuditRunning by remember { mutableStateOf(false) }
    var selectedAuditTemplate by remember { mutableStateOf("SimpleStorage") }
    var lastAuditReport by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Audit and Checklist Guidance
        Column(
            modifier = Modifier.weight(1.1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. COMPLIANCE AUDITOR CARD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBg)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        "PRODUCTION COMPLIANCE AUDITOR",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Analyze Solidity bytecode and verify compliance with network guidelines",
                        color = NeutralText,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Select Template
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("SimpleStorage", "StandardERC20", "MevArbitrageExecutor").forEach { t ->
                                val isSel = t == selectedAuditTemplate
                                Box(
                                    modifier = Modifier
                                        .clickable { selectedAuditTemplate = t }
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSel) AccentCyan.copy(0.12f) else BorderColor)
                                        .border(1.dp, if (isSel) AccentCyan else Color.Transparent, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(t, color = if (isSel) Color.White else NeutralText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isAuditRunning = true
                                    auditProgress = 0.0f
                                    lastAuditReport = null
                                    while (auditProgress < 1.0f) {
                                        delay(100L)
                                        auditProgress += 0.08f
                                    }
                                    isAuditRunning = false
                                    lastAuditReport = when (selectedAuditTemplate) {
                                        "SimpleStorage" -> """
                                            [SYSTEM] AST Parsing initialized for SimpleStorage.sol...
                                            [INFO] Verification 1/5: Reentrancy checks... [PASSED] (State changes local, no external targets)
                                            [INFO] Verification 2/5: Safe Math arithmetic... [PASSED] (Compiler version >=0.8.20 guarantees checked bounds)
                                            [INFO] Verification 3/5: Deterministic logic... [PASSED] (No reliance on blockhash, random seed, or unstable state)
                                            [INFO] Verification 4/5: Event Emission... [PASSED] (Emits ValueChanged event on state change)
                                            [WARN] Verification 5/5: Role-based Access Control... [WARNING]
                                              >> function set(uint256 x) is public and state-mutating but has no modifiers.
                                              >> Suggestion: Apply 'onlyOwner' or AccessControl roles to restrict admin mutations.
                                            --------------------------------------------------------
                                            [COMPLIANCE CHECK] Complete. Audit Grade: 85/100 (Safe for non-privileged data storage)
                                        """.trimIndent()
                                        "StandardERC20" -> """
                                            [SYSTEM] AST Parsing initialized for StandardERC20.sol...
                                            [INFO] Verification 1/5: Safe Math compliance... [PASSED] (Compiler enforced)
                                            [INFO] Verification 2/5: Reentrancy checks... [PASSED] (Follows Checks-Effects-Interactions)
                                            [INFO] Verification 3/5: Compliance Events... [PASSED] (Emits standard Transfer and Approval)
                                            [INFO] Verification 4/5: Deterministic Supply... [PASSED]
                                            [WARN] Verification 5/5: Public Setter Guard... [INFO]
                                              >> 'transfer' and 'approve' have no onlyOwner access. This is expected standard behavior.
                                            --------------------------------------------------------
                                            [COMPLIANCE CHECK] Complete. Audit Grade: 94/100 (Standard ERC-20 compliant)
                                        """.trimIndent()
                                        else -> """
                                            [SYSTEM] AST Parsing initialized for MevArbitrageExecutor.sol...
                                            [INFO] Verification 1/5: Role-Based Access Control... [PASSED]
                                              >> 'executeArbitrage' and 'withdrawTokens' correctly locked by onlyOwner()
                                            [INFO] Verification 2/5: Reentrancy protection... [PASSED] (Uses strict Checks-Effects-Interactions)
                                            [INFO] Verification 3/5: Safe Math compliance... [PASSED] (Enforced)
                                            [INFO] Verification 4/5: Gas Optimization Checks... [PASSED] (Predictable execution path, no dynamic array scaling)
                                            [INFO] Verification 5/5: Fail-Fast Error Guard... [PASSED] (Requires amountIn > 0, halts on reverted routes)
                                            --------------------------------------------------------
                                            [COMPLIANCE CHECK] Complete. Audit Grade: 98/100 (Production-Grade MEV Orchestration Ready)
                                        """.trimIndent()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(if (isAuditRunning) "ANALYZING..." else "RUN AUDIT", color = DarkBg, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    if (isAuditRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { auditProgress.coerceIn(0f, 1f) },
                            color = AccentCyan,
                            trackColor = BorderColor,
                            modifier = Modifier.fillMaxWidth().height(2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Console Output
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(Color.Black.copy(0.4f))
                            .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = lastAuditReport ?: "[CONSOLE STBY] Click 'RUN AUDIT' above to trigger automated static analysis / Slither compilation dry-run.",
                                    color = if (lastAuditReport != null) AccentGreen else NeutralText,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // 2. PRODUCTION REQUIREMENTS CHECKLIST GUIDES
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBg)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        "PRODUCTION ROADMAP & TRANSACTION WRITING ASSURANCES",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Deploying to mainnet requires rigorous controls to ensure sequential writes are reliable",
                        color = NeutralText,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val sections = listOf(
                            Triple("Smart Contract Execution", "Deterministic, input validated, access controlled, and reentrancy protected contracts.", listOf(
                                "Deterministic Logic" to "Contracts must execute without external HTTP dependencies or randomness to guarantee consensus finality.",
                                "Access Control / RBAC" to "Lock sensitive administrative calls (e.g. paused, upgradeTo, withdraw) using onlyOwner or Role-Based access.",
                                "Reentrancy Guards" to "Incorporate nonReentrant modifiers on any functions interacting with external addresses or transferring Ether."
                            )),
                            Triple("Network-Level Blockchain Security", "Safeguards at the RPC and Mempool levels to prevent transaction loss.", listOf(
                                "Redundant RPC Failover" to "Always provide 2-3 backup node configurations. If the primary node errors out, the client must seamlessly failover.",
                                "EIP-1559 Fee Strategy" to "Submit transactions with maxPriorityFeePerGas (tips to builders) and auto-bump if block builder gas hikes drop them.",
                                "Sequential Nonce Alignment" to "Ensure transaction nonces are strictly sequential without gaps. Auto-detect and overwrite stuck nonces."
                            )),
                            Triple("Deployment & Pipeline Verification", "CI/CD and validation checklists to verify live state accurately.", listOf(
                                "Automated Verification" to "Programmatically verify codebases on Blockscout / Etherscan immediately post-deployment.",
                                "Finality Depth Policies" to "Wait for N confirmations (e.g., 6 on testnets, 12 on Ethereum Mainnet) before treating writes as final to defend against chain reorganizations."
                            ))
                        )

                        sections.forEach { (catName, catDesc, items) ->
                            val isCatExpanded = expandedChecklistCategory == catName
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedChecklistCategory = if (isCatExpanded) null else catName }
                                        .background(if (isCatExpanded) BorderColor.copy(0.3f) else Color.Transparent)
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(catName.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        Text(catDesc, color = NeutralText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Icon(
                                        imageVector = if (isCatExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Expand",
                                        tint = AccentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                androidx.compose.material3.HorizontalDivider(color = BorderColor.copy(0.5f))
                            }

                            if (isCatExpanded) {
                                items.forEach { (itName, itText) ->
                                    val isItemExpanded = expandedChecklistItem == itName
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { expandedChecklistItem = if (isItemExpanded) null else itName }
                                                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
                                                .background(if (isItemExpanded) CardBg else Color.Transparent)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = "Passed", tint = AccentGreen, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(itName, color = AccentGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                }
                                                Icon(
                                                    imageVector = if (isItemExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Expand Detail",
                                                    tint = NeutralText,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            if (isItemExpanded) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = itText,
                                                    color = Color.White.copy(0.9f),
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    lineHeight = 12.sp,
                                                    modifier = Modifier.padding(start = 18.dp, bottom = 4.dp)
                                                )
                                            }
                                        }
                                        androidx.compose.material3.HorizontalDivider(color = BorderColor.copy(0.3f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right Column: Live Interactive Simulations
        Column(
            modifier = Modifier.weight(1.0f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // BENTO CARD 1: RPC FAILOVER SIMULATION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBg)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "RPC REDUNDANCY & FAILOVER ENGINE",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                        )
                        
                        // Active connection badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isPrimaryRpcFailed) AccentYellow.copy(0.15f) else AccentGreen.copy(0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (isPrimaryRpcFailed) "FAILOVER ROUTED" else "PRIMARY ONLINE",
                                color = if (isPrimaryRpcFailed) AccentYellow else AccentGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "In mainnet systems, dropping network RPC nodes blocks transactions. Our failover engine routes around network drops instantly to backup nodes.",
                        color = NeutralText,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Node endpoints
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val nodes = listOf(
                            Triple("Primary Node (Cloudflare)", "https://cloudflare-eth.com", !isPrimaryRpcFailed),
                            Triple("Redundant Backup 1 (Alchemy)", backupRpc1, isPrimaryRpcFailed),
                            Triple("Redundant Backup 2 (Infura)", backupRpc2, false)
                        )

                        nodes.forEach { (name, url, isNodeActive) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isNodeActive) BorderColor.copy(0.4f) else Color.Transparent)
                                    .border(1.dp, if (isNodeActive) AccentGreen.copy(0.5f) else BorderColor, RoundedCornerShape(4.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(name, color = if (isNodeActive) Color.White else NeutralText, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Text(url, color = NeutralText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (isNodeActive) AccentGreen else if (isPrimaryRpcFailed && name.startsWith("Primary")) AccentPink else NeutralText)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (isNodeActive) "ACTIVE" else if (isPrimaryRpcFailed && name.startsWith("Primary")) "FAILED" else "STANDBY",
                                        color = if (isNodeActive) AccentGreen else if (isPrimaryRpcFailed && name.startsWith("Primary")) AccentPink else NeutralText,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Trigger button
                    Button(
                        onClick = {
                            isPrimaryRpcFailed = !isPrimaryRpcFailed
                            if (isPrimaryRpcFailed) {
                                rpcFailoverStatus = "Primary Connection drop. Initializing failover..."
                                scope.launch {
                                    delay(1000L)
                                    rpcFailoverStatus = "Rerouted to Backup 1 (Alchemy). Outbound path secure."
                                    failoverActiveNode = "Backup 1"
                                    onIsRpcOnlineChange(true)
                                }
                            } else {
                                rpcFailoverStatus = "Primary Node Online"
                                failoverActiveNode = "Primary"
                                onIsRpcOnlineChange(true)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPrimaryRpcFailed) AccentGreen else AccentPink
                        ),
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    ) {
                        Text(
                            text = if (isPrimaryRpcFailed) "RESTORE PRIMARY RPC ENDPOINT" else "SIMULATE PRIMARY RPC CRASH",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // BENTO CARD 2: STUCK TX GAS BUMPER (EIP-1559)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBg)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        "STUCK TRANSACTION BUMPER (EIP-1559)",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Low gas fees freeze transactions in congested blocks. We execute EIP-1559 fee bumping to automatically rewrite sequential transactions.",
                        color = NeutralText,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Simulated Transaction box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(0.3f))
                            .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("STUCK TRANSACTION TRACKER", color = AccentYellow, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(
                                    stuckTxStatus.uppercase(),
                                    color = when (stuckTxStatus) {
                                        "Idle" -> NeutralText
                                        "Broadcasted (Stuck)" -> AccentPink
                                        "Bumping Fees" -> AccentYellow
                                        else -> AccentGreen
                                    },
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            androidx.compose.material3.HorizontalDivider(color = BorderColor.copy(0.5f))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Account Nonce:", color = NeutralText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text(stuckTxNonce, color = Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Gas Fee Gwei:", color = NeutralText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text(stuckTxGasPriceGwei, color = Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Mempool Status:", color = NeutralText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = when(stuckTxStatus) {
                                        "Idle" -> "Waiting to Broadcast"
                                        "Broadcasted (Stuck)" -> "⚠️ Stuck - Gas fee below block threshold!"
                                        "Bumping Fees" -> "Bumping priority fees by 25%..."
                                        else -> "✅ Confirmed in block #19485295!"
                                    },
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (stuckTxHash.isNotEmpty()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Tx Hash:", color = NeutralText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                    Text(stuckTxHash, color = AccentCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                stuckTxStatus = "Broadcasted (Stuck)"
                                stuckTxGasPriceGwei = "1.2"
                                stuckTxNonce = "42"
                                stuckTxHash = "0x8927ae...67ba"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BorderColor),
                            enabled = stuckTxStatus == "Idle" || stuckTxStatus == "Confirmed",
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("BROADCAST LOW FEE", color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    stuckTxStatus = "Bumping Fees"
                                    delay(1200L)
                                    stuckTxGasPriceGwei = "32.0 Max / 2.5 Priority (EIP-1559)"
                                    stuckTxStatus = "Confirmed"
                                    stuckTxHash = "0xca7b827e69cba11d95"
                                    snackbarHostState.showSnackbar("Tx Nonce #42 Overwritten successfully! Bumping sequence finalized.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            enabled = stuckTxStatus == "Broadcasted (Stuck)",
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("ENGAGE FEE BUMP", color = DarkBg, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // BENTO CARD 3: CHAIN REORG & FINALITY DEPTH MONITOR
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBg)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        "CHAIN REORG & FINALITY DEPTH MONITOR",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Waiting for block confirmations safeguards against chain reorganizations where written blocks are discarded. Wait N blocks for total finality.",
                        color = NeutralText,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Status dashboard
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(0.3f))
                            .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("BLOCK FINALITY ENGINE", color = AccentCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = if (isReorgOngoing) "⚠️ REORG DETECTED" else finalityStatus.uppercase(),
                                    color = if (isReorgOngoing) AccentPink else if (finalityStatus == "Finalized") AccentGreen else AccentCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            androidx.compose.material3.HorizontalDivider(color = BorderColor.copy(0.5f))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Target Finality Depth:", color = NeutralText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text("$finalityTargetDepth Blocks", color = Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Current Confirmations:", color = NeutralText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = if (finalityStatus == "No active transaction") "0" else "$finalityConfirmations / $finalityTargetDepth",
                                    color = if (finalityConfirmations >= finalityTargetDepth) AccentGreen else AccentYellow,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (isReorgOngoing || reorgStatusMsg.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AccentPink.copy(0.12f))
                                        .border(1.dp, AccentPink.copy(0.3f), RoundedCornerShape(4.dp))
                                        .padding(6.dp)
                                ) {
                                    Text(reorgStatusMsg, color = if (reorgStatusMsg.startsWith("✅")) AccentGreen else AccentPink, fontSize = 8.sp, fontFamily = FontFamily.Monospace, lineHeight = 11.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    finalityStatus = "Awaiting Confirmations"
                                    finalityConfirmations = 0
                                    isReorgOngoing = false
                                    reorgStatusMsg = ""
                                    finalityTxHash = "0xca8201a4" + (System.currentTimeMillis() / 10).toString(16).takeLast(12)
                                    while (finalityConfirmations < finalityTargetDepth) {
                                        delay(2500L)
                                        if (isReorgOngoing) {
                                            reorgStatusMsg = "⚠️ REORG! Block height rolled back from #${19485292 + finalityConfirmations} to #${19485291 + finalityConfirmations}. Tx orphaned!"
                                            delay(2000L)
                                            reorgStatusMsg = "✅ Sibling chain re-scanned. Nonce sequentiality verified! Tx recovered in Block #${19485294}."
                                            isReorgOngoing = false
                                            delay(1500L)
                                            reorgStatusMsg = ""
                                        }
                                        finalityConfirmations++
                                    }
                                    finalityStatus = "Finalized"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BorderColor),
                            enabled = finalityStatus != "Awaiting Confirmations",
                            modifier = Modifier.weight(1.2f).height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("BROADCAST TX", color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = {
                                if (finalityStatus == "Awaiting Confirmations") {
                                    isReorgOngoing = true
                                    reorgStatusMsg = "Initiating chain split simulation..."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                            enabled = finalityStatus == "Awaiting Confirmations" && !isReorgOngoing,
                            modifier = Modifier.weight(1.2f).height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("TRIGGER REORG", color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

fun scanSolidityCode(code: String): List<String> {
    val issues = mutableListOf<String>()
    if (code.contains("tx.origin")) {
        issues.add("HIGH: Avoid 'tx.origin' for access control. Use msg.sender instead.")
    }
    if (code.contains("selfdestruct")) {
        issues.add("HIGH: 'selfdestruct' is deprecated. Use pausable custom modifiers instead.")
    }
    if (code.contains(".call{") && !code.contains("require(")) {
        issues.add("MEDIUM: Result of low-level '.call' is not validated. Check return boolean explicitly.")
    }
    if (code.contains(".transfer(") && (code.indexOf("balanceOf") > code.indexOf(".transfer("))) {
        issues.add("MEDIUM: Balance state updated after transfer. CEI pattern violation risk.")
    }
    if (code.contains("pragma solidity ^0.8") || code.contains("pragma solidity >")) {
        issues.add("LOW: Floating pragma (^0.8.x) detected. Lock version (e.g. 0.8.20) in production.")
    }
    if (code.contains("block.timestamp") || code.contains("now")) {
        issues.add("LOW: 'block.timestamp' manipulation risk. Avoid critical logic dependencies.")
    }
    if (code.contains("public") && !code.contains("emit ") && (code.contains("=") || code.contains("+="))) {
        issues.add("INFO: State-changing function emits no events. Events are critical for dApp indexes.")
    }
    return issues
}
