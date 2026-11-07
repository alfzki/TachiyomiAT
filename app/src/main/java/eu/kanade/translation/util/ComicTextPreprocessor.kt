package eu.kanade.translation.util

import eu.kanade.translation.recognizer.TextRecognizerLanguage
import eu.kanade.translation.translator.TextTranslatorLanguage

object ComicTextPreprocessor {

    private val terminalPunctuation = setOf('.', '?', '!', '…', '"', '\'', '”', '’', ':', ';', ')', ']', '}')

    fun isTerminalPunctuation(char: Char?): Boolean {
        return char != null && char in terminalPunctuation
    }

    fun isLikelyOcrNoiseOrWatermark(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.equals("RTMTH", ignoreCase = true)) return true

        // Filter punctuation-only noise unless it's valid comic speech punctuation
        if (trimmed.none { it.isLetterOrDigit() }) {
            val allowedPunctuation = setOf("...", "!?", "?!", "!", "?", "!!", "???", "??", "--")
            if (trimmed !in allowedPunctuation) {
                return true
            }
        }

        // Single character artifacts (e.g. speed line or edge artifact misread as 'I', 'l', '|', 'A')
        if (trimmed.length == 1 && !trimmed[0].isDigit() && trimmed != "?" && trimmed != "!") {
            return true
        }

        // Pseudo-hex / zero-heavy background pattern noise
        val pseudoHexNoiseChars = setOf('0', 'o', 'O', 'e', 'E', 's', 'S', 'd', 'D', '4', ' ')
        if (trimmed.all { it in pseudoHexNoiseChars } && trimmed.count { it == '0' } >= 3) {
            return true
        }

        if (trimmed.length >= 4) {
            val nonWhitespace = trimmed.filterNot { it.isWhitespace() }
            if (nonWhitespace.isNotEmpty()) {
                val digitCount = nonWhitespace.count { it.isDigit() }
                if (digitCount.toFloat() / nonWhitespace.length.toFloat() >= 0.40f) {
                    val hasDigitContextWord = Regex(
                        "(?i)\\b(?:chapter|ch|volume|vol|page|lvl|level|hp|mp|exp|episode|ep|season|part|pt|rank|years?|number|no|am|pm|days?|hours?|mins?|minutes?|secs?|seconds?)\\b",
                    ).containsMatchIn(trimmed)
                    if (!hasDigitContextWord) {
                        return true
                    }
                }
            }
        }

        // Obvious website watermarks / scanlation URLs
        val urlPattern =
            Regex(
                "(?i)\\b(?:www\\.[a-z0-9-]+\\.[a-z]{2,}|[a-z0-9-]+\\.(?:com|net|org|gg|io|me|to|xyz|site|online|manga|scans))\\b",
            )
        if (urlPattern.containsMatchIn(trimmed)) {
            if (trimmed.length < 80 || trimmed.lines().size <= 2) {
                return true
            }
        }

        // Scanlation credits
        val creditPattern =
            Regex("(?i)\\b(?:read\\s+(?:manga|first|at)|uploaded\\s+by|scanlated\\s+by|translated\\s+by)\\b")
        if (creditPattern.containsMatchIn(trimmed) && trimmed.length < 80) {
            return true
        }

        return false
    }

    fun normalizeWhitespaceAndHyphens(text: String): String {
        return text
            .replace(Regex("([A-Za-z])-\\s*\\n\\s*([A-Za-z])"), "$1$2")
            .replace(Regex("-\\s*\\n\\s*"), "")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun replacePreservingCase(text: String, pattern: String, replacementUpper: String): String {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return regex.replace(text) { match ->
            val matched = match.value
            val letters = matched.filter { it.isLetter() }
            when {
                letters.isNotEmpty() && letters.all { it.isUpperCase() } -> replacementUpper
                letters.isNotEmpty() && letters.all { it.isLowerCase() } -> replacementUpper.lowercase()
                matched.firstOrNull()?.isUpperCase() == true -> {
                    val words = replacementUpper.split(" ")
                    words.joinToString(" ") { word ->
                        if (word.isNotEmpty()) {
                            word.lowercase().replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase() else it.toString()
                            }
                        } else {
                            ""
                        }
                    }
                }
                else -> replacementUpper
            }
        }
    }

    fun fixCommonComicOcrTypos(text: String): String {
        var result = text

        // P! misread as ?! in comic all-caps fonts
        result = result.replace(Regex("\\b([A-Z]{2,})P!\\b"), "$1?!")

        // Specific comic vocabulary typos
        result = result.replace(Regex("(?i)\\bANYNATSP\\b"), "ANYWAYS?")
            .replace(Regex("(?i)\\bANYNATS\\b"), "ANYWAYS")
            .replace(Regex("(?i)\\bCONSERUENCESP\\b"), "CONSEQUENCES?")
            .replace(Regex("(?i)\\bCONSERUENCES\\b"), "CONSEQUENCES")
            .replace(Regex("\\bSD\\s+WH(?:Y)?\\b"), "SO WHY")
            .replace(Regex("\\bSD\\s+(?=I\\b|YOU\\b|HE\\b|SHE\\b|WE\\b|THEY\\b|IT\\b|THAT\\b)"), "SO ")
            .replace(Regex("(?i)\\bSLDNLH\\b"), "SLOWLY")
            .replace(
                Regex(
                    "(?i)\\bVY\\s+(?=TEAM|GOD|LIFE|HAND|HEAD|EYES|HEART|BODY|FAULT|OWN|SELF|SON|KID|WIFE|FAMILY|GUILD|RANK|MANA)\\b",
                ),
                "MY ",
            )
            .replace(
                Regex(
                    "(?i)\\bHON\\s+(?=AM|ARE|IS|WAS|WERE|CAN|COULD|DO|DOES|DID|WILL|WOULD|SHOULD|MUCH|MANY|LONG|FAR)\\b",
                ),
                "HOW ",
            )
            .replace(Regex("(?i)\\bPEAL\\s+WITH\\b"), "DEAL WITH")

        // Number/letter substitutions in contractions
        result = result.replace(Regex("\\b1'M\\b|\\bI'V\\b"), "I'M")
            .replace(Regex("\\b1'LL\\b"), "I'LL")
            .replace(Regex("\\b1'VE\\b"), "I'VE")
            .replace(Regex("\\b1'D\\b"), "I'D")

        // Standalone OCR word corruptions
        result = result.replace(Regex("\\bY0U\\b|\\bYOL\\b"), "YOU")
            .replace(Regex("\\bT0\\b"), "TO")
            .replace(Regex("\\bD0\\b"), "DO")
            .replace(Regex("\\bG0\\b"), "GO")
            .replace(Regex("\\bN0\\b"), "NO")
            .replace(Regex("\\bS0\\b"), "SO")

        // 0/1 inside uppercase words
        result = result.replace(Regex("(?<=[A-Z])0(?=[A-Z])"), "O")
            .replace(Regex("(?<=[A-Z])1(?=[A-Z])"), "I")

        // Missing-apostrophe contractions
        result = replacePreservingCase(result, "\\bDONT\\b", "DON'T")
        result = replacePreservingCase(result, "\\bDOESNT\\b", "DOESN'T")
        result = replacePreservingCase(result, "\\bDIDNT\\b", "DIDN'T")
        result = replacePreservingCase(result, "\\bCANT\\b", "CAN'T")
        result = replacePreservingCase(result, "\\bWONT\\b", "WON'T")
        result = replacePreservingCase(result, "\\bISNT\\b", "ISN'T")
        result = replacePreservingCase(result, "\\bARENT\\b", "AREN'T")
        result = replacePreservingCase(result, "\\bWASNT\\b", "WASN'T")
        result = replacePreservingCase(result, "\\bWERENT\\b", "WEREN'T")
        result = replacePreservingCase(result, "\\bHAVENT\\b", "HAVEN'T")
        result = replacePreservingCase(result, "\\bHASNT\\b", "HASN'T")
        result = replacePreservingCase(result, "\\bHADNT\\b", "HADN'T")
        result = replacePreservingCase(result, "\\bYOURE\\b", "YOU'RE")
        result = replacePreservingCase(result, "\\bTHEYRE\\b", "THEY'RE")
        result = replacePreservingCase(result, "\\bTHEYVE\\b|\\bTHEYVB\\b", "THEY'VE")
        result = replacePreservingCase(result, "\\bTHEYLL\\b", "THEY'LL")
        result = replacePreservingCase(result, "\\bYOULL\\b", "YOU'LL")
        result = replacePreservingCase(result, "\\bTHATS\\b", "THAT'S")
        result = replacePreservingCase(result, "\\bWHATS\\b", "WHAT'S")
        result =
            replacePreservingCase(
                result,
                "\\bLTS\\s+(?=THE\\b|A\\b|AN\\b|GO\\b|NOT\\b|ONLY\\b|JUST\\b|TOO\\b|SO\\b|VERY\\b|PRETTY\\b)",
                "IT'S ",
            )

        // Comic / manhua / martial arts OCR stroke confusions
        result = replacePreservingCase(result, "\\bOTHEER\\b", "OTHER")
        result = replacePreservingCase(result, "\\bSEGTSAT\\b|\\bSBGTS\\s+AT\\b", "SECTS AT")
        result = replacePreservingCase(result, "\\bSBGTS\\b|\\bSEGTS\\b", "SECTS")
        result = replacePreservingCase(result, "\\bSEGT\\b", "SECT")
        result = replacePreservingCase(result, "\\bDISGIPLES\\b|\\bDICIPLES\\b|\\bDISCPLES\\b", "DISCIPLES")
        result = replacePreservingCase(result, "\\bDISGIPLE\\b|\\bDICIPLE\\b|\\bDISCPLE\\b", "DISCIPLE")
        result = replacePreservingCase(result, "\\bSTL\\b|\\bSTIL\\b|\\bSULL\\b", "STILL")
        result = replacePreservingCase(result, "\\bEXUEND\\b", "EXTEND")
        result = replacePreservingCase(result, "\\bA\\s+CHAND\\b", "A HAND")
        result = replacePreservingCase(result, "\\bFRENDSHIP\\b", "FRIENDSHIP")
        result = replacePreservingCase(result, "\\bFRENDS\\b", "FRIENDS")
        result = replacePreservingCase(result, "\\bFREND\\b", "FRIEND")
        result = replacePreservingCase(result, "\\bPAVLION\\b", "PAVILION")
        result = replacePreservingCase(result, "\\bMEAVEN\\b", "HEAVEN")
        result = replacePreservingCase(result, "\\bAIGH\\s+HEAVEN\\b", "HIGH HEAVEN")
        result = replacePreservingCase(result, "\\bLAMA\\s+DISCIPLE\\b|\\bIAMADISCIPLE\\b", "I AM A DISCIPLE")
        result = replacePreservingCase(result, "\\bHASA\\b", "HAS A")
        result = replacePreservingCase(result, "\\bBYTHE\\b", "BY THE")
        result = replacePreservingCase(result, "\\bACCEPTA\\b", "ACCEPT A")
        result = replacePreservingCase(result, "\\bTHEY\\s+LL\\b", "THEY'LL")
        result = replacePreservingCase(result, "\\bONES\\s+(?=HAND\\b)", "ONE'S ")
        result = replacePreservingCase(result, "\\bCOMPORTIS\\b", "COMFORT IS")
        result = replacePreservingCase(result, "\\bOBVIOUSLYI\\b", "OBVIOUSLY I")
        result = replacePreservingCase(result, "\\bHAPPENAGAIN\\b", "HAPPEN AGAIN")
        result = replacePreservingCase(result, "\\bSPECAL\\b", "SPECIAL")
        result = replacePreservingCase(result, "\\bREQURED\\b", "REQUIRED")
        result = replacePreservingCase(result, "\\bTO\\s+TRAN\\b", "TO TRAIN")
        result = replacePreservingCase(result, "\\bNAMB\\b", "NAME")
        result = replacePreservingCase(result, "\\bEVIGT\\b", "EVICT")
        result = Regex(
            "\\b(EVICT|EVIGT|TO|LET|GIVE|TELL|WITH|FOR|HELP|BEAT|SAVING)(\\s+)(MB|MEB)\\b",
            RegexOption.IGNORE_CASE,
        ).replace(result) { m ->
            val prefix = m.groupValues[1] + m.groupValues[2]
            val mb = m.groupValues[3]
            val me = if (mb.all { it.isLowerCase() }) "me" else "ME"
            prefix + me
        }
        result = replacePreservingCase(result, "\\bBEFOR[ÉÈÊéèê](?=[^a-zA-Z0-9]|$)", "BEFORE")
        result = replacePreservingCase(result, "\\bOBMOUSLY\\b", "OBVIOUSLY")
        result = replacePreservingCase(result, "\\bCAPPEN\\b", "HAPPEN")
        result = replacePreservingCase(result, "\\bCOMPORT\\b", "COMFORT")
        result = replacePreservingCase(result, "\\bITIS\\b", "IT IS")
        result = replacePreservingCase(result, "\\bMISSEDA\\b", "MISSED A")
        result = replacePreservingCase(result, "\\bUPA\\s+(?=BIT\\b|LITTLE\\b|LOT\\b)", "UP A ")
        result = replacePreservingCase(result, "\\bBEDEMOTED\\b", "BE DEMOTED")
        result = replacePreservingCase(result, "\\bTOTRIAL\\b", "TO TRIAL")
        result = Regex(
            "\\b(OF|IN|FOR|WITH|FROM)(\\s+)(TS)(?=\\s+(?:DISCIPLES|MEMBERS|RULES|POWER|SECT)\\b)",
            RegexOption.IGNORE_CASE,
        ).replace(result) { m ->
            val prefix = m.groupValues[1] + m.groupValues[2]
            val ts = m.groupValues[3]
            val its = when {
                ts.all { it.isLowerCase() } -> "its"
                ts.first().isUpperCase() && ts.drop(1).all { it.isLowerCase() } -> "Its"
                else -> "ITS"
            }
            prefix + its
        }
        result = result.replace(Regex("([A-Za-z]{2,})ooo\\b"), "$1...")
        result = replacePreservingCase(result, "\\bYANG\\s+KA\\b", "YANG KAI")
        result = replacePreservingCase(result, "\\bMORNINGI\\b", "MORNING")
        result = replacePreservingCase(result, "\\bTRYNG\\b", "TRYING")
        result = replacePreservingCase(result, "\\bCONMPARED\\b", "COMPARED")
        result = replacePreservingCase(result, "\\bNON\\s+EXISTENT\\b", "NON-EXISTENT")

        return result
    }

    fun preprocessOcrBlockText(rawText: String): String {
        val normalized = normalizeWhitespaceAndHyphens(rawText)
        return fixCommonComicOcrTypos(normalized)
    }

    fun toSentenceCaseForNmt(text: String): String {
        val totalLetters = text.count { it.isLetter() }
        if (totalLetters == 0) return text

        val upperLetters = text.count { it.isUpperCase() }
        if (upperLetters.toFloat() / totalLetters.toFloat() <= 0.60f) {
            return text
        }

        var lower = text.lowercase()

        // 1. Capitalize first letter of string
        lower = lower.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // 2. Capitalize letter at the very start even if preceded by punctuation/quotes/whitespace
        lower = lower.replace(Regex("""^(\s*["'“‘(]*)([a-z])""")) { m ->
            m.groupValues[1] + m.groupValues[2].uppercase()
        }

        // 3. Capitalize first letter after sentence-ending punctuation [.?!…:;]
        lower = lower.replace(Regex("""([.?!…:;]["'”’)]?\s+["'“‘(]?)([a-z])""")) { m ->
            m.groupValues[1] + m.groupValues[2].uppercase()
        }

        // 4. Standalone pronoun 'i' and contractions
        lower = lower.replace(Regex("""\bi\b"""), "I")
            .replace(Regex("""\bi'm\b""", RegexOption.IGNORE_CASE), "I'm")
            .replace(Regex("""\bi'll\b""", RegexOption.IGNORE_CASE), "I'll")
            .replace(Regex("""\bi've\b""", RegexOption.IGNORE_CASE), "I've")
            .replace(Regex("""\bi'd\b""", RegexOption.IGNORE_CASE), "I'd")

        // 5. Common honorifics
        lower = lower.replace(Regex("""\bmr(?:\.|\b)""", RegexOption.IGNORE_CASE), "Mr.")
            .replace(Regex("""\bmrs(?:\.|\b)""", RegexOption.IGNORE_CASE), "Mrs.")
            .replace(Regex("""\bms(?:\.|\b)""", RegexOption.IGNORE_CASE), "Ms.")
            .replace(Regex("""\bdr(?:\.|\b)""", RegexOption.IGNORE_CASE), "Dr.")

        // 6. Common manhwa names
        lower = lower.replace(Regex("""\bsung\b""", RegexOption.IGNORE_CASE), "Sung")
            .replace(Regex("""\bjinwoo\b""", RegexOption.IGNORE_CASE), "Jinwoo")
            .replace(Regex("""\bjin-woo\b""", RegexOption.IGNORE_CASE), "Jin-Woo")

        return lower
    }

    fun prepareForNmt(
        text: String,
        fromLang: TextRecognizerLanguage,
    ): String {
        val preprocessed = preprocessOcrBlockText(text)
        return if (fromLang == TextRecognizerLanguage.ENGLISH) {
            toSentenceCaseForNmt(preprocessed)
        } else {
            preprocessed
        }
    }

    fun postProcessNmtOutput(
        sourceText: String,
        translatedText: String,
        toLang: TextTranslatorLanguage,
    ): String {
        var result = translatedText.trim()
        if (result.isEmpty() || result.equals("RTMTH", ignoreCase = true)) return result

        result = result.replace(Regex("^[-–—]+\\s*"), "").replace(Regex("\\s+[-–—]+$"), "").trim()

        if (toLang == TextTranslatorLanguage.INDONESIAN) {
            // Hunter
            if (Regex("(?i)\\bhunters?\\b").containsMatchIn(sourceText)) {
                result = result.replace(Regex("(?i)\\bpemburu-pemburu\\b"), "Hunter")
                    .replace(Regex("(?i)\\bpemburu\\b"), "Hunter")
            }
            // Dungeon
            if (Regex("(?i)\\bdungeons?\\b").containsMatchIn(sourceText)) {
                result = result.replace(Regex("(?i)\\bpenjara bawah tanah\\b"), "Dungeon")
                    .replace(Regex("(?i)\\bruang bawah tanah\\b"), "Dungeon")
            }
            // Guild
            if (Regex("(?i)\\bguilds?\\b").containsMatchIn(sourceText)) {
                result = result.replace(Regex("(?i)\\bserikat pekerja\\b"), "Guild")
                    .replace(Regex("(?i)\\bserikat\\b"), "Guild")
            }
            // Raid
            if (Regex("(?i)\\braids?\\b").containsMatchIn(sourceText)) {
                result = result.replace(Regex("(?i)\\bpenggerebekan\\b"), "Raid")
                    .replace(Regex("(?i)\\bserbuan\\b"), "Raid")
            }
            // Mana
            if (Regex("(?i)\\bmana\\b").containsMatchIn(sourceText)) {
                result = result.replace(Regex("(?i)\\bmana\\b"), "Mana")
            }
            // Gate
            if (Regex("(?i)\\bgates?\\b").containsMatchIn(sourceText)) {
                result = result.replace(Regex("(?i)\\bpintu gerbang\\b"), "Gerbang")
                    .replace(Regex("(?i)\\bgerbang\\b"), "Gerbang")
            }
            // Rank
            if (Regex("(?i)\\brank\\b").containsMatchIn(sourceText)) {
                result = result.replace(Regex("(?i)\\bperingkat\\s+([A-Za-z0-9]+)\\b"), "Rank $1")
                    .replace(Regex("(?i)\\bperingkat\\b"), "Rank")
            }
            // Awakening
            if (Regex("(?i)\\bawakening\\b").containsMatchIn(sourceText)) {
                result = result.replace(Regex("(?i)\\bkebangkitan\\b"), "Awakening")
            }
            // Tempered Body
            if (Regex("(?i)\\btempered\\s+body\\b").containsMatchIn(sourceText)) {
                result = result.replace(Regex("(?i)\\btubuh\\s+yang\\s+marah\\b"), "Tempered Body")
                    .replace(Regex("(?i)\\btubuh\\s+marah\\s+tubuh\\s+marah\\b"), "Tempered Body")
                    .replace(Regex("(?i)\\btubuh\\s+marah\\b"), "Tempered Body")
            }
        }

        return result
    }

    fun splitJoinedTranslation(
        joinedTranslation: String,
        lenA: Int,
        lenB: Int,
        delimiter: String = " -- ",
    ): Pair<String, String> {
        val trimmed = joinedTranslation.trim()
        if (trimmed.contains(delimiter)) {
            val parts = trimmed.split(delimiter, limit = 2)
            return parts[0].trim() to parts[1].trim()
        }
        for (alt in listOf(" -- ", "--", " || ", "||", " | ", "|", " <#> ", "<#>", " // ", "//", " - ", " — ", " – ")) {
            if (trimmed.contains(alt)) {
                val parts = trimmed.split(alt, limit = 2)
                return parts[0].trim() to parts[1].trim()
            }
        }
        // Fallback: split words proportionally based on source text lengths
        val words = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size <= 1) {
            return trimmed to ""
        }
        val totalLen = (lenA + lenB).coerceAtLeast(1).toFloat()
        val ratioA = lenA.toFloat() / totalLen
        val splitIndex = (words.size * ratioA).toInt().coerceIn(1, words.size - 1)
        val partA = words.subList(0, splitIndex).joinToString(" ")
        val partB = words.subList(splitIndex, words.size).joinToString(" ")
        return partA to partB
    }
}
