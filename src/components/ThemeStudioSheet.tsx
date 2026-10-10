import React from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { AccentPaletteId, AppThemeMode } from '../types';
import { ACCENT_PALETTES } from '../data/constants';
import { X, Check, Moon, Sun, Monitor, Sparkles } from 'lucide-react';

interface ThemeStudioSheetProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ThemeStudioSheet: React.FC<ThemeStudioSheetProps> = ({ isOpen, onClose }) => {
  const { themeMode, setThemeMode, accentPalette, setAccentPalette } = useEdgeLLM();

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
            <div
              className="p-2 rounded-xl"
              style={{ backgroundColor: `${accentPalette.primary}20` }}
            >
              <Sparkles className="w-5 h-5" style={{ color: accentPalette.primary }} />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">Visual Aesthetic Studio</h2>
              <p className="text-xs text-slate-400">Futuristic Cyberpunk & Clean Palettes</p>
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
          {/* Theme Mode Mode Selector */}
          <div>
            <label className="text-xs font-mono uppercase tracking-wider text-slate-400 block mb-2">
              Luminance Mode
            </label>
            <div className="grid grid-cols-3 gap-2">
              {[
                {
                  mode: AppThemeMode.DARK,
                  label: 'Futuristic Dark',
                  desc: 'OLED Deep Void',
                  icon: Moon,
                },
                {
                  mode: AppThemeMode.LIGHT,
                  label: 'Precision Light',
                  desc: 'Lab White',
                  icon: Sun,
                },
                {
                  mode: AppThemeMode.SYSTEM,
                  label: 'System Sync',
                  desc: 'OS Automatic',
                  icon: Monitor,
                },
              ].map((item) => {
                const Icon = item.icon;
                const isSelected = themeMode === item.mode;
                return (
                  <button
                    key={item.mode}
                    onClick={() => setThemeMode(item.mode)}
                    className="flex flex-col items-center p-3 rounded-xl border text-center transition-all"
                    style={{
                      backgroundColor: isSelected
                        ? `${accentPalette.primary}20`
                        : accentPalette.surfaceDark,
                      borderColor: isSelected
                        ? accentPalette.primary
                        : `${accentPalette.borderDark}60`,
                    }}
                  >
                    <Icon
                      className="w-5 h-5 mb-1.5"
                      style={{ color: isSelected ? accentPalette.primary : '#94A3B8' }}
                    />
                    <span className="text-xs font-semibold text-white">{item.label}</span>
                    <span className="text-[10px] text-slate-400">{item.desc}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Accent Palettes Selection */}
          <div>
            <label className="text-xs font-mono uppercase tracking-wider text-slate-400 block mb-2">
              Signature Cyberpunk Color Schemes
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {Object.values(ACCENT_PALETTES).map((pal) => {
                const isSelected = accentPalette.id === pal.id;

                return (
                  <button
                    key={pal.id}
                    onClick={() => setAccentPalette(pal.id)}
                    className="flex items-center justify-between p-3.5 rounded-xl border text-left transition-all hover:scale-[1.01]"
                    style={{
                      backgroundColor: isSelected
                        ? `${pal.primary}18`
                        : pal.surfaceDark,
                      borderColor: isSelected ? pal.primary : `${pal.borderDark}80`,
                    }}
                  >
                    <div className="flex items-center gap-3">
                      {/* Swatch dots */}
                      <div className="flex -space-x-1.5 items-center">
                        <span
                          className="w-5 h-5 rounded-full border border-black/40 shadow-sm"
                          style={{ backgroundColor: pal.primary }}
                        />
                        <span
                          className="w-5 h-5 rounded-full border border-black/40 shadow-sm"
                          style={{ backgroundColor: pal.secondary }}
                        />
                      </div>
                      <div>
                        <div className="text-xs font-bold text-white flex items-center gap-1.5">
                          {pal.title}
                        </div>
                        <div className="text-[10px] text-slate-400">{pal.subtitle}</div>
                      </div>
                    </div>

                    {isSelected && (
                      <div
                        className="w-6 h-6 rounded-full flex items-center justify-center"
                        style={{ backgroundColor: pal.primary }}
                      >
                        <Check className="w-3.5 h-3.5 text-black font-bold" />
                      </div>
                    )}
                  </button>
                );
              })}
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
            className="px-5 py-2 rounded-xl text-xs font-semibold text-black transition-all"
            style={{ backgroundColor: accentPalette.primary }}
          >
            Apply & Close
          </button>
        </div>
      </div>
    </div>
  );
};
