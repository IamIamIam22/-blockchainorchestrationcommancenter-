package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txHash: String,
    val networkName: String,
    val txType: String, // "DEPLOY", "READ", "WRITE", "AUTOMATION", "FAUCET"
    val senderAddress: String,
    val destinationAddress: String,
    val value: String,
    val gasPrice: String,
    val gasUsed: String,
    val blockNumber: String,
    val timestamp: Long,
    val status: String, // "PENDING", "SUCCESS", "FAILED"
    val error: String? = null,
    val details: String? = null
)

@Entity(tableName = "contracts")
data class ContractAbi(
    @PrimaryKey val contractAddress: String,
    val name: String,
    val networkName: String,
    val abiJson: String,
    val bytecode: String,
    val timestamp: Long
)
