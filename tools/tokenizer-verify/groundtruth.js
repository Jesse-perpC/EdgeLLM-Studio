const fs = require('fs');
const { Tokenizer, BPE, ByteLevelPreTokenizer, ByteLevelDecoder } = require('@huggingface/tokenizers');
const { Tok } = require('./tok.js');

const root = JSON.parse(fs.readFileSync('./gpt2.json', 'utf8'));

// Build the authoritative tokenizer straight from tokenizer.json's own settings.
const hf = new Tokenizer(JSON.parse(fs.readFileSync("./gpt2.json","utf8")), {});

const mine = new Tok(root, { strictModelType: false });
console.log("HF tokenizer built from gpt2 tokenizer.json (its own normalizer/pre-tokenizer/decoder)\n");

const fixed = [
  'Hello world',
  'Hello, my dog is cute',
  'hello world',
  'The quick brown fox jumps over the lazy dog.',
  '  leading and trailing whitespace  ',
  'Tabs\tand\nnewlines\r\nhere.',
  'Numbers 1234567890 and 3.14159',
  'punctuation!!! ??? ... ---',
  'café naïve',
  '日本語のテキストです',
  'emoji 🚀🎉 and math Ω≈ç√∫',
  'MiXeD CaSe WoRdS with underscores_and_camelCase',
  'https://example.com/path?query=1&x=2',
  'def f(x):\n    return x ** 2  # comment',
  'a'.repeat(200),
  'The '.repeat(50),
  JSON.stringify({ status: 'ok', data: [1, 2, 3] }),
  'trailing spaces   ',
  '',
  'x',
  ' ',
  "I'm sure you're right, don't worry",
  "I'm sure you're right, don't worry",
  "can't won't I'll we've I'd",
  "she'ld've 'tis",
  "'tis the season",
];

let seed = 12345;
const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
const alphabet = [...'abcXYZ 019_.,!?é日🚀\t\n"\'\\{}[]'];
for (let i = 0; i < 400; i++) {
  let s = '';
  const n = 1 + Math.floor(rnd() * 40);
  for (let j = 0; j < n; j++) s += alphabet[Math.floor(rnd() * alphabet.length)];
  fixed.push(s);
}

let mismatch = 0, nonEmpty = 0;
const examples = [];

for (const text of fixed) {
  const want = hf.encode(text).ids;
  const got = mine.encode(text);
  if (want.length) nonEmpty++;
  if (JSON.stringify(want) !== JSON.stringify(got)) {
    mismatch++;
    if (examples.length < 6) {
      examples.push({
        text: JSON.stringify(text).slice(0, 64),
        want: JSON.stringify(want).slice(0, 110),
        got: JSON.stringify(got).slice(0, 110),
      });
    }
  }
}

console.log(`encode: ${fixed.length} strings (${nonEmpty} non-empty), mismatches: ${mismatch}`);
for (const e of examples) {
  console.log('  text ' + e.text);
  console.log('    HF ' + e.want);
  console.log('    me ' + e.got);
}

// decode() intentionally skips special tokens (an LLM decoder must not surface
// <|endoftext|> as text), so that difference is asserted rather than tolerated.
let decBad = 0;
{
  const ids = mine.encode("<|endoftext|> after special");
  const back = Buffer.concat(ids.map(i => mine.decodeTokenBytes(i))).toString("utf8");
  const ok = back === " after special";
  console.log(`special tokens are skipped, not decoded: ${ok ? 'PASS' : 'FAIL'}`);
  if (!ok) decBad++;
}
const decChecked = fixed.slice(0, 60);
for (const text of decChecked) {
  const ids = hf.encode(text).ids;
  const back = Buffer.concat(ids.map(i => mine.decodeTokenBytes(i))).toString('utf8');
  if (back !== text) {
    decBad++;
    if (decBad <= 4) console.log('  decode FAIL', JSON.stringify(text), '->', JSON.stringify(back));
  }
}
console.log(`decode: ${decBad} mismatches / ${decChecked.length} (plus the special-token assertion above)`);

const ok = mismatch === 0 && decBad === 0;
console.log('\n' + (ok ? 'RESULT: identical to HuggingFace reference' : 'RESULT: DIVERGENT'));
process.exit(ok ? 0 : 1);
