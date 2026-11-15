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
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kmanu.turnos.domain.BackupData
import com.kmanu.turnos.domain.BreakPolicy
import com.kmanu.turnos.domain.LegalPay
import com.kmanu.turnos.domain.PayPeriod
import com.kmanu.turnos.domain.PaySettings
import com.kmanu.turnos.domain.SalaryHistory
import com.kmanu.turnos.domain.Shift
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val id: String,
    val start: String,
    val end: String?,
    @ColumnInfo(name = "scheduled_end") val scheduledEnd: String,
    @ColumnInfo(name = "break_minutes") val breakMinutes: Int,
    @ColumnInfo(name = "compensatory_rest") val compensatoryRest: Boolean,
    @ColumnInfo(name = "scheduled_start") val scheduledStart: String?,
    @ColumnInfo(name = "margin_minutes") val marginMinutes: Int,
    @ColumnInfo(name = "extras_authorized") val extrasAuthorized: Boolean,
    @ColumnInfo(name = "authorized_by") val authorizedBy: String?,
    @ColumnInfo(name = "extra_note") val extraNote: String?,
)

private fun ShiftEntity.toDomain() = Shift(
    id = id,
    start = LocalDateTime.parse(start),
    end = end?.let(LocalDateTime::parse),
    scheduledEnd = LocalDateTime.parse(scheduledEnd),
    breakMinutes = breakMinutes,
    compensatoryRest = compensatoryRest,
    scheduledStart = scheduledStart?.let(LocalDateTime::parse),
    marginMinutes = marginMinutes,
    extrasAuthorized = extrasAuthorized,
    authorizedBy = authorizedBy,
    extraNote = extraNote,
)

private fun Shift.toEntity() = ShiftEntity(
    id = id,
    start = start.toString(),
    end = end?.toString(),
    scheduledEnd = scheduledEnd.toString(),
    breakMinutes = breakMinutes,
    compensatoryRest = compensatoryRest,
    scheduledStart = scheduledStart?.toString(),
    marginMinutes = marginMinutes,
    extrasAuthorized = extrasAuthorized,
    authorizedBy = authorizedBy,
    extraNote = extraNote,
)

/** Los turnos existentes quedan sin horario programado y se siguen pagando como se marcaron. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shifts ADD COLUMN scheduled_start TEXT")
        db.execSQL("ALTER TABLE shifts ADD COLUMN margin_minutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE shifts ADD COLUMN extras_authorized INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE shifts ADD COLUMN authorized_by TEXT")
        db.execSQL("ALTER TABLE shifts ADD COLUMN extra_note TEXT")
    }
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY start DESC")
    fun observeAll(): Flow<List<ShiftEntity>>

    @Upsert
    suspend fun upsert(shift: ShiftEntity)

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM shifts")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertAll(shifts: List<ShiftEntity>)

    /** Todo o nada: si algo falla al restaurar, los turnos quedan como estaban. */
    @Transaction
    suspend fun replaceAll(shifts: List<ShiftEntity>) {
        deleteAll()
        upsertAll(shifts)
    }
}

@Database(entities = [ShiftEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
}

class ShiftRepository(private val dao: ShiftDao) {
    val shifts: Flow<List<Shift>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun save(shift: Shift) = dao.upsert(shift.toEntity())
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun replaceAll(shifts: List<Shift>) = dao.replaceAll(shifts.map { it.toEntity() })
}

/**
 * @property monthlySalary salario vigente hoy; [salaryHistory] guarda los anteriores.
 * @property monthlyAllowance auxilio vigente hoy, de ley o personalizado.
 * @property customAllowance auxilio que paga la empresa; `null` sigue la ley.
 */
data class UserSettings(
    val monthlySalary: Long = 0,
    val monthlyAllowance: Long = 0,
    val salaryHistory: SalaryHistory = SalaryHistory.fixed(monthlySalary),
    val customAllowance: Long? = null,
    val defaultShiftMinutes: Int = 450,
    val breakMinutes: Int = 0,
    val breakOverMinutes: Int = 8 * 60,
    val cutoffDay: Int = PayPeriod.DEFAULT_CUTOFF_DAY,
    val marginMinutes: Int = 15,
    val workerName: String = "",
) {
    val breakPolicy get() = BreakPolicy(breakMinutes, breakOverMinutes)
    val paySettings get() = PaySettings(salaryHistory, customAllowance)
}

/** Lo que decide qué ve la persona al abrir la app. */
data class AppAccess(val configured: Boolean, val lockEnabled: Boolean)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val salaryKey = longPreferencesKey("monthly_salary")
    private val allowanceKey = longPreferencesKey("monthly_allowance")
    private val shiftKey = intPreferencesKey("default_shift_minutes")
    private val breakKey = intPreferencesKey("break_minutes")
    private val breakOverKey = intPreferencesKey("break_over_minutes")
    private val cutoffKey = intPreferencesKey("cutoff_day")
    private val lockKey = booleanPreferencesKey("lock_enabled")
    private val marginKey = intPreferencesKey("margin_minutes")
    private val nameKey = stringPreferencesKey("worker_name")
    private val salaryHistoryKey = stringPreferencesKey("salary_history")
    private val manualDraftsKey = stringPreferencesKey("manual_drafts")

    /** Ajustes que van en la copia de seguridad. El bloqueo no: depende de cada teléfono. */
    private val backupIntKeys = listOf(shiftKey, breakKey, breakOverKey, cutoffKey, marginKey)

    val access: Flow<AppAccess> = context.dataStore.data.map { prefs ->
        AppAccess(configured = prefs[salaryKey] != null, lockEnabled = prefs[lockKey] ?: false)
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val defaults = UserSettings()
        val today = LocalDate.now()
        // Antes del historial solo había un salario: aplica a todos los turnos.
        val history = prefs[salaryHistoryKey]?.let(SalaryHistory::decode)
            ?: SalaryHistory.fixed(prefs[salaryKey] ?: defaults.monthlySalary)
        val salary = history.at(today)
        UserSettings(
            monthlySalary = salary,
            // Sin valor personalizado, el auxilio sigue lo que diga la ley.
            monthlyAllowance = prefs[allowanceKey] ?: LegalPay.allowanceFor(salary, today),
            salaryHistory = history,
            customAllowance = prefs[allowanceKey],
            defaultShiftMinutes = prefs[shiftKey] ?: defaults.defaultShiftMinutes,
            breakMinutes = prefs[breakKey] ?: defaults.breakMinutes,
            breakOverMinutes = prefs[breakOverKey] ?: defaults.breakOverMinutes,
            cutoffDay = (prefs[cutoffKey] ?: defaults.cutoffDay)
                .coerceIn(PayPeriod.MIN_CUTOFF_DAY, PayPeriod.MAX_CUTOFF_DAY),
            marginMinutes = prefs[marginKey] ?: defaults.marginMinutes,
            workerName = prefs[nameKey] ?: defaults.workerName,
        )
    }

    /**
     * Guarda los ajustes. Si cambió el salario, el nuevo aplica desde [salarySince]; los cortes
     * anteriores conservan el que tenían. [SalaryHistory.SINCE_ALWAYS] lo corrige en todos.
     */
    suspend fun save(value: UserSettings, salarySince: LocalDate) {
        context.dataStore.edit { prefs ->
            if (value.monthlySalary != value.salaryHistory.at(LocalDate.now())) {
                prefs[salaryHistoryKey] = value.salaryHistory.withChange(salarySince, value.monthlySalary).encode()
            }
            val customAllowance = LegalPay.customAllowanceToStore(
                allowance = value.monthlyAllowance,
                salary = value.monthlySalary,
                previousCustom = prefs[allowanceKey],
                previousSalary = prefs[salaryKey] ?: 0,
                date = LocalDate.now(),
            )
            if (customAllowance == null) prefs.remove(allowanceKey) else prefs[allowanceKey] = customAllowance
            prefs[salaryKey] = value.monthlySalary
            prefs[shiftKey] = value.defaultShiftMinutes
            prefs[breakKey] = value.breakMinutes
            prefs[breakOverKey] = value.breakOverMinutes
            prefs[cutoffKey] = value.cutoffDay
            prefs[marginKey] = value.marginMinutes
            prefs[nameKey] = value.workerName
        }
    }

    suspend fun completeSetup(salary: Long, cutoffDay: Int) {
        context.dataStore.edit {
            it[salaryKey] = salary
            it[salaryHistoryKey] = SalaryHistory.fixed(salary).encode()
            it[cutoffKey] = cutoffDay
        }
    }

    val manualDrafts: Flow<String?> = context.dataStore.data.map { it[manualDraftsKey] }

    suspend fun saveManualDrafts(text: String) {
        context.dataStore.edit { it[manualDraftsKey] = text }
    }

    suspend fun backup(shifts: List<Shift>): BackupData {
        val prefs = context.dataStore.data.first()
        val settings = buildMap {
            backupIntKeys.forEach { key -> prefs[key]?.let { put(key.name, it.toString()) } }
            prefs[allowanceKey]?.let { put(allowanceKey.name, it.toString()) }
            prefs[nameKey]?.let { put(nameKey.name, it) }
        }
        return BackupData(shifts, this.settings.first().salaryHistory, settings)
    }

    /** Reemplaza los ajustes por los de la copia. Lo que la copia no trae vuelve a su valor inicial. */
    suspend fun restore(data: BackupData) {
        context.dataStore.edit { prefs ->
            backupIntKeys.forEach { key -> data.settings[key.name]?.toIntOrNull()?.let { prefs[key] = it } ?: prefs.remove(key) }
            data.settings[allowanceKey.name]?.toLongOrNull()?.let { prefs[allowanceKey] = it } ?: prefs.remove(allowanceKey)
            data.settings[nameKey.name]?.let { prefs[nameKey] = it } ?: prefs.remove(nameKey)
            prefs[salaryHistoryKey] = data.salary.encode()
            prefs[salaryKey] = data.salary.at(LocalDate.now())
        }
    }

    suspend fun setLockEnabled(value: Boolean) {
        context.dataStore.edit { it[lockKey] = value }
    }
}

fun createDatabase(context: Context): AppDatabase =
    Room.databaseBuilder(context, AppDatabase::class.java, "turnos.db").addMigrations(MIGRATION_1_2).build()
