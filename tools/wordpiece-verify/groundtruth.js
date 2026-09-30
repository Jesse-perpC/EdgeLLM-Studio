const fs = require('fs');
const { Tokenizer } = require('@huggingface/tokenizers');
const { WP } = require('./wp.js');

const vocab = fs.readFileSync('./vocab.txt', 'utf8').split('\n');
while (vocab.length && vocab[vocab.length-1] === '') vocab.pop();
const hf = new Tokenizer({
  model: { type: 'WordPiece', vocab: Object.fromEntries(vocab.map((t,i)=>[t,i])), unk_token: '[UNK]', continuing_subword_prefix: '##', max_input_chars_per_word: 200 },
  normalizer: { type: 'BertNormalizer', clean_text: true, handle_chinese_chars: true, strip_accents: true, lowercase: true },
  pre_tokenizer: { type: 'BertPreTokenizer' },
  post_processor: null, decoder: null, added_tokens: [],
}, {});
// HF encode returns raw wordpiece ids; wrap with CLS/SEP to match our encode()
const hfEncode = (t) => [101, ...hfEncode(t), 102];

const mine = new WP(vocab);
console.log(`vocab=${vocab.length} cls=${mine.clsId} sep=${mine.sepId} unk=${mine.unkId} pad=${mine.padId}\n`);

const fixed = [
  'Hello world',
  'Hello, my dog is cute',
  'hello world',
  'The quick brown fox jumps over the lazy dog.',
  '  extra   spaces\ttabs\nnewlines  ',
  'Numbers 123 and 45.67, plus #hashtag @mention',
  'café naïve résumé',
  '日本語のテスト',
  'UPPERCASE lower MiXeD',
  "don't can't won't I'm",
  'email@example.com and https://example.com/a?b=1',
  'a'.repeat(50),
  'pneumonoultramicroscopicsilicovolcanoconiosis',
  'x'.repeat(250),
  '',
  ' ',
  '7',
  '... --- !!! ???',
  '"quoted" (parens) [brackets] {braces}',
  'C++ and C# and F#',
  '100% sure & <tag> Vendetta',
];

let seed = 777;
const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
const alphabet = [...'abcXYZ 019_.,!?é日\t\n"\'-+#/'];
for (let i = 0; i < 300; i++) {
  let s = '';
  const n = 1 + Math.floor(rnd() * 30);
  for (let j = 0; j < n; j++) s += alphabet[Math.floor(rnd() * alphabet.length)];
  fixed.push(s);
}

let mismatch = 0;
const examples = [];
for (const text of fixed) {
  let want, got;
  try { want = hfEncode(text); } catch (e) { console.log('HF threw on', JSON.stringify(text), e.message.slice(0,60)); continue; }
  got = mine.encode(text);
  if (JSON.stringify(want) !== JSON.stringify(got)) {
    mismatch++;
    if (examples.length < 6) examples.push({ text: JSON.stringify(text).slice(0, 60), want, got });
  }
}
console.log(`encode: ${fixed.length} strings, mismatches: ${mismatch}`);
for (const e of examples) {
  console.log('  text ' + e.text);
  console.log('    HF ' + JSON.stringify(e.want).slice(0, 130));
  console.log('    me ' + JSON.stringify(e.got).slice(0, 130));
}
process.exit(mismatch === 0 ? 0 : 1);
