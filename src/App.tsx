import React, { useState } from 'react';
import { EdgeLLMProvider, useEdgeLLM } from './context/EdgeLLMContext';
import { AppDestination } from './types';
import { TopAppBar } from './components/TopAppBar';
import { BottomNavBar } from './components/BottomNavBar';
import { AppNavigationMenuSheet } from './components/AppNavigationMenuSheet';
import { ThemeStudioSheet } from './components/ThemeStudioSheet';
import { SiliconGovernorSheet } from './components/SiliconGovernorSheet';
import { QuantizationCalculatorSheet } from './components/QuantizationCalculatorSheet';
import { VoiceCloningStudioSheet } from './components/VoiceCloningStudioSheet';

import { DashboardScreen } from './screens/DashboardScreen';
import { InferenceScreen } from './screens/InferenceScreen';
import { DeviceAndModelsScreen } from './screens/DeviceAndModelsScreen';
import { HfExplorerScreen } from './screens/HfExplorerScreen';
import { ModelArenaScreen } from './screens/ModelArenaScreen';
import { ImageTaskScreen } from './screens/ImageTaskScreen';
import { RagDebugScreen } from './screens/RagDebugScreen';
import { ApiServerScreen } from './screens/ApiServerScreen';
import { BackgroundTasksScreen } from './screens/BackgroundTasksScreen';
import { PluginPipelineScreen } from './screens/PluginPipelineScreen';
import { EncryptedVaultScreen } from './screens/EncryptedVaultScreen';
import { SettingsScreen } from './screens/SettingsScreen';

const MainLayout: React.FC = () => {
  const { currentDestination, setCurrentDestination, isInSettings, accentPalette } = useEdgeLLM();

  const [showNavMenu, setShowNavMenu] = useState<boolean>(false);
  const [showThemeStudio, setShowThemeStudio] = useState<boolean>(false);
  const [showSiliconGovernor, setShowSiliconGovernor] = useState<boolean>(false);
  const [showQuantCalc, setShowQuantCalc] = useState<boolean>(false);
  const [showVoiceStudio, setShowVoiceStudio] = useState<boolean>(false);
  const [vaultPrefilledContent, setVaultPrefilledContent] = useState<string>('');

  const navigateToVaultWithContent = (text: string) => {
    setVaultPrefilledContent(text);
    setCurrentDestination(AppDestination.VAULT);
  };

  const renderActiveScreen = () => {
    if (isInSettings) {
      return <SettingsScreen />;
    }

    switch (currentDestination) {
      case AppDestination.DASHBOARD:
        return (
          <DashboardScreen
            onNavigateToChat={() => setCurrentDestination(AppDestination.CHAT)}
            onNavigateToModels={() => setCurrentDestination(AppDestination.MODELS)}
            onNavigateToApi={() => setCurrentDestination(AppDestination.API)}
            onNavigateToImageStudio={() => setCurrentDestination(AppDestination.IMAGE_STUDIO)}
            onNavigateToRagDebug={() => setCurrentDestination(AppDestination.RAG_DEBUG)}
            onNavigateToArena={() => setCurrentDestination(AppDestination.BENCHMARK)}
          />
        );
      case AppDestination.CHAT:
        return <InferenceScreen onNavigateToExport={navigateToVaultWithContent} />;
      case AppDestination.MODELS:
        return (
          <DeviceAndModelsScreen
            onNavigateToChat={() => setCurrentDestination(AppDestination.CHAT)}
          />
        );
      case AppDestination.HF_EXPLORER:
        return (
          <HfExplorerScreen
            onNavigateToChat={() => setCurrentDestination(AppDestination.CHAT)}
          />
        );
      case AppDestination.BENCHMARK:
        return (
          <ModelArenaScreen
            onNavigateToChat={() => setCurrentDestination(AppDestination.CHAT)}
          />
        );
      case AppDestination.IMAGE_STUDIO:
        return <ImageTaskScreen />;
      case AppDestination.RAG_DEBUG:
        return <RagDebugScreen />;
      case AppDestination.API:
        return <ApiServerScreen />;
      case AppDestination.QUEUE:
        return <BackgroundTasksScreen onNavigateToExport={navigateToVaultWithContent} />;
      case AppDestination.PLUGINS:
        return <PluginPipelineScreen onNavigateToExport={navigateToVaultWithContent} />;
      case AppDestination.VAULT:
        return <EncryptedVaultScreen prefilledExportContent={vaultPrefilledContent} />;
      default:
        return (
          <DashboardScreen
            onNavigateToChat={() => setCurrentDestination(AppDestination.CHAT)}
            onNavigateToModels={() => setCurrentDestination(AppDestination.MODELS)}
            onNavigateToApi={() => setCurrentDestination(AppDestination.API)}
            onNavigateToImageStudio={() => setCurrentDestination(AppDestination.IMAGE_STUDIO)}
            onNavigateToRagDebug={() => setCurrentDestination(AppDestination.RAG_DEBUG)}
            onNavigateToArena={() => setCurrentDestination(AppDestination.BENCHMARK)}
          />
        );
    }
  };

  return (
    <div
      className="min-h-screen text-slate-100 flex flex-col font-sans selection:bg-cyan-500/30 selection:text-cyan-200"
      style={{ backgroundColor: accentPalette.bgDark }}
    >
      <TopAppBar
        onOpenNavMenu={() => setShowNavMenu(true)}
        onOpenThemeStudio={() => setShowThemeStudio(true)}
      />

      <main className="flex-1 overflow-x-hidden">{renderActiveScreen()}</main>

      <BottomNavBar />

      {/* Global Modals & Drawer Sheets */}
      <AppNavigationMenuSheet
        isOpen={showNavMenu}
        onClose={() => setShowNavMenu(false)}
        onOpenSiliconGovernor={() => setShowSiliconGovernor(true)}
        onOpenQuantCalc={() => setShowQuantCalc(true)}
        onOpenVoiceStudio={() => setShowVoiceStudio(true)}
        onOpenThemeStudio={() => setShowThemeStudio(true)}
      />

      <ThemeStudioSheet
        isOpen={showThemeStudio}
        onClose={() => setShowThemeStudio(false)}
      />

      <SiliconGovernorSheet
        isOpen={showSiliconGovernor}
        onClose={() => setShowSiliconGovernor(false)}
      />

      <QuantizationCalculatorSheet
        isOpen={showQuantCalc}
        onClose={() => setShowQuantCalc(false)}
      />

      <VoiceCloningStudioSheet
        isOpen={showVoiceStudio}
        onClose={() => setShowVoiceStudio(false)}
      />
    </div>
  );
};

export function App() {
  return (
    <EdgeLLMProvider>
      <MainLayout />
    </EdgeLLMProvider>
  );
}

export default App;
