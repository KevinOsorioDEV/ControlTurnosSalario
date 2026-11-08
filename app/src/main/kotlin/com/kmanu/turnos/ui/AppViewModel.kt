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
import com.kmanu.turnos.data.SettingsStore
import com.kmanu.turnos.data.ShiftRepository
import com.kmanu.turnos.data.UserSettings
import com.kmanu.turnos.domain.Liquidation
import com.kmanu.turnos.domain.PayPeriod
import com.kmanu.turnos.domain.PaySettings
import com.kmanu.turnos.domain.PayrollCalculator
import com.kmanu.turnos.domain.Shift
import com.kmanu.turnos.domain.ShiftValidation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val shifts: List<Shift>,
    val settings: UserSettings,
    val period: PayPeriod,
) {
    val openShift: Shift? = shifts.firstOrNull { it.end == null }
    val lastClosedShift: Shift? = shifts.firstOrNull { it.end != null }

    val periodShifts: List<Shift> = shifts.filter { shift ->
        shift.start.toLocalDate() in period || shift.end?.toLocalDate()?.let { it in period } == true
    }

    val liquidation: Liquidation by lazy {
        PayrollCalculator.liquidate(
            shifts,
            PaySettings(settings.monthlySalary, settings.monthlyAllowance),
            period,
        )
    }

    /** Salidas programadas más usadas, para elegirlas con un toque. */
    val frequentEndTimes: List<LocalTime> = shifts
        .groupingBy { it.scheduledEnd.toLocalTime() }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(4)
        .map { it.key }
        .sorted()
}

class AppViewModel(
    private val repository: ShiftRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    /** Mes elegido en los paginadores; `null` sigue el corte en curso según el día de corte. */
    private val selectedMonth = MutableStateFlow<YearMonth?>(null)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    /** La calculadora manual vive aquí para no perder lo escrito al cambiar de pestaña o de corte. */
    var manualMode by mutableStateOf(false)
    val manualDrafts = mutableStateMapOf<YearMonth, ManualDraft>()

    val state: StateFlow<UiState?> = combine(repository.shifts, settingsStore.settings, selectedMonth) { shifts, settings, month ->
        val cutoffDay = settings.cutoffDay
        UiState(shifts, settings, PayPeriod(month ?: PayPeriod.containing(LocalDate.now(), cutoffDay).month, cutoffDay))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectPeriod(value: PayPeriod) {
        selectedMonth.value = value.month
    }

    fun checkIn(scheduledEnd: LocalDateTime, breakMinutes: Int) {
        val current = state.value ?: return
        if (current.openShift != null) return
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        val shift = Shift(UUID.randomUUID().toString(), now, null, scheduledEnd, breakMinutes)
        val overlapsFuture = current.shifts.any { other -> other.start >= now && other.start < scheduledEnd }
        val error = when {
            overlapsFuture -> "Se cruza con otro turno registrado."
            !scheduledEnd.isAfter(now) -> "La salida programada debe ser posterior a la entrada."
            else -> null
        }
        if (error != null) {
            _messages.tryEmit(error)
            return
        }
        viewModelScope.launch { repository.save(shift) }
    }

    fun checkOut() {
        val open = state.value?.openShift ?: return
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        viewModelScope.launch {
            if (now.isAfter(open.start)) {
                repository.save(open.copy(end = now))
            } else {
                repository.delete(open.id)
                _messages.emit("Turno descartado: duró menos de un minuto.")
            }
        }
    }

    /** Guarda un turno creado o editado. Devuelve el error si no es válido. */
    fun save(shift: Shift): String? {
        val current = state.value ?: return "Los datos aún se están cargando."
        val error = ShiftValidation.validate(shift, current.shifts, LocalDateTime.now())
        if (error == null) viewModelScope.launch { repository.save(shift) }
        return error
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun saveSettings(settings: UserSettings) {
        // Con otro día de corte, el corte en curso puede ser otro mes.
        if (state.value?.settings?.cutoffDay != settings.cutoffDay) selectedMonth.value = null
        viewModelScope.launch {
            settingsStore.save(settings)
            _messages.emit("Ajustes guardados")
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as TurnosApplication
                AppViewModel(app.repository, app.settingsStore)
            }
        }
    }
}
