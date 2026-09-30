// Faithful port of HuggingFaceByteLevelTokenizer.kt (Kotlin) to JS so the
// algorithm can be executed and compared against known-correct GPT-2 ids.
// Any divergence from the Kotlin source is a bug in this file, not a licence to
// change the Kotlin.
const fs = require('fs');

const GPT2_SPLIT_PATTERN =
  "'(?:s|t|re|ve|m|ll|d)| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+";

function bytesToUnicode() {
  const bs = [];
  for (let i = 33; i <= 126; i++) bs.push(i);
  for (let i = 161; i <= 172; i++) bs.push(i);
  for (let i = 174; i <= 255; i++) bs.push(i);
  const cs = bs.slice();
  let n = 0;
  for (let b = 0; b < 256; b++) {
    if (!bs.includes(b)) { bs.push(b); cs.push(256 + n); n++; }
  }
  const out = new Map();
  for (let i = 0; i < bs.length; i++) out.set(bs[i], String.fromCodePoint(cs[i]));
  return out;
}

class Tok {
  constructor(root, { strictModelType = true } = {}) {
    const model = root.model;
    if (!model) throw new Error("tokenizer.json has no 'model' object");

    const type = model.type;
    if (strictModelType && type && type !== 'BPE') {
      throw new Error(
        `Unsupported tokenizer model.type='${type}'. This engine implements byte-level BPE only.`);
    }

    this.tokenToId = new Map(Object.entries(model.vocab));
    this.idToToken = new Map();
    for (const [t, i] of this.tokenToId) this.idToToken.set(i, t);
    this.mergeRanks = new Map();
    (model.merges || []).forEach((item, i) => {
      const pair = Array.isArray(item) ? `${item[0]} ${item[1]}` : item;
      this.mergeRanks.set(pair, i);
    });

    this.byteEncoder = bytesToUnicode();
    this.byteDecoder = new Map();
    for (const [b, s] of this.byteEncoder) this.byteDecoder.set(s, b);

    // readAddedTokens
    this.addedTokenToId = new Map();
    this.specialIds = new Set();
    for (const o of root.added_tokens || []) {
      if (!o.content) continue;
      const id = o.hasOwnProperty('id') ? o.id : this.tokenToId.get(o.content);
      if (id === undefined) continue;
      this.addedTokenToId.set(o.content, id);
      if (o.special === true) this.specialIds.add(id);
    }

    // split regex: from pre_tokenizer Split, else GPT-2 default
    let pattern = null;
    const pre = root.pre_tokenizer;
    if (pre) pattern = findSplitPattern(pre);
    this.splitRegex = new RegExp(pattern || GPT2_SPLIT_PATTERN, 'u');

    this.unkId = this.tokenToId.get('<|endoftext|>');
    if (this.unkId === undefined)
      this.unkId = this.tokenToId.get('[UNK]') ?? this.tokenToId.get('<unk>');
    if (this.unkId === undefined) throw new Error('no unknown token');

    // eos: root eos_token, plus known stop tokens
    const eos = new Set();
    const rootEos = root.eos_token;
    if (typeof rootEos === 'string') {
      const id = this.tokenToId.get(rootEos);
      if (id !== undefined) eos.add(id);
    } else if (rootEos && typeof rootEos === 'object' && rootEos.content) {
      const id = this.tokenToId.get(rootEos.content);
      if (id !== undefined) eos.add(id);
    }
    for (const t of ['<|endoftext|>', '<|im_end|>', '<|eot_id|>', '<|end_of_text|>', '<|eom_id|>', '<end_of_turn>']) {
      const id = this.addedTokenToId.get(t);
      if (id !== undefined) eos.add(id);
    }
    this.eosIds = eos;
  }

  encode(text) {
    const out = [];
    for (const piece of this.splitAddedTokens(text)) {
      if (piece.id >= 0) { out.push(piece.id); continue; }
      // Split the RAW text, then byte-encode each piece. Byte-level maps space to
      // U+0120 (a letter) and non-ASCII bytes to assorted letters and symbols, so
      // regexing the encoded string would cut inside " 1234" and inside "é".
      const re = new RegExp(this.splitRegex.source, 'gu');
      let m;
      while ((m = re.exec(piece.text)) !== null) {
        if (m[0].length === 0) { re.lastIndex++; continue; }
        out.push(...this.bpe(this.toByteLevel(m[0])));
      }
    }
    return out;
  }

  splitAddedTokens(text) {
    if (this.addedTokenToId.size === 0) return [{ text, id: -1 }];
    const literals = [...this.addedTokenToId.keys()].sort((a, b) => b.length - a.length);
    const out = [];
    let rest = text;
    while (rest.length > 0) {
      let bestIdx = -1, bestLit = null;
      for (const lit of literals) {
        const idx = rest.indexOf(lit);
        if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) { bestIdx = idx; bestLit = lit; }
      }
      if (bestLit === null) { out.push({ text: rest, id: -1 }); break; }
      if (bestIdx > 0) out.push({ text: rest.substring(0, bestIdx), id: -1 });
      out.push({ text: bestLit, id: this.addedTokenToId.get(bestLit) });
      rest = rest.substring(bestIdx + bestLit.length);
    }
    return out;
  }

  toByteLevel(text) {
    let sb = '';
    for (const b of Buffer.from(text, 'utf8')) sb += this.byteEncoder.get(b);
    return sb;
  }

  bpe(token) {
    if (token.length === 0) return [];
    let word = [...token];
    if (word.length === 1) return [this.tokenToId.get(word[0]) ?? this.unkId];

    while (word.length > 1) {
      let bestRank = Infinity, bestIdx = -1;
      for (let i = 0; i < word.length - 1; i++) {
        const r = this.mergeRanks.get(word[i] + ' ' + word[i + 1]);
        if (r === undefined) continue;
        if (r < bestRank) { bestRank = r; bestIdx = i; }
      }
      if (bestIdx < 0) break;
      const merged = word[bestIdx] + word[bestIdx + 1];
      const next = [];
      let i = 0;
      while (i < word.length) {
        if (i === bestIdx) { next.push(merged); i += 2; }
        else { next.push(word[i]); i++; }
      }
      word = next;
    }
    return word.map(w => this.tokenToId.get(w) ?? this.unkId);
  }

  decodeTokenBytes(id) {
    if (this.specialIds.has(id)) return Buffer.alloc(0);
    const tok = this.idToToken.get(id);
    if (tok === undefined) return Buffer.alloc(0);
    const parts = [];
    for (const ch of tok) {
      const b = this.byteDecoder.get(ch);
      parts.push(b !== undefined ? Buffer.from([b]) : Buffer.from(ch, 'utf8'));
    }
    return Buffer.concat(parts);
  }

  decode(ids) {
    return Buffer.concat(ids.map(i => this.decodeTokenBytes(i))).toString('utf8');
  }
}

function findSplitPattern(node) {
  switch (node.type) {
    case 'Sequence': {
      for (const child of node.pretokenizers || []) {
        const p = findSplitPattern(child);
        if (p) return p;
      }
      return null;
    }
    case 'Split':
      return (node.pattern && node.pattern !== 'default') ? node.pattern : null;
    default:
      return null;
  }
}

module.exports = { Tok, GPT2_SPLIT_PATTERN };
