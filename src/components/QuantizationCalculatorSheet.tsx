import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { X, Calculator, Cpu, HardDrive, CheckCircle2, AlertTriangle } from 'lucide-react';

interface QuantizationCalculatorSheetProps {
  isOpen: boolean;
  onClose: () => void;
}

export const QuantizationCalculatorSheet: React.FC<QuantizationCalculatorSheetProps> = ({
  isOpen,
  onClose,
}) => {
  const { hardwareInfo, accentPalette } = useEdgeLLM();

  const [paramSizeB, setParamSizeB] = useState<number>(2.0); // 2B
  const [quantBits, setQuantBits] = useState<number>(4.5); // Q4_K_M ~ 4.5 bits/param
  const [contextLength, setContextLength] = useState<number>(4096);

  if (!isOpen) return null;

  // Calculate Weights RAM
  // Weights = (paramSize * 10^9 * quantBits) / 8 bytes
  const weightsBytes = (paramSizeB * 1e9 * quantBits) / 8;
  const weightsMb = Math.round(weightsBytes / (1024 * 1024));

  // Calculate KV Cache RAM
  // KV Cache ≈ 2 * layers * hidden_dim * context * bytes_per_elem
  // Roughly: ~0.5 MB per 1,000 tokens for 2B, scale with paramSize
  const kvCacheMb = Math.round((contextLength / 1024) * 128 * (paramSizeB / 2.0));

  // Compute buffer overhead (~250 MB)
  const overheadMb = 250;
  const totalRamRequiredMb = weightsMb + kvCacheMb + overheadMb;

  const availableRamMb = Math.round(hardwareInfo.availableRamBytes / (1024 * 1024));
  const fitsComfortably = totalRamRequiredMb <= availableRamMb;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-150">
      <div
        className="w-full max-w-lg rounded-2xl border shadow-2xl overflow-hidden flex flex-col max-h-[90vh]"
        style={{
          backgroundColor: accentPalette.bgDark,
          borderColor: accentPalette.borderDark,
        }}
      >
        {/* Header */}
        <div
          className="flex items-center justify-between px-6 py-4 border-b"
          style={{
            backgroundColor: accentPalette.surfaceDark,
            borderColor: `${accentPalette.borderDark}80`,
          }}
        >
          <div className="flex items-center gap-2.5">
            <div
              className="p-2 rounded-xl"
              style={{ backgroundColor: `${accentPalette.secondary}20` }}
            >
              <Calculator className="w-5 h-5" style={{ color: accentPalette.secondary }} />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">Quantization & VRAM Sizer</h2>
              <p className="text-xs text-slate-400">Calculate Model Weights & KV Cache Footprint</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="p-6 overflow-y-auto space-y-6">
          {/* Parameter Size */}
          <div>
            <div className="flex justify-between items-center mb-2">
              <label className="text-xs font-mono uppercase tracking-wider text-slate-400">
                Model Parameter Count
              </label>
              <span className="text-xs font-mono font-bold" style={{ color: accentPalette.primary }}>
                {paramSizeB} Billion Parameters
              </span>
            </div>
            <div className="grid grid-cols-5 gap-2">
              {[0.5, 1.1, 2.0, 3.8, 7.0].map((b) => (
                <button
                  key={b}
                  onClick={() => setParamSizeB(b)}
                  className="py-2 text-xs font-mono rounded-lg border text-center transition-all"
                  style={{
                    backgroundColor:
                      paramSizeB === b ? `${accentPalette.primary}20` : accentPalette.surfaceDark,
                    borderColor:
                      paramSizeB === b ? accentPalette.primary : `${accentPalette.borderDark}60`,
                    color: paramSizeB === b ? '#FFF' : '#94A3B8',
                  }}
                >
                  {b}B
                </button>
              ))}
            </div>
          </div>

          {/* Quantization Tier */}
          <div>
            <label className="text-xs font-mono uppercase tracking-wider text-slate-400 block mb-2">
              Quantization Precision Tier
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {[
                { label: 'Q2_K (2.5-bit)', bits: 2.5 },
                { label: 'Q4_K_M (4.5-bit)', bits: 4.5 },
                { label: 'Q8_0 (8.5-bit)', bits: 8.5 },
                { label: 'FP16 (16-bit)', bits: 16.0 },
              ].map((tier) => (
                <button
                  key={tier.label}
                  onClick={() => setQuantBits(tier.bits)}
                  className="py-2.5 px-2 text-xs font-mono rounded-lg border text-center transition-all"
                  style={{
                    backgroundColor:
                      quantBits === tier.bits
                        ? `${accentPalette.primary}20`
                        : accentPalette.surfaceDark,
                    borderColor:
                      quantBits === tier.bits
                        ? accentPalette.primary
                        : `${accentPalette.borderDark}60`,
                    color: quantBits === tier.bits ? '#FFF' : '#94A3B8',
                  }}
                >
                  {tier.label}
                </button>
              ))}
            </div>
          </div>

          {/* Context Tokens */}
          <div>
            <div className="flex justify-between items-center mb-2">
              <label className="text-xs font-mono uppercase tracking-wider text-slate-400">
                Context Window Length
              </label>
              <span className="text-xs font-mono font-bold" style={{ color: accentPalette.secondary }}>
                {contextLength.toLocaleString()} Tokens
              </span>
            </div>
            <div className="grid grid-cols-4 gap-2">
              {[2048, 4096, 8192, 16384].map((tokens) => (
                <button
                  key={tokens}
                  onClick={() => setContextLength(tokens)}
                  className="py-2 text-xs font-mono rounded-lg border text-center transition-all"
                  style={{
                    backgroundColor:
                      contextLength === tokens
                        ? `${accentPalette.secondary}20`
                        : accentPalette.surfaceDark,
                    borderColor:
                      contextLength === tokens
                        ? accentPalette.secondary
                        : `${accentPalette.borderDark}60`,
                    color: contextLength === tokens ? '#FFF' : '#94A3B8',
                  }}
                >
                  {tokens >= 1000 ? `${tokens / 1024}k` : tokens}
                </button>
              ))}
            </div>
          </div>

          {/* Memory Footprint Breakdown Box */}
          <div
            className="p-4 rounded-xl border space-y-3"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}80`,
            }}
          >
            <div className="text-xs font-mono uppercase tracking-wider text-slate-400 flex items-center justify-between">
              <span>Required Memory Allocation</span>
              <span>Available RAM: {availableRamMb} MB</span>
            </div>

            <div className="space-y-1.5 text-xs font-mono">
              <div className="flex justify-between text-slate-300">
                <span className="flex items-center gap-1.5">
                  <HardDrive className="w-3.5 h-3.5 text-cyan-400" />
                  Model Weights:
                </span>
                <span className="font-bold text-white">{weightsMb.toLocaleString()} MB</span>
              </div>
              <div className="flex justify-between text-slate-300">
                <span className="flex items-center gap-1.5">
                  <Cpu className="w-3.5 h-3.5 text-pink-400" />
                  KV Cache Attention States:
                </span>
                <span className="font-bold text-white">{kvCacheMb.toLocaleString()} MB</span>
              </div>
              <div className="flex justify-between text-slate-300">
                <span>Runtime Tensor Buffer Overhead:</span>
                <span className="font-bold text-white">{overheadMb} MB</span>
              </div>
              <div className="pt-2 border-t border-slate-700/60 flex justify-between text-sm">
                <span className="font-bold text-white">Total Memory Required:</span>
                <span className="font-bold" style={{ color: accentPalette.primary }}>
                  {totalRamRequiredMb.toLocaleString()} MB ({(totalRamRequiredMb / 1024).toFixed(2)} GB)
                </span>
              </div>
            </div>

            {/* Device Verdict */}
            <div
              className={`p-3 rounded-lg border flex items-center gap-2.5 text-xs ${
                fitsComfortably
                  ? 'bg-emerald-950/40 border-emerald-500/30 text-emerald-300'
                  : 'bg-red-950/40 border-red-500/30 text-red-300'
              }`}
            >
              {fitsComfortably ? (
                <>
                  <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
                  <span>
                    Optimal Fit! Fits comfortably in available physical memory with 0 swap thrashing.
                  </span>
                </>
              ) : (
                <>
                  <AlertTriangle className="w-4 h-4 text-red-400 flex-shrink-0" />
                  <span>
                    Memory Constrained. Consider reducing context length or switching to Q4_K_M.
                  </span>
                </>
              )}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div
          className="p-4 border-t flex justify-end"
          style={{
            backgroundColor: accentPalette.surfaceDark,
            borderColor: `${accentPalette.borderDark}80`,
          }}
        >
          <button
            onClick={onClose}
            className="px-5 py-2 rounded-xl text-xs font-semibold text-black"
            style={{ backgroundColor: accentPalette.primary }}
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
