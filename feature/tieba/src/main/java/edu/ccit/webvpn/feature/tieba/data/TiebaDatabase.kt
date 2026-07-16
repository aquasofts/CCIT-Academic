package edu.ccit.webvpn.feature.tieba.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val uid: Long,
    val name: String,
    val nickname: String,
    val bduss: String,
    val tbs: String,
    val portrait: String,
    @ColumnInfo(name = "s_token") val sToken: String,
    val cookie: String,
    val intro: String = "",
    val fans: String = "0",
    val posts: String = "0",
    val concerned: String = "0",
    val zid: String? = null,
    @ColumnInfo(name = "last_update") val lastUpdate: Long = System.currentTimeMillis(),
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM account LIMIT 1")
    fun observe(): Flow<AccountEntity?>

    @Query("SELECT * FROM account LIMIT 1")
    suspend fun get(): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity)

    @Query("DELETE FROM account")
    suspend fun deleteAll()

    @Transaction
    suspend fun replace(account: AccountEntity) {
        deleteAll()
        insert(account)
    }
}

@Database(entities = [AccountEntity::class], version = 1, exportSchema = true)
abstract class TiebaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
}
