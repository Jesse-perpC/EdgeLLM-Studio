package com.perpcorp.edgellm.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Minimum surface an ONNX decoder model needs to turn text into `input_ids` and
 * generated ids back into text. Deliberately tiny so a SentencePiece-backed
 * implementation can be dropped in without touching the engine.
 */
interface LlmTokenizer {
    val vocabSize: Int
    val eosTokenIds: Set<Int>
    fun encode(text: String): IntArray
    /**
     * Raw bytes for one id, still undecoded. Stays a ByteArray because a single
     * BPE token can be a fragment of a multi-byte UTF-8 sequence, so it cannot be
     * turned into a String until the caller's byte buffer holds a complete one.
     */
    fun decodeTokenBytes(id: Int): ByteArray
    fun decode(ids: IntArray): String
}

/**
 * Reader for HuggingFace `tokenizer.json`, restricted to byte-level BPE.
 *
 * Supports the GPT-2 family (GPT-2, Qwen2/2.5, SmolLM, CodeGen, Phi-3-GPT),
 * which is what almost every decoder-only `.onnx` export on the Hub uses.
 *
 * Deliberately refuses anything it cannot do correctly. SentencePiece `Unigram`
 * and `Metaspace` models (Gemma, Llama-2, Mistral) and tiktoken `Unigram` fail
 * loudly here rather than silently producing wrong ids, which is what a
 * hand-rolled fallback would do.
 */
class HuggingFaceByteLevelTokenizer private constructor(
    private val tokenToId: Map<String, Int>,
    private val idToToken: Map<Int, String>,
    private val mergeRanks: Map<String, Int>,
    private val splitRegex: Regex,
    private val addedTokenToId: Map<String, Int>,
    private val specialIds: Set<Int>,
    private val byteEncoder: Map<Int, String>,
    private val byteDecoder: Map<String, Int>,
    private val unkId: Int,
    override val eosTokenIds: Set<Int>
) : LlmTokenizer {

    override val vocabSize: Int get() = idToToken.size

    companion object {
        private val EMPTY = ByteArray(0)

        /**
         * Beyond the model's declared `eos_token`, chat-tuned exports frequently
         * also stop on these. Missing them produces a model that keeps generating
         * after the end of its turn instead of returning control.
         */
        private val KNOWN_STOP_TOKENS = setOf(
            "<|endoftext|>",
            "<|im_end|>",
            "<|eot_id|>",
            "<|end_of_text|>",
            "<|eom_id|>",
            "<|end_of_sentence|>",
            "<|end_sentence|>",
            "<|assistant_end|>",
            "<end_of_turn>",
            "[EOS]"
        )

        /** GPT-2 pre-tokenization pattern; the default for ByteLevel tokenizers. */
        private const val GPT2_SPLIT_PATTERN =
            "'(?:s|t|re|ve|m|ll|d)| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+"

        fun load(tokenizerJson: File): HuggingFaceByteLevelTokenizer {
            require(tokenizerJson.isFile) {
                "tokenizer.json not found at ${tokenizerJson.absolutePath}"
            }
            val root = JSONObject(tokenizerJson.readText())

            val model = root.optJSONObject("model")
                ?: throw UnsupportedOperationException("tokenizer.json has no 'model' object")
            // Older exports (including the canonical GPT-2 tokenizer.json) omit
            // model.type entirely, so only an explicitly different type is fatal.
            val type = model.optString("type")
            if (type.isNotEmpty() && type != "BPE") {
                throw UnsupportedOperationException(
                    "Unsupported tokenizer model.type='$type'. This engine implements byte-level " +
                        "BPE only (GPT-2/Qwen2/SmolLM/CodeGen). Convert SentencePiece models " +
                        "(Gemma, Llama-2, Mistral) to byte-level BPE before ONNX export, or add " +
                        "an LlmTokenizer implementation for them."
                )
            }

            // vocab is a token -> id object in tokenizer.json (not a list).
            val tokenToId = LinkedHashMap<String, Int>()
            val vocabObj = model.optJSONObject("vocab")
                ?: throw UnsupportedOperationException("tokenizer.json has no model.vocab")
            for (key in vocabObj.keys()) {
                tokenToId[key] = vocabObj.getInt(key)
            }
            if (tokenToId.isEmpty()) {
                throw UnsupportedOperationException("model.vocab is empty")
            }
            val idToToken = tokenToId.entries.associate { (t, i) -> i to t }

            // merges: either ["a b", ...] (legacy) or [["a","b"], ...] (current).
            val mergeRanks = HashMap<String, Int>()
            val merges = model.optJSONArray("merges")
            if (merges != null) {
                for (i in 0 until merges.length()) {
                    val pair: String = when (val item = merges.get(i)) {
                        is String -> item
                        is JSONArray -> "${item.getString(0)} ${item.getString(1)}"
                        else -> continue
                    }
                    mergeRanks[pair] = i
                }
            }

            // Byte-level mapping is the whole basis of encode/decode here; without
            // it every id would be wrong, so verify rather than assume.
            val byteEncoder = bytesToUnicode()
            val byteDecoder = HashMap<String, Int>(byteEncoder.size)
            for ((b, s) in byteEncoder) byteDecoder[s] = b

            val (addedTokenToId, specialIds) = readAddedTokens(root, tokenToId)
            val splitRegex = buildSplitRegex(root)

            val unkId = tokenToId["<|endoftext|>"]
                ?: tokenToId["[UNK]"]
                ?: tokenToId["<unk>"]
                ?: throw UnsupportedOperationException(
                    "tokenizer.json has no unknown token; cannot encode out-of-vocabulary input"
                )

            val eos = LinkedHashSet<Int>()
            readEosToken(root, tokenToId)?.let(eos::add)
            for (content in KNOWN_STOP_TOKENS) {
                addedTokenToId[content]?.let(eos::add)
            }
            if (eos.isEmpty()) {
                throw UnsupportedOperationException(
                    "tokenizer.json declares no eos_token; the engine cannot tell when to stop"
                )
            }

            return HuggingFaceByteLevelTokenizer(
                tokenToId = tokenToId,
                idToToken = idToToken,
                mergeRanks = mergeRanks,
                splitRegex = splitRegex,
                addedTokenToId = addedTokenToId,
                specialIds = specialIds,
                byteEncoder = byteEncoder,
                byteDecoder = byteDecoder,
                unkId = unkId,
                eosTokenIds = eos
            )
        }

        /** Returns (literal -> id, ids that must never be emitted as text). */
        private fun readAddedTokens(
            root: JSONObject,
            tokenToId: Map<String, Int>
        ): Pair<MutableMap<String, Int>, Set<Int>> {
            val map = LinkedHashMap<String, Int>()
            val special = HashSet<Int>()
            val arr = root.optJSONArray("added_tokens")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val content = o.optString("content")
                    if (content.isEmpty()) continue
                    val id = when {
                        o.has("id") -> o.getInt("id")
                        else -> tokenToId[content] ?: continue
                    }
                    map[content] = id
                    if (o.optBoolean("special", false)) special += id
                }
            }
            return map to special
        }

        private fun readEosToken(root: JSONObject, tokenToId: Map<String, Int>): Int? {
            val eos = root.opt("eos_token") ?: return null
            return when (eos) {
                is String -> tokenToId[eos]
                is JSONObject -> eos.optString("content").let { tokenToId[it] }
                else -> null
            }
        }

        /**
         * Uses the Split pattern declared inside the pre_tokenizer when present,
         * which is how Llama-3 style tiktoken regexes get honored. Falls back to
         * the GPT-2 pattern for a bare ByteLevel pre-tokenizer.
         */
        private fun buildSplitRegex(root: JSONObject): Regex {
            val pre = root.optJSONObject("pre_tokenizer")
            if (pre != null) {
                findSplitPattern(pre)?.let { return Regex(it) }
            }
            return Regex(GPT2_SPLIT_PATTERN)
        }

        private fun findSplitPattern(node: JSONObject): String? {
            when (node.optString("type")) {
                "Sequence" -> {
                    val items = node.optJSONArray("pretokenizers")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val child = items.optJSONObject(i) ?: continue
                            findSplitPattern(child)?.let { return it }
                        }
                    }
                }
                "Split" -> node.optString("pattern")
                    .takeIf { it.isNotEmpty() && it != "default" }
            }
            return null
        }

        /**
         * The GPT-2 byte-to-unicode table: printable ASCII and Latin-1 map to
         * themselves, every other byte is shifted past 0xFF so it survives a
         * round trip through JSON text without becoming a control or surrogate.
         */
        private fun bytesToUnicode(): Map<Int, String> {
            val bs = ArrayList<Int>(256)
            for (i in 33..126) bs.add(i)
            for (i in 161..172) bs.add(i)
            for (i in 174..255) bs.add(i)
            val cs = ArrayList<Int>(bs)
            var n = 0
            for (b in 0..255) {
                if (b !in bs) {
                    bs.add(b)
                    cs.add(256 + n)
                    n++
                }
            }
            val out = HashMap<Int, String>(256)
            for (i in bs.indices) {
                out[bs[i]] = String(Character.toChars(cs[i]))
            }
            return out
        }
    }

    override fun encode(text: String): IntArray {
        val out = ArrayList<Int>(text.length / 2 + 8)
        for (piece in splitAddedTokens(text)) {
            if (piece.id >= 0) {
                out.add(piece.id)
                continue
            }
            // Split the RAW text first, then byte-encode each piece. This ordering
            // is load-bearing: byte-level maps space to U+0120 (a *letter*) and
            // maps most non-ASCII bytes to a mix of letters and symbols, so running
            // the regex over the already-encoded string cuts inside " 1234" and
            // inside a single character like "é". Verified against the HuggingFace
            // reference implementation; reversing these two steps breaks both.
            for (match in splitRegex.findAll(piece.text)) {
                out.addAll(bpe(toByteLevel(match.value)))
            }
        }
        return out.toIntArray()
    }

    override fun decodeTokenBytes(id: Int): ByteArray {
        if (id in specialIds) return EMPTY
        val token = idToToken[id] ?: return EMPTY
        val out = ByteArray(token.length)
        var n = 0
        for (ch in token) {
            val b = byteDecoder[ch.toString()]
            if (b != null) {
                out[n++] = b.toByte()
            } else {
                // Not byte-level encoded; fall back to this character's UTF-8 bytes.
                for (bb in ch.toString().toByteArray(Charsets.UTF_8)) out[n++] = bb
            }
        }
        return if (n == out.size) out else out.copyOf(n)
    }

    override fun decode(ids: IntArray): String {
        val bytes = java.io.ByteArrayOutputStream()
        for (id in ids) bytes.write(decodeTokenBytes(id))
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private class Piece(val text: String, val id: Int)

    /**
     * Splits out added-token literals (e.g. `<|im_start|>`) so they bypass BPE.
     * Without this a chat model sees them as ordinary text and never emits them.
     */
    private fun splitAddedTokens(text: String): List<Piece> {
        if (addedTokenToId.isEmpty()) return listOf(Piece(text, -1))
        val literals = addedTokenToId.keys.sortedByDescending { it.length }
        val out = ArrayList<Piece>(4)
        var rest = text
        while (rest.isNotEmpty()) {
            var bestIdx = -1
            var bestLit: String? = null
            for (lit in literals) {
                val idx = rest.indexOf(lit)
                if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                    bestIdx = idx
                    bestLit = lit
                }
            }
            if (bestLit == null) {
                out.add(Piece(rest, -1))
                break
            }
            if (bestIdx > 0) out.add(Piece(rest.substring(0, bestIdx), -1))
            out.add(Piece(bestLit, addedTokenToId.getValue(bestLit)))
            rest = rest.substring(bestIdx + bestLit.length)
        }
        return out
    }

    private fun toByteLevel(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length * 2)
        for (b in text.toByteArray(Charsets.UTF_8)) {
            sb.append(byteEncoder[b.toInt() and 0xFF])
        }
        return sb.toString()
    }

    /** Greedy lowest-rank-first merge, matching the reference BPE exactly. */
    private fun bpe(token: String): List<Int> {
        if (token.isEmpty()) return emptyList()
        var word = ArrayList<String>(token.length)
        for (ch in token) word.add(ch.toString())
        if (word.size == 1) return listOf(tokenToId[word[0]] ?: unkId)

        while (word.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestIdx = -1
            for (i in 0 until word.size - 1) {
                val rank = mergeRanks[word[i] + " " + word[i + 1]] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestIdx = i
                }
            }
            if (bestIdx < 0) break
            val merged = word[bestIdx] + word[bestIdx + 1]
            val next = ArrayList<String>(word.size - 1)
            var i = 0
            while (i < word.size) {
                if (i == bestIdx) {
                    next.add(merged)
                    i += 2
                } else {
                    next.add(word[i])
                    i++
                }
            }
            word = next
        }
        return word.map { tokenToId[it] ?: unkId }
    }
}
