import React from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { AppDestination } from '../types';
import { LayoutDashboard, MessageSquare, Cpu, Swords, Sparkles } from 'lucide-react';

export const BottomNavBar: React.FC = () => {
  const { currentDestination, setCurrentDestination, accentPalette, isInSettings } = useEdgeLLM();

  if (isInSettings) return null;

  const tabs = [
    { destination: AppDestination.DASHBOARD, label: 'Home', icon: LayoutDashboard },
    { destination: AppDestination.CHAT, label: 'Chat', icon: MessageSquare },
    { destination: AppDestination.MODELS, label: 'Models', icon: Cpu },
    { destination: AppDestination.BENCHMARK, label: 'Arena', icon: Swords },
    { destination: AppDestination.IMAGE_STUDIO, label: 'Studio', icon: Sparkles },
  ];

  return (
    <nav
      className="fixed bottom-0 left-0 right-0 z-20 border-t backdrop-blur-xl transition-colors pb-safe"
      style={{
        backgroundColor: `${accentPalette.surfaceDark}F2`,
        borderColor: `${accentPalette.borderDark}60`,
      }}
    >
      <div className="max-w-md mx-auto flex items-center justify-around px-2 py-1.5">
        {tabs.map((tab) => {
          const isSelected = currentDestination === tab.destination;
          const Icon = tab.icon;

          return (
            <button
              key={tab.destination}
              onClick={() => setCurrentDestination(tab.destination)}
              className="flex-1 flex flex-col items-center justify-center py-1 px-1 rounded-xl transition-all"
            >
              <div
                className="w-12 h-7 flex items-center justify-center rounded-full transition-all"
                style={{
                  backgroundColor: isSelected ? `${accentPalette.primary}25` : 'transparent',
                }}
              >
                <Icon
                  className="w-5 h-5 transition-transform duration-200"
                  style={{
                    color: isSelected ? accentPalette.primary : '#94A3B8',
                    transform: isSelected ? 'scale(1.08)' : 'scale(1.0)',
                  }}
                />
              </div>
              <span
                className="text-[11px] mt-0.5 tracking-tight font-medium"
                style={{
                  color: isSelected ? accentPalette.primary : '#94A3B8',
                  fontWeight: isSelected ? 600 : 500,
                }}
              >
                {tab.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};
