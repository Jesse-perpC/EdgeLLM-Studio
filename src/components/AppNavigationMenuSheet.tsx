import React from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { AppDestination } from '../types';
import {
  X,
  LayoutDashboard,
  MessageSquare,
  Cpu,
  Globe,
  Swords,
  Sparkles,
  Database,
  Server,
  ListOrdered,
  Puzzle,
  Lock,
  Flame,
  Calculator,
  Mic,
  Palette,
  Settings,
  ChevronRight,
  ShieldCheck,
} from 'lucide-react';

interface AppNavigationMenuSheetProps {
  isOpen: boolean;
  onClose: () => void;
  onOpenSiliconGovernor: () => void;
  onOpenQuantCalc: () => void;
  onOpenVoiceStudio: () => void;
  onOpenThemeStudio: () => void;
}

export const AppNavigationMenuSheet: React.FC<AppNavigationMenuSheetProps> = ({
  isOpen,
  onClose,
  onOpenSiliconGovernor,
  onOpenQuantCalc,
  onOpenVoiceStudio,
  onOpenThemeStudio,
}) => {
  const { currentDestination, setCurrentDestination, accentPalette, setIsInSettings } = useEdgeLLM();

  if (!isOpen) return null;

  const studios = [
    {
      destination: AppDestination.DASHBOARD,
      title: 'Home Dashboard',
      subtitle: 'System Telemetry & Quick Studios',
      icon: LayoutDashboard,
      badge: 'Main',
    },
    {
      destination: AppDestination.CHAT,
      title: 'Chat Studio',
      subtitle: 'Decisive Zero-Fluff Neural Inference',
      icon: MessageSquare,
      badge: 'Streaming',
    },
    {
      destination: AppDestination.MODELS,
      title: 'Local Model Vault',
      subtitle: 'GGUF, LiteRT, ONNX & MNN Checkpoints',
      icon: Cpu,
      badge: 'Universal',
    },
    {
      destination: AppDestination.HF_EXPLORER,
      title: 'Hugging Face Hub Explorer',
      subtitle: '100,000+ Open Checkpoints Direct Download',
      icon: Globe,
      badge: 'Live Hub',
    },
    {
      destination: AppDestination.BENCHMARK,
      title: 'Neural Model Arena',
      subtitle: 'LMSYS Blind Battle & Tensor Clash Colosseum',
      icon: Swords,
      badge: 'Ranked',
    },
    {
      destination: AppDestination.IMAGE_STUDIO,
      title: 'Stable Diffusion Studio',
      subtitle: 'On-Device Text2Img & Interactive Inpainting',
      icon: Sparkles,
      badge: ':ai_sd',
    },
    {
      destination: AppDestination.RAG_DEBUG,
      title: 'RAG Knowledge Debugger',
      subtitle: 'SHA-256 Chunks & Cosine Threshold Slider',
      icon: Database,
      badge: '128-d',
    },
    {
      destination: AppDestination.API,
      title: 'OpenAI / Ollama HTTP Server',
      subtitle: 'Local Daemon on Port 8080 / 11434',
      icon: Server,
      badge: 'Daemon',
    },
    {
      destination: AppDestination.QUEUE,
      title: 'Background Tasks Queue',
      subtitle: 'Screen-Off Batch Analysis & Audit',
      icon: ListOrdered,
      badge: 'Async',
    },
    {
      destination: AppDestination.PLUGINS,
      title: 'Sandboxed Plugins & MCP',
      subtitle: 'PII Redactor & Model Context Protocol',
      icon: Puzzle,
      badge: 'Sandbox',
    },
    {
      destination: AppDestination.VAULT,
      title: 'Zero-Knowledge Encrypted Vault',
      subtitle: 'AES-256-GCM Hardware Encrypted Exports',
      icon: Lock,
      badge: 'PBKDF2',
    },
  ];

  const handleSelect = (dest: AppDestination) => {
    setCurrentDestination(dest);
    setIsInSettings(false);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div
        className="w-full max-w-md h-full flex flex-col border-l overflow-hidden shadow-2xl transition-colors"
        style={{
          backgroundColor: accentPalette.bgDark,
          borderColor: `${accentPalette.borderDark}90`,
        }}
      >
        {/* Drawer Header */}
        <div
          className="flex items-center justify-between px-5 py-4 border-b"
          style={{
            backgroundColor: accentPalette.surfaceDark,
            borderColor: `${accentPalette.borderDark}60`,
          }}
        >
          <div>
            <div className="flex items-center gap-2">
              <span
                className="w-2.5 h-2.5 rounded-full"
                style={{ backgroundColor: accentPalette.primary }}
              />
              <h2 className="text-lg font-bold text-white tracking-tight">EdgeLLM Suite</h2>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">All 11 Neural Studios & Power Tools</p>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content List */}
        <div className="flex-1 overflow-y-auto px-4 py-4 space-y-6">
          {/* Quick Power Tools Shelf */}
          <div>
            <p className="text-[11px] font-mono uppercase tracking-wider text-slate-400 mb-2.5 px-1">
              Hardware & Power Controls
            </p>
            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => {
                  onClose();
                  onOpenSiliconGovernor();
                }}
                className="flex items-center gap-2.5 p-3 rounded-xl border text-left transition-all hover:border-slate-500"
                style={{
                  backgroundColor: accentPalette.surfaceDark,
                  borderColor: `${accentPalette.borderDark}80`,
                }}
              >
                <div
                  className="p-2 rounded-lg"
                  style={{ backgroundColor: `${accentPalette.primary}18` }}
                >
                  <Flame className="w-4 h-4" style={{ color: accentPalette.primary }} />
                </div>
                <div>
                  <div className="text-xs font-semibold text-white">Silicon Governor</div>
                  <div className="text-[10px] text-slate-400">Thermals & Threads</div>
                </div>
              </button>

              <button
                onClick={() => {
                  onClose();
                  onOpenQuantCalc();
                }}
                className="flex items-center gap-2.5 p-3 rounded-xl border text-left transition-all hover:border-slate-500"
                style={{
                  backgroundColor: accentPalette.surfaceDark,
                  borderColor: `${accentPalette.borderDark}80`,
                }}
              >
                <div
                  className="p-2 rounded-lg"
                  style={{ backgroundColor: `${accentPalette.secondary}18` }}
                >
                  <Calculator className="w-4 h-4" style={{ color: accentPalette.secondary }} />
                </div>
                <div>
                  <div className="text-xs font-semibold text-white">Quant Calc</div>
                  <div className="text-[10px] text-slate-400">VRAM & KV Sizer</div>
                </div>
              </button>

              <button
                onClick={() => {
                  onClose();
                  onOpenVoiceStudio();
                }}
                className="flex items-center gap-2.5 p-3 rounded-xl border text-left transition-all hover:border-slate-500"
                style={{
                  backgroundColor: accentPalette.surfaceDark,
                  borderColor: `${accentPalette.borderDark}80`,
                }}
              >
                <div
                  className="p-2 rounded-lg bg-pink-500/15"
                >
                  <Mic className="w-4 h-4 text-pink-400" />
                </div>
                <div>
                  <div className="text-xs font-semibold text-white">Voice Cloning</div>
                  <div className="text-[10px] text-slate-400">Neural TTS & Audio</div>
                </div>
              </button>

              <button
                onClick={() => {
                  onClose();
                  onOpenThemeStudio();
                }}
                className="flex items-center gap-2.5 p-3 rounded-xl border text-left transition-all hover:border-slate-500"
                style={{
                  backgroundColor: accentPalette.surfaceDark,
                  borderColor: `${accentPalette.borderDark}80`,
                }}
              >
                <div
                  className="p-2 rounded-lg"
                  style={{ backgroundColor: `${accentPalette.primary}18` }}
                >
                  <Palette className="w-4 h-4" style={{ color: accentPalette.primary }} />
                </div>
                <div>
                  <div className="text-xs font-semibold text-white">Theme Studio</div>
                  <div className="text-[10px] text-slate-400">6 Cyber Palettes</div>
                </div>
              </button>
            </div>
          </div>

          {/* All 11 Studios List */}
          <div>
            <p className="text-[11px] font-mono uppercase tracking-wider text-slate-400 mb-2.5 px-1">
              Neural Navigation Menu
            </p>
            <div className="space-y-1.5">
              {studios.map((item) => {
                const Icon = item.icon;
                const isSelected = currentDestination === item.destination;

                return (
                  <button
                    key={item.destination}
                    onClick={() => handleSelect(item.destination)}
                    className="w-full flex items-center justify-between p-3 rounded-xl border text-left transition-all hover:translate-x-0.5"
                    style={{
                      backgroundColor: isSelected
                        ? `${accentPalette.primary}16`
                        : accentPalette.surfaceDark,
                      borderColor: isSelected
                        ? accentPalette.primary
                        : `${accentPalette.borderDark}60`,
                    }}
                  >
                    <div className="flex items-center gap-3">
                      <div
                        className="p-2 rounded-lg"
                        style={{
                          backgroundColor: isSelected
                            ? `${accentPalette.primary}30`
                            : 'rgba(255,255,255,0.05)',
                        }}
                      >
                        <Icon
                          className="w-4 h-4"
                          style={{
                            color: isSelected ? accentPalette.primary : '#94A3B8',
                          }}
                        />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span
                            className="text-sm font-semibold"
                            style={{ color: isSelected ? '#FFFFFF' : '#E2E8F0' }}
                          >
                            {item.title}
                          </span>
                          <span
                            className="text-[10px] font-mono px-1.5 py-0.5 rounded border"
                            style={{
                              color: isSelected ? accentPalette.primary : '#94A3B8',
                              borderColor: isSelected
                                ? `${accentPalette.primary}50`
                                : 'rgba(255,255,255,0.1)',
                              backgroundColor: 'rgba(0,0,0,0.2)',
                            }}
                          >
                            {item.badge}
                          </span>
                        </div>
                        <p className="text-xs text-slate-400 mt-0.5">{item.subtitle}</p>
                      </div>
                    </div>
                    <ChevronRight
                      className="w-4 h-4 transition-transform text-slate-500"
                      style={{ color: isSelected ? accentPalette.primary : undefined }}
                    />
                  </button>
                );
              })}
            </div>
          </div>

          {/* System Settings bottom entry */}
          <div className="pt-2">
            <button
              onClick={() => {
                setIsInSettings(true);
                onClose();
              }}
              className="w-full flex items-center justify-between p-3 rounded-xl border transition-all text-left bg-slate-800/40 border-slate-700/60 hover:bg-slate-800/80"
            >
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-slate-700/50">
                  <Settings className="w-4 h-4 text-slate-300" />
                </div>
                <div>
                  <div className="text-sm font-semibold text-white">Full System Settings</div>
                  <div className="text-xs text-slate-400">Vulkan, WebGPU & Thermal Envelope</div>
                </div>
              </div>
              <ChevronRight className="w-4 h-4 text-slate-400" />
            </button>
          </div>
        </div>

        {/* Footer */}
        <div
          className="p-4 border-t text-center text-xs text-slate-400 flex items-center justify-center gap-2"
          style={{
            backgroundColor: accentPalette.surfaceDark,
            borderColor: `${accentPalette.borderDark}60`,
          }}
        >
          <ShieldCheck className="w-4 h-4 text-emerald-400" />
          <span>100% Air-Gapped Sovereign Neural Engine</span>
        </div>
      </div>
    </div>
  );
};
