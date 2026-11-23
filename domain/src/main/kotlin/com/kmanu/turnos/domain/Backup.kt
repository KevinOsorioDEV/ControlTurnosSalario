package com.kmanu.turnos.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Copia de seguridad: todos los turnos, el historial de salarios y los ajustes.
 *
 * @property settings ajustes como texto, por nombre; la app decide cómo leerlos.
 */
data class BackupData(
    val shifts: List<Shift>,
    val salary: SalaryHistory,
    val settings: Map<String, String>,
)

object Backup {
    private const val FORMAT = "turnos-respaldo"
    private const val VERSION = 1L
    private const val DAMAGED = "La copia de seguridad está dañada."

    fun encode(data: BackupData): String = Json.write(
        linkedMapOf(
            "formato" to FORMAT,
            "version" to VERSION,
            "salarios" to data.salary.changes.map {
                mapOf("desde" to it.from.toString(), "salario" to it.monthlySalary, "minimo" to it.minimumWage)
            },
            "ajustes" to data.settings,
            "turnos" to data.shifts.map(::shiftToMap),
        ),
    )

    /** Lanza [IllegalArgumentException] con un mensaje para la persona si el archivo no sirve. */
    fun decode(text: String): BackupData {
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *>
        require(root != null && root["formato"] == FORMAT) { "El archivo no es una copia de seguridad de Turnos." }
        // Sin versión, o con una que no es un entero positivo, la copia está dañada; 1.0 vale como 1.
        val version = (root["version"] as? Number)?.toDouble()?.takeIf { it >= 1 && it % 1 == 0.0 }
        require(version != null) { DAMAGED }
        require(version <= VERSION) { "La copia es de una versión más nueva de la app. Actualiza Turnos." }
        return runCatching {
            val salary = (root["salarios"] as List<*>).map { entry ->
                val map = entry as Map<*, *>
                SalaryChange(
                    from = LocalDate.parse(map["desde"] as String),
                    monthlySalary = (map["salario"] as Number).toLong(),
                    // Las copias anteriores a esta opción no traen el campo.
                    minimumWage = map["minimo"] as? Boolean ?: false,
                )
            }
            val shifts = (root["turnos"] as List<*>).map { shiftFromMap(it as Map<*, *>) }
            checkShifts(shifts)
            BackupData(
                shifts = shifts,
                salary = SalaryHistory(salary),
                // Un ajuste nulo se omite para que la app use su valor por defecto.
                settings = (root["ajustes"] as Map<*, *>).entries
                    .filter { it.value != null }
                    .associate { (key, value) -> key.toString() to value.toString() },
            )
        }.getOrElse { throw IllegalArgumentException(DAMAGED, it) }
    }

    /** Una copia editada a mano o corrupta no debe meter turnos imposibles en la base. */
    private fun checkShifts(shifts: List<Shift>) {
        require(shifts.map { it.id }.toSet().size == shifts.size) { "Turnos con el mismo id." }
        require(shifts.count { it.end == null } <= 1) { "Más de un turno abierto." }
        shifts.forEach { shift ->
            require(shift.end == null || shift.end.isAfter(shift.start)) { "Salida antes de la entrada: ${shift.id}" }
            require(shift.scheduledEnd.isAfter(shift.start)) { "Salida programada antes de la entrada: ${shift.id}" }
            require(shift.breakMinutes >= 0 && shift.marginMinutes >= 0) { "Minutos negativos: ${shift.id}" }
        }
    }

    private fun shiftToMap(shift: Shift): Map<String, Any?> = linkedMapOf(
        "id" to shift.id,
        "entrada" to shift.start.toString(),
        "salida" to shift.end?.toString(),
        "entradaProgramada" to shift.scheduledStart?.toString(),
        "salidaProgramada" to shift.scheduledEnd.toString(),
        "almuerzo" to shift.breakMinutes,
        "compensatorio" to shift.compensatoryRest,
        "margen" to shift.marginMinutes,
        "extrasAutorizadas" to shift.extrasAuthorized,
        "autorizo" to shift.authorizedBy,
        "nota" to shift.extraNote,
    )

    private fun shiftFromMap(map: Map<*, *>): Shift = Shift(
        id = map["id"] as String,
        start = LocalDateTime.parse(map["entrada"] as String),
        end = (map["salida"] as String?)?.let(LocalDateTime::parse),
        scheduledStart = (map["entradaProgramada"] as String?)?.let(LocalDateTime::parse),
        scheduledEnd = LocalDateTime.parse(map["salidaProgramada"] as String),
        breakMinutes = (map["almuerzo"] as Number).toInt(),
        compensatoryRest = map["compensatorio"] as Boolean,
        marginMinutes = (map["margen"] as Number).toInt(),
        extrasAuthorized = map["extrasAutorizadas"] as Boolean,
        authorizedBy = map["autorizo"] as String?,
        extraNote = map["nota"] as String?,
    )
}
