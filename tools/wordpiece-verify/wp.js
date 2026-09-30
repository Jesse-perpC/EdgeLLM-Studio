// Faithful port of WordPieceTokenizer.kt to JS for verification against the
// HuggingFace reference (WordPiece + BertNormalizer + BertPreTokenizer).
const fs = require('fs');

class WP {
  constructor(vocabLines) {
    this.vocab = vocabLines;
    this.tokenToId = new Map();
    vocabLines.forEach((t, i) => { if (!this.tokenToId.has(t)) this.tokenToId.set(t, i); });
    const idOf = (t) => {
      const id = this.tokenToId.get(t);
      if (id === undefined) throw new Error('no ' + t);
      return id;
    };
    this.unkId = idOf('[UNK]');
    this.clsId = idOf('[CLS]');
    this.sepId = idOf('[SEP]');
    this.padId = idOf('[PAD]');
  }

  encode(text) {
    const out = [this.clsId];
    for (const token of this.basicTokenize(text)) out.push(...this.wordpiece(token));
    out.push(this.sepId);
    return out;
  }

  basicTokenize(text) {
    let s = text.toLowerCase();
    s = s.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    let spaced = '';
    for (const ch of s) {
      const v = ch.codePointAt(0);
      const cjk = (v >= 0x4E00 && v <= 0x9FFF) || (v >= 0x3400 && v <= 0x4DBF) ||
        (v >= 0x20000 && v <= 0x2A6DF) || (v >= 0xF900 && v <= 0xFAFF);
      spaced += cjk ? ` ${ch} ` : ch;
    }
    const out = [];
    let cur = '';
    const flush = () => { if (cur) { out.push(cur); cur = ''; } };
    for (const ch of spaced) {
      if (/\s/.test(ch)) flush();
      else if (/[\p{P}]/u.test(ch)) { flush(); out.push(ch); }
      else cur += ch;
    }
    flush();
    return out;
  }

  workpiece(token) { return this.wordpiece(token); }

  wordpiece(token) {
    if ([...token].length > 200) return [this.unkId];
    const chars = [...token];
    const ids = [];
    let start = 0;
    while (start < chars.length) {
      let end = chars.length, curId = -1;
      while (start < end) {
        let sub = chars.slice(start, end).join('');
        if (start > 0) sub = '##' + sub;
        const id = this.tokenToId.get(sub);
        if (id !== undefined) { curId = id; break; }
        end--;
      }
      if (curId < 0) return [this.unkId];
      ids.push(curId);
      start = end;
    }
    return ids;
  }
}

module.exports = { WP };
