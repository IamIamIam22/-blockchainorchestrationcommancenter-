package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.crypto.KeyStoreHelper
import kotlinx.coroutines.flow.Flow
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionRecord): Long

    @Update
    suspend fun update(transaction: TransactionRecord)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionRecord>>

    @Query("SELECT * FROM transactions WHERE txHash = :hash LIMIT 1")
    suspend fun getTransactionByHash(hash: String): TransactionRecord?
}

@Dao
interface ContractDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contract: ContractAbi)

    @Delete
    suspend fun delete(contract: ContractAbi)

    @Query("SELECT * FROM contracts ORDER BY timestamp DESC")
    fun getAllContracts(): Flow<List<ContractAbi>>

    @Query("SELECT * FROM contracts WHERE contractAddress = :address LIMIT 1")
    suspend fun getContractByAddress(address: String): ContractAbi?
}

@Database(entities = [TransactionRecord::class, ContractAbi::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun contractDao(): ContractDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "orchestration.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)

                try {
                    // Initialize SQLCipher libraries
                    SQLiteDatabase.loadLibs(context)

                    // Get or generate a secure DB passphrase from KeyStoreHelper
                    var dbPassphrase = KeyStoreHelper.getCredential(context, "db_passphrase")
                    if (dbPassphrase == null) {
                        // Generate a high-entropy passphrase
                        val rawPass = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
                        KeyStoreHelper.saveCredential(context, "db_passphrase", rawPass)
                        dbPassphrase = rawPass
                    }

                    val factory = SupportFactory(dbPassphrase.toByteArray(Charsets.UTF_8))
                    builder.openHelperFactory(factory)
                } catch (e: Throwable) {
                    // Fall back to standard Room SQLite database if SQLCipher fails to load on device
                }

                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }
    }
}
