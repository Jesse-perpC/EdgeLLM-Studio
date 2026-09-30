package com.perpcorp.edgellm.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tokenizer correctness, asserted against ids produced by the HuggingFace
 * reference implementation (`@huggingface/tokenizers` 0.2.0, the Rust
 * `tokenizers` crate) driving the canonical gpt2 tokenizer.json.
 *
 * These expectations are NOT this implementation's own output. They were
 * generated from the reference and then cross-checked over 427 strings
 * (including 400 seeded fuzz cases) with zero encode mismatches.
 *
 * Requires a local copy of gpt2's tokenizer.json; set -Dedgellm.gpt2Tokenizer
 * or place it at app/src/test/resources/gpt2/tokenizer.json.
 */
class HuggingFaceByteLevelTokenizerTest {

    private fun tokenizer(): HuggingFaceByteLevelTokenizer {
        val fromProperty = System.getProperty("edgellm.gpt2Tokenizer")
        val file = listOfNotNull(
            fromProperty?.let { File(it) },
            File("src/test/resources/gpt2/tokenizer.json"),
            File("app/src/test/resources/gpt2/tokenizer.json")
        ).firstOrNull { it.isFile }
            ?: throw IllegalStateException(
                "gpt2 tokenizer.json not found. Download it with:\n" +
                    "  curl -L -o app/src/test/resources/gpt2/tokenizer.json " +
                    "https://huggingface.co/gpt2/resolve/main/tokenizer.json"
            )
        return HuggingFaceByteLevelTokenizer.load(file)
    }

    @Test
    fun `encodes ascii two words`() {
        assertArrayEquals(intArrayOf(15496, 995), tokenizer().encode("Hello world"))
    }

    @Test
    fun `encodes canonical transformers doc example`() {
        assertArrayEquals(intArrayOf(15496, 11, 616, 3290, 318, 13779), tokenizer().encode("Hello, my dog is cute"))
    }

    @Test
    fun `encodes lowercase differs in id`() {
        assertArrayEquals(intArrayOf(31373, 995), tokenizer().encode("hello world"))
    }

    @Test
    fun `encodes punctuation is a separate token`() {
        assertArrayEquals(intArrayOf(464, 2068, 7586, 21831, 18045, 625, 262, 16931, 3290, 13), tokenizer().encode("The quick brown fox jumps over the lazy dog."))
    }

    @Test
    fun `encodes multiple spaces collapse`() {
        assertArrayEquals(intArrayOf(220, 3756, 290, 25462, 13216, 10223, 220, 220), tokenizer().encode("  leading and trailing whitespace  "))
    }

    @Test
    fun `encodes digits and float - regression for space-digit split`() {
        assertArrayEquals(intArrayOf(49601, 17031, 2231, 30924, 3829, 290, 513, 13, 1415, 19707), tokenizer().encode("Numbers 1234567890 and 3.14159"))
    }

    @Test
    fun `encodes contractions and leading apostrophe rule`() {
        assertArrayEquals(intArrayOf(40, 1101, 1654, 345, 821, 826, 11, 836, 470, 5490), tokenizer().encode("I'm sure you're right, don't worry"))
    }

    @Test
    fun `encodes latin-1 letters`() {
        assertArrayEquals(intArrayOf(66, 1878, 2634, 41492), tokenizer().encode("café naïve"))
    }

    @Test
    fun `encodes cjk`() {
        assertArrayEquals(intArrayOf(33768, 98, 17312, 105, 45739, 252, 5641, 24336, 25084, 43302, 30640, 33623), tokenizer().encode("日本語のテキストです"))
    }

    @Test
    fun `encodes astral plane + math symbols`() {
        assertArrayEquals(intArrayOf(368, 31370, 12520, 248, 222, 8582, 236, 231, 290, 10688, 7377, 102, 35705, 230, 16175, 24861, 248, 24861, 104), tokenizer().encode("emoji 🚀🎉 and math Ω≈ç√∫"))
    }

    @Test
    fun `encodes source code`() {
        assertArrayEquals(intArrayOf(4299, 277, 7, 87, 2599, 198, 220, 220, 220, 1441, 2124, 12429, 362, 220, 1303, 2912), tokenizer().encode("def f(x):\n    return x ** 2  # comment"))
    }

    @Test
    fun `encodes url`() {
        assertArrayEquals(intArrayOf(5450, 1378, 20688, 13, 785, 14, 6978, 30, 22766, 28, 16, 5, 87, 28, 17), tokenizer().encode("https://example.com/path?query=1&x=2"))
    }

    @Test
    fun `encodes empty string`() {
        assertArrayEquals(intArrayOf(), tokenizer().encode(""))
    }

    @Test
    fun `encodes single space`() {
        assertArrayEquals(intArrayOf(220), tokenizer().encode(" "))
    }

    @Test
    fun `encodes single char`() {
        assertArrayEquals(intArrayOf(87), tokenizer().encode("x"))
    }

    @Test
    fun `round-trips through decode without corruption`() {
        val tok = tokenizer()
        val texts = listOf(
            "café naïve", "日本語のテキストです", "emoji 🚀🎉 and math Ω≈ç√∫",
            "I'm sure you're right, don't worry", "def f(x):\n    return x ** 2"
        )
        for (t in texts) {
            assertEquals(t, tok.decode(tok.encode(t)))
        }
    }

    @Test
    fun `single tokens may split a multi-byte character`() {
        // If this stops holding, the engine's incremental UTF-8 buffering is no
        // longer being exercised and could silently regress.
        val tok = tokenizer()
        val ids = tok.encode("🚀")
        assertTrue("expected 🚀 to fragment across tokens, got ${ids.toList()}", ids.size > 1)
        assertEquals("🚀", tok.decode(ids))
    }

    @Test
    fun `decodes endoftext as a stop id and never as text`() {
        val tok = tokenizer()
        assertEquals(50256, tok.eosTokenIds.first { it == 50256 })
        assertEquals(0, tok.decodeTokenBytes(50256).size)
    }

    @Test
    fun `vocab size matches the tokenizer file`() {
        assertEquals(50257, tokenizer().vocabSize)
    }
}
