package com.example.crypto

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import java.math.BigDecimal
import java.math.BigInteger
import java.security.SecureRandom

data class EvmNetwork(
    val name: String,
    val chainId: Long,
    val rpcUrl: String,
    val wsUrl: String,
    val flashbotsUrl: String,
    val currencySymbol: String,
    val explorerUrl: String
)

object WalletEngine {
    private const val ACTIVE_ADDRESS_KEY = "active_wallet_address"
    private const val ACTIVE_MNEMONIC_KEY = "active_wallet_mnemonic"
    private const val ACTIVE_PRIVATE_KEY = "active_wallet_private_key"
    private const val ACTIVE_NETWORK_ID_KEY = "active_network_id"

    val networks = listOf(
        EvmNetwork(
            name = "Ethereum Mainnet",
            chainId = 1L,
            rpcUrl = "https://cloudflare-eth.com",
            wsUrl = "wss://mainnet.infura.io/ws/v3/your_project_id",
            flashbotsUrl = "https://rpc.flashbots.net",
            currencySymbol = "ETH",
            explorerUrl = "https://etherscan.io"
        ),
        EvmNetwork(
            name = "Arbitrum One",
            chainId = 42161L,
            rpcUrl = "https://arb1.arbitrum.io/rpc",
            wsUrl = "wss://arb1.arbitrum.io/feed",
            flashbotsUrl = "https://rpc.flashbots.net", // arbitrum can route through builder relay
            currencySymbol = "ETH",
            explorerUrl = "https://arbiscan.io"
        ),
        EvmNetwork(
            name = "Optimism Mainnet",
            chainId = 10L,
            rpcUrl = "https://mainnet.optimism.io",
            wsUrl = "wss://optimism.publicnode.com",
            flashbotsUrl = "https://rpc.flashbots.net",
            currencySymbol = "ETH",
            explorerUrl = "https://optimistic.etherscan.io"
        ),
        EvmNetwork(
            name = "Base Mainnet",
            chainId = 8453L,
            rpcUrl = "https://mainnet.base.org",
            wsUrl = "wss://base.publicnode.com",
            flashbotsUrl = "https://rpc.flashbots.net",
            currencySymbol = "ETH",
            explorerUrl = "https://basescan.org"
        ),
        EvmNetwork(
            name = "Ethereum Sepolia Testnet",
            chainId = 11155111L,
            rpcUrl = "https://rpc.sepolia.org",
            wsUrl = "wss://sepolia.gateway.tenderly.co",
            flashbotsUrl = "https://rpc-sepolia.flashbots.net",
            currencySymbol = "SepoliaETH",
            explorerUrl = "https://sepolia.etherscan.io"
        )
    )

    fun getActiveNetwork(context: Context): EvmNetwork {
        val prefs = context.getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)
        val chainId = prefs.getLong(ACTIVE_NETWORK_ID_KEY, 11155111L) // Default Sepolia testnet for safety
        return networks.find { it.chainId == chainId } ?: networks.last()
    }

    fun setActiveNetwork(context: Context, network: EvmNetwork) {
        val prefs = context.getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong(ACTIVE_NETWORK_ID_KEY, network.chainId).apply()
    }

    fun getWalletAddress(context: Context): String? {
        return KeyStoreHelper.getCredential(context, ACTIVE_ADDRESS_KEY)
    }

    fun getWalletMnemonic(context: Context): String? {
        return KeyStoreHelper.getCredential(context, ACTIVE_MNEMONIC_KEY)
    }

    fun getWalletPrivateKey(context: Context): String? {
        return KeyStoreHelper.getCredential(context, ACTIVE_PRIVATE_KEY)
    }

    fun hasWallet(context: Context): Boolean {
        return getWalletAddress(context) != null
    }

    fun logout(context: Context) {
        KeyStoreHelper.deleteCredential(context, ACTIVE_ADDRESS_KEY)
        KeyStoreHelper.deleteCredential(context, ACTIVE_MNEMONIC_KEY)
        KeyStoreHelper.deleteCredential(context, ACTIVE_PRIVATE_KEY)
    }

    suspend fun generateNewWallet(context: Context): Map<String, String> = withContext(Dispatchers.Default) {
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        val mnemonic = MnemonicUtils.generateMnemonic(entropy)
        
        return@withContext importMnemonicWallet(context, mnemonic)
    }

    suspend fun importMnemonicWallet(context: Context, mnemonic: String): Map<String, String> = withContext(Dispatchers.Default) {
        val cleanMnemonic = mnemonic.trim().lowercase().replace("\\s+".toRegex(), " ")
        if (!MnemonicUtils.validateMnemonic(cleanMnemonic)) {
            throw IllegalArgumentException("Invalid BIP-39 mnemonic phrase")
        }

        val seed = MnemonicUtils.generateSeed(cleanMnemonic, "")
        val masterKey = Bip32ECKeyPair.generateKeyPair(seed)
        
        // BIP-44 path: m/44'/60'/0'/0/0
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            60 or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0,
            0
        )
        val derivedKeyPair = Bip32ECKeyPair.deriveKeyPair(masterKey, path)
        val credentials = Credentials.create(derivedKeyPair)

        val address = credentials.address
        val privateKey = credentials.ecKeyPair.privateKey.toString(16)

        KeyStoreHelper.saveCredential(context, ACTIVE_ADDRESS_KEY, address)
        KeyStoreHelper.saveCredential(context, ACTIVE_MNEMONIC_KEY, cleanMnemonic)
        KeyStoreHelper.saveCredential(context, ACTIVE_PRIVATE_KEY, privateKey)

        return@withContext mapOf(
            "address" to address,
            "privateKey" to privateKey,
            "mnemonic" to cleanMnemonic
        )
    }

    suspend fun importPrivateKeyWallet(context: Context, rawPrivateKey: String): Map<String, String> = withContext(Dispatchers.Default) {
        val cleanKey = rawPrivateKey.trim().removePrefix("0x")
        val credentials = Credentials.create(cleanKey)
        val address = credentials.address

        KeyStoreHelper.saveCredential(context, ACTIVE_ADDRESS_KEY, address)
        KeyStoreHelper.saveCredential(context, ACTIVE_PRIVATE_KEY, cleanKey)
        // No mnemonic was used to import
        KeyStoreHelper.deleteCredential(context, ACTIVE_MNEMONIC_KEY)

        return@withContext mapOf(
            "address" to address,
            "privateKey" to cleanKey,
            "mnemonic" to ""
        )
    }

    suspend fun getNativeBalance(context: Context, address: String): BigDecimal = withContext(Dispatchers.IO) {
        return@withContext try {
            val network = getActiveNetwork(context)
            val web3j = Web3j.build(HttpService(network.rpcUrl))
            val balanceResult = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send()
            val wei = balanceResult.balance ?: BigInteger.ZERO
            BigDecimal(wei).divide(BigDecimal(BigInteger.TEN.pow(18)), 6, java.math.RoundingMode.HALF_UP)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }

    suspend fun getErc20Balance(context: Context, tokenAddress: String, walletAddress: String): BigDecimal = withContext(Dispatchers.IO) {
        return@withContext try {
            val network = getActiveNetwork(context)
            val web3j = Web3j.build(HttpService(network.rpcUrl))
            
            // Standard ERC-20 balanceOf data payload: 0x70a08231 + 32-byte zero-padded address
            val cleanAddress = walletAddress.removePrefix("0x").padStart(64, '0')
            val data = "0x70a08231$cleanAddress"
            
            val transaction = org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                walletAddress,
                tokenAddress,
                data
            )
            val callResult = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send()
            val hexResult = callResult.value ?: "0x0"
            val rawBalance = BigInteger(hexResult.removePrefix("0x"), 16)
            // Defaulting to 18 decimals for display, formatted gracefully
            BigDecimal(rawBalance).divide(BigDecimal(BigInteger.TEN.pow(18)), 4, java.math.RoundingMode.HALF_UP)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }
}
