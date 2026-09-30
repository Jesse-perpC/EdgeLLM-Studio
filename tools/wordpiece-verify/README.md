# WordPiece verification

`WordPieceTokenizer.kt` implements BERT WordPiece in Kotlin. This folder proves
it correct against the HuggingFace reference.

## Running it

```bash
npm install @huggingface/tokenizers     # 0.2.0, the Rust `tokenizers` as WASM
cp ../../app/src/test/resources/bert/vocab.txt .
node groundtruth.js
```

`wp.js` is a faithful port of `WordPieceTokenizer.kt`. `groundtruth.js` builds
the reference (`WordPiece` + `BertNormalizer` + `BertPreTokenizer`, uncased)
from the same `vocab.txt` and compares 321 strings (curated + seeded fuzz).

Current result: **0 mismatches**. (The reference 0.2.0 WASM binding itself throws
"Maximum call stack size exceeded" on some fuzz inputs; those are excluded, not
counted as passes.)

## Regenerating the JUnit fixture

```bash
node genwp.js
```

Expectations come from the **reference**, never from our own code.
