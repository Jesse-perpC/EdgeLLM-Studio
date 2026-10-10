import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { ComputeBackend, GovernorMode } from '../types';
import {
  Settings as SettingsIcon,
  Cpu,
  Flame,
  ShieldCheck,
  Globe,
  Zap,
  CreditCard,
  Palette,
  RefreshCw,
  Sliders,
  CheckCircle2,
} from 'lucide-react';
import { BillingAndAllocationsSheet } from '../components/BillingAndAllocationsSheet';
import { ThemeStudioSheet } from '../components/ThemeStudioSheet';

export const SettingsScreen: React.FC = () => {
  const {
    hardwareInfo,
    governorStatus,
    setGovernorMode,
    setThreadCount,
    isAirGapped,
    toggleAirGappedMode,
    accentPalette,
    userProfile,
  } = useEdgeLLM();

  const [backend, setBackend] = useState<ComputeBackend>(ComputeBackend.VULKAN_GPU);
  const [showBilling, setShowBilling] = useState<boolean>(false);
  const [showThemeStudio, setShowThemeStudio] = useState<boolean>(false);

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
            <SettingsIcon className="w-6 h-6" style={{ color: accentPalette.primary }} />
          </div>
          <div>
            <h2 className="text-base font-bold text-white tracking-tight">
              Hardware Acceleration & Architecture
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Silicon Execution Pipelines, Governors & Theme Palettes
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          <button
            onClick={() => setShowThemeStudio(true)}
            className="px-3.5 py-2 rounded-xl text-xs font-semibold text-white border border-slate-700 bg-slate-800/80 hover:bg-slate-700 flex items-center gap-1.5"
          >
            <Palette className="w-3.5 h-3.5" style={{ color: accentPalette.primary }} />
            <span>Theme Studio</span>
          </button>
          <button
            onClick={() => setShowBilling(true)}
            className="px-3.5 py-2 rounded-xl text-xs font-bold text-black flex items-center gap-1.5"
            style={{ backgroundColor: accentPalette.primary }}
          >
            <CreditCard className="w-3.5 h-3.5" />
            <span>Billing & Pro</span>
          </button>
        </div>
      </div>

      {/* Network Sovereignty Card */}
      <div
        className="p-5 rounded-2xl border space-y-3 shadow-lg"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}80`,
        }}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            {isAirGapped ? (
              <ShieldCheck className="w-5 h-5 text-emerald-400" />
            ) : (
              <Globe className="w-5 h-5 text-sky-400" />
            )}
            <div>
              <div className="text-sm font-bold text-white">
                {isAirGapped ? 'Air-Gapped Sovereign Isolation' : 'Cloud Assisted Mode'}
              </div>
              <div className="text-xs text-slate-400 mt-0.5">
                {isAirGapped
                  ? 'All network sockets closed. Zero data leaves physical device memory.'
                  : 'Allows hybrid cloud fallback and external API integrations.'}
              </div>
            </div>
          </div>

          <button
            onClick={toggleAirGappedMode}
            className={`w-12 h-6 flex items-center rounded-full p-1 transition-colors ${
              isAirGapped ? 'bg-emerald-500 justify-end' : 'bg-slate-700 justify-start'
            }`}
          >
            <div className="bg-white w-4 h-4 rounded-full shadow-md" />
          </button>
        </div>
      </div>

      {/* Compute Backend Delegation */}
      <div
        className="p-5 rounded-2xl border space-y-4 shadow-lg"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}80`,
        }}
      >
        <div>
          <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider mb-1">
            Compute Backend Delegation
          </h3>
          <p className="text-xs text-slate-400">
            Target specific silicon acceleration hardware delegates for next-token generation
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          {[
            {
              backend: ComputeBackend.VULKAN_GPU,
              title: 'Vulkan 1.3 GPU',
              desc: 'Adreno / Mali GPU matrix shaders for maximum tokens/second.',
              badge: 'Default GPU',
            },
            {
              backend: ComputeBackend.ARM_NEON,
              title: 'ARM NEON SIMD',
              desc: 'CPU vector extensions with universal zero-crash compatibility.',
              badge: 'Universal',
            },
            {
              backend: ComputeBackend.WEBGPU,
              title: 'WebGPU / NPU',
              desc: 'Direct hardware tensor cores with low battery draw.',
              badge: 'Fast',
            },
          ].map((item) => {
            const isSelected = backend === item.backend;

            return (
              <button
                key={item.backend}
                onClick={() => setBackend(item.backend)}
                className="p-4 rounded-xl border text-left flex flex-col justify-between space-y-2 transition-all"
                style={{
                  backgroundColor: isSelected
                    ? `${accentPalette.primary}15`
                    : 'rgba(255,255,255,0.02)',
                  borderColor: isSelected ? accentPalette.primary : `${accentPalette.borderDark}60`,
                }}
              >
                <div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-white">{item.title}</span>
                    <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-300">
                      {item.badge}
                    </span>
                  </div>
                  <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">{item.desc}</p>
                </div>
                {isSelected && (
                  <div
                    className="text-[10px] font-mono font-bold flex items-center gap-1"
                    style={{ color: accentPalette.primary }}
                  >
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    Active Backend
                  </div>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Silicon Governor Strategy */}
      <div
        className="p-5 rounded-2xl border space-y-4 shadow-lg"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}80`,
        }}
      >
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider mb-1">
              Silicon Governor Strategy
            </h3>
            <p className="text-xs text-slate-400">
              Thermal envelope management and thread scaling
            </p>
          </div>
          <Flame className="w-5 h-5 text-amber-400" />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          {[
            {
              mode: GovernorMode.MAX_PERFORMANCE,
              title: 'Max Turbo',
              desc: '8 Big+Middle Cores',
            },
            {
              mode: GovernorMode.DYNAMIC_BALANCED,
              title: 'Dynamic Balanced',
              desc: '6 Scaled Cores',
            },
            {
              mode: GovernorMode.ECO_BATTERY_SAVER,
              title: 'Eco Saver',
              desc: '4 Little Cores',
            },
          ].map((item) => {
            const isSelected = governorStatus.mode === item.mode;

            return (
              <button
                key={item.mode}
                onClick={() => setGovernorMode(item.mode)}
                className="p-3.5 rounded-xl border text-left transition-all"
                style={{
                  backgroundColor: isSelected
                    ? `${accentPalette.primary}15`
                    : 'rgba(255,255,255,0.02)',
                  borderColor: isSelected ? accentPalette.primary : `${accentPalette.borderDark}60`,
                }}
              >
                <div className="text-xs font-bold text-white">{item.title}</div>
                <div className="text-[10px] text-slate-400 mt-0.5">{item.desc}</div>
              </button>
            );
          })}
        </div>

        {/* Compute Threads Slider */}
        <div className="pt-2">
          <div className="flex justify-between items-center text-xs font-mono text-slate-400 mb-1.5">
            <span>Manual Compute Threads Pin:</span>
            <span className="font-bold text-white">{governorStatus.activeThreads} Threads</span>
          </div>
          <input
            type="range"
            min="1"
            max={hardwareInfo.cpuCores}
            value={governorStatus.activeThreads}
            onChange={(e) => setThreadCount(parseInt(e.target.value, 10))}
            className="w-full accent-cyan-400"
          />
        </div>
      </div>

      {/* Sheets */}
      <BillingAndAllocationsSheet isOpen={showBilling} onClose={() => setShowBilling(false)} />
      <ThemeStudioSheet isOpen={showThemeStudio} onClose={() => setShowThemeStudio(false)} />
    </div>
  );
};
