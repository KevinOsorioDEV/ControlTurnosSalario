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

    fun encode(data: BackupData): String = Json.write(
        linkedMapOf(
            "formato" to FORMAT,
            "version" to VERSION,
            "salarios" to data.salary.changes.map { mapOf("desde" to it.from.toString(), "salario" to it.monthlySalary) },
            "ajustes" to data.settings,
            "turnos" to data.shifts.map(::shiftToMap),
        ),
    )

    /** Lanza [IllegalArgumentException] con un mensaje para la persona si el archivo no sirve. */
    fun decode(text: String): BackupData {
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *>
        require(root != null && root["formato"] == FORMAT) { "El archivo no es una copia de seguridad de Turnos." }
        val version = (root["version"] as? Long) ?: 0
        require(version in 1..VERSION) { "La copia es de una versión más nueva de la app. Actualiza Turnos." }
        return runCatching {
            val salary = (root["salarios"] as List<*>).map { entry ->
                val map = entry as Map<*, *>
                SalaryChange(LocalDate.parse(map["desde"] as String), (map["salario"] as Number).toLong())
            }
            BackupData(
                shifts = (root["turnos"] as List<*>).map { shiftFromMap(it as Map<*, *>) },
                salary = SalaryHistory(salary),
                settings = (root["ajustes"] as Map<*, *>).entries.associate { (key, value) -> key.toString() to value.toString() },
            )
        }.getOrElse { throw IllegalArgumentException("La copia de seguridad está dañada.", it) }
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
