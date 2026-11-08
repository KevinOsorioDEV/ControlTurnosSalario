package com.kmanu.turnos.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.kmanu.turnos.domain.BreakPolicy
import com.kmanu.turnos.domain.Shift
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val id: String,
    val start: String,
    val end: String?,
    @ColumnInfo(name = "scheduled_end") val scheduledEnd: String,
    @ColumnInfo(name = "break_minutes") val breakMinutes: Int,
    @ColumnInfo(name = "compensatory_rest") val compensatoryRest: Boolean,
)

private fun ShiftEntity.toDomain() = Shift(
    id = id,
    start = LocalDateTime.parse(start),
    end = end?.let(LocalDateTime::parse),
    scheduledEnd = LocalDateTime.parse(scheduledEnd),
    breakMinutes = breakMinutes,
    compensatoryRest = compensatoryRest,
)

private fun Shift.toEntity() = ShiftEntity(
    id = id,
    start = start.toString(),
    end = end?.toString(),
    scheduledEnd = scheduledEnd.toString(),
    breakMinutes = breakMinutes,
    compensatoryRest = compensatoryRest,
)

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY start DESC")
    fun observeAll(): Flow<List<ShiftEntity>>

    @Upsert
    suspend fun upsert(shift: ShiftEntity)

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [ShiftEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
}

class ShiftRepository(private val dao: ShiftDao) {
    val shifts: Flow<List<Shift>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun save(shift: Shift) = dao.upsert(shift.toEntity())
    suspend fun delete(id: String) = dao.delete(id)
}

data class UserSettings(
    val monthlySalary: Long = 2_800_000,
    val monthlyAllowance: Long = 249_095,
    val defaultShiftMinutes: Int = 450,
    val breakMinutes: Int = 0,
    val breakOverMinutes: Int = 8 * 60,
) {
    val breakPolicy get() = BreakPolicy(breakMinutes, breakOverMinutes)
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val salaryKey = longPreferencesKey("monthly_salary")
    private val allowanceKey = longPreferencesKey("monthly_allowance")
    private val shiftKey = intPreferencesKey("default_shift_minutes")
    private val breakKey = intPreferencesKey("break_minutes")
    private val breakOverKey = intPreferencesKey("break_over_minutes")

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            monthlySalary = prefs[salaryKey] ?: defaults.monthlySalary,
            monthlyAllowance = prefs[allowanceKey] ?: defaults.monthlyAllowance,
            defaultShiftMinutes = prefs[shiftKey] ?: defaults.defaultShiftMinutes,
            breakMinutes = prefs[breakKey] ?: defaults.breakMinutes,
            breakOverMinutes = prefs[breakOverKey] ?: defaults.breakOverMinutes,
        )
    }

    suspend fun save(value: UserSettings) {
        context.dataStore.edit { prefs ->
            prefs[salaryKey] = value.monthlySalary
            prefs[allowanceKey] = value.monthlyAllowance
            prefs[shiftKey] = value.defaultShiftMinutes
            prefs[breakKey] = value.breakMinutes
            prefs[breakOverKey] = value.breakOverMinutes
        }
    }
}

fun createDatabase(context: Context): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, "turnos.db").build()
