import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import {
  Database,
  Search,
  Sliders,
  Plus,
  Trash2,
  FileText,
  Hash,
  ShieldCheck,
  CheckCircle2,
} from 'lucide-react';

export const RagDebugScreen: React.FC = () => {
  const {
    knowledgeDocs,
    ragChunks,
    similarityThreshold,
    setSimilarityThreshold,
    queryRag,
    ingestDoc,
    deleteDoc,
    accentPalette,
  } = useEdgeLLM();

  const [searchQuery, setSearchQuery] = useState<string>('air-gapped silicon governor');
  const [showIngestModal, setShowIngestModal] = useState<boolean>(false);
  const [newTitle, setNewTitle] = useState<string>('');
  const [newContent, setNewContent] = useState<string>('');

  const searchResults = searchQuery.trim() ? queryRag(searchQuery) : ragChunks;

  const handleIngest = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newContent.trim()) return;
    ingestDoc(newTitle.trim() || 'Custom_Notes.txt', newContent.trim());
    setNewTitle('');
    setNewContent('');
    setShowIngestModal(false);
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-5 space-y-6 pb-24">
      {/* Header Banner */}
      <div
        className="p-5 rounded-2xl border flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-xl"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}90`,
        }}
      >
        <div className="flex items-center gap-3">
          <div className="p-3 rounded-xl bg-emerald-500/20 shadow-lg border border-emerald-500/30">
            <Database className="w-6 h-6 text-emerald-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-white tracking-tight">
                RAG Semantic Chunk Debugger
              </h2>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-950 text-emerald-300 border border-emerald-700">
                128-d Cosine
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Inspect Content-Addressed SHA-256 Chunks & Calibrate Retrieval Confidence
            </p>
          </div>
        </div>

        <button
          onClick={() => setShowIngestModal(true)}
          className="flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold text-black self-start sm:self-auto transition-transform hover:scale-[1.02]"
          style={{ backgroundColor: accentPalette.primary }}
        >
          <Plus className="w-4 h-4" />
          <span>Ingest Document</span>
        </button>
      </div>

      {/* Cosine Slider & Query Inspector */}
      <div
        className="p-5 rounded-2xl border space-y-4 shadow-lg"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}80`,
        }}
      >
        <div className="flex items-center gap-2 bg-slate-900 border border-slate-700 rounded-xl px-3 py-2">
          <Search className="w-4 h-4 text-emerald-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Test query across semantic embeddings..."
            className="w-full bg-transparent text-xs sm:text-sm text-white focus:outline-none"
          />
        </div>

        {/* Live Cosine Similarity Slider */}
        <div>
          <div className="flex justify-between items-center text-xs font-mono mb-1.5">
            <span className="text-slate-400 flex items-center gap-1.5">
              <Sliders className="w-3.5 h-3.5 text-emerald-400" />
              Cosine Similarity Threshold Filter:
            </span>
            <span className="font-bold text-emerald-400">{similarityThreshold.toFixed(2)}</span>
          </div>
          <input
            type="range"
            min="0.1"
            max="0.95"
            step="0.05"
            value={similarityThreshold}
            onChange={(e) => setSimilarityThreshold(parseFloat(e.target.value))}
            className="w-full accent-emerald-400"
          />
          <div className="flex justify-between text-[10px] font-mono text-slate-500 mt-1">
            <span>0.10 (Broad Recall)</span>
            <span>0.65 (Balanced RAG)</span>
            <span>0.95 (Exact Match)</span>
          </div>
        </div>
      </div>

      {/* Matched Chunks Inspector Feed */}
      <div>
        <div className="flex items-center justify-between mb-3 px-1">
          <h3 className="text-sm font-bold text-white uppercase tracking-wider font-mono">
            Retrieved Semantic Chunks ({searchResults.length})
          </h3>
          <span className="text-xs text-emerald-400 font-mono">SHA-256 Content-Addressed</span>
        </div>

        <div className="space-y-3">
          {searchResults.length === 0 ? (
            <div className="p-8 text-center border rounded-2xl border-slate-800 bg-slate-900/40 text-slate-500 text-xs font-mono">
              No chunks matched the current threshold ({similarityThreshold}). Lower the slider above.
            </div>
          ) : (
            searchResults.map((chunk) => (
              <div
                key={chunk.id}
                className="p-4 rounded-2xl border space-y-2.5 shadow-md"
                style={{
                  backgroundColor: accentPalette.surfaceDark,
                  borderColor: `${accentPalette.borderDark}80`,
                }}
              >
                <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                  <div className="flex items-center gap-2">
                    <FileText className="w-4 h-4 text-emerald-400" />
                    <span className="text-xs font-bold text-white">{chunk.documentTitle}</span>
                    <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-300">
                      Chunk {chunk.chunkIndex}/{chunk.totalChunks}
                    </span>
                  </div>

                  <span className="text-xs font-mono font-bold text-emerald-400">
                    Similarity: {(chunk.cosineSimilarityScore * 100).toFixed(0)}%
                  </span>
                </div>

                <p className="text-xs text-slate-200 leading-relaxed font-sans">{chunk.content}</p>

                <div className="flex flex-wrap items-center justify-between gap-2 text-[10px] font-mono text-slate-400 pt-2 border-t border-slate-800">
                  <div className="flex items-center gap-1 text-slate-500 truncate max-w-xs">
                    <Hash className="w-3 h-3 flex-shrink-0" />
                    <span className="truncate">{chunk.sha256ContentHash}</span>
                  </div>
                  <div>
                    <span>{chunk.tokenCount} Tokens</span> • <span>{chunk.retrievalLatencyMs}ms Latency</span>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Ingest Modal */}
      {showIngestModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-sm animate-in fade-in">
          <form
            onSubmit={handleIngest}
            className="w-full max-w-md rounded-2xl border p-6 space-y-4 shadow-2xl"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: accentPalette.borderDark,
            }}
          >
            <div className="flex justify-between items-center">
              <h3 className="text-base font-bold text-white">Ingest Grounding Document</h3>
              <button
                type="button"
                onClick={() => setShowIngestModal(false)}
                className="text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Document Title</label>
              <input
                type="text"
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                placeholder="e.g. My_Project_Notes.md"
                className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2.5 text-xs text-white"
              />
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Raw Content</label>
              <textarea
                required
                rows={6}
                value={newContent}
                onChange={(e) => setNewContent(e.target.value)}
                placeholder="Paste document text to compute 128-d vector embeddings locally..."
                className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2.5 text-xs text-white font-mono"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowIngestModal(false)}
                className="px-4 py-2 rounded-xl text-xs text-slate-400 hover:text-white"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-5 py-2 rounded-xl text-xs font-bold text-black"
                style={{ backgroundColor: accentPalette.primary }}
              >
                Compute Embeddings & Index
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
