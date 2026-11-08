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
 */
data class Shift(
    val id: String,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val scheduledEnd: LocalDateTime,
    val breakMinutes: Int = 0,
    val compensatoryRest: Boolean = true,
)

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

data class PaySettings(
    val monthlySalary: Long,
    val monthlyAllowance: Long,
)

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

    private data class Bucket(val concept: Concept, val rules: LaborRules)

    /** Desglose de un turno. [until] permite calcular un turno abierto hasta el momento actual. */
    fun breakdown(
        shift: Shift,
        monthlySalary: Long,
        until: LocalDateTime? = null,
        includeDate: (LocalDate) -> Boolean = { true },
    ): ShiftBreakdown = accumulate(listOf(shift), monthlySalary, until, includeDate)

    fun liquidate(shifts: List<Shift>, settings: PaySettings, period: PayPeriod): Liquidation {
        val breakdown = accumulate(shifts.filter { it.end != null }, settings.monthlySalary, null, period::contains)
        val noveltiesTotal = breakdown.total
        val earned = settings.monthlySalary + noveltiesTotal
        val deduction = BigDecimal(earned).multiply(HEALTH_AND_PENSION).setScale(0, RoundingMode.HALF_UP).toLong()
        return Liquidation(
            period = period,
            salary = settings.monthlySalary,
            workedMinutes = breakdown.workedMinutes,
            lines = breakdown.lines,
            noveltiesTotal = noveltiesTotal,
            earned = earned,
            allowance = settings.monthlyAllowance,
            healthAndPension = deduction,
            net = earned + settings.monthlyAllowance - deduction,
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

    private fun accumulate(
        shifts: List<Shift>,
        monthlySalary: Long,
        until: LocalDateTime?,
        includeDate: (LocalDate) -> Boolean,
    ): ShiftBreakdown {
        val minutes = HashMap<Bucket, Long>()
        var worked = 0L
        for (shift in shifts) {
            val end = shift.end ?: until ?: continue
            val start = shift.start.truncatedTo(ChronoUnit.MINUTES)
            val finish = end.truncatedTo(ChronoUnit.MINUTES)
            val breakRange = unpaidBreak(shift, start, finish)
            var minute = start
            while (minute < finish) {
                val isBreak = breakRange != null && minute >= breakRange.first && minute < breakRange.second
                if (!isBreak && includeDate(minute.toLocalDate())) {
                    worked++
                    val rules = LaborRules.at(minute.toLocalDate())
                    conceptsFor(minute, shift, rules).forEach { concept ->
                        minutes.merge(Bucket(concept, rules), 1L, Long::plus)
                    }
                }
                minute = minute.plusMinutes(1)
            }
        }
        val lines = minutes.entries
            .groupBy({ it.key.concept }) { it }
            .map { (concept, entries) ->
                val amount = entries.fold(BigDecimal.ZERO) { sum, (bucket, count) ->
                    sum + amountFor(bucket, count, monthlySalary)
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

    private fun amountFor(bucket: Bucket, minutes: Long, monthlySalary: Long): BigDecimal =
        BigDecimal(monthlySalary)
            .multiply(BigDecimal(minutes))
            .multiply(bucket.concept.factor(bucket.rules.sundayRate))
            .divide(BigDecimal(bucket.rules.monthlyHours).multiply(MINUTES_PER_HOUR), 10, RoundingMode.HALF_UP)
}
