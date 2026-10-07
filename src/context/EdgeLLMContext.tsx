import React, { createContext, useContext, useEffect, useState, useRef } from 'react';
import {
  AccentPaletteId,
  AiPersona,
  ApiRequestLog,
  ApiServerConfig,
  ApiServerStats,
  AppDestination,
  AppThemeMode,
  ArenaLeaderboardEntry,
  ArenaWinner,
  BackgroundJob,
  DeviceHardwareInfo,
  EncryptedExportRecord,
  GeneratedImageItem,
  GenerationParameters,
  GovernorMode,
  ImageTaskMode,
  InferenceMessage,
  JobStatus,
  JobType,
  KnowledgeDocument,
  McpServerSpec,
  MessageSender,
  ModelArenaMatch,
  ModelFormat,
  ModelSpec,
  PaletteDef,
  PluginSpec,
  PluginResult,
  RagChunkItem,
  SiliconGovernorStatus,
  StreamTokenChunk,
  SubscriptionTier,
  UserSubscriptionProfile,
  VoiceProfile,
} from '../types';
import {
  ACCENT_PALETTES,
  ARENA_CHALLENGES,
  BUILT_IN_PERSONAS,
  INITIAL_ARENA_LEADERBOARD,
  INITIAL_BACKGROUND_JOBS,
  INITIAL_DEVICE_HARDWARE,
  INITIAL_KNOWLEDGE_DOCS,
  INITIAL_MCP_SERVERS,
  INITIAL_MODELS,
  INITIAL_PLUGINS,
  INITIAL_RAG_CHUNKS,
  INITIAL_SUBSCRIPTION_PROFILE,
  INITIAL_VOICE_PROFILES,
} from '../data/constants';
import { OfflineKnowledgeEngine } from '../engine/offlineKnowledgeEngine';
import confetti from 'canvas-confetti';

interface EdgeLLMContextType {
  // Navigation
  currentDestination: AppDestination;
  setCurrentDestination: (dest: AppDestination) => void;
  isInSettings: boolean;
  setIsInSettings: (inSettings: boolean) => void;

  // Theming & Palette
  themeMode: AppThemeMode;
  setThemeMode: (mode: AppThemeMode) => void;
  accentPalette: PaletteDef;
  setAccentPalette: (paletteId: AccentPaletteId) => void;
  isAirGapped: boolean;
  toggleAirGappedMode: () => void;

  // Models
  models: ModelSpec[];
  activeModel: ModelSpec;
  setActiveModel: (id: string) => void;
  downloadModel: (id: string) => void;
  deleteModel: (id: string) => void;
  addCustomModel: (model: Partial<ModelSpec>) => void;

  // Chat & Inference
  chatMessages: InferenceMessage[];
  isGenerating: boolean;
  streamingChunk: StreamTokenChunk | null;
  sendPrompt: (text: string, imageUri?: string | null, imageLabel?: string | null) => void;
  regenerateResponse: (lastMsg: InferenceMessage, promptText: string) => void;
  deleteChatMessage: (id: string) => void;
  clearChat: () => void;
  cancelGeneration: () => void;
  rateMessageFeedback: (id: string, rating: number) => void;

  // Personas & Generation Params
  activePersona: AiPersona;
  selectPersona: (persona: AiPersona) => void;
  generationParams: GenerationParameters;
  updateGenerationParams: (params: Partial<GenerationParameters>) => void;

  // Context attachments
  activeKnowledgeDoc: KnowledgeDocument | null;
  attachKnowledgeDoc: (doc: KnowledgeDocument | null) => void;
  activeAttachedImage: { uri: string; label: string } | null;
  attachImage: (uri: string, label: string) => void;
  detachImage: () => void;

  // Voice & TTS
  isSpeaking: boolean;
  activeVoiceProfile: VoiceProfile;
  voiceProfiles: VoiceProfile[];
  autoVoiceReadout: boolean;
  toggleAutoVoiceReadout: () => void;
  selectVoiceProfile: (profile: VoiceProfile) => void;
  createClonedVoiceProfile: (name: string, pitch: number, rate: number, timbre: string) => void;
  deleteVoiceProfile: (id: string) => void;
  speakText: (text: string) => void;
  stopSpeaking: () => void;

  // Hardware & Silicon Governor
  hardwareInfo: DeviceHardwareInfo;
  governorStatus: SiliconGovernorStatus;
  setGovernorMode: (mode: GovernorMode) => void;
  setThreadCount: (count: number) => void;

  // Model Arena
  activeArenaMatch: ModelArenaMatch | null;
  arenaHistory: ModelArenaMatch[];
  arenaLeaderboard: ArenaLeaderboardEntry[];
  startArenaBattle: (prompt: string, isBlind: boolean, modelAId?: string, modelBId?: string, category?: string) => void;
  voteArenaMatch: (winner: ArenaWinner) => void;

  // Image AI
  imageItems: GeneratedImageItem[];
  isGeneratingImage: boolean;
  generateImage: (params: {
    prompt: string;
    negativePrompt?: string;
    mode: ImageTaskMode;
    modelName: string;
    steps: number;
    cfgScale: number;
    seed: number;
    maskData?: string | null;
  }) => Promise<GeneratedImageItem>;
  upscaleImage: (id: string) => void;
  deleteImageItem: (id: string) => void;

  // RAG Debugger
  knowledgeDocs: KnowledgeDocument[];
  ragChunks: RagChunkItem[];
  similarityThreshold: number;
  setSimilarityThreshold: (val: number) => void;
  queryRag: (query: string) => RagChunkItem[];
  ingestDoc: (title: string, content: string) => void;
  deleteDoc: (id: string) => void;

  // API Server Daemon
  apiConfig: ApiServerConfig;
  apiStats: ApiServerStats;
  apiLogs: ApiRequestLog[];
  toggleApiServer: () => void;
  rotateApiToken: () => void;
  updateApiPort: (port: number) => void;
  testApiCall: (prompt: string) => Promise<string>;

  // Plugins & MCP
  plugins: PluginSpec[];
  togglePlugin: (id: string) => void;
  executePlugin: (pluginId: string, input: string) => Promise<PluginResult>;
  mcpServers: McpServerSpec[];
  toggleMcpServer: (id: string) => void;

  // Background Tasks Queue
  backgroundJobs: BackgroundJob[];
  addBackgroundJob: (title: string, type: JobType, modelId: string, priority?: 'LOW' | 'NORMAL' | 'HIGH') => void;
  cancelJob: (id: string) => void;
  clearCompletedJobs: () => void;

  // Encrypted Vault
  vaultRecords: EncryptedExportRecord[];
  createEncryptedVaultExport: (title: string, plaintext: string, passphrase: string, category: 'CHAT_SESSION' | 'VECTOR_MEMORY' | 'SYSTEM_CONFIG') => Promise<void>;
  decryptVaultRecord: (record: EncryptedExportRecord, passphrase: string) => Promise<string>;
  deleteVaultRecord: (id: string) => void;

  // Billing / Allocations
  userProfile: UserSubscriptionProfile;
  upgradeSubscription: (tier: SubscriptionTier) => void;
  resetAllocations: () => void;
}

const EdgeLLMContext = createContext<EdgeLLMContextType | undefined>(undefined);

export const EdgeLLMProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // Navigation
  const [currentDestination, setCurrentDestination] = useState<AppDestination>(AppDestination.DASHBOARD);
  const [isInSettings, setIsInSettings] = useState<boolean>(false);

  // Theming & Palette
  const [themeMode, setThemeModeState] = useState<AppThemeMode>(() => {
    const saved = localStorage.getItem('edgellm_theme_mode');
    return (saved as AppThemeMode) || AppThemeMode.DARK;
  });
  const [accentPaletteId, setAccentPaletteId] = useState<AccentPaletteId>(() => {
    const saved = localStorage.getItem('edgellm_palette');
    return (saved as AccentPaletteId) || AccentPaletteId.CYBER_CYAN;
  });
  const accentPalette = ACCENT_PALETTES[accentPaletteId] || ACCENT_PALETTES[AccentPaletteId.CYBER_CYAN];

  const [isAirGapped, setIsAirGapped] = useState<boolean>(() => {
    return localStorage.getItem('edgellm_airgapped') === 'true';
  });

  const toggleAirGappedMode = () => {
    setIsAirGapped((prev) => {
      const next = !prev;
      localStorage.setItem('edgellm_airgapped', String(next));
      return next;
    });
  };

  const setThemeMode = (mode: AppThemeMode) => {
    setThemeModeState(mode);
    localStorage.setItem('edgellm_theme_mode', mode);
  };

  const setAccentPalette = (paletteId: AccentPaletteId) => {
    setAccentPaletteId(paletteId);
    localStorage.setItem('edgellm_palette', paletteId);
  };

  // Models
  const [models, setModels] = useState<ModelSpec[]>(() => {
    const saved = localStorage.getItem('edgellm_models');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch {
        // fallback
      }
    }
    return INITIAL_MODELS;
  });

  const activeModel = models.find((m) => m.isActive) || models[0];

  const setActiveModel = (id: string) => {
    setModels((prev) => {
      const updated = prev.map((m) => ({ ...m, isActive: m.id === id }));
      localStorage.setItem('edgellm_models', JSON.stringify(updated));
      return updated;
    });
  };

  const downloadModel = (id: string) => {
    setModels((prev) =>
      prev.map((m) => (m.id === id ? { ...m, isDownloading: true, downloadProgressPercent: 10 } : m))
    );
    // Simulate progressive download
    let prog = 10;
    const interval = setInterval(() => {
      prog += 20;
      if (prog >= 100) {
        clearInterval(interval);
        setModels((prev) => {
          const updated = prev.map((m) =>
            m.id === id
              ? {
                  ...m,
                  isDownloaded: true,
                  isDownloading: false,
                  downloadProgressPercent: 100,
                  downloadStatusText: 'Ready (Cached)',
                }
              : m
          );
          localStorage.setItem('edgellm_models', JSON.stringify(updated));
          return updated;
        });
      } else {
        setModels((prev) =>
          prev.map((m) => (m.id === id ? { ...m, downloadProgressPercent: prog } : m))
        );
      }
    }, 250);
  };

  const deleteModel = (id: string) => {
    setModels((prev) => {
      const updated = prev.map((m) =>
        m.id === id ? { ...m, isDownloaded: false, downloadProgressPercent: 0, isActive: false } : m
      );
      if (activeModel.id === id) {
        const fallback = updated.find((m) => m.isDownloaded);
        if (fallback) fallback.isActive = true;
      }
      localStorage.setItem('edgellm_models', JSON.stringify(updated));
      return updated;
    });
  };

  const addCustomModel = (partial: Partial<ModelSpec>) => {
    const newModel: ModelSpec = {
      id: `custom_${Date.now()}`,
      name: partial.name || 'Custom Model',
      parameterCount: partial.parameterCount || '1.0B',
      format: partial.format || ModelFormat.GGUF,
      quantization: partial.quantization || 'Q4_K_M',
      fileSizeBytes: partial.fileSizeBytes || 550000000,
      requiredRamBytes: partial.requiredRamBytes || 800000000,
      contextLength: 4096,
      description: partial.description || 'Custom imported model.',
      category: partial.category || ('Chat & Reasoning' as any),
      downloadUrl: partial.downloadUrl || 'local_filesystem',
      sha256Checksum: 'abcdef1234567890abcdef1234567890',
      isDownloaded: true,
      downloadProgressPercent: 100,
      isDownloading: false,
      isPaused: false,
      downloadSpeedFormatted: '',
      downloadedBytes: 550000000,
      downloadStatusText: 'Imported',
      isActive: true,
      isImported: true,
      supportsVision: false,
      supportsCoT: true,
      supportsGrammar: true,
      supportsToolCalling: true,
      benchmarkMmluScore: 50.0,
      benchmarkGsm8kScore: 45.0,
      benchmarkHumanEvalScore: 40.0,
      maxTurboTokPerSec: 65.0,
      ...partial,
    };
    setModels((prev) => {
      const updated = prev.map((m) => ({ ...m, isActive: false })).concat(newModel);
      localStorage.setItem('edgellm_models', JSON.stringify(updated));
      return updated;
    });
  };

  // Personas & Generation Params
  const [activePersona, setActivePersonaState] = useState<AiPersona>(() => BUILT_IN_PERSONAS[0]);
  const [generationParams, setGenerationParams] = useState<GenerationParameters>({
    temperature: 0.0,
    topP: 0.85,
    topK: 1,
    maxNewTokens: 512,
    systemPrompt: BUILT_IN_PERSONAS[0].systemPrompt,
    enableThinking: false,
    thinkingTokenBudget: 256,
    grammarConstraint: 'NONE',
  });

  const selectPersona = (persona: AiPersona) => {
    setActivePersonaState(persona);
    setGenerationParams((prev) => ({
      ...prev,
      systemPrompt: persona.systemPrompt,
      temperature: persona.defaultTemperature,
      topP: persona.defaultTopP,
      enableThinking: persona.supportsReasoningTrace,
    }));
  };

  const updateGenerationParams = (params: Partial<GenerationParameters>) => {
    setGenerationParams((prev) => ({ ...prev, ...params }));
  };

  // Context attachments
  const [activeKnowledgeDoc, setActiveKnowledgeDoc] = useState<KnowledgeDocument | null>(null);
  const [activeAttachedImage, setActiveAttachedImage] = useState<{ uri: string; label: string } | null>(null);

  const attachKnowledgeDoc = (doc: KnowledgeDocument | null) => setActiveKnowledgeDoc(doc);
  const attachImage = (uri: string, label: string) => setActiveAttachedImage({ uri, label });
  const detachImage = () => setActiveAttachedImage(null);

  // Chat & Messages
  const [chatMessages, setChatMessages] = useState<InferenceMessage[]>(() => {
    const saved = localStorage.getItem('edgellm_chat_messages');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch {}
    }
    return [
      {
        id: 'msg_initial',
        sender: MessageSender.ASSISTANT,
        text: 'EdgeLLM Studio initialized. All models run 100% locally on-device with zero cloud dependencies and air-gapped security. Ask any factual question, technical problem, or hardware query for immediate, decisive answers.',
        timestamp: Date.now() - 60000,
        tokensGenerated: 34,
        tokensPerSecond: 88.5,
        timeToFirstTokenMs: 42,
        executionBackend: 'ARM NEON SIMD • INT4 LiteRT',
        modelId: 'Gemma 2 2B IT (LiteRT)',
      },
    ];
  });

  const [isGenerating, setIsGenerating] = useState<boolean>(false);
  const [streamingChunk, setStreamingChunk] = useState<StreamTokenChunk | null>(null);
  const streamAbortRef = useRef<boolean>(false);

  const sendPrompt = (text: string, imageUri?: string | null, imageLabel?: string | null) => {
    if (!text.trim() || isGenerating) return;

    const userMsg: InferenceMessage = {
      id: `user_${Date.now()}`,
      sender: MessageSender.USER,
      text: text.trim(),
      timestamp: Date.now(),
      imageUri: imageUri || activeAttachedImage?.uri || null,
      imageLabel: imageLabel || activeAttachedImage?.label || null,
    };

    setChatMessages((prev) => {
      const updated = [...prev, userMsg];
      localStorage.setItem('edgellm_chat_messages', JSON.stringify(updated));
      return updated;
    });

    detachImage();
    executeInferenceStream(text.trim());
  };

  const regenerateResponse = (lastMsg: InferenceMessage, promptText: string) => {
    if (isGenerating) return;
    setChatMessages((prev) => prev.filter((m) => m.id !== lastMsg.id));
    executeInferenceStream(promptText);
  };

  const deleteChatMessage = (id: string) => {
    setChatMessages((prev) => {
      const updated = prev.filter((m) => m.id !== id);
      localStorage.setItem('edgellm_chat_messages', JSON.stringify(updated));
      return updated;
    });
  };

  const clearChat = () => {
    setChatMessages([]);
    localStorage.removeItem('edgellm_chat_messages');
  };

  const cancelGeneration = () => {
    streamAbortRef.current = true;
    setIsGenerating(false);
    setStreamingChunk(null);
  };

  const rateMessageFeedback = (id: string, rating: number) => {
    setChatMessages((prev) =>
      prev.map((m) => (m.id === id ? { ...m, userRating: rating } : m))
    );
  };

  const executeInferenceStream = (userText: string) => {
    setIsGenerating(true);
    streamAbortRef.current = false;
    const startTime = Date.now();

    const result = OfflineKnowledgeEngine.answerQuery(
      userText,
      activeModel,
      activePersona,
      generationParams.enableThinking
    );

    const fullAnswer = result.text;
    const thoughtTrace = result.thoughtTrace;

    // Simulate streaming by word chunks
    const words = fullAnswer.split(' ');
    let currentIdx = 0;
    let accumulated = '';
    const speed = activeModel.maxTurboTokPerSec || 80;
    const delayPerWord = Math.max(15, Math.floor(1000 / (speed / 1.3)));
    const ttftMs = Math.floor(Math.random() * 30 + 35);

    const timer = setInterval(() => {
      if (streamAbortRef.current) {
        clearInterval(timer);
        return;
      }

      if (currentIdx < words.length) {
        accumulated += (currentIdx > 0 ? ' ' : '') + words[currentIdx];
        currentIdx++;
        setStreamingChunk({
          accumulatedText: accumulated,
          tokenCount: Math.round(accumulated.length / 4),
          tokensPerSecond: Math.round(speed + (Math.random() * 6 - 3)),
          timeToFirstTokenMs: ttftMs,
          backendUsed: `${activeModel.format} • ${activeModel.quantization}`,
          isComplete: false,
          trustScore: 0.99,
          factualAccuracyScore: 0.98,
        });
      } else {
        clearInterval(timer);
        setIsGenerating(false);
        const finalTokens = Math.round(accumulated.length / 4);
        const finalDurationSec = (Date.now() - startTime) / 1000;
        const finalTokSec = Math.round(finalTokens / Math.max(0.1, finalDurationSec));

        const assistantMsg: InferenceMessage = {
          id: `asst_${Date.now()}`,
          sender: MessageSender.ASSISTANT,
          text: accumulated,
          timestamp: Date.now(),
          tokensGenerated: finalTokens,
          tokensPerSecond: finalTokSec || speed,
          timeToFirstTokenMs: ttftMs,
          executionBackend: `${activeModel.format} • ${activeModel.quantization}`,
          modelId: activeModel.name,
          thoughtTrace: thoughtTrace,
          grammarModeUsed: generationParams.grammarConstraint,
        };

        setChatMessages((prev) => {
          const updated = [...prev, assistantMsg];
          localStorage.setItem('edgellm_chat_messages', JSON.stringify(updated));
          return updated;
        });
        setStreamingChunk(null);

        // Record usage
        setUserProfile((prev) => ({
          ...prev,
          tokensUsedThisMonth: prev.tokensUsedThisMonth + finalTokens,
        }));

        // Auto TTS if enabled
        if (autoVoiceReadout) {
          speakText(accumulated);
        }
      }
    }, delayPerWord);
  };

  // Voice & TTS
  const [isSpeaking, setIsSpeaking] = useState<boolean>(false);
  const [autoVoiceReadout, setAutoVoiceReadout] = useState<boolean>(false);
  const [voiceProfiles, setVoiceProfiles] = useState<VoiceProfile[]>(() => {
    const saved = localStorage.getItem('edgellm_voice_profiles');
    return saved ? JSON.parse(saved) : INITIAL_VOICE_PROFILES;
  });
  const [activeVoiceProfile, setActiveVoiceProfile] = useState<VoiceProfile>(voiceProfiles[0]);

  const toggleAutoVoiceReadout = () => setAutoVoiceReadout((p) => !p);
  const selectVoiceProfile = (profile: VoiceProfile) => setActiveVoiceProfile(profile);

  const createClonedVoiceProfile = (name: string, pitch: number, rate: number, timbre: string) => {
    const newProfile: VoiceProfile = {
      id: `voice_${Date.now()}`,
      name,
      pitch,
      rate,
      timbre,
    };
    setVoiceProfiles((prev) => {
      const updated = [...prev, newProfile];
      localStorage.setItem('edgellm_voice_profiles', JSON.stringify(updated));
      return updated;
    });
    setActiveVoiceProfile(newProfile);
  };

  const deleteVoiceProfile = (id: string) => {
    setVoiceProfiles((prev) => {
      const updated = prev.filter((p) => p.id !== id);
      localStorage.setItem('edgellm_voice_profiles', JSON.stringify(updated));
      return updated;
    });
  };

  const speakText = (text: string) => {
    if (!('speechSynthesis' in window)) return;
    window.speechSynthesis.cancel();
    // Clean markdown and think tags
    const clean = text
      .replace(/<think>[\s\S]*?<\/think>/g, '')
      .replace(/[#*`_~]/g, '')
      .trim();
    if (!clean) return;

    const utterance = new SpeechSynthesisUtterance(clean);
    utterance.pitch = activeVoiceProfile.pitch;
    utterance.rate = activeVoiceProfile.rate;
    utterance.onstart = () => setIsSpeaking(true);
    utterance.onend = () => setIsSpeaking(false);
    utterance.onerror = () => setIsSpeaking(false);
    window.speechSynthesis.speak(utterance);
  };

  const stopSpeaking = () => {
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
    setIsSpeaking(false);
  };

  // Hardware & Governor
  const [hardwareInfo] = useState<DeviceHardwareInfo>(INITIAL_DEVICE_HARDWARE);
  const [governorStatus, setGovernorStatus] = useState<SiliconGovernorStatus>({
    mode: GovernorMode.MAX_PERFORMANCE,
    activeThreads: 8,
    gpuFrequencyCapMhz: 900,
    thermalThrottled: false,
    kvCompactionEnabled: true,
    powerConsumptionWatts: 4.8,
  });

  const setGovernorMode = (mode: GovernorMode) => {
    setGovernorStatus((prev) => ({
      ...prev,
      mode,
      activeThreads: mode === GovernorMode.ECO_BATTERY_SAVER ? 4 : mode === GovernorMode.DYNAMIC_BALANCED ? 6 : 8,
      powerConsumptionWatts: mode === GovernorMode.ECO_BATTERY_SAVER ? 2.1 : mode === GovernorMode.DYNAMIC_BALANCED ? 3.4 : 4.8,
    }));
  };

  const setThreadCount = (threads: number) => {
    setGovernorStatus((prev) => ({ ...prev, activeThreads: threads }));
  };

  // Model Arena
  const [activeArenaMatch, setActiveArenaMatch] = useState<ModelArenaMatch | null>(null);
  const [arenaHistory, setArenaHistory] = useState<ModelArenaMatch[]>([]);
  const [arenaLeaderboard, setArenaLeaderboard] = useState<ArenaLeaderboardEntry[]>(INITIAL_ARENA_LEADERBOARD);

  const startArenaBattle = (
    prompt: string,
    isBlind: boolean,
    modelAId?: string,
    modelBId?: string,
    category: string = 'Reasoning & Logic'
  ) => {
    const modelA = models.find((m) => m.id === modelAId) || models[0];
    const modelB = models.find((m) => m.id === modelBId) || models[1] || models[0];

    const match: ModelArenaMatch = {
      id: `match_${Date.now()}`,
      prompt,
      category,
      modelAId: modelA.id,
      modelAName: modelA.name,
      modelAFormat: modelA.format,
      modelAParameters: modelA.parameterCount,
      modelAQuant: modelA.quantization,
      modelAComputeBackend: 'Vulkan 1.3 GPU',
      responseA: '',
      tokPerSecA: modelA.maxTurboTokPerSec,
      ttftMsA: 44,
      modelBId: modelB.id,
      modelBName: modelB.name,
      modelBFormat: modelB.format,
      modelBParameters: modelB.parameterCount,
      modelBQuant: modelB.quantization,
      modelBComputeBackend: 'Hexagon NPU',
      responseB: '',
      tokPerSecB: modelB.maxTurboTokPerSec,
      ttftMsB: 36,
      isBlindMode: isBlind,
      isBattling: true,
      isFinished: false,
      timestamp: Date.now(),
    };

    setActiveArenaMatch(match);

    // Generate grounded responses
    const respA = OfflineKnowledgeEngine.answerQuery(prompt, modelA).text;
    const respB = OfflineKnowledgeEngine.answerQuery(prompt, modelB).text;

    // Stream responses
    let step = 0;
    const interval = setInterval(() => {
      step++;
      const fraction = Math.min(1, step / 10);
      const subA = respA.slice(0, Math.floor(respA.length * fraction));
      const subB = respB.slice(0, Math.floor(respB.length * fraction));

      setActiveArenaMatch((prev) =>
        prev ? { ...prev, responseA: subA, responseB: subB } : null
      );

      if (fraction >= 1) {
        clearInterval(interval);
        setActiveArenaMatch((prev) =>
          prev ? { ...prev, responseA: respA, responseB: respB, isBattling: false } : null
        );
      }
    }, 120);
  };

  const voteArenaMatch = (winner: ArenaWinner) => {
    if (!activeArenaMatch) return;
    const deltaA = winner === ArenaWinner.MODEL_A ? 18 : winner === ArenaWinner.MODEL_B ? -14 : 0;
    const deltaB = winner === ArenaWinner.MODEL_B ? 18 : winner === ArenaWinner.MODEL_A ? -14 : 0;

    // Victory confetti celebration!
    try {
      confetti({
        particleCount: 80,
        spread: 70,
        origin: { y: 0.6 },
        colors: ['#00F0FF', '#B066FF', '#00FFA3', '#FF9F1C'],
      });
    } catch {}

    const finished: ModelArenaMatch = {
      ...activeArenaMatch,
      isFinished: true,
      userVote: winner,
      eloDeltaA: deltaA,
      eloDeltaB: deltaB,
    };

    setActiveArenaMatch(finished);
    setArenaHistory((prev) => [finished, ...prev.slice(0, 15)]);

    // Update leaderboard
    setArenaLeaderboard((prev) =>
      prev
        .map((entry) => {
          if (entry.id === finished.modelAId) {
            return {
              ...entry,
              eloRating: entry.eloRating + deltaA,
              wins: entry.wins + (winner === ArenaWinner.MODEL_A ? 1 : 0),
              losses: entry.losses + (winner === ArenaWinner.MODEL_B ? 1 : 0),
              ties: entry.ties + (winner === ArenaWinner.TIE ? 1 : 0),
            };
          }
          if (entry.id === finished.modelBId) {
            return {
              ...entry,
              eloRating: entry.eloRating + deltaB,
              wins: entry.wins + (winner === ArenaWinner.MODEL_B ? 1 : 0),
              losses: entry.losses + (winner === ArenaWinner.MODEL_A ? 1 : 0),
              ties: entry.ties + (winner === ArenaWinner.TIE ? 1 : 0),
            };
          }
          return entry;
        })
        .sort((a, b) => b.eloRating - a.eloRating)
    );
  };

  // Image AI
  const [imageItems, setImageItems] = useState<GeneratedImageItem[]>(() => {
    const saved = localStorage.getItem('edgellm_images');
    return saved
      ? JSON.parse(saved)
      : [
          {
            id: 'img_initial',
            prompt: 'Cybernetic neural processor chip with glowing neon cyan bus pathways, photorealistic 8k',
            negativePrompt: 'low quality, blurry, deformed',
            mode: ImageTaskMode.TEXT_TO_IMAGE,
            modelName: 'Stable Diffusion 1.5 (Q4)',
            steps: 20,
            cfgScale: 7.5,
            seed: 428190,
            timestamp: Date.now() - 3600000,
            generationTimeMs: 2450,
            imageUrl:
              'https://images.unsplash.com/photo-1620712943543-bcc4688e7485?auto=format&fit=crop&w=800&q=80',
            width: 512,
            height: 512,
          },
        ];
  });
  const [isGeneratingImage, setIsGeneratingImage] = useState<boolean>(false);

  const generateImage = async (params: {
    prompt: string;
    negativePrompt?: string;
    mode: ImageTaskMode;
    modelName: string;
    steps: number;
    cfgScale: number;
    seed: number;
    maskData?: string | null;
  }): Promise<GeneratedImageItem> => {
    setIsGeneratingImage(true);
    await new Promise((r) => setTimeout(r, 1800));

    // Diverse cyberpunk/hardware thematic placeholder imagery based on prompt
    const sampleUrls = [
      'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80',
      'https://images.unsplash.com/photo-1634017839464-5c339ebe3cb4?auto=format&fit=crop&w=800&q=80',
      'https://images.unsplash.com/photo-1614741118887-7a4ee193a5fa?auto=format&fit=crop&w=800&q=80',
      'https://images.unsplash.com/photo-1550751827-4bd374c3f58b?auto=format&fit=crop&w=800&q=80',
    ];
    const pickedUrl = sampleUrls[Math.floor(Math.random() * sampleUrls.length)];

    const newItem: GeneratedImageItem = {
      id: `img_${Date.now()}`,
      prompt: params.prompt,
      negativePrompt: params.negativePrompt || '',
      mode: params.mode,
      modelName: params.modelName,
      steps: params.steps,
      cfgScale: params.cfgScale,
      seed: params.seed || Math.floor(Math.random() * 9999999),
      timestamp: Date.now(),
      generationTimeMs: 1800 + Math.floor(Math.random() * 400),
      imageUrl: pickedUrl,
      width: 512,
      height: 512,
    };

    setImageItems((prev) => {
      const updated = [newItem, ...prev];
      localStorage.setItem('edgellm_images', JSON.stringify(updated));
      return updated;
    });
    setIsGeneratingImage(false);
    return newItem;
  };

  const upscaleImage = (id: string) => {
    setImageItems((prev) =>
      prev.map((item) =>
        item.id === id
          ? { ...item, isUpscaled: true, width: item.width * 2, height: item.height * 2 }
          : item
      )
    );
  };

  const deleteImageItem = (id: string) => {
    setImageItems((prev) => {
      const updated = prev.filter((item) => item.id !== id);
      localStorage.setItem('edgellm_images', JSON.stringify(updated));
      return updated;
    });
  };

  // RAG Debugger
  const [knowledgeDocs, setKnowledgeDocs] = useState<KnowledgeDocument[]>(() => {
    const saved = localStorage.getItem('edgellm_docs');
    return saved ? JSON.parse(saved) : INITIAL_KNOWLEDGE_DOCS;
  });
  const [ragChunks, setRagChunks] = useState<RagChunkItem[]>(INITIAL_RAG_CHUNKS);
  const [similarityThreshold, setSimilarityThreshold] = useState<number>(0.65);

  const queryRag = (query: string): RagChunkItem[] => {
    const qLower = query.toLowerCase();
    return ragChunks
      .map((chunk) => {
        // Calculate mock cosine similarity score based on term overlap
        const words = qLower.split(' ').filter(Boolean);
        const matches = words.filter((w) => chunk.content.toLowerCase().includes(w));
        const baseScore = matches.length / Math.max(1, words.length);
        const score = Math.min(0.99, Math.max(0.2, baseScore * 0.8 + 0.15));
        return { ...chunk, cosineSimilarityScore: parseFloat(score.toFixed(2)) };
      })
      .filter((c) => c.cosineSimilarityScore >= similarityThreshold)
      .sort((a, b) => b.cosineSimilarityScore - a.cosineSimilarityScore);
  };

  const ingestDoc = (title: string, content: string) => {
    const words = content.split(/\s+/).filter(Boolean);
    const newDoc: KnowledgeDocument = {
      id: `doc_${Date.now()}`,
      title: title.trim() || 'Ingested_Notes.txt',
      summary: `User document (${words.length} words)`,
      content,
      sizeBytes: content.length,
      tokenCountEstimate: Math.round(words.length * 1.3),
      isPreloaded: false,
      chunksCount: Math.max(1, Math.ceil(words.length / 50)),
    };
    setKnowledgeDocs((prev) => {
      const updated = [newDoc, ...prev];
      localStorage.setItem('edgellm_docs', JSON.stringify(updated));
      return updated;
    });

    // Add chunks
    const newChunk: RagChunkItem = {
      id: `chunk_${Date.now()}`,
      documentTitle: newDoc.title,
      chunkIndex: 1,
      totalChunks: 1,
      sha256ContentHash: '9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c3b2a1f0e9d8c7b6a5f4e3d2c1b0a9f8e',
      content: content.slice(0, 300),
      tokenCount: Math.min(60, words.length),
      embeddingDimension: 128,
      cosineSimilarityScore: 0.85,
      retrievalLatencyMs: 6,
      sourceFormat: 'TXT',
    };
    setRagChunks((prev) => [newChunk, ...prev]);
  };

  const deleteDoc = (id: string) => {
    setKnowledgeDocs((prev) => {
      const updated = prev.filter((d) => d.id !== id);
      localStorage.setItem('edgellm_docs', JSON.stringify(updated));
      return updated;
    });
  };

  // API Server Daemon
  const [apiConfig, setApiConfig] = useState<ApiServerConfig>({
    port: 8080,
    bindToLan: false,
    allowCors: true,
    enableOpenAiCompat: true,
    authToken: 'sk-edgellm-local-tensor-token-98f2',
  });

  const [apiStats, setApiStats] = useState<ApiServerStats>({
    isRunning: true,
    port: 8080,
    localIp: '127.0.0.1',
    lanIp: '192.168.1.142',
    activeConnections: 0,
    totalRequestsServed: 24,
    totalTokensGenerated: 1420,
    averageLatencyMs: 46,
    uptimeSeconds: 3820,
  });

  const [apiLogs, setApiLogs] = useState<ApiRequestLog[]>([
    {
      id: 'log_1',
      timestamp: Date.now() - 320000,
      method: 'POST',
      path: '/v1/chat/completions',
      clientIp: '127.0.0.1',
      statusCode: 200,
      durationMs: 54,
      tokensGenerated: 42,
      model: 'gemma_2b_it',
      promptPreview: 'What is an object in Java?',
    },
    {
      id: 'log_2',
      timestamp: Date.now() - 120000,
      method: 'GET',
      path: '/v1/models',
      clientIp: '127.0.0.1',
      statusCode: 200,
      durationMs: 3,
      tokensGenerated: 0,
      model: 'system',
      promptPreview: 'Catalog inspect',
    },
  ]);

  const toggleApiServer = () => {
    setApiStats((prev) => ({
      ...prev,
      isRunning: !prev.isRunning,
      activeConnections: !prev.isRunning ? 0 : 0,
    }));
  };

  const rotateApiToken = () => {
    const rand = Math.random().toString(36).substring(2, 10);
    setApiConfig((prev) => ({ ...prev, authToken: `sk-edgellm-${rand}` }));
  };

  const updateApiPort = (port: number) => {
    setApiConfig((prev) => ({ ...prev, port }));
    setApiStats((prev) => ({ ...prev, port }));
  };

  const testApiCall = async (prompt: string): Promise<string> => {
    const startTime = Date.now();
    await new Promise((r) => setTimeout(r, 120));
    const result = OfflineKnowledgeEngine.answerQuery(prompt, activeModel).text;
    const duration = Date.now() - startTime;
    const tokens = Math.round(result.length / 4);

    const newLog: ApiRequestLog = {
      id: `log_${Date.now()}`,
      timestamp: Date.now(),
      method: 'POST',
      path: '/v1/chat/completions',
      clientIp: '127.0.0.1',
      statusCode: 200,
      durationMs: duration,
      tokensGenerated: tokens,
      model: activeModel.id,
      promptPreview: prompt.slice(0, 40),
    };

    setApiLogs((prev) => [newLog, ...prev.slice(0, 30)]);
    setApiStats((prev) => ({
      ...prev,
      totalRequestsServed: prev.totalRequestsServed + 1,
      totalTokensGenerated: prev.totalTokensGenerated + tokens,
    }));

    return result;
  };

  // Plugins & MCP
  const [plugins, setPlugins] = useState<PluginSpec[]>(INITIAL_PLUGINS);
  const [mcpServers, setMcpServers] = useState<McpServerSpec[]>(INITIAL_MCP_SERVERS);

  const togglePlugin = (id: string) => {
    setPlugins((prev) =>
      prev.map((p) => (p.id === id ? { ...p, isEnabled: !p.isEnabled } : p))
    );
  };

  const toggleMcpServer = (id: string) => {
    setMcpServers((prev) =>
      prev.map((s) => (s.id === id ? { ...s, isConnected: !s.isConnected } : s))
    );
  };

  const executePlugin = async (pluginId: string, input: string): Promise<PluginResult> => {
    const start = Date.now();
    await new Promise((r) => setTimeout(r, 200));

    if (pluginId === 'plugin_pii_redactor') {
      let redacted = input;
      redacted = redacted.replace(/[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+/g, '[REDACTED_EMAIL]');
      redacted = redacted.replace(/\b\d{3}[-.]?\d{3}[-.]?\d{4}\b/g, '[REDACTED_PHONE]');
      redacted = redacted.replace(/\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b/g, '[REDACTED_IP]');

      return {
        pluginId,
        success: true,
        processedOutput: redacted,
        metrics: {
          'Sanitization Mode': '100% On-Device',
          'PII Stripped': 'Emails, Phones, IPs',
          'Confidence': '99.9%',
        },
        durationMs: Date.now() - start,
      };
    }

    if (pluginId === 'plugin_log_anomaly') {
      const lines = input.split('\n');
      const errs = lines.filter((l) => /error|fail|fatal|exception/i.test(l));
      return {
        pluginId,
        success: true,
        processedOutput: `=== LOG AUDIT REPORT ===\nTotal Lines: ${lines.length}\nCritical Issues (${errs.length}):\n${errs.map((e) => `• ${e.slice(0, 80)}`).join('\n') || 'None detected.'}\nStatus: ${errs.length > 2 ? 'HIGH ALERT' : 'HEALTHY'}`,
        metrics: {
          'Analyzed Lines': String(lines.length),
          'Detected Anomalies': String(errs.length),
        },
        durationMs: Date.now() - start,
      };
    }

    return {
      pluginId,
      success: true,
      processedOutput: `Processed locally by plugin ${pluginId}:\n\n${input}`,
      metrics: { Status: 'Completed', Backend: 'Local Engine' },
      durationMs: Date.now() - start,
    };
  };

  // Background Tasks Queue
  const [backgroundJobs, setBackgroundJobs] = useState<BackgroundJob[]>(INITIAL_BACKGROUND_JOBS);

  const addBackgroundJob = (
    title: string,
    type: JobType,
    modelId: string,
    priority: 'LOW' | 'NORMAL' | 'HIGH' = 'NORMAL'
  ) => {
    const newJob: BackgroundJob = {
      id: `job_${Date.now()}`,
      title,
      type,
      status: JobStatus.RUNNING,
      progressPercent: 10,
      priority,
      modelId,
      createdAt: Date.now(),
    };
    setBackgroundJobs((prev) => [newJob, ...prev]);

    // Progress simulation
    let p = 10;
    const interval = setInterval(() => {
      p += 30;
      if (p >= 100) {
        clearInterval(interval);
        setBackgroundJobs((prev) =>
          prev.map((j) =>
            j.id === newJob.id
              ? {
                  ...j,
                  status: JobStatus.COMPLETED,
                  progressPercent: 100,
                  completedAt: Date.now(),
                  resultOutput: 'Successfully completed on-device background computation with zero data egress.',
                }
              : j
          )
        );
      } else {
        setBackgroundJobs((prev) =>
          prev.map((j) => (j.id === newJob.id ? { ...j, progressPercent: p } : j))
        );
      }
    }, 400);
  };

  const cancelJob = (id: string) => {
    setBackgroundJobs((prev) =>
      prev.map((j) => (j.id === id ? { ...j, status: JobStatus.FAILED, error: 'Cancelled by user.' } : j))
    );
  };

  const clearCompletedJobs = () => {
    setBackgroundJobs((prev) => prev.filter((j) => j.status === JobStatus.RUNNING));
  };

  // Encrypted Vault (AES-GCM Web Crypto)
  const [vaultRecords, setVaultRecords] = useState<EncryptedExportRecord[]>(() => {
    const saved = localStorage.getItem('edgellm_vault');
    return saved
      ? JSON.parse(saved)
      : [
          {
            id: 'vault_seed_1',
            title: 'Initial Confidential System Parameters',
            timestamp: Date.now() - 86400000,
            algorithm: 'AES-256-GCM / PBKDF2',
            cipherTextBase64: 'U2FsdGVkX1+vM13k8G34j6H3l9Xy5z8v2a4b8c9d0e1f',
            ivHex: '4a5b6c7d8e9f0a1b2c3d4e5f',
            saltHex: '1a2b3c4d5e6f7a8b',
            itemCount: 4,
            category: 'SYSTEM_CONFIG',
          },
        ];
  });

  const createEncryptedVaultExport = async (
    title: string,
    plaintext: string,
    passphrase: string,
    category: 'CHAT_SESSION' | 'VECTOR_MEMORY' | 'SYSTEM_CONFIG'
  ): Promise<void> => {
    const enc = new TextEncoder();
    const salt = window.crypto.getRandomValues(new Uint8Array(16));
    const iv = window.crypto.getRandomValues(new Uint8Array(12));

    const keyMaterial = await window.crypto.subtle.importKey(
      'raw',
      enc.encode(passphrase),
      'PBKDF2',
      false,
      ['deriveKey']
    );

    const key = await window.crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        salt,
        iterations: 100000,
        hash: 'SHA-256',
      },
      keyMaterial,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt']
    );

    const ciphertextBuffer = await window.crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      key,
      enc.encode(plaintext)
    );

    const cipherBytes = new Uint8Array(ciphertextBuffer);
    let binary = '';
    for (let i = 0; i < cipherBytes.byteLength; i++) {
      binary += String.fromCharCode(cipherBytes[i]);
    }
    const cipherTextBase64 = btoa(binary);

    const ivHex = Array.from(iv)
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
    const saltHex = Array.from(salt)
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');

    const record: EncryptedExportRecord = {
      id: `vault_${Date.now()}`,
      title,
      timestamp: Date.now(),
      algorithm: 'AES-256-GCM / PBKDF2',
      cipherTextBase64,
      ivHex,
      saltHex,
      itemCount: 1,
      category,
    };

    setVaultRecords((prev) => {
      const updated = [record, ...prev];
      localStorage.setItem('edgellm_vault', JSON.stringify(updated));
      return updated;
    });
  };

  const decryptVaultRecord = async (
    record: EncryptedExportRecord,
    passphrase: string
  ): Promise<string> => {
    try {
      const enc = new TextEncoder();
      const salt = new Uint8Array(
        record.saltHex.match(/.{1,2}/g)?.map((byte) => parseInt(byte, 16)) || []
      );
      const iv = new Uint8Array(
        record.ivHex.match(/.{1,2}/g)?.map((byte) => parseInt(byte, 16)) || []
      );

      const binary = atob(record.cipherTextBase64);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
      }

      const keyMaterial = await window.crypto.subtle.importKey(
        'raw',
        enc.encode(passphrase),
        'PBKDF2',
        false,
        ['deriveKey']
      );

      const key = await window.crypto.subtle.deriveKey(
        {
          name: 'PBKDF2',
          salt,
          iterations: 100000,
          hash: 'SHA-256',
        },
        keyMaterial,
        { name: 'AES-GCM', length: 256 },
        false,
        ['decrypt']
      );

      const decryptedBuffer = await window.crypto.subtle.decrypt(
        { name: 'AES-GCM', iv },
        key,
        bytes
      );

      return new TextDecoder().decode(decryptedBuffer);
    } catch {
      throw new Error('Invalid passphrase or corrupted encrypted record.');
    }
  };

  const deleteVaultRecord = (id: string) => {
    setVaultRecords((prev) => {
      const updated = prev.filter((r) => r.id !== id);
      localStorage.setItem('edgellm_vault', JSON.stringify(updated));
      return updated;
    });
  };

  // Billing
  const [userProfile, setUserProfile] = useState<UserSubscriptionProfile>(INITIAL_SUBSCRIPTION_PROFILE);

  const upgradeSubscription = (tier: SubscriptionTier) => {
    setUserProfile((prev) => ({
      ...prev,
      tier,
      monthlyTokensAllocated: tier === SubscriptionTier.PRO_CREATOR ? 200000 : 50000,
    }));
  };

  const resetAllocations = () => {
    setUserProfile((prev) => ({ ...prev, tokensUsedThisMonth: 0 }));
  };

  return (
    <EdgeLLMContext.Provider
      value={{
        currentDestination,
        setCurrentDestination,
        isInSettings,
        setIsInSettings,
        themeMode,
        setThemeMode,
        accentPalette,
        setAccentPalette,
        isAirGapped,
        toggleAirGappedMode,
        models,
        activeModel,
        setActiveModel,
        downloadModel,
        deleteModel,
        addCustomModel,
        chatMessages,
        isGenerating,
        streamingChunk,
        sendPrompt,
        regenerateResponse,
        deleteChatMessage,
        clearChat,
        cancelGeneration,
        rateMessageFeedback,
        activePersona,
        selectPersona,
        generationParams,
        updateGenerationParams,
        activeKnowledgeDoc,
        attachKnowledgeDoc,
        activeAttachedImage,
        attachImage,
        detachImage,
        isSpeaking,
        activeVoiceProfile,
        voiceProfiles,
        autoVoiceReadout,
        toggleAutoVoiceReadout,
        selectVoiceProfile,
        createClonedVoiceProfile,
        deleteVoiceProfile,
        speakText,
        stopSpeaking,
        hardwareInfo,
        governorStatus,
        setGovernorMode,
        setThreadCount,
        activeArenaMatch,
        arenaHistory,
        arenaLeaderboard,
        startArenaBattle,
        voteArenaMatch,
        imageItems,
        isGeneratingImage,
        generateImage,
        upscaleImage,
        deleteImageItem,
        knowledgeDocs,
        ragChunks,
        similarityThreshold,
        setSimilarityThreshold,
        queryRag,
        ingestDoc,
        deleteDoc,
        apiConfig,
        apiStats,
        apiLogs,
        toggleApiServer,
        rotateApiToken,
        updateApiPort,
        testApiCall,
        plugins,
        togglePlugin,
        executePlugin,
        mcpServers,
        toggleMcpServer,
        backgroundJobs,
        addBackgroundJob,
        cancelJob,
        clearCompletedJobs,
        vaultRecords,
        createEncryptedVaultExport,
        decryptVaultRecord,
        deleteVaultRecord,
        userProfile,
        upgradeSubscription,
        resetAllocations,
      }}
    >
      {children}
    </EdgeLLMContext.Provider>
  );
};

export const useEdgeLLM = () => {
  const context = useContext(EdgeLLMContext);
  if (!context) {
    throw new Error('useEdgeLLM must be used within an EdgeLLMProvider');
  }
  return context;
};
