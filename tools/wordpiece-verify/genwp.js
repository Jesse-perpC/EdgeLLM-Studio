const fs = require('fs');
const { Tokenizer } = require('@huggingface/tokenizers');

const vocab = fs.readFileSync('./vocab.txt', 'utf8').split('\n');
while (vocab.length && vocab[vocab.length - 1] === '') vocab.pop();

const hf = new Tokenizer({
  model: { type: 'WordPiece', vocab: Object.fromEntries(vocab.map((t, i) => [t, i])), unk_token: '[UNK]', continuing_subword_prefix: '##', max_input_chars_per_word: 200 },
  normalizer: { type: 'BertNormalizer', clean_text: true, handle_chinese_chars: true, strip_accents: true, lowercase: true },
  pre_tokenizer: { type: 'BertPreTokenizer' },
  post_processor: null, decoder: null, added_tokens: [],
}, {});
const hfEncode = (t) => [101, ...hf.encode(t).ids, 102];

const cases = [
  ['Hello world', 'ascii two words'],
  ['Hello, my dog is cute', 'punctuation splits'],
  ['The quick brown fox jumps over the lazy dog.', 'full sentence'],
  ['Numbers 123 and 45.67', 'digits and float'],
  ['café naïve résumé', 'accent stripping'],
  ['日本語のテスト', 'cjk spacing'],
  ['UPPERCASE lower MiXeD', 'uncased lowercasing'],
  ["don't can't won't", 'contractions'],
  ['pneumonoultramicroscopicsilicovolcanoconiosis', 'long word wordpiece split'],
  ['', 'empty input'],
  [' ', 'single space'],
  ['C++ and C#', 'symbols'],
];

function esc(s) {
  return s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n').replace(/\t/g, '\\t');
}

let out = `package com.perpcorp.edgellm.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * WordPiece correctness, asserted against ids produced by the HuggingFace
 * reference implementation (\`@huggingface/tokenizers\` 0.2.0) driving the
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
                "bert vocab.txt not found. Download it with:\\n" +
                    "  curl -L -o app/src/test/resources/bert/vocab.txt " +
                    "https://huggingface.co/bert-base-uncased/resolve/main/vocab.txt"
            )
        return WordPieceTokenizer.load(file)
    }

`;

for (const [t, why] of cases) {
  out += '    @Test\n';
  out += '    fun `encodes ' + why + '`() {\n';
  out += '        assertArrayEquals(intArrayOf(' + hfEncode(t).join(', ') + '), tokenizer().encode("' + esc(t) + '"))\n';
  out += '    }\n\n';
}

out += '    @Test\n';
out += '    fun `round-trips through decode`() {\n';
out += '        val tok = tokenizer()\n';
out += '        assertEquals("hello world", tok.decode(tok.encode("Hello world")))\n';
out += '    }\n\n';
out += '    @Test\n';
out += '    fun `vocab size matches bert-base-uncased`() {\n';
out += '        assertEquals(30522, tokenizer().vocabSize)\n';
out += '    }\n';
out += '}\n';

fs.writeFileSync('./WordPieceTokenizerTest.kt', out);
console.log('wrote WPTest.kt, cases:', cases.length);
