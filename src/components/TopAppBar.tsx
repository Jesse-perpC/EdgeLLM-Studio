import React from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { AppDestination, AppThemeMode } from '../types';
import {
  ArrowLeft,
  Server,
  Palette,
  Moon,
  Sun,
  Monitor,
  Settings,
  Menu,
  ShieldCheck,
  Globe,
} from 'lucide-react';

interface TopAppBarProps {
  onOpenNavMenu: () => void;
  onOpenThemeStudio: () => void;
}

export const TopAppBar: React.FC<TopAppBarProps> = ({ onOpenNavMenu, onOpenThemeStudio }) => {
  const {
    isInSettings,
    setIsInSettings,
    isAirGapped,
    toggleAirGappedMode,
    apiStats,
    themeMode,
    setThemeMode,
    accentPalette,
    setCurrentDestination,
  } = useEdgeLLM();

  const cycleThemeMode = () => {
    if (themeMode === AppThemeMode.DARK) setThemeMode(AppThemeMode.LIGHT);
    else if (themeMode === AppThemeMode.LIGHT) setThemeMode(AppThemeMode.SYSTEM);
    else setThemeMode(AppThemeMode.DARK);
  };

  return (
    <header
      className="sticky top-0 z-30 flex items-center justify-between px-4 py-3 border-b transition-colors backdrop-blur-md"
      style={{
        backgroundColor: `${accentPalette.surfaceDark}E6`,
        borderColor: `${accentPalette.borderDark}60`,
      }}
    >
      <div className="flex items-center gap-3">
        {isInSettings ? (
          <button
            onClick={() => setIsInSettings(false)}
            className="p-2 rounded-xl text-slate-300 hover:text-white hover:bg-white/10 transition-colors"
            title="Back from Settings"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
        ) : null}

        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-base font-bold tracking-tight text-white flex items-center gap-1.5">
              <span>{isInSettings ? 'Acceleration & System' : 'EdgeLLM Studio'}</span>
              <span
                className="text-[10px] font-mono px-1.5 py-0.5 rounded border uppercase"
                style={{
                  color: accentPalette.primary,
                  borderColor: `${accentPalette.primary}40`,
                  backgroundColor: `${accentPalette.primary}15`,
                }}
              >
                v1.0 #1 Ranked
              </span>
            </h1>
          </div>

          {!isInSettings && (
            <button
              onClick={toggleAirGappedMode}
              className="mt-0.5 flex items-center gap-1.5 text-xs font-mono transition-opacity hover:opacity-80"
              title="Click to toggle Air-Gapped / Cloud Assist mode"
            >
              <span
                className="w-2 h-2 rounded-full animate-pulse"
                style={{ backgroundColor: isAirGapped ? '#10B981' : '#38BDF8' }}
              />
              <span className="text-slate-400 flex items-center gap-1">
                {isAirGapped ? (
                  <>
                    <ShieldCheck className="w-3 h-3 text-emerald-400 inline" />
                    <span className="text-emerald-400 font-medium">Air-Gapped Private</span>
                  </>
                ) : (
                  <>
                    <Globe className="w-3 h-3 text-sky-400 inline" />
                    <span className="text-sky-400 font-medium">Cloud Assisted</span>
                  </>
                )}
              </span>
            </button>
          )}
        </div>
      </div>

      {!isInSettings && (
        <div className="flex items-center gap-1">
          {/* API Server status button */}
          <button
            onClick={() => setCurrentDestination(AppDestination.API)}
            className="relative p-2 rounded-xl text-slate-300 hover:text-white hover:bg-white/10 transition-colors"
            title={`Local API Server: ${apiStats.isRunning ? 'Active on port ' + apiStats.port : 'Stopped'}`}
          >
            <Server
              className="w-5 h-5"
              style={{ color: apiStats.isRunning ? '#10B981' : undefined }}
            />
            {apiStats.isRunning && (
              <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full bg-emerald-400 ring-2 ring-emerald-950" />
            )}
          </button>

          {/* Theme Studio quick launcher */}
          <button
            onClick={onOpenThemeStudio}
            className="relative p-2 rounded-xl hover:bg-white/10 transition-colors"
            title={`Theme Studio (${accentPalette.title})`}
          >
            <Palette className="w-5 h-5" style={{ color: accentPalette.primary }} />
            <span
              className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full"
              style={{ backgroundColor: accentPalette.primary }}
            />
          </button>

          {/* Theme Mode Cycle */}
          <button
            onClick={cycleThemeMode}
            className="p-2 rounded-xl text-slate-300 hover:text-white hover:bg-white/10 transition-colors"
            title={`Cycle Mode (Current: ${themeMode})`}
          >
            {themeMode === AppThemeMode.DARK ? (
              <Moon className="w-5 h-5 text-indigo-400" />
            ) : themeMode === AppThemeMode.LIGHT ? (
              <Sun className="w-5 h-5 text-amber-400" />
            ) : (
              <Monitor className="w-5 h-5 text-cyan-400" />
            )}
          </button>

          {/* Settings */}
          <button
            onClick={() => setIsInSettings(true)}
            className="p-2 rounded-xl text-slate-300 hover:text-white hover:bg-white/10 transition-colors"
            title="System Settings"
          >
            <Settings className="w-5 h-5" />
          </button>

          {/* 3-lines hamburger menu */}
          <button
            onClick={onOpenNavMenu}
            className="p-2 ml-0.5 rounded-xl text-white hover:bg-white/10 transition-colors"
            title="All Studios Menu"
          >
            <Menu className="w-5 h-5" style={{ color: accentPalette.primary }} />
          </button>
        </div>
      )}
    </header>
  );
};
