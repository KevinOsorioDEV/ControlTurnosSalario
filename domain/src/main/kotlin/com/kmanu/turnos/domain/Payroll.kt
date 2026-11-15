package com.kmanu.turnos.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Un turno marcado. Las horas posteriores a [scheduledEnd] son extra.
 *
 * @property end `null` mientras el turno sigue abierto.
 * @property breakMinutes descanso no pagado, que se descuenta del centro del tiempo programado.
 * @property compensatoryRest `false` si un domingo o festivo trabajado no tiene descanso
 * compensatorio: entonces, además del recargo, se paga la hora trabajada.
 * @property scheduledStart entrada programada. Si existe, se paga según el turno programado y
 * [start] y [end] quedan como registro de lo marcado. Los turnos anteriores a esta función no la
 * tienen y se pagan tal como se marcaron.
 * @property marginMinutes margen de puntualidad vigente cuando se marcó el turno.
 * @property extrasAuthorized si las horas después del margen se pagan como extra.
 * @property authorizedBy quién autorizó las horas extra, para la bitácora.
 */
data class Shift(
    val id: String,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val scheduledEnd: LocalDateTime,
    val breakMinutes: Int = 0,
    val compensatoryRest: Boolean = true,
    val scheduledStart: LocalDateTime? = null,
    val marginMinutes: Int = 0,
    val extrasAuthorized: Boolean = true,
    val authorizedBy: String? = null,
    val extraNote: String? = null,
)

/** Tramo del turno que se paga. */
data class PaidInterval(val start: LocalDateTime, val end: LocalDateTime)

enum class Concept(val label: String) {
    EXTRA_DAY("Hora extra diurna"),
    EXTRA_NIGHT("Hora extra nocturna"),
    EXTRA_DAY_SUNDAY("Hora extra diurna en domingo o festivo"),
    EXTRA_NIGHT_SUNDAY("Hora extra nocturna en domingo o festivo"),
    NIGHT("Recargo nocturno ordinario"),
    SUNDAY_DAY("Recargo diurno en domingo o festivo"),
    SUNDAY_NIGHT("Recargo nocturno en domingo o festivo"),
    SUNDAY_NO_REST("Hora trabajada en domingo o festivo sin compensatorio");

    /** Fracción de la hora ordinaria que paga este concepto. */
    fun factor(sundayRate: BigDecimal): BigDecimal = when (this) {
        EXTRA_DAY -> BigDecimal("1.25")
        EXTRA_NIGHT -> BigDecimal("1.75")
        EXTRA_DAY_SUNDAY -> BigDecimal("1.25") + sundayRate
        EXTRA_NIGHT_SUNDAY -> BigDecimal("1.75") + sundayRate
        NIGHT -> BigDecimal("0.35")
        SUNDAY_DAY -> sundayRate
        SUNDAY_NIGHT -> BigDecimal("0.35") + sundayRate
        SUNDAY_NO_REST -> BigDecimal.ONE
    }
}

/**
 * Datos de pago de la persona.
 *
 * @property customAllowance auxilio que paga la empresa; `null` sigue el valor de ley de cada año.
 */
data class PaySettings(
    val salary: SalaryHistory,
    val customAllowance: Long? = null,
) {
    /** Salario y auxilio fijos, sin historial. */
    constructor(monthlySalary: Long, monthlyAllowance: Long) : this(SalaryHistory.fixed(monthlySalary), monthlyAllowance)

    fun allowanceAt(date: LocalDate): Long = customAllowance ?: LegalPay.allowanceFor(salary.at(date), date)
}

/**
 * Liquidación de un mes: salario del mes más las novedades desde el día siguiente al corte del
 * mes anterior hasta el corte de este mes, pagadas al final del mes.
 *
 * @property cutoffDay último día de novedades de cada corte. En los meses que no tienen ese día,
 * el corte es el último del mes, de modo que los cortes siguen siendo continuos.
 */
data class PayPeriod(val month: YearMonth, val cutoffDay: Int = DEFAULT_CUTOFF_DAY) {
    init {
        require(cutoffDay in MIN_CUTOFF_DAY..MAX_CUTOFF_DAY) { "Día de corte inválido: $cutoffDay" }
    }

    val noveltiesFrom: LocalDate get() = cutoffDate(month.minusMonths(1), cutoffDay).plusDays(1)
    val noveltiesTo: LocalDate get() = cutoffDate(month, cutoffDay)
    val payday: LocalDate get() = month.atEndOfMonth()

    operator fun contains(date: LocalDate): Boolean = date in noveltiesFrom..noveltiesTo
    fun next() = copy(month = month.plusMonths(1))
    fun previous() = copy(month = month.minusMonths(1))

    companion object {
        const val DEFAULT_CUTOFF_DAY = 15
        const val MIN_CUTOFF_DAY = 1
        const val MAX_CUTOFF_DAY = 31

        private fun cutoffDate(month: YearMonth, cutoffDay: Int): LocalDate =
            month.atDay(minOf(cutoffDay, month.lengthOfMonth()))

        /** Corte al que pertenecen las horas trabajadas en [date]. */
        fun containing(date: LocalDate, cutoffDay: Int = DEFAULT_CUTOFF_DAY): PayPeriod {
            val month = YearMonth.from(date)
            return PayPeriod(if (date <= cutoffDate(month, cutoffDay)) month else month.plusMonths(1), cutoffDay)
        }
    }
}

data class ConceptLine(val concept: Concept, val minutes: Long, val amount: Long)

data class ShiftBreakdown(
    val workedMinutes: Long,
    val lines: List<ConceptLine>,
) {
    val total: Long get() = lines.sumOf { it.amount }
    fun minutesOf(concept: Concept): Long = lines.firstOrNull { it.concept == concept }?.minutes ?: 0
}

data class Liquidation(
    val period: PayPeriod,
    val salary: Long,
    val workedMinutes: Long,
    val lines: List<ConceptLine>,
    val noveltiesTotal: Long,
    val earned: Long,
    val allowance: Long,
    val healthAndPension: Long,
    val net: Long,
)

/**
 * Datos de una liquidación manual: las horas ya totalizadas por concepto, como en el Excel.
 * [monthlyHours] y [sundayRate] se indican aparte porque un corte puede cruzar un cambio de ley.
 */
data class ManualInput(
    val monthlySalary: Long,
    val monthlyAllowance: Long,
    val daysWorked: Int,
    val monthlyHours: Int,
    val sundayRate: BigDecimal,
    val hours: Map<Concept, BigDecimal>,
)

object PayrollCalculator {
    private val HEALTH_AND_PENSION = BigDecimal("0.08")
    private val MINUTES_PER_HOUR = BigDecimal(60)
    private const val DAYS_PER_MONTH = 30

    private data class Bucket(val concept: Concept, val rules: LaborRules, val monthlySalary: Long)

    /** Desglose de un turno. [until] permite calcular un turno abierto hasta el momento actual. */
    fun breakdown(
        shift: Shift,
        salary: SalaryHistory,
        until: LocalDateTime? = null,
        includeDate: (LocalDate) -> Boolean = { true },
    ): ShiftBreakdown = accumulate(listOf(shift), salary, until, includeDate)

    fun breakdown(
        shift: Shift,
        monthlySalary: Long,
        until: LocalDateTime? = null,
        includeDate: (LocalDate) -> Boolean = { true },
    ): ShiftBreakdown = breakdown(shift, SalaryHistory.fixed(monthlySalary), until, includeDate)

    /** Turnos cerrados que pueden tener horas en [period]; el resto ni se recorre. */
    fun shiftsIn(shifts: List<Shift>, period: PayPeriod): List<Shift> {
        // Un día de holgura: el tramo pagado puede empezar un poco antes de lo marcado.
        val from = period.noveltiesFrom.minusDays(1)
        val to = period.noveltiesTo.plusDays(1)
        return shifts.filter { shift ->
            val end = shift.end ?: return@filter false
            shift.start.toLocalDate() <= to && end.toLocalDate() >= from
        }
    }

    /** El salario del mes es el vigente el día de pago; cada hora, el vigente el día trabajado. */
    fun liquidate(shifts: List<Shift>, settings: PaySettings, period: PayPeriod): Liquidation {
        val breakdown = accumulate(shiftsIn(shifts, period), settings.salary, null, period::contains)
        val salary = settings.salary.at(period.payday)
        val allowance = settings.allowanceAt(period.payday)
        val noveltiesTotal = breakdown.total
        val earned = salary + noveltiesTotal
        val deduction = BigDecimal(earned).multiply(HEALTH_AND_PENSION).setScale(0, RoundingMode.HALF_UP).toLong()
        return Liquidation(
            period = period,
            salary = salary,
            workedMinutes = breakdown.workedMinutes,
            lines = breakdown.lines,
            noveltiesTotal = noveltiesTotal,
            earned = earned,
            allowance = allowance,
            healthAndPension = deduction,
            net = earned + allowance - deduction,
        )
    }

    /** Valor de una hora de [concept]; el salario base es siempre el mensual completo. */
    fun hourValue(monthlySalary: Long, monthlyHours: Int, concept: Concept, sundayRate: BigDecimal): Long =
        manualAmount(monthlySalary, monthlyHours, concept, sundayRate, BigDecimal.ONE).setScale(0, RoundingMode.HALF_UP).toLong()

    /** Liquidación a partir de horas ya totalizadas, sin turnos marcados. */
    fun liquidateManual(input: ManualInput, period: PayPeriod): Liquidation {
        val lines = input.hours.entries
            .filter { it.value.signum() > 0 }
            .sortedBy { it.key.ordinal }
            .map { (concept, hours) ->
                ConceptLine(
                    concept = concept,
                    minutes = hours.multiply(MINUTES_PER_HOUR).setScale(0, RoundingMode.HALF_UP).toLong(),
                    amount = manualAmount(input.monthlySalary, input.monthlyHours, concept, input.sundayRate, hours)
                        .setScale(0, RoundingMode.HALF_UP).toLong(),
                )
            }
        val noveltiesTotal = lines.sumOf { it.amount }
        val salary = prorate(input.monthlySalary, input.daysWorked)
        val allowance = prorate(input.monthlyAllowance, input.daysWorked)
        val earned = salary + noveltiesTotal
        val deduction = BigDecimal(earned).multiply(HEALTH_AND_PENSION).setScale(0, RoundingMode.HALF_UP).toLong()
        return Liquidation(
            period = period,
            salary = salary,
            workedMinutes = lines.sumOf { it.minutes },
            lines = lines,
            noveltiesTotal = noveltiesTotal,
            earned = earned,
            allowance = allowance,
            healthAndPension = deduction,
            net = earned + allowance - deduction,
        )
    }

    private fun manualAmount(
        monthlySalary: Long,
        monthlyHours: Int,
        concept: Concept,
        sundayRate: BigDecimal,
        hours: BigDecimal,
    ): BigDecimal = BigDecimal(monthlySalary)
        .multiply(hours)
        .multiply(concept.factor(sundayRate))
        .divide(BigDecimal(monthlyHours), 10, RoundingMode.HALF_UP)

    private fun prorate(monthlyValue: Long, days: Int): Long =
        BigDecimal(monthlyValue).multiply(BigDecimal(days))
            .divide(BigDecimal(DAYS_PER_MONTH), 0, RoundingMode.HALF_UP).toLong()

    /** Tramo que se paga de un turno, o `null` si sigue abierto y no se indicó [until]. */
    fun paidInterval(shift: Shift, until: LocalDateTime? = null): PaidInterval? {
        val realEnd = (shift.end ?: until ?: return null).truncatedTo(ChronoUnit.MINUTES)
        val realStart = shift.start.truncatedTo(ChronoUnit.MINUTES)
        val scheduledStart = shift.scheduledStart?.truncatedTo(ChronoUnit.MINUTES)
            ?: return PaidInterval(realStart, realEnd)
        val scheduledEnd = shift.scheduledEnd.truncatedTo(ChronoUnit.MINUTES)
        val margin = shift.marginMinutes.toLong()

        val paidStart = if (realStart <= scheduledStart.plusMinutes(margin)) scheduledStart else realStart
        val paidEnd = when {
            // En curso se muestra lo que va: la decisión sobre las extras se toma al salir.
            shift.end == null -> realEnd
            realEnd > scheduledEnd.plusMinutes(margin) -> if (shift.extrasAuthorized) realEnd else scheduledEnd
            realEnd >= scheduledEnd.minusMinutes(margin) -> scheduledEnd
            else -> realEnd
        }
        return PaidInterval(paidStart, maxOf(paidStart, paidEnd))
    }

    private fun accumulate(
        shifts: List<Shift>,
        salary: SalaryHistory,
        until: LocalDateTime?,
        includeDate: (LocalDate) -> Boolean,
    ): ShiftBreakdown {
        val minutes = HashMap<Bucket, Long>()
        var worked = 0L
        for (shift in shifts) {
            val (start, finish) = paidInterval(shift, until) ?: continue
            val breakRange = unpaidBreak(shift, start, finish)
            var minute = start
            while (minute < finish) {
                val isBreak = breakRange != null && minute >= breakRange.first && minute < breakRange.second
                val date = minute.toLocalDate()
                if (!isBreak && includeDate(date)) {
                    worked++
                    val rules = LaborRules.at(date)
                    val monthlySalary = salary.at(date)
                    conceptsFor(minute, shift, rules).forEach { concept ->
                        minutes.merge(Bucket(concept, rules, monthlySalary), 1L, Long::plus)
                    }
                }
                minute = minute.plusMinutes(1)
            }
        }
        val lines = minutes.entries
            .groupBy({ it.key.concept }) { it }
            .map { (concept, entries) ->
                val amount = entries.fold(BigDecimal.ZERO) { sum, (bucket, count) ->
                    sum + amountFor(bucket, count)
                }
                ConceptLine(concept, entries.sumOf { it.value }, amount.setScale(0, RoundingMode.HALF_UP).toLong())
            }
            .sortedBy { it.concept.ordinal }
        return ShiftBreakdown(worked, lines)
    }

    private fun unpaidBreak(
        shift: Shift,
        start: LocalDateTime,
        finish: LocalDateTime,
    ): Pair<LocalDateTime, LocalDateTime>? {
        if (shift.breakMinutes <= 0) return null
        val scheduledFinish = minOf(finish, shift.scheduledEnd.truncatedTo(ChronoUnit.MINUTES))
        val span = ChronoUnit.MINUTES.between(start, scheduledFinish)
        if (span <= 0) return null
        val length = minOf(shift.breakMinutes.toLong(), span)
        val breakStart = start.plusMinutes((span - length) / 2)
        return breakStart to breakStart.plusMinutes(length)
    }

    private fun conceptsFor(minute: LocalDateTime, shift: Shift, rules: LaborRules): List<Concept> {
        val night = rules.isNight(minute.toLocalTime())
        val sunday = ColombiaHolidays.isSundayOrHoliday(minute.toLocalDate())
        val extra = minute >= shift.scheduledEnd.truncatedTo(ChronoUnit.MINUTES)
        return when {
            extra && sunday -> listOf(if (night) Concept.EXTRA_NIGHT_SUNDAY else Concept.EXTRA_DAY_SUNDAY)
            extra -> listOf(if (night) Concept.EXTRA_NIGHT else Concept.EXTRA_DAY)
            sunday -> buildList {
                add(if (night) Concept.SUNDAY_NIGHT else Concept.SUNDAY_DAY)
                if (!shift.compensatoryRest) add(Concept.SUNDAY_NO_REST)
            }
            night -> listOf(Concept.NIGHT)
            else -> emptyList()
        }
    }

    private fun amountFor(bucket: Bucket, minutes: Long): BigDecimal =
        BigDecimal(bucket.monthlySalary)
            .multiply(BigDecimal(minutes))
            .multiply(bucket.concept.factor(bucket.rules.sundayRate))
            .divide(BigDecimal(bucket.rules.monthlyHours).multiply(MINUTES_PER_HOUR), 10, RoundingMode.HALF_UP)
}
