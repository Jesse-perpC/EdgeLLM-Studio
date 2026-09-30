package com.perpcorp.edgellm.engine

import java.io.File
import java.text.Normalizer

/**
 * BERT WordPiece tokenization for `.tflite` classifiers such as MobileBERT.
 *
 * Reads the model's `vocab.txt` (one token per line, line number = id) that
 * ships next to the `.tflite` file. Implements the uncased pipeline exactly:
 * lowercase, accent strip, CJK spacing, punctuation split, then greedy
 * longest-match WordPiece with `##` continuations. Anything else — cased
 * vocabs, SentencePiece `▁`, custom normalizers — fails loudly instead of
 * producing silently wrong ids.
 */
class WordPieceTokenizer private constructor(
    private val vocab: List<String>,
    private val tokenToId: Map<String, Int>,
    private val doLowerCase: Boolean,
    val unkId: Int,
    val clsId: Int,
    val sepId: Int,
    val padId: Int
) {
    val vocabSize: Int get() = vocab.size

    companion object {
        private const val MAX_INPUT_CHARS_PER_WORD = 200

        fun load(vocabTxt: File, doLowerCase: Boolean = true): WordPieceTokenizer {
            require(vocabTxt.isFile) {
                "vocab.txt not found at ${vocabTxt.absolutePath}"
            }
            val vocab = vocabTxt.readLines().map { it.trimEnd('\n', '\r') }
            if (vocab.isEmpty()) {
                throw UnsupportedOperationException("vocab.txt is empty")
            }
            val tokenToId = HashMap<String, Int>(vocab.size * 2)
            for ((i, t) in vocab.withIndex()) {
                // First occurrence wins, matching the reference behavior for
                // duplicated lines.
                if (!tokenToId.containsKey(t)) tokenToId[t] = i
            }
            fun idOf(token: String): Int = tokenToId[token]
                ?: throw UnsupportedOperationException(
                    "vocab.txt has no $token; not a BERT WordPiece vocabulary"
                )
            return WordPieceTokenizer(
                vocab = vocab,
                tokenToId = tokenToId,
                doLowerCase = doLowerCase,
                unkId = idOf("[UNK]"),
                clsId = idOf("[CLS]"),
                sepId = idOf("[SEP]"),
                padId = idOf("[PAD]")
            )
        }
    }

    /** Full BERT input preparation: `[CLS] pieces [SEP]`, no padding. */
    fun encode(text: String): IntArray {
        val out = ArrayList<Int>(32)
        out.add(clsId)
        for (token in basicTokenize(text)) {
            out.addAll(wordpiece(token))
        }
        out.add(sepId)
        return out.toIntArray()
    }

    fun decode(ids: IntArray): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (id == clsId || id == sepId || id == padId) continue
            val token = vocab.getOrNull(id) ?: continue
            if (token.startsWith("##")) {
                sb.append(token.substring(2))
            } else {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(token)
            }
        }
        return sb.toString()
    }

    private fun basicTokenize(text: String): List<String> {
        var s = if (doLowerCase) text.lowercase() else text
        // Strip accents (NFD then drop Mn), matching BertNormalizer(stripAccents).
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        // Space out every CJK codepoint so each becomes its own token.
        val spaced = StringBuilder(s.length * 2)
        for (ch in s) {
            if (isCjk(ch)) {
                spaced.append(' ').append(ch).append(' ')
            } else {
                spaced.append(ch)
            }
        }
        // Split punctuation into standalone tokens, flushing on whitespace.
        val out = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotEmpty()) {
                out.add(cur.toString())
                cur.clear()
            }
        }
        for (ch in spaced) {
            if (ch.isWhitespace()) {
                flush()
            } else if (isPunctuation(ch)) {
                flush()
                out.add(ch.toString())
            } else {
                cur.append(ch)
            }
        }
        flush()
        return out
    }

    private fun wordpiece(token: String): List<Int> {
        if (token.length > MAX_INPUT_CHARS_PER_WORD) return listOf(unkId)
        val ids = ArrayList<Int>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var curId = -1
            while (start < end) {
                var sub = token.substring(start, end)
                if (start > 0) sub = "##$sub"
                val id = tokenToId[sub]
                if (id != null) {
                    curId = id
                    break
                }
                end--
            }
            if (curId < 0) return listOf(unkId)
            ids.add(curId)
            start = end
        }
        return ids
    }

    private fun isCjk(ch: Char): Boolean {
        val v = ch.code
        return v in 0x4E00..0x9FFF || v in 0x3400..0x4DBF || v in 0x20000..0x2A6DF ||
            v in 0x2A600..0x2B73F || v in 0x2B740..0x2B81F || v in 0x2B820..0x2CEAF ||
            v in 0xF900..0xFAFF || v in 0x2F800..0x2FA1F
    }

    private fun isPunctuation(ch: Char): Boolean {
        // Matches HF's _is_punctuation exactly: ASCII 33-47, 58-64, 91-96,
        // 123-126 are punctuation regardless of Unicode category. This matters
        // because + < = > | ~ ^ ` $ are Sm/Sc/Sk, not P — without this branch
        // "C++" never splits and wordpieces to C, ##+, ##+ instead of C, +, +.
        val v = ch.code
        if ((v in 33..47) || (v in 58..64) || (v in 91..96) || (v in 123..126)) return true
        val type = Character.getType(ch)
        return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
            type == Character.DASH_PUNCTUATION.toInt() ||
            type == Character.START_PUNCTUATION.toInt() ||
            type == Character.END_PUNCTUATION.toInt() ||
            type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            type == Character.OTHER_PUNCTUATION.toInt()
    }
}
