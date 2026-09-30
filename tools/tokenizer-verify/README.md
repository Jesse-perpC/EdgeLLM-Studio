# Tokenizer verification

`OnnxTokenizer.kt` implements HuggingFace byte-level BPE in Kotlin. This folder
is the harness used to prove it correct, so the claim is reproducible instead of
asserted.

## Why this exists

The first Kotlin implementation was verified here and turned out to be wrong in
two ways, both of which would have silently corrupted ONNX inference:

1. **`encode` byte-encoded the whole string before regex-splitting.** Byte-level
   maps space to `U+0120` (a *letter*) and maps non-ASCII bytes to a mix of
   letters and symbols, so splitting the encoded string cuts inside `" 1234"` and
   inside a single character like `"é"`. The reference implementation splits the
   **raw** text first, then byte-encodes each piece. Every model with a space or
   non-ASCII prompt produced garbage before this fix.
2. **`model.type` was required to equal `"BPE"`.** The canonical `gpt2`
   `tokenizer.json` omits `model.type` entirely, so real GPT-2 was rejected.

## Running it

```bash
npm install @huggingface/tokenizers     # 0.2.0, the Rust `tokenizers` crate as WASM
cp ../../app/src/test/resources/gpt2/tokenizer.json .   # or curl it from the Hub
node groundtruth.js
```

`tok.js` is a faithful port of `OnnxTokenizer.kt` — including its bug fixes, but
**not** its Kotlin-specific syntax. `groundtruth.js` builds the reference
tokenizer straight from `tokenizer.json` and compares 427 strings (curated cases
plus 400 seeded fuzz strings) covering ASCII, digits, contractions, whitespace,
code, URLs, Latin-1, CJK, emoji and math symbols.

Current result: **0 encode mismatches, 0 decode mismatches** (excluding the
deliberate skip of special tokens such as `<|endoftext|>`).

## Regenerating the JUnit fixture

```bash
node genfixture.js
```

Writes `HuggingFaceByteLevelTokenizerTest.kt` with expectations produced by the
**reference** implementation, never by our own code, so the unit test cannot
agree with a regression by construction.

The known-good fixture `tokenizer.json` lives in
`app/src/test/resources/gpt2/tokenizer.json`.
