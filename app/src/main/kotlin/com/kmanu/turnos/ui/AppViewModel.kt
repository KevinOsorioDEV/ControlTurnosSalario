package com.kmanu.turnos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kmanu.turnos.TurnosApplication
import com.kmanu.turnos.data.AppAccess
import com.kmanu.turnos.data.SettingsStore
import com.kmanu.turnos.data.ShiftReminder
import com.kmanu.turnos.data.ShiftRepository
import com.kmanu.turnos.data.UserSettings
import com.kmanu.turnos.domain.Backup
import com.kmanu.turnos.domain.ExcelReport
import com.kmanu.turnos.domain.Liquidation
import com.kmanu.turnos.domain.PayPeriod
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.domain.SalaryHistory
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.domain.ShiftValidation
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Todo se calcula al pedirlo: el paginador crea una copia por corte y casi nunca usa todo. */
data class UiState(
    val shifts: List<Shift>,
    val settings: UserSettings,
    val period: PayPeriod,
) {
    val openShift: Shift? by lazy { shifts.firstOrNull { it.end == null } }
    val lastClosedShift: Shift? by lazy { shifts.firstOrNull { it.end != null } }

    val periodShifts: List<Shift> by lazy {
        shifts.filter { shift ->
            shift.start.toLocalDate() in period || shift.end?.toLocalDate()?.let { it in period } == true
        }
    }

    val liquidation: Liquidation by lazy { PayrollCalculator.liquidate(shifts, settings.paySettings, period) }

    val salary: SalaryHistory get() = settings.salaryHistory

    /** Último horario programado, para sugerirlo al marcar la entrada. */
    val lastSchedule: Pair<LocalTime, LocalTime>? by lazy {
        shifts
            .firstOrNull { it.scheduledStart != null }
            ?.let { requireNotNull(it.scheduledStart).toLocalTime() to it.scheduledEnd.toLocalTime() }
    }

    /** Quienes ya autorizaron extras, para no tener que volver a escribirlos. */
    val knownAuthorizers: List<String> by lazy {
        shifts
            .mapNotNull { it.authorizedBy?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .take(MAX_KNOWN_AUTHORIZERS)
    }

    /** Horarios completos más repetidos, para elegirlos con un toque. */
    val frequentShifts: List<Pair<LocalTime, LocalTime>> by lazy {
        shifts
            .mapNotNull { shift ->
                shift.scheduledStart?.let { it to shift.scheduledEnd } ?: shift.end?.let { shift.start to it }
            }
            .filter { (start, end) -> Duration.between(start, end).toMinutes() >= MIN_FREQUENT_SHIFT_MINUTES }
            .groupingBy { (start, end) -> start.toLocalTime() to end.toLocalTime() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(MAX_FREQUENT_SHIFTS)
            .map { it.key }
    }
}

private const val MIN_FREQUENT_SHIFT_MINUTES = 60L
private const val MAX_FREQUENT_SHIFTS = 4
private const val MAX_KNOWN_AUTHORIZERS = 4
private const val DRAFT_SAVE_DELAY_MS = 600L

class AppViewModel(
    private val repository: ShiftRepository,
    private val settingsStore: SettingsStore,
    private val reminder: ShiftReminder,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    /** Mes elegido en los paginadores; `null` sigue el corte en curso según el día de corte. */
    private val selectedMonth = MutableStateFlow<YearMonth?>(null)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    /** Marcar entrada o salida lee y escribe de a una: un doble toque no crea dos turnos. */
    private val punchLock = Mutex()

    /** La calculadora manual vive aquí para no perder lo escrito al cambiar de pestaña o de corte. */
    var manualMode by mutableStateOf(false)
    val manualDrafts = mutableStateMapOf<YearMonth, ManualDraft>()
    private var draftSave: Job? = null

    private fun now(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)

    val state: StateFlow<UiState?> = combine(repository.shifts, settingsStore.settings, selectedMonth) { shifts, settings, month ->
        val cutoffDay = settings.cutoffDay
        UiState(shifts, settings, PayPeriod(month ?: PayPeriod.containing(LocalDate.now(clock), cutoffDay).month, cutoffDay))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val access: StateFlow<AppAccess?> = settingsStore.access.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // El aviso de salida sigue al turno abierto, se marque, se edite o se borre.
        viewModelScope.launch {
            repository.shifts.map { shifts -> shifts.firstOrNull { it.end == null } }
                .distinctUntilChanged()
                .collect(reminder::sync)
        }
        viewModelScope.launch {
            settingsStore.manualDrafts.first()?.let { manualDrafts.putAll(decodeDrafts(it)) }
        }
    }

    /** Empieza en `true`: la app nunca muestra datos antes de autenticar. Solo aplica con el bloqueo activo. */
    var locked by mutableStateOf(true)
        private set

    /** Evita pedir la huella otra vez si la persona canceló; se reinicia al salir de la app. */
    var lockPrompted = false

    /** El diálogo del sistema puede detener la actividad: eso no cuenta como salir de la app. */
    var authInProgress = false

    fun onAppLeft() {
        if (authInProgress) return
        locked = true
        lockPrompted = false
    }

    fun unlock() {
        locked = false
    }

    fun setLockEnabled(enabled: Boolean) {
        locked = false
        viewModelScope.launch { settingsStore.setLockEnabled(enabled) }
    }

    fun completeSetup(salary: Long, cutoffDay: Int) {
        viewModelScope.launch { settingsStore.completeSetup(salary, cutoffDay) }
    }

    fun showMessage(text: String) {
        _messages.tryEmit(text)
    }

    fun selectPeriod(value: PayPeriod) {
        selectedMonth.value = value.month
    }

    /** Guarda el borrador al dejar de escribir, no en cada tecla. */
    fun updateDraft(month: YearMonth, draft: ManualDraft) {
        manualDrafts[month] = draft
        draftSave?.cancel()
        draftSave = viewModelScope.launch {
            delay(DRAFT_SAVE_DELAY_MS)
            settingsStore.saveManualDrafts(encodeDrafts(manualDrafts.toMap()))
        }
    }

    fun checkIn(scheduledStart: LocalDateTime, scheduledEnd: LocalDateTime, breakMinutes: Int) {
        viewModelScope.launch {
            punchLock.withLock {
                // Se lee de la base y no del estado en pantalla, que puede ir un paso atrás.
                val shifts = repository.shifts.first()
                val settings = settingsStore.settings.first()
                val now = now()
                ShiftValidation.validateCheckIn(scheduledEnd, shifts, now)?.let {
                    _messages.emit(it)
                    return@withLock
                }
                repository.save(
                    Shift(
                        id = UUID.randomUUID().toString(),
                        start = now,
                        end = null,
                        scheduledEnd = scheduledEnd,
                        breakMinutes = breakMinutes,
                        scheduledStart = scheduledStart,
                        marginMinutes = settings.marginMinutes,
                        extrasAuthorized = false,
                    ),
                )
            }
        }
    }

    /**
     * Cierra el turno abierto a las [exit], o ahora si es `null`.
     * [authorized] es `null` si no hubo que preguntar por horas extra.
     */
    fun checkOut(exit: LocalDateTime? = null, authorized: Boolean? = null, authorizedBy: String? = null, note: String? = null) {
        viewModelScope.launch {
            punchLock.withLock {
                val shifts = repository.shifts.first()
                val open = shifts.firstOrNull { it.end == null } ?: return@withLock
                val now = now()
                val end = exit?.truncatedTo(ChronoUnit.MINUTES) ?: now
                if (exit == null && !now.isAfter(open.start)) {
                    repository.delete(open.id)
                    _messages.emit("Turno descartado: duró menos de un minuto.")
                    return@withLock
                }
                ShiftValidation.validateCheckOut(open, end, shifts, now)?.let {
                    _messages.emit(it)
                    return@withLock
                }
                repository.save(
                    open.copy(
                        end = end,
                        extrasAuthorized = authorized ?: open.extrasAuthorized,
                        authorizedBy = authorizedBy?.trim()?.takeIf(String::isNotEmpty),
                        extraNote = note?.trim()?.takeIf(String::isNotEmpty),
                    ),
                )
            }
        }
    }

    /** Guarda un turno creado o editado. Devuelve el error si no es válido. */
    fun save(shift: Shift): String? {
        val current = state.value ?: return "Los datos aún se están cargando."
        val error = ShiftValidation.validate(shift, current.shifts, LocalDateTime.now(clock))
        if (error == null) viewModelScope.launch { repository.save(shift) }
        return error
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** Crea el Excel del corte en [directory]. Devuelve `null` si no hay turnos que exportar. */
    suspend fun writeExcel(directory: File, period: PayPeriod, workerName: String): File? {
        val current = state.value ?: return null
        if (current.copy(period = period).periodShifts.none { it.end != null }) {
            showMessage("No hay turnos cerrados en este corte.")
            return null
        }
        return withContext(Dispatchers.IO) {
            directory.mkdirs()
            File(directory, "Turnos_corte_${period.month}.xlsx").also { file ->
                file.outputStream().use { output ->
                    ExcelReport.write(output, workerName, current.shifts, current.settings.paySettings, period)
                }
            }
        }
    }

    /** Copia de seguridad completa, como texto para escribir en el archivo que eligió la persona. */
    suspend fun backupText(): String = withContext(Dispatchers.Default) {
        Backup.encode(settingsStore.backup(repository.shifts.first()))
    }

    /** Reemplaza todos los datos por los de la copia. Devuelve el error para mostrarlo, o `null`. */
    suspend fun restore(text: String): String? {
        val data = runCatching { withContext(Dispatchers.Default) { Backup.decode(text) } }
            .getOrElse { return it.message ?: "No se pudo leer la copia." }
        punchLock.withLock {
            repository.replaceAll(data.shifts)
            settingsStore.restore(data)
        }
        selectedMonth.value = null
        return null
    }

    fun saveWorkerName(name: String) {
        val current = state.value ?: return
        viewModelScope.launch { settingsStore.save(current.settings.copy(workerName = name), salaryChangeStart()) }
    }

    /** Un salario nuevo aplica desde el primer día del mes en curso, que es el mes que se paga. */
    fun salaryChangeStart(): LocalDate = YearMonth.now(clock).atDay(1)

    /** [salarySince] dice desde cuándo aplica un salario nuevo; ver [SettingsStore.save]. */
    fun saveSettings(settings: UserSettings, salarySince: LocalDate = salaryChangeStart()) {
        // Con otro día de corte, el corte en curso puede ser otro mes.
        if (state.value?.settings?.cutoffDay != settings.cutoffDay) selectedMonth.value = null
        viewModelScope.launch {
            settingsStore.save(settings, salarySince)
            _messages.emit("Ajustes guardados")
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TurnosApplication
                AppViewModel(app.repository, app.settingsStore, app.reminder)
            }
        }
    }
}
