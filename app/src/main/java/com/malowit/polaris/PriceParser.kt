package com.malowit.polaris

fun looksAmbiguous(rawText: String): Boolean {
    val trimmed = rawText.trim()

    val phonePlus   = Regex("""^\+\d""")
    val phoneParens = Regex("""\(\d{2,4}\)""")
    val phoneDashes = Regex("""\d{2,3}(-\d{2,4}){2,}""")
    val datePattern = Regex("""\b\d{1,2}[./]\d{1,2}[./]\d{2,4}\b""")
    val timePattern = Regex("""\b\d{1,2}:\d{2}\b""")

    val monthNames = Regex(
        """\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|""" +
                """january|february|march|april|june|july|august|september|october|november|december|""" +
                """янв|фев|мар|апр|ма[йя]|июн|июл|авг|сен|окт|ноя|дек|""" +
                """января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря|""" +
                """январь|февраль|март|апрель|май|июнь|июль|август|сентябрь|октябрь|ноябрь|декабрь)\.?\b""",
        RegexOption.IGNORE_CASE
    )

    return phonePlus.containsMatchIn(trimmed)
            || phoneParens.containsMatchIn(trimmed)
            || phoneDashes.containsMatchIn(trimmed)
            || datePattern.containsMatchIn(trimmed)
            || timePattern.containsMatchIn(trimmed)
            || monthNames.containsMatchIn(trimmed)
}

fun extractPrice(rawText: String): Double? {
    val cleaned = rawText.trim()

    val regex = Regex("""\d[\d\s.,]*\d|\d""")
    val match = regex.find(cleaned) ?: return null
    var numberStr = match.value.replace(" ", "")

    val lastDot = numberStr.lastIndexOf('.')
    val lastComma = numberStr.lastIndexOf(',')
    val decimalSepIndex = maxOf(lastDot, lastComma)

    if (decimalSepIndex == -1) {
        return numberStr.toDoubleOrNull()
    }

    val afterSep = numberStr.substring(decimalSepIndex + 1)
    val isDecimal = afterSep.length in 1..2

    numberStr = if (isDecimal) {
        val integerPart = numberStr.substring(0, decimalSepIndex)
            .replace(".", "").replace(",", "")
        "$integerPart.$afterSep"
    } else {
        numberStr.replace(".", "").replace(",", "")
    }

    return numberStr.toDoubleOrNull()
}