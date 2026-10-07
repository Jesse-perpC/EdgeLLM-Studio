import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { ModelFormat } from '../types';
import {
  Globe,
  Search,
  Download,
  Filter,
  CheckCircle2,
  HardDrive,
  Cpu,
  Layers,
  ArrowRight,
} from 'lucide-react';

interface HfExplorerScreenProps {
  onNavigateToChat: () => void;
}

interface HfModelCard {
  id: string;
  name: string;
  author: string;
  downloads: string;
  likes: number;
  format: ModelFormat;
  quant: string;
  sizeMb: number;
  params: string;
  description: string;
}

const SAMPLE_HF_MODELS: HfModelCard[] = [
  {
    id: 'deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B',
    name: 'DeepSeek-R1-Distill-Qwen-1.5B',
    author: 'deepseek-ai',
    downloads: '1.2M',
    likes: 8400,
    format: ModelFormat.GGUF,
    quant: 'Q4_K_M',
    sizeMb: 1100,
    params: '1.5B',
    description: 'Distilled reasoning model with rigorous mathematical chain-of-thought verification.',
  },
  {
    id: 'google/gemma-2-2b-it',
    name: 'Gemma-2-2B-IT',
    author: 'google',
    downloads: '940k',
    likes: 6200,
    format: ModelFormat.MEDIAPIPE_TASK,
    quant: 'INT4 LiteRT',
    sizeMb: 1420,
    params: '2.0B',
    description: 'Official Google Gemma 2 optimized for on-device reasoning and mobile execution.',
  },
  {
    id: 'Qwen/Qwen2.5-Coder-1.5B-Instruct',
    name: 'Qwen2.5-Coder-1.5B-Instruct',
    author: 'Qwen',
    downloads: '680k',
    likes: 4100,
    format: ModelFormat.MNN_LLM,
    quant: 'INT4 MNN',
    sizeMb: 990,
    params: '1.5B',
    description: 'Dedicated coding specialist tuned for Python, Kotlin, Rust, and algorithmic synthesis.',
  },
  {
    id: 'microsoft/Phi-3.5-mini-instruct',
    name: 'Phi-3.5-mini-instruct',
    author: 'microsoft',
    downloads: '820k',
    likes: 5400,
    format: ModelFormat.GGUF,
    quant: 'Q4_K_M',
    sizeMb: 2390,
    params: '3.8B',
    description: 'Microsoft compact model rivaling 7B architectures in logic, math, and code generation.',
  },
  {
    id: 'HuggingFaceTB/SmolLM2-360M-Instruct',
    name: 'SmolLM2-360M-Instruct',
    author: 'HuggingFaceTB',
    downloads: '450k',
    likes: 2900,
    format: ModelFormat.GGUF,
    quant: 'Q4_K_M',
    sizeMb: 240,
    params: '360M',
    description: 'Ultra-lightweight edge model designed for low-power continuous background inference.',
  },
];

export const HfExplorerScreen: React.FC<HfExplorerScreenProps> = ({ onNavigateToChat }) => {
  const { addCustomModel, accentPalette } = useEdgeLLM();
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedFormat, setSelectedFormat] = useState<string>('ALL');
  const [downloadingIds, setDownloadingIds] = useState<Record<string, number>>({});
  const [downloadedIds, setDownloadedIds] = useState<Record<string, boolean>>({});

  const filtered = SAMPLE_HF_MODELS.filter((m) => {
    const matchesSearch =
      m.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      m.author.toLowerCase().includes(searchQuery.toLowerCase()) ||
      m.description.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesFormat = selectedFormat === 'ALL' || m.format === selectedFormat;
    return matchesSearch && matchesFormat;
  });

  const handleDownload = (model: HfModelCard) => {
    setDownloadingIds((prev) => ({ ...prev, [model.id]: 10 }));
    let prog = 10;
    const interval = setInterval(() => {
      prog += 25;
      if (prog >= 100) {
        clearInterval(interval);
        setDownloadingIds((prev) => {
          const next = { ...prev };
          delete next[model.id];
          return next;
        });
        setDownloadedIds((prev) => ({ ...prev, [model.id]: true }));

        addCustomModel({
          name: model.name,
          parameterCount: model.params,
          format: model.format,
          quantization: model.quant,
          fileSizeBytes: model.sizeMb * 1024 * 1024,
          requiredRamBytes: (model.sizeMb + 400) * 1024 * 1024,
          description: model.description,
          isDownloaded: true,
        });
      } else {
        setDownloadingIds((prev) => ({ ...prev, [model.id]: prog }));
      }
    }, 300);
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
          <div
            className="p-3 rounded-xl shadow-lg"
            style={{ backgroundColor: `${accentPalette.primary}20` }}
          >
            <Globe className="w-6 h-6" style={{ color: accentPalette.primary }} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-white tracking-tight">
                Hugging Face Hub Live Explorer
              </h2>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-cyan-950 text-cyan-300 border border-cyan-800">
                100,000+ Models
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Directly Search and Cache Open-Source Edge Models Without Leaving the App
            </p>
          </div>
        </div>
      </div>

      {/* Search & Filter Bar */}
      <div
        className="p-4 rounded-2xl border space-y-3"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}80`,
        }}
      >
        <div className="flex items-center gap-2 bg-slate-900 border border-slate-700 rounded-xl px-3 py-2">
          <Search className="w-4 h-4 text-slate-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search Hugging Face models by name, author, or task (e.g. DeepSeek, Qwen, Gemma)..."
            className="w-full bg-transparent text-xs sm:text-sm text-white focus:outline-none"
          />
        </div>

        <div className="flex items-center gap-2 overflow-x-auto pb-1 text-xs font-mono">
          <span className="text-slate-500 flex items-center gap-1">
            <Filter className="w-3.5 h-3.5" />
            Format:
          </span>
          {['ALL', ModelFormat.GGUF, ModelFormat.MEDIAPIPE_TASK, ModelFormat.MNN_LLM].map((f) => (
            <button
              key={f}
              onClick={() => setSelectedFormat(f)}
              className={`px-2.5 py-1 rounded-lg border transition-colors whitespace-nowrap ${
                selectedFormat === f
                  ? 'bg-cyan-500/20 border-cyan-400 text-cyan-300 font-bold'
                  : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              {f === 'ALL' ? 'All Formats' : f}
            </button>
          ))}
        </div>
      </div>

      {/* Catalog Grid */}
      <div className="space-y-3">
        {filtered.map((item) => {
          const isDownloading = downloadingIds[item.id] !== undefined;
          const isDownloaded = downloadedIds[item.id];

          return (
            <div
              key={item.id}
              className="p-4 rounded-2xl border space-y-3 shadow-md"
              style={{
                backgroundColor: accentPalette.surfaceDark,
                borderColor: `${accentPalette.borderDark}80`,
              }}
            >
              <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-2">
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-sm font-bold text-white hover:text-cyan-400 cursor-pointer">
                      {item.name}
                    </h3>
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                      {item.format}
                    </span>
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-cyan-950 text-cyan-300 border border-cyan-800">
                      {item.quant}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 mt-1">{item.description}</p>
                </div>

                {/* Download / Loaded Action */}
                <div className="self-end sm:self-center mt-2 sm:mt-0">
                  {isDownloaded ? (
                    <button
                      onClick={onNavigateToChat}
                      className="px-3 py-1.5 rounded-xl text-xs font-bold text-black flex items-center gap-1.5"
                      style={{ backgroundColor: accentPalette.primary }}
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>Ready in Vault</span>
                    </button>
                  ) : isDownloading ? (
                    <div className="flex items-center gap-2 font-mono text-xs text-cyan-400">
                      <div className="w-20 h-1.5 bg-slate-800 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-cyan-400 transition-all duration-200"
                          style={{ width: `${downloadingIds[item.id]}%` }}
                        />
                      </div>
                      <span>{downloadingIds[item.id]}%</span>
                    </div>
                  ) : (
                    <button
                      onClick={() => handleDownload(item)}
                      className="px-3.5 py-1.5 rounded-xl text-xs font-bold bg-slate-800 border border-slate-700 text-white hover:border-cyan-500 hover:text-cyan-400 flex items-center gap-1.5 transition-colors"
                    >
                      <Download className="w-3.5 h-3.5" />
                      <span>1-Tap Download</span>
                    </button>
                  )}
                </div>
              </div>

              {/* Stats Footer */}
              <div className="flex items-center gap-4 text-[11px] font-mono text-slate-400 pt-2 border-t border-slate-800">
                <span>By {item.author}</span>
                <span>• {item.params} Params</span>
                <span>• {item.sizeMb} MB</span>
                <span>• {item.downloads} Downloads</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
