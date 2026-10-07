import React from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { GovernorMode } from '../types';
import { X, Flame, Cpu, Zap, BatteryCharging, ShieldAlert } from 'lucide-react';

interface SiliconGovernorSheetProps {
  isOpen: boolean;
  onClose: () => void;
}

export const SiliconGovernorSheet: React.FC<SiliconGovernorSheetProps> = ({ isOpen, onClose }) => {
  const { governorStatus, setGovernorMode, setThreadCount, hardwareInfo, accentPalette } = useEdgeLLM();

  if (!isOpen) return null;

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
            <div className="p-2 rounded-xl bg-amber-500/15">
              <Flame className="w-5 h-5 text-amber-400" />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">Silicon Governor & Thermals</h2>
              <p className="text-xs text-slate-400">NPU / GPU Compute & Thread Allocation</p>
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
          {/* Live Sensor Metrics */}
          <div
            className="p-4 rounded-xl border grid grid-cols-3 gap-3 text-center"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}60`,
            }}
          >
            <div>
              <div className="text-[10px] uppercase font-mono text-slate-400">Battery Temp</div>
              <div className="text-sm font-bold text-emerald-400 mt-1">
                {hardwareInfo.batteryTemperatureCelsius}°C
              </div>
              <div className="text-[10px] text-slate-500">Nominal Zone</div>
            </div>
            <div>
              <div className="text-[10px] uppercase font-mono text-slate-400">Power Draw</div>
              <div className="text-sm font-bold text-amber-400 mt-1">
                {governorStatus.powerConsumptionWatts} W
              </div>
              <div className="text-[10px] text-slate-500">Estimated SoC</div>
            </div>
            <div>
              <div className="text-[10px] uppercase font-mono text-slate-400">Thermal Guard</div>
              <div className="text-sm font-bold text-sky-400 mt-1">42.0°C Cap</div>
              <div className="text-[10px] text-slate-500">Auto-Throttled</div>
            </div>
          </div>

          {/* Governor Modes */}
          <div>
            <label className="text-xs font-mono uppercase tracking-wider text-slate-400 block mb-2">
              Governor Strategy Profile
            </label>
            <div className="space-y-2">
              {[
                {
                  mode: GovernorMode.MAX_PERFORMANCE,
                  title: 'Max Turbo Performance',
                  desc: 'Allocates all 8 Big+Middle CPU cores and max GPU shaders for lowest latency.',
                  icon: Zap,
                  color: '#FF9F1C',
                },
                {
                  mode: GovernorMode.DYNAMIC_BALANCED,
                  title: 'Dynamic Balanced (Default)',
                  desc: 'Dynamically shifts threads between 4 and 6 cores with thermal cooling periods.',
                  icon: Cpu,
                  color: '#00F0FF',
                },
                {
                  mode: GovernorMode.ECO_BATTERY_SAVER,
                  title: 'Eco Battery Saver',
                  desc: 'Locks threads to 4 Little efficiency cores to minimize battery drain.',
                  icon: BatteryCharging,
                  color: '#10B981',
                },
              ].map((item) => {
                const isSelected = governorStatus.mode === item.mode;
                const Icon = item.icon;

                return (
                  <button
                    key={item.mode}
                    onClick={() => setGovernorMode(item.mode)}
                    className="w-full flex items-start gap-3 p-3.5 rounded-xl border text-left transition-all"
                    style={{
                      backgroundColor: isSelected
                        ? `${item.color}15`
                        : accentPalette.surfaceDark,
                      borderColor: isSelected ? item.color : `${accentPalette.borderDark}60`,
                    }}
                  >
                    <div
                      className="p-2 rounded-lg mt-0.5"
                      style={{ backgroundColor: `${item.color}20` }}
                    >
                      <Icon className="w-4 h-4" style={{ color: item.color }} />
                    </div>
                    <div>
                      <div className="text-xs font-bold text-white">{item.title}</div>
                      <div className="text-[11px] text-slate-400 mt-0.5">{item.desc}</div>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Manual Compute Threads Override */}
          <div>
            <div className="flex justify-between items-center mb-2">
              <label className="text-xs font-mono uppercase tracking-wider text-slate-400">
                Active Compute Threads
              </label>
              <span className="text-xs font-mono font-bold" style={{ color: accentPalette.primary }}>
                {governorStatus.activeThreads} Threads (SoC Max: {hardwareInfo.cpuCores})
              </span>
            </div>
            <input
              type="range"
              min="1"
              max={hardwareInfo.cpuCores}
              value={governorStatus.activeThreads}
              onChange={(e) => setThreadCount(parseInt(e.target.value, 10))}
              className="w-full h-2 rounded-lg appearance-none cursor-pointer accent-cyan-400 bg-slate-800"
            />
            <div className="flex justify-between text-[10px] font-mono text-slate-500 mt-1">
              <span>1 Core (Eco)</span>
              <span>4 Cores (Balanced)</span>
              <span>8 Cores (Max Turbo)</span>
            </div>
          </div>

          {/* Attention Sink & KV Compaction Info */}
          <div
            className="p-3.5 rounded-xl border flex items-start gap-3"
            style={{
              backgroundColor: `${accentPalette.primary}10`,
              borderColor: `${accentPalette.primary}30`,
            }}
          >
            <ShieldAlert className="w-5 h-5 flex-shrink-0" style={{ color: accentPalette.primary }} />
            <div className="text-xs">
              <span className="font-semibold text-white">Attention Sink & KV Compaction Enabled: </span>
              <span className="text-slate-300">
                Compresses Key-Value cache attention states during extended conversations to completely prevent Out-Of-Memory (OOM) aborts.
              </span>
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
            Save & Dismiss
          </button>
        </div>
      </div>
    </div>
  );
};
