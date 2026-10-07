import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { ModelFormat, ModelSpec, ModelCategory } from '../types';
import {
  Cpu,
  Download,
  Trash2,
  CheckCircle2,
  Layers,
  Plus,
  HardDrive,
  Activity,
  ArrowRight,
  ShieldCheck,
  FileCode,
} from 'lucide-react';

interface DeviceAndModelsScreenProps {
  onNavigateToChat: () => void;
}

export const DeviceAndModelsScreen: React.FC<DeviceAndModelsScreenProps> = ({ onNavigateToChat }) => {
  const { models, activeModel, setActiveModel, downloadModel, deleteModel, addCustomModel, accentPalette } =
    useEdgeLLM();

  const [showImportDialog, setShowImportDialog] = useState<boolean>(false);
  const [customName, setCustomName] = useState<string>('');
  const [customUrl, setCustomUrl] = useState<string>('');
  const [customFormat, setCustomFormat] = useState<ModelFormat>(ModelFormat.GGUF);
  const [customQuant, setCustomQuant] = useState<string>('Q4_K_M');
  const [customParams, setCustomParams] = useState<string>('1.5B');

  const handleImportSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!customName.trim()) return;
    addCustomModel({
      name: customName.trim(),
      downloadUrl: customUrl.trim() || 'local_filesystem',
      format: customFormat,
      quantization: customQuant,
      parameterCount: customParams,
      category: ModelCategory.CHAT_REASONING,
    });
    setCustomName('');
    setCustomUrl('');
    setShowImportDialog(false);
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-5 space-y-6 pb-24">
      {/* Vault Header Card */}
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
            <Cpu className="w-6 h-6" style={{ color: accentPalette.primary }} />
          </div>
          <div>
            <h2 className="text-base font-bold text-white tracking-tight">
              On-Device Model Vault & Checkpoints
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Universal GGUF, LiteRT, ONNX & MNN Checkpoints
            </p>
          </div>
        </div>

        <button
          onClick={() => setShowImportDialog(true)}
          className="flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-bold text-black self-start sm:self-auto transition-transform hover:scale-[1.02]"
          style={{ backgroundColor: accentPalette.primary }}
        >
          <Plus className="w-4 h-4" />
          <span>Import Custom Checkpoint</span>
        </button>
      </div>

      {/* Model Cards List */}
      <div className="space-y-3.5">
        {models.map((model) => {
          const isActive = model.id === activeModel.id;
          const ramMb = Math.round(model.requiredRamBytes / (1024 * 1024));
          const sizeMb = Math.round(model.fileSizeBytes / (1024 * 1024));

          return (
            <div
              key={model.id}
              className="p-4 sm:p-5 rounded-2xl border transition-all shadow-md"
              style={{
                backgroundColor: isActive
                  ? `${accentPalette.primary}10`
                  : accentPalette.surfaceDark,
                borderColor: isActive ? accentPalette.primary : `${accentPalette.borderDark}80`,
              }}
            >
              <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
                <div className="space-y-1.5 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="text-base font-bold text-white">{model.name}</h3>
                    {isActive && (
                      <span
                        className="text-[10px] font-mono px-2 py-0.5 rounded border"
                        style={{
                          color: accentPalette.primary,
                          borderColor: `${accentPalette.primary}60`,
                          backgroundColor: `${accentPalette.primary}18`,
                        }}
                      >
                        Active Engine
                      </span>
                    )}
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                      {model.format}
                    </span>
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-cyan-950 text-cyan-300 border border-cyan-800">
                      {model.quantization}
                    </span>
                  </div>

                  <p className="text-xs text-slate-300 leading-relaxed">{model.description}</p>

                  {/* Specs & Hardware Allocation Strip */}
                  <div className="flex flex-wrap items-center gap-4 pt-1 text-xs font-mono text-slate-400">
                    <div className="flex items-center gap-1">
                      <Layers className="w-3.5 h-3.5 text-cyan-400" />
                      <span>{model.parameterCount} Params</span>
                    </div>
                    <div className="flex items-center gap-1">
                      <HardDrive className="w-3.5 h-3.5 text-pink-400" />
                      <span>{sizeMb} MB Size</span>
                    </div>
                    <div className="flex items-center gap-1">
                      <Cpu className="w-3.5 h-3.5 text-emerald-400" />
                      <span>{ramMb} MB RAM</span>
                    </div>
                    <div className="flex items-center gap-1">
                      <Activity className="w-3.5 h-3.5 text-amber-400" />
                      <span>{model.maxTurboTokPerSec} tok/s peak</span>
                    </div>
                  </div>
                </div>

                {/* Right Action Buttons */}
                <div className="flex items-center gap-2 self-end sm:self-center mt-2 sm:mt-0">
                  {model.isDownloaded ? (
                    <>
                      {isActive ? (
                        <button
                          onClick={onNavigateToChat}
                          className="px-3.5 py-1.5 rounded-xl text-xs font-bold text-black flex items-center gap-1.5"
                          style={{ backgroundColor: accentPalette.primary }}
                        >
                          <span>Chat</span>
                          <ArrowRight className="w-3.5 h-3.5" />
                        </button>
                      ) : (
                        <button
                          onClick={() => setActiveModel(model.id)}
                          className="px-3.5 py-1.5 rounded-xl text-xs font-semibold text-slate-200 border border-slate-700 hover:text-white hover:border-cyan-400 transition-colors"
                        >
                          Load & Activate
                        </button>
                      )}

                      {models.length > 1 && (
                        <button
                          onClick={() => deleteModel(model.id)}
                          className="p-2 rounded-xl text-slate-500 hover:text-red-400 hover:bg-slate-800 transition-colors"
                          title="Delete downloaded weights"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                    </>
                  ) : model.isDownloading ? (
                    <div className="flex items-center gap-2 font-mono text-xs text-cyan-400">
                      <div className="w-16 h-1.5 bg-slate-800 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-cyan-400 transition-all duration-300"
                          style={{ width: `${model.downloadProgressPercent}%` }}
                        />
                      </div>
                      <span>{model.downloadProgressPercent}%</span>
                    </div>
                  ) : (
                    <button
                      onClick={() => downloadModel(model.id)}
                      className="px-3.5 py-1.5 rounded-xl text-xs font-bold bg-slate-800 border border-slate-700 text-white hover:border-cyan-500 flex items-center gap-1.5"
                    >
                      <Download className="w-3.5 h-3.5" />
                      <span>Cache Locally</span>
                    </button>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Import Modal */}
      {showImportDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-sm animate-in fade-in">
          <form
            onSubmit={handleImportSubmit}
            className="w-full max-w-md rounded-2xl border p-6 space-y-4 shadow-2xl"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: accentPalette.borderDark,
            }}
          >
            <div className="flex justify-between items-center">
              <h3 className="text-base font-bold text-white">Import Custom Model File</h3>
              <button
                type="button"
                onClick={() => setShowImportDialog(false)}
                className="text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Model Name / Alias</label>
              <input
                type="text"
                required
                value={customName}
                onChange={(e) => setCustomName(e.target.value)}
                placeholder="e.g. Mistral 7B Instruct v0.3"
                className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2.5 text-xs text-white"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 block mb-1">Format</label>
                <select
                  value={customFormat}
                  onChange={(e) => setCustomFormat(e.target.value as ModelFormat)}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-xs text-white"
                >
                  <option value={ModelFormat.GGUF}>GGUF (llama.cpp)</option>
                  <option value={ModelFormat.MEDIAPIPE_TASK}>MediaPipe / LiteRT</option>
                  <option value={ModelFormat.ONNX}>ONNX Runtime</option>
                  <option value={ModelFormat.MNN_LLM}>Alibaba MNN</option>
                </select>
              </div>

              <div>
                <label className="text-xs text-slate-400 block mb-1">Quantization</label>
                <input
                  type="text"
                  value={customQuant}
                  onChange={(e) => setCustomQuant(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-xs text-white"
                />
              </div>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Parameters (e.g. 1.0B, 3.8B)</label>
              <input
                type="text"
                value={customParams}
                onChange={(e) => setCustomParams(e.target.value)}
                className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2.5 text-xs text-white"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowImportDialog(false)}
                className="px-4 py-2 rounded-xl text-xs text-slate-400 hover:text-white"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-5 py-2 rounded-xl text-xs font-bold text-black"
                style={{ backgroundColor: accentPalette.primary }}
              >
                Register & Load
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
