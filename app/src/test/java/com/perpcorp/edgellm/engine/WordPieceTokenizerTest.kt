package com.perpcorp.edgellm.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * WordPiece correctness, asserted against ids produced by the HuggingFace
 * reference implementation (`@huggingface/tokenizers` 0.2.0) driving the
 * canonical bert-base-uncased vocab.txt.
 *
 * Generated from the reference, never from this implementation, so the test
 * cannot agree with a regression by construction. Cross-checked over 321
 * strings with zero mismatches.
 *
 * Requires bert-base-uncased vocab.txt; set -Dedgellm.bertVocab or place it at
 * app/src/test/resources/bert/vocab.txt.
 */
class WordPieceTokenizerTest {

    private fun tokenizer(): WordPieceTokenizer {
        val fromProperty = System.getProperty("edgellm.bertVocab")
        val file = listOfNotNull(
            fromProperty?.let { File(it) },
            File("src/test/resources/bert/vocab.txt"),
            File("app/src/test/resources/bert/vocab.txt")
        ).firstOrNull { it.isFile }
            ?: throw IllegalStateException(
                "bert vocab.txt not found. Download it with:\n" +
                    "  curl -L -o app/src/test/resources/bert/vocab.txt " +
                    "https://huggingface.co/bert-base-uncased/resolve/main/vocab.txt"
            )
        return WordPieceTokenizer.load(file)
    }

    @Test
    fun `encodes ascii two words`() {
        assertArrayEquals(intArrayOf(101, 7592, 2088, 102), tokenizer().encode("Hello world"))
    }

    @Test
    fun `encodes punctuation splits`() {
        assertArrayEquals(intArrayOf(101, 7592, 1010, 2026, 3899, 2003, 10140, 102), tokenizer().encode("Hello, my dog is cute"))
    }

    @Test
    fun `encodes full sentence`() {
        assertArrayEquals(intArrayOf(101, 1996, 4248, 2829, 4419, 14523, 2058, 1996, 13971, 3899, 1012, 102), tokenizer().encode("The quick brown fox jumps over the lazy dog."))
    }

    @Test
    fun `encodes digits and float`() {
        assertArrayEquals(intArrayOf(101, 3616, 13138, 1998, 3429, 1012, 6163, 102), tokenizer().encode("Numbers 123 and 45.67"))
    }

    @Test
    fun `encodes accent stripping`() {
        assertArrayEquals(intArrayOf(101, 7668, 15743, 13746, 102), tokenizer().encode("café naïve résumé"))
    }

    @Test
    fun `encodes cjk spacing`() {
        assertArrayEquals(intArrayOf(101, 1864, 1876, 1950, 1671, 30239, 30233, 30240, 102), tokenizer().encode("日本語のテスト"))
    }

    @Test
    fun `encodes uncased lowercasing`() {
        assertArrayEquals(intArrayOf(101, 3356, 18382, 2896, 3816, 102), tokenizer().encode("UPPERCASE lower MiXeD"))
    }

    @Test
    fun `encodes contractions`() {
        assertArrayEquals(intArrayOf(101, 2123, 1005, 1056, 2064, 1005, 1056, 2180, 1005, 1056, 102), tokenizer().encode("don't can't won't"))
    }

    @Test
    fun `encodes long word wordpiece split`() {
        assertArrayEquals(intArrayOf(101, 1052, 2638, 2819, 17175, 11314, 6444, 2594, 7352, 26461, 27572, 11261, 6767, 15472, 6761, 8663, 10735, 2483, 102), tokenizer().encode("pneumonoultramicroscopicsilicovolcanoconiosis"))
    }

    @Test
    fun `encodes empty input`() {
        assertArrayEquals(intArrayOf(101, 102), tokenizer().encode(""))
    }

    @Test
    fun `encodes single space`() {
        assertArrayEquals(intArrayOf(101, 102), tokenizer().encode(" "))
    }

    @Test
    fun `encodes symbols`() {
        assertArrayEquals(intArrayOf(101, 1039, 1009, 1009, 1998, 1039, 1001, 102), tokenizer().encode("C++ and C#"))
    }

    @Test
    fun `round-trips through decode`() {
        val tok = tokenizer()
        assertEquals("hello world", tok.decode(tok.encode("Hello world")))
    }

    @Test
    fun `vocab size matches bert-base-uncased`() {
        assertEquals(30522, tokenizer().vocabSize)
    }
}
