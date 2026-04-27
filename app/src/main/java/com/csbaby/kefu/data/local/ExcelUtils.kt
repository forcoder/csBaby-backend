package com.csbaby.kefu.data.local

import android.util.Xml
import org.xmlpull.v1.XmlSerializer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.io.StringWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Excel (.xlsx) 工具类
 * 零第三方依赖，使用 ZipOutputStream + XmlSerializer 生成标准 xlsx 文件
 * 复用 KnowledgeBaseManager 中的 xlsx 解析思路
 */
object ExcelUtils {

    private const val CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"

    /**
     * 将多个 Sheet 数据写入 xlsx 文件
     * @param sheets Sheet 列表，每个 Sheet 包含名称和行数据（第一行为表头）
     * @param outputStream 输出流
     */
    fun writeXlsx(sheets: List<ExcelSheet>, outputStream: OutputStream) {
        BufferedOutputStream(outputStream).use { buffered ->
            ZipOutputStream(buffered).use { zipOut ->
                // [Content_Types].xml
                zipOut.putNextEntry(ZipEntry("[Content_Types].xml"))
                zipOut.write(generateContentTypes(sheets).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // _rels/.rels
                zipOut.putNextEntry(ZipEntry("_rels/.rels"))
                zipOut.write(generateRels().toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // xl/_rels/workbook.xml.rels
                zipOut.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                zipOut.write(generateWorkbookRels(sheets).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // xl/workbook.xml
                zipOut.putNextEntry(ZipEntry("xl/workbook.xml"))
                zipOut.write(generateWorkbook(sheets).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // xl/styles.xml
                zipOut.putNextEntry(ZipEntry("xl/styles.xml"))
                zipOut.write(generateStyles().toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // xl/worksheets/sheetN.xml
                sheets.forEachIndexed { index, sheet ->
                    zipOut.putNextEntry(ZipEntry("xl/worksheets/sheet${index + 1}.xml"))
                    zipOut.write(generateWorksheet(sheet).toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }
            }
        }
    }

    data class ExcelSheet(
        val name: String,
        val rows: List<List<String>> // 第一行是表头
    )

    private fun generateContentTypes(sheets: List<ExcelSheet>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        sb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        sb.append("""<Default Extension="xml" ContentType="application/xml"/>""")
        sheets.forEachIndexed { index, _ ->
            sb.append("""<Override PartName="/xl/worksheets/sheet${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        sb.append("""<Override PartName="/xl/workbook.xml" ContentType="$CONTENT_TYPE"/>""")
        sb.append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        sb.append("</Types>")
        return sb.toString()
    }

    private fun generateRels(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun generateWorkbookRels(sheets: List<ExcelSheet>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sheets.forEachIndexed { index, _ ->
            sb.append("""<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${index + 1}.xml"/>""")
        }
        sb.append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun generateWorkbook(sheets: List<ExcelSheet>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("<sheets>")
        sheets.forEachIndexed { index, sheet ->
            val safeName = sheet.name.take(31) // Excel sheet name max length
            sb.append("""<sheet name="$safeName" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        sb.append("</sheets>")
        sb.append("</workbook>")
        return sb.toString()
    }

    private fun generateStyles(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
    <fonts count="1">
        <font><sz val="11"/></font>
    </fonts>
    <fills count="1">
        <fill><patternFill patternType="none"/></fill>
    </fills>
    <borders count="1">
        <border><left/><right/><top/><bottom/></border>
    </borders>
    <cellStyleXfs count="1">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
    </cellStyleXfs>
    <cellXfs count="1">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    </cellXfs>
</styleSheet>"""
    }

    private fun generateWorksheet(sheet: ExcelSheet): String {
        val serializer = Xml.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)

        serializer.startTag(null, "worksheet")
        serializer.attribute(null, "xmlns", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")
        serializer.attribute(null, "xmlns:r", "http://schemas.openxmlformats.org/officeDocument/2006/relationships")

        serializer.startTag(null, "sheetData")

        sheet.rows.forEachIndexed { rowIndex, row ->
            serializer.startTag(null, "row")
            serializer.attribute(null, "r", "${rowIndex + 1}")

            row.forEachIndexed { colIndex, cellValue ->
                val colLetter = columnToLetters(colIndex)
                val cellRef = "$colLetter${rowIndex + 1}"

                serializer.startTag(null, "c")
                serializer.attribute(null, "r", cellRef)

                if (cellValue.isNotEmpty()) {
                    // 判断是否为数字
                    val numValue = cellValue.toDoubleOrNull()
                    if (numValue != null) {
                        serializer.startTag(null, "v")
                        serializer.text(cellValue)
                        serializer.endTag(null, "v")
                    } else {
                        // 字符串类型
                        serializer.attribute(null, "t", "inlineStr")
                        serializer.startTag(null, "is")
                        serializer.startTag(null, "t")
                        serializer.text(cellValue)
                        serializer.endTag(null, "t")
                        serializer.endTag(null, "is")
                    }
                }

                serializer.endTag(null, "c")
            }

            serializer.endTag(null, "row")
        }

        serializer.endTag(null, "sheetData")
        serializer.endTag(null, "worksheet")
        serializer.endDocument()

        return writer.toString()
    }

    /**
     * 将列索引（0-based）转换为 Excel 列字母（A, B, ..., Z, AA, AB, ...）
     */
    private fun columnToLetters(colIndex: Int): String {
        val sb = StringBuilder()
        var n = colIndex
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }
}
