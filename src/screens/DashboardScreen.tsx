import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { AppDestination } from '../types';
import {
  Cpu,
  Zap,
  HardDrive,
  Flame,
  Swords,
  Sparkles,
  Server,
  Database,
  ArrowRight,
  ShieldCheck,
  Activity,
  Layers,
  ChevronRight,
} from 'lucide-react';
import { QuantizationCalculatorSheet } from '../components/QuantizationCalculatorSheet';
import { SiliconGovernorSheet } from '../components/SiliconGovernorSheet';

interface DashboardScreenProps {
  onNavigateToChat: () => void;
  onNavigateToModels: () => void;
  onNavigateToApi: () => void;
  onNavigateToImageStudio: () => void;
  onNavigateToRagDebug: () => void;
  onNavigateToArena: () => void;
}

export const DashboardScreen: React.FC<DashboardScreenProps> = ({
  onNavigateToChat,
  onNavigateToModels,
  onNavigateToApi,
  onNavigateToImageStudio,
  onNavigateToRagDebug,
  onNavigateToArena,
}) => {
  const { hardwareInfo, activeModel, governorStatus, accentPalette, isAirGapped } = useEdgeLLM();

  const [showQuantCalc, setShowQuantCalc] = useState<boolean>(false);
  const [showSiliconGovernor, setShowSiliconGovernor] = useState<boolean>(false);

  const totalRamGb = (hardwareInfo.totalRamBytes / (1024 * 1024 * 1024)).toFixed(1);
  const availableRamGb = (hardwareInfo.availableRamBytes / (1024 * 1024 * 1024)).toFixed(1);
  const usedRamGb = (
    (hardwareInfo.totalRamBytes - hardwareInfo.availableRamBytes) /
    (1024 * 1024 * 1024)
  ).toFixed(1);
  const ramUsagePercent = Math.round(
    ((hardwareInfo.totalRamBytes - hardwareInfo.availableRamBytes) / hardwareInfo.totalRamBytes) * 100
  );

  return (
    <div className="max-w-4xl mx-auto px-4 py-5 space-y-6 pb-24">
      {/* Hardware Telemetry Card */}
      <div
        className="p-5 rounded-2xl border relative overflow-hidden shadow-xl"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}90`,
        }}
      >
        <div
          className="absolute -top-12 -right-12 w-48 h-48 rounded-full blur-3xl pointer-events-none opacity-20"
          style={{ backgroundColor: accentPalette.primary }}
        />

        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800/80 pb-4 mb-4">
          <div className="flex items-center gap-3">
            <div
              className="p-3 rounded-xl flex items-center justify-center shadow-lg"
              style={{
                backgroundColor: `${accentPalette.primary}20`,
                border: `1px solid ${accentPalette.primary}40`,
              }}
            >
              <Cpu className="w-6 h-6" style={{ color: accentPalette.primary }} />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-base font-bold text-white tracking-tight">
                  {hardwareInfo.socModel}
                </h2>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
                  NOMINAL
                </span>
              </div>
              <p className="text-xs text-slate-400 mt-0.5">
                {hardwareInfo.gpuRenderer} • {hardwareInfo.cpuCores} Cores Active
              </p>
            </div>
          </div>

          <button
            onClick={() => setShowSiliconGovernor(true)}
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold text-white border transition-colors self-start sm:self-auto"
            style={{
              backgroundColor: 'rgba(255,255,255,0.05)',
              borderColor: `${accentPalette.borderDark}90`,
            }}
          >
            <Flame className="w-4 h-4 text-amber-400" />
            <span>Governor Controls</span>
          </button>
        </div>

        {/* Telemetry Metric Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
          <div className="p-3 rounded-xl bg-slate-900/60 border border-slate-800">
            <div className="text-[10px] uppercase font-mono text-slate-400">NPU Accelerator</div>
            <div className="text-sm font-bold text-white mt-1 flex items-center justify-center gap-1">
              <Zap className="w-3.5 h-3.5 text-cyan-400" />
              {hardwareInfo.npuTopsEstimate} TOPS
            </div>
            <div className="text-[10px] text-slate-500">Hexagon Direct</div>
          </div>

          <div className="p-3 rounded-xl bg-slate-900/60 border border-slate-800">
            <div className="text-[10px] uppercase font-mono text-slate-400">Memory Pressure</div>
            <div className="text-sm font-bold text-white mt-1">
              {usedRamGb} / {totalRamGb} GB
            </div>
            <div className="text-[10px] text-slate-500">{ramUsagePercent}% In Use</div>
          </div>

          <div className="p-3 rounded-xl bg-slate-900/60 border border-slate-800">
            <div className="text-[10px] uppercase font-mono text-slate-400">SoC Envelope</div>
            <div className="text-sm font-bold text-emerald-400 mt-1">
              {hardwareInfo.batteryTemperatureCelsius}°C
            </div>
            <div className="text-[10px] text-slate-500">Auto Throttle: 42°C</div>
          </div>

          <div className="p-3 rounded-xl bg-slate-900/60 border border-slate-800">
            <div className="text-[10px] uppercase font-mono text-slate-400">Governor State</div>
            <div className="text-sm font-bold text-amber-400 mt-1 uppercase text-xs">
              {governorStatus.mode.replace('_', ' ')}
            </div>
            <div className="text-[10px] text-slate-500">{governorStatus.activeThreads} CPU Threads</div>
          </div>
        </div>

        {/* RAM Bar */}
        <div className="mt-4 pt-4 border-t border-slate-800/80">
          <div className="flex justify-between items-center text-xs mb-1.5 font-mono text-slate-400">
            <span>Physical RAM Pool</span>
            <span>{availableRamGb} GB Free for Models</span>
          </div>
          <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden">
            <div
              className="h-full rounded-full transition-all duration-500"
              style={{
                width: `${ramUsagePercent}%`,
                backgroundColor: accentPalette.primary,
              }}
            />
          </div>
        </div>
      </div>

      {/* Active Model Hub Banner */}
      <div
        className="p-5 rounded-2xl border flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-lg"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.primary}40`,
        }}
      >
        <div className="flex items-start gap-3.5">
          <div
            className="p-2.5 rounded-xl mt-0.5"
            style={{ backgroundColor: `${accentPalette.primary}20` }}
          >
            <Layers className="w-5 h-5" style={{ color: accentPalette.primary }} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-mono uppercase text-slate-400">Active Loaded Model</span>
              <span
                className="text-[10px] font-mono px-2 py-0.5 rounded border"
                style={{
                  color: accentPalette.primary,
                  borderColor: `${accentPalette.primary}50`,
                }}
              >
                Ready
              </span>
            </div>
            <h3 className="text-base font-bold text-white mt-0.5">{activeModel.name}</h3>
            <p className="text-xs text-slate-300 mt-0.5">
              {activeModel.parameterCount} • {activeModel.format} • {activeModel.quantization} • Up to{' '}
              {activeModel.maxTurboTokPerSec} tok/s
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={onNavigateToModels}
            className="px-3.5 py-2 rounded-xl text-xs font-semibold text-slate-300 border border-slate-700 hover:text-white hover:border-slate-500 transition-colors"
          >
            Swap Model
          </button>
          <button
            onClick={onNavigateToChat}
            className="px-4 py-2 rounded-xl text-xs font-bold text-black flex items-center gap-1.5 transition-transform hover:scale-[1.02]"
            style={{ backgroundColor: accentPalette.primary }}
          >
            <span>Launch Studio</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Quick Launch Studios Grid */}
      <div>
        <div className="flex items-center justify-between mb-3 px-1">
          <h3 className="text-sm font-bold text-white uppercase tracking-wider font-mono">
            On-Device Neural Studios
          </h3>
          <span className="text-xs text-slate-400">100% Offline Capable</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
          {/* Chat Studio */}
          <div
            onClick={onNavigateToChat}
            className="p-4 rounded-xl border cursor-pointer group transition-all hover:border-cyan-400 hover:shadow-lg"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}80`,
            }}
          >
            <div className="flex items-center justify-between mb-2.5">
              <div
                className="p-2 rounded-lg"
                style={{ backgroundColor: `${accentPalette.primary}20` }}
              >
                <Activity className="w-5 h-5" style={{ color: accentPalette.primary }} />
              </div>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-cyan-950/60 text-cyan-300 border border-cyan-800">
                0-Fluff AI
              </span>
            </div>
            <h4 className="text-sm font-bold text-white group-hover:text-cyan-400 transition-colors">
              Chat & Reason Studio
            </h4>
            <p className="text-xs text-slate-400 mt-1">
              Direct, decisive answers with live tok/s speedometer and Chain-of-Thought controls.
            </p>
          </div>

          {/* Model Arena */}
          <div
            onClick={onNavigateToArena}
            className="p-4 rounded-xl border cursor-pointer group transition-all hover:border-violet-400 hover:shadow-lg"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}80`,
            }}
          >
            <div className="flex items-center justify-between mb-2.5">
              <div className="p-2 rounded-lg bg-violet-500/20">
                <Swords className="w-5 h-5 text-violet-400" />
              </div>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-violet-950/60 text-violet-300 border border-violet-800">
                LMSYS Arena
              </span>
            </div>
            <h4 className="text-sm font-bold text-white group-hover:text-violet-400 transition-colors">
              Neural Arena & Clash
            </h4>
            <p className="text-xs text-slate-400 mt-1">
              Side-by-side A/B model battles with blind holographic shields and dynamic Elo ranking.
            </p>
          </div>

          {/* Image Studio */}
          <div
            onClick={onNavigateToImageStudio}
            className="p-4 rounded-xl border cursor-pointer group transition-all hover:border-pink-400 hover:shadow-lg"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}80`,
            }}
          >
            <div className="flex items-center justify-between mb-2.5">
              <div className="p-2 rounded-lg bg-pink-500/20">
                <Sparkles className="w-5 h-5 text-pink-400" />
              </div>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-pink-950/60 text-pink-300 border border-pink-800">
                :ai_sd
              </span>
            </div>
            <h4 className="text-sm font-bold text-white group-hover:text-pink-400 transition-colors">
              Stable Diffusion AI
            </h4>
            <p className="text-xs text-slate-400 mt-1">
              On-device Text2Img, Image2Image, 4× ESRGAN upscale, and inpainting touch mask brush.
            </p>
          </div>

          {/* RAG Debugger */}
          <div
            onClick={onNavigateToRagDebug}
            className="p-4 rounded-xl border cursor-pointer group transition-all hover:border-emerald-400 hover:shadow-lg"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}80`,
            }}
          >
            <div className="flex items-center justify-between mb-2.5">
              <div className="p-2 rounded-lg bg-emerald-500/20">
                <Database className="w-5 h-5 text-emerald-400" />
              </div>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-emerald-950/60 text-emerald-300 border border-emerald-800">
                128-d Vector
              </span>
            </div>
            <h4 className="text-sm font-bold text-white group-hover:text-emerald-400 transition-colors">
              RAG Knowledge Base
            </h4>
            <p className="text-xs text-slate-400 mt-1">
              Ingest private PDFs & Markdown with SHA-256 chunk inspector and cosine slider.
            </p>
          </div>

          {/* API Server */}
          <div
            onClick={onNavigateToApi}
            className="p-4 rounded-xl border cursor-pointer group transition-all hover:border-amber-400 hover:shadow-lg"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}80`,
            }}
          >
            <div className="flex items-center justify-between mb-2.5">
              <div className="p-2 rounded-lg bg-amber-500/20">
                <Server className="w-5 h-5 text-amber-400" />
              </div>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-amber-950/60 text-amber-300 border border-amber-800">
                Port 8080
              </span>
            </div>
            <h4 className="text-sm font-bold text-white group-hover:text-amber-400 transition-colors">
              OpenAI / Ollama HTTP Daemon
            </h4>
            <p className="text-xs text-slate-400 mt-1">
              Local OpenAI & Ollama REST server with Bearer auth and live request audit logger.
            </p>
          </div>

          {/* Quantization Calculator */}
          <div
            onClick={() => setShowQuantCalc(true)}
            className="p-4 rounded-xl border cursor-pointer group transition-all hover:border-sky-400 hover:shadow-lg"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}80`,
            }}
          >
            <div className="flex items-center justify-between mb-2.5">
              <div className="p-2 rounded-lg bg-sky-500/20">
                <HardDrive className="w-5 h-5 text-sky-400" />
              </div>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-sky-950/60 text-sky-300 border border-sky-800">
                VRAM Sizer
              </span>
            </div>
            <h4 className="text-sm font-bold text-white group-hover:text-sky-400 transition-colors">
              Quant & KV Calculator
            </h4>
            <p className="text-xs text-slate-400 mt-1">
              Calculate exact model weight VRAM and KV cache memory requirements before running.
            </p>
          </div>
        </div>
      </div>

      {/* Dialogs */}
      <QuantizationCalculatorSheet isOpen={showQuantCalc} onClose={() => setShowQuantCalc(false)} />
      <SiliconGovernorSheet
        isOpen={showSiliconGovernor}
        onClose={() => setShowSiliconGovernor(false)}
      />
    </div>
  );
};
