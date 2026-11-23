package com.kmanu.turnos.domain

import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Genera el Excel (.xlsx) de un corte: turnos, liquidación y bitácora de horas extra. */
object ExcelReport {
    const val MISSING_AUTHORIZER = "Falta quién autorizó"

    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DAY = DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("es-CO"))
    private val SIXTY = BigDecimal(60)

    private val EXTRA_CONCEPTS = setOf(
        Concept.EXTRA_DAY,
        Concept.EXTRA_NIGHT,
        Concept.EXTRA_DAY_SUNDAY,
        Concept.EXTRA_NIGHT_SUNDAY,
    )

    private val HOUR_COLUMNS = listOf(
        "H.E.D" to Concept.EXTRA_DAY,
        "H.E.N" to Concept.EXTRA_NIGHT,
        "H.E.F.D" to Concept.EXTRA_DAY_SUNDAY,
        "H.E.F.N" to Concept.EXTRA_NIGHT_SUNDAY,
        "R.N" to Concept.NIGHT,
        "R.D.F" to Concept.SUNDAY_DAY,
        "R.N.F" to Concept.SUNDAY_NIGHT,
    )
    private const val NO_REST_COLUMN = "H.F sin compensatorio"

    private const val STYLE_TITLE = 1
    private const val STYLE_HEADER = 2
    private const val STYLE_HOURS = 3
    private const val STYLE_MONEY = 4
    private const val STYLE_BOLD = 5
    private const val STYLE_BOLD_HOURS = 6
    private const val STYLE_BOLD_MONEY = 7

    private sealed interface Cell {
        val style: Int
    }

    private data class Text(val value: String, override val style: Int = 0) : Cell
    private data class Number(val value: BigDecimal, override val style: Int) : Cell

    private data class Item(val shift: Shift, val breakdown: ShiftBreakdown)

    fun write(output: OutputStream, workerName: String, shifts: List<Shift>, settings: PaySettings, period: PayPeriod) {
        val items = PayrollCalculator.shiftsOf(shifts, period)
            .filter { it.end != null }
            .sortedBy { it.start }
            .map { Item(it, PayrollCalculator.breakdown(it, settings.salary, includeDate = period::contains)) }
        val liquidation = PayrollCalculator.liquidate(shifts, settings, period)

        ZipOutputStream(output).use { zip ->
            zip.entry("[Content_Types].xml", CONTENT_TYPES)
            zip.entry("_rels/.rels", ROOT_RELS)
            zip.entry("xl/workbook.xml", WORKBOOK)
            zip.entry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.entry("xl/styles.xml", STYLES)
            zip.entry("xl/worksheets/sheet1.xml", sheetXml(shiftsSheet(workerName, items, liquidation), shiftWidths(items)))
            zip.entry("xl/worksheets/sheet2.xml", sheetXml(extrasSheet(items), EXTRA_WIDTHS))
        }
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun hoursValue(minutes: Long): BigDecimal = BigDecimal(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP)

    private fun hoursCell(minutes: Long, style: Int = STYLE_HOURS): Cell? =
        if (minutes == 0L) null else Number(hoursValue(minutes), style)

    private fun moneyCell(value: Long, style: Int = STYLE_MONEY): Cell = Number(BigDecimal(value), style)

    private fun extraMinutes(breakdown: ShiftBreakdown): Long =
        breakdown.lines.filter { it.concept in EXTRA_CONCEPTS }.sumOf { it.minutes }

    private fun observations(item: Item): String = buildList {
        if (extraMinutes(item.breakdown) > 0) {
            val by = item.shift.authorizedBy?.takeIf { it.isNotBlank() }
            add(if (by != null) "Extra autorizada por $by" else MISSING_AUTHORIZER)
            item.shift.extraNote?.takeIf { it.isNotBlank() }?.let(::add)
        }
        if (item.breakdown.minutesOf(Concept.SUNDAY_NO_REST) > 0) add("Sin descanso compensatorio")
    }.joinToString(". ")

    private fun shiftsSheet(workerName: String, items: List<Item>, liquidation: Liquidation): List<List<Cell?>> = buildList {
        val period = liquidation.period
        val showNoRest = items.any { it.breakdown.minutesOf(Concept.SUNDAY_NO_REST) > 0 }

        add(listOf(Text("Turnos, recargos y horas extra", STYLE_TITLE)))
        add(listOf(Text("Nombre", STYLE_BOLD), Text(workerName)))
        add(listOf(Text("Corte", STYLE_BOLD), Text("${period.noveltiesFrom.format(DATE)} a ${period.noveltiesTo.format(DATE)}")))
        add(listOf(Text("Pago", STYLE_BOLD), Text(period.payday.format(DATE))))
        add(emptyList())

        val headers = listOf("Fecha", "Día", "Entrada marcada", "Salida marcada", "Entrada pagada", "Salida pagada", "Total horas", "H.O") +
            HOUR_COLUMNS.map { it.first } + (if (showNoRest) listOf(NO_REST_COLUMN) else emptyList()) + "Observaciones"
        add(headers.map { Text(it, STYLE_HEADER) })

        items.forEach { item ->
            val paid = requireNotNull(PayrollCalculator.paidInterval(item.shift))
            val row = mutableListOf<Cell?>(
                Text(item.shift.start.format(DATE)),
                Text(item.shift.start.format(DAY)),
                Text(item.shift.start.format(TIME)),
                Text(requireNotNull(item.shift.end).format(TIME)),
                Text(paid.start.format(TIME)),
                Text(paid.end.format(TIME)),
                Number(hoursValue(item.breakdown.workedMinutes), STYLE_HOURS),
                Number(hoursValue(item.breakdown.workedMinutes - extraMinutes(item.breakdown)), STYLE_HOURS),
            )
            HOUR_COLUMNS.forEach { (_, concept) -> row += hoursCell(item.breakdown.minutesOf(concept)) }
            if (showNoRest) row += hoursCell(item.breakdown.minutesOf(Concept.SUNDAY_NO_REST))
            row += Text(observations(item))
            add(row)
        }

        val workedTotal = items.sumOf { it.breakdown.workedMinutes }
        val extraTotal = items.sumOf { extraMinutes(it.breakdown) }
        val totals = mutableListOf<Cell?>(
            Text("TOTAL", STYLE_BOLD), null, null, null, null, null,
            Number(hoursValue(workedTotal), STYLE_BOLD_HOURS),
            Number(hoursValue(workedTotal - extraTotal), STYLE_BOLD_HOURS),
        )
        HOUR_COLUMNS.forEach { (_, concept) ->
            totals += hoursCell(items.sumOf { it.breakdown.minutesOf(concept) }, STYLE_BOLD_HOURS)
        }
        if (showNoRest) totals += hoursCell(items.sumOf { it.breakdown.minutesOf(Concept.SUNDAY_NO_REST) }, STYLE_BOLD_HOURS)
        add(totals)
        add(emptyList())

        add(listOf(Text("Liquidación", STYLE_HEADER), Text("Horas", STYLE_HEADER), Text("Valor", STYLE_HEADER)))
        liquidation.lines.forEach { line ->
            add(listOf(Text(line.concept.label), hoursCell(line.minutes), moneyCell(line.amount)))
        }
        add(listOf(Text("Total recargos y extras", STYLE_BOLD), null, moneyCell(liquidation.noveltiesTotal, STYLE_BOLD_MONEY)))
        add(listOf(Text("Salario del mes"), null, moneyCell(liquidation.salary)))
        add(listOf(Text("Devengado", STYLE_BOLD), null, moneyCell(liquidation.earned, STYLE_BOLD_MONEY)))
        add(listOf(Text("Auxilio de transporte o conectividad"), null, moneyCell(liquidation.allowance)))
        add(listOf(Text("Salud y pensión (8%)"), null, moneyCell(-liquidation.healthAndPension)))
        add(listOf(Text("Neto a recibir", STYLE_BOLD), null, moneyCell(liquidation.net, STYLE_BOLD_MONEY)))
    }

    private fun extrasSheet(items: List<Item>): List<List<Cell?>> = buildList {
        add(listOf("Fecha", "Salida programada", "Salida marcada", "Horas extra", "Tipo", "Valor", "Autorizó", "Nota").map { Text(it, STYLE_HEADER) })
        val extras = items.filter { extraMinutes(it.breakdown) > 0 }
        if (extras.isEmpty()) add(listOf(Text("Sin horas extra en este corte.")))
        extras.forEach { item ->
            val lines = item.breakdown.lines.filter { it.concept in EXTRA_CONCEPTS }
            add(
                listOf(
                    Text(item.shift.start.format(DATE)),
                    Text(item.shift.scheduledEnd.format(TIME)),
                    Text(requireNotNull(item.shift.end).format(TIME)),
                    hoursCell(lines.sumOf { it.minutes }),
                    Text(lines.joinToString(", ") { it.concept.label }),
                    moneyCell(lines.sumOf { it.amount }),
                    Text(item.shift.authorizedBy?.takeIf { it.isNotBlank() } ?: MISSING_AUTHORIZER),
                    Text(item.shift.extraNote.orEmpty()),
                ),
            )
        }
    }

    private fun columnName(index: Int): String {
        var remaining = index + 1
        return buildString {
            while (remaining > 0) {
                val letter = (remaining - 1) % 26
                insert(0, 'A' + letter)
                remaining = (remaining - 1) / 26
            }
        }
    }

    private fun escape(text: String): String = text
        .filter { it == '\t' || it == '\n' || it >= ' ' }
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun sheetXml(rows: List<List<Cell?>>, widths: List<Int>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols>""")
        widths.forEachIndexed { index, width ->
            append("""<col min="${index + 1}" max="${index + 1}" width="$width" customWidth="1"/>""")
        }
        append("</cols><sheetData>")
        rows.forEachIndexed { rowIndex, row ->
            append("""<row r="${rowIndex + 1}">""")
            row.forEachIndexed { columnIndex, cell ->
                val reference = "${columnName(columnIndex)}${rowIndex + 1}"
                when (cell) {
                    null -> Unit
                    is Text -> append("""<c r="$reference" s="${cell.style}" t="inlineStr"><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>""")
                    is Number -> append("""<c r="$reference" s="${cell.style}"><v>${cell.value.toPlainString()}</v></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private val SHIFT_WIDTHS = listOf(36, 12, 11, 11, 11, 11, 9, 9, 9, 9, 9, 9, 9, 9, 9, 40)
    private const val NO_REST_WIDTH = 12

    /** La columna "sin compensatorio" va antes de Observaciones, que conserva su ancho. */
    private fun shiftWidths(items: List<Item>): List<Int> =
        if (items.any { it.breakdown.minutesOf(Concept.SUNDAY_NO_REST) > 0 }) {
            SHIFT_WIDTHS.dropLast(1) + NO_REST_WIDTH + SHIFT_WIDTHS.last()
        } else {
            SHIFT_WIDTHS
        }
    private val EXTRA_WIDTHS = listOf(12, 12, 12, 10, 40, 12, 24, 40)

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Turnos" sheetId="1" r:id="rId1"/><sheet name="Extras autorizadas" sheetId="2" r:id="rId2"/></sheets></workbook>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="&quot;$&quot;#,##0"/></numFmts><fonts count="4"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="14"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF0B6E4F"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="8"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="0" fontId="3" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="2" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/><xf numFmtId="164" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
