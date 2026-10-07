export enum AppDestination {
  DASHBOARD = 'DASHBOARD',
  CHAT = 'CHAT',
  MODELS = 'MODELS',
  BENCHMARK = 'BENCHMARK',
  IMAGE_STUDIO = 'IMAGE_STUDIO',
  HF_EXPLORER = 'HF_EXPLORER',
  RAG_DEBUG = 'RAG_DEBUG',
  API = 'API',
  QUEUE = 'QUEUE',
  PLUGINS = 'PLUGINS',
  VAULT = 'VAULT',
}

export enum AppThemeMode {
  DARK = 'DARK',
  LIGHT = 'LIGHT',
  SYSTEM = 'SYSTEM',
}

export enum AccentPaletteId {
  CYBER_CYAN = 'CYBER_CYAN',
  NEURAL_VIOLET = 'NEURAL_VIOLET',
  EMERALD_MATRIX = 'EMERALD_MATRIX',
  SOLAR_PLASMA = 'SOLAR_PLASMA',
  CRIMSON_VALKYRIE = 'CRIMSON_VALKYRIE',
  OBSIDIAN_STEALTH = 'OBSIDIAN_STEALTH',
}

export interface PaletteDef {
  id: AccentPaletteId;
  title: string;
  subtitle: string;
  primary: string;
  secondary: string;
  accentGlow: string;
  bgDark: string;
  surfaceDark: string;
  surfaceVariantDark: string;
  borderDark: string;
  bgLight: string;
  surfaceLight: string;
  surfaceVariantLight: string;
  borderLight: string;
}

export enum ModelFormat {
  GGUF = 'GGUF',
  TFLITE = 'TFLITE',
  ONNX = 'ONNX',
  MEDIAPIPE_TASK = 'MEDIAPIPE_TASK',
  MNN_LLM = 'MNN_LLM',
  ANDROID_AICORE = 'ANDROID_AICORE',
}

export enum ModelCategory {
  CHAT_REASONING = 'Chat & Reasoning',
  CODE_ANALYSIS = 'Code & Security',
  EMBEDDINGS_CLASSIFICATION = 'Classification & Fast Tasks',
  VISION_MULTIMODAL = 'Multimodal / Vision',
}

export interface ModelSpec {
  id: string;
  name: string;
  parameterCount: string;
  format: ModelFormat;
  quantization: string;
  fileSizeBytes: number;
  requiredRamBytes: number;
  contextLength: number;
  description: string;
  category: ModelCategory;
  downloadUrl: string;
  sha256Checksum: string;
  isDownloaded: boolean;
  downloadProgressPercent: number;
  isDownloading: boolean;
  isPaused: boolean;
  downloadSpeedFormatted: string;
  downloadedBytes: number;
  downloadStatusText: string;
  isActive: boolean;
  isImported: boolean;
  supportsVision: boolean;
  supportsCoT: boolean;
  supportsGrammar: boolean;
  supportsToolCalling: boolean;
  benchmarkMmluScore: number;
  benchmarkGsm8kScore: number;
  benchmarkHumanEvalScore: number;
  maxTurboTokPerSec: number;
}

export enum MessageSender {
  USER = 'USER',
  ASSISTANT = 'ASSISTANT',
}

export interface InferenceMessage {
  id: string;
  sender: MessageSender;
  text: string;
  timestamp: number;
  tokensGenerated?: number;
  tokensPerSecond?: number;
  timeToFirstTokenMs?: number;
  executionBackend?: string;
  modelId?: string;
  sessionId?: string;
  imageUri?: string | null;
  imageLabel?: string | null;
  recalledMemories?: string[];
  userRating?: number; // 1 for up, -1 for down, 0 for unrated
  isThinking?: boolean;
  thoughtTrace?: string;
  grammarModeUsed?: string;
}

export interface StreamTokenChunk {
  accumulatedText: string;
  tokenCount: number;
  tokensPerSecond: number;
  timeToFirstTokenMs: number;
  backendUsed: string;
  isComplete: boolean;
  trustScore?: number;
  factualAccuracyScore?: number;
  sentimentToneScore?: number;
  topicAdherenceScore?: number;
  wasMultiAgentRefined?: boolean;
  critiqueSummary?: string;
  critiqueReasons?: string[];
  isTurboBoost?: boolean;
  isPrefixCacheHit?: boolean;
  grammarModeUsed?: string;
}

export interface AiPersona {
  id: string;
  name: string;
  tag: string;
  emoji: string;
  description: string;
  systemPrompt: string;
  defaultTemperature: number;
  defaultTopP: number;
  supportsReasoningTrace: boolean;
  samplePrompts: string[];
}

export interface GenerationParameters {
  temperature: number;
  topP: number;
  topK: number;
  maxNewTokens: number;
  systemPrompt: string;
  enableThinking: boolean;
  thinkingTokenBudget: number;
  grammarConstraint: 'NONE' | 'JSON' | 'SQL' | 'REGEX' | 'STRICT_GBNF';
}

export enum ComputeBackend {
  ARM_NEON = 'ARM_NEON',
  VULKAN_GPU = 'VULKAN_GPU',
  OPENCL_GPU = 'OPENCL_GPU',
  HEXAGON_NPU = 'HEXAGON_NPU',
  NNAPI = 'NNAPI',
  WEBGPU = 'WEBGPU',
}

export interface HardwareAccelerationSettings {
  computeBackend: ComputeBackend;
  threadCount: number;
  enableFlashAttention: boolean;
  enableMmap: boolean;
  kvCacheQuantization: string;
  offloadLayers: number;
}

export interface DeviceHardwareInfo {
  socModel: string;
  cpuCores: number;
  gpuRenderer: string;
  npuAvailable: boolean;
  npuTopsEstimate: number;
  totalRamBytes: number;
  availableRamBytes: number;
  batteryLevelPercent: number;
  batteryTemperatureCelsius: number;
  thermalZoneEnvelope: 'NOMINAL' | 'LIGHT_THROTTLE' | 'CRITICAL';
}

export enum GovernorMode {
  MAX_PERFORMANCE = 'MAX_PERFORMANCE',
  DYNAMIC_BALANCED = 'DYNAMIC_BALANCED',
  ECO_BATTERY_SAVER = 'ECO_BATTERY_SAVER',
}

export interface SiliconGovernorStatus {
  mode: GovernorMode;
  activeThreads: number;
  gpuFrequencyCapMhz: number;
  thermalThrottled: boolean;
  kvCompactionEnabled: boolean;
  powerConsumptionWatts: number;
}

// Model Arena Models
export interface ArenaChallengePrompt {
  id: string;
  title: string;
  category: string;
  prompt: string;
  difficulty: string;
  iconEmoji: string;
}

export enum ArenaWinner {
  MODEL_A = 'MODEL_A',
  MODEL_B = 'MODEL_B',
  TIE = 'TIE',
  BOTH_BAD = 'BOTH_BAD',
}

export interface ModelArenaMatch {
  id: string;
  prompt: string;
  category: string;
  modelAId: string;
  modelAName: string;
  modelAFormat: string;
  modelAParameters: string;
  modelAQuant: string;
  modelAComputeBackend: string;
  responseA: string;
  tokPerSecA: number;
  ttftMsA: number;
  modelBId: string;
  modelBName: string;
  modelBFormat: string;
  modelBParameters: string;
  modelBQuant: string;
  modelBComputeBackend: string;
  responseB: string;
  tokPerSecB: number;
  ttftMsB: number;
  isBlindMode: boolean;
  isBattling: boolean;
  isFinished: boolean;
  userVote?: ArenaWinner;
  eloDeltaA?: number;
  eloDeltaB?: number;
  timestamp: number;
}

export interface ArenaLeaderboardEntry {
  id: string;
  modelName: string;
  format: string;
  eloRating: number;
  wins: number;
  losses: number;
  ties: number;
  averageTokSec: number;
  rankTier: string;
  siliconCore: string;
}

// Image AI Models
export enum ImageTaskMode {
  TEXT_TO_IMAGE = 'TEXT_TO_IMAGE',
  IMAGE_TO_IMAGE = 'IMAGE_TO_IMAGE',
  INPAINTING = 'INPAINTING',
  SUPER_RESOLUTION = 'SUPER_RESOLUTION',
}

export interface GeneratedImageItem {
  id: string;
  prompt: string;
  negativePrompt: string;
  mode: ImageTaskMode;
  modelName: string;
  steps: number;
  cfgScale: number;
  seed: number;
  timestamp: number;
  generationTimeMs: number;
  imageUrl: string;
  width: number;
  height: number;
  isUpscaled?: boolean;
}

// RAG Models
export interface KnowledgeDocument {
  id: string;
  title: string;
  summary: string;
  content: string;
  sizeBytes: number;
  tokenCountEstimate: number;
  isPreloaded: boolean;
  chunksCount: number;
}

export interface RagChunkItem {
  id: string;
  documentTitle: string;
  chunkIndex: number;
  totalChunks: number;
  sha256ContentHash: string;
  content: string;
  tokenCount: number;
  embeddingDimension: number;
  cosineSimilarityScore: number;
  retrievalLatencyMs: number;
  sourceFormat: string;
}

// API Server Models
export interface ApiServerConfig {
  port: number;
  bindToLan: boolean;
  allowCors: boolean;
  enableOpenAiCompat: boolean;
  authToken: string;
}

export interface ApiServerStats {
  isRunning: boolean;
  port: number;
  localIp: string;
  lanIp: string;
  activeConnections: number;
  totalRequestsServed: number;
  totalTokensGenerated: number;
  averageLatencyMs: number;
  uptimeSeconds: number;
}

export interface ApiRequestLog {
  id: string;
  timestamp: number;
  method: string;
  path: string;
  clientIp: string;
  statusCode: number;
  durationMs: number;
  tokensGenerated: number;
  model: string;
  promptPreview: string;
}

// Plugins & MCP
export interface PluginSpec {
  id: string;
  name: string;
  category: string;
  description: string;
  version: string;
  isEnabled: boolean;
  iconKey: string;
}

export interface PluginResult {
  pluginId: string;
  success: boolean;
  processedOutput: string;
  metrics: Record<string, string>;
  durationMs: number;
}

export interface McpServerSpec {
  id: string;
  name: string;
  endpointUrl: string;
  isEnabled: boolean;
  isConnected: boolean;
  protocolVersion: string;
  latencyMs: number;
  toolsCount: number;
}

// Background Queue
export enum JobStatus {
  QUEUED = 'QUEUED',
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
}

export enum JobType {
  BATCH_SUMMARIZATION = 'BATCH_SUMMARIZATION',
  DOCUMENT_ANALYSIS = 'DOCUMENT_ANALYSIS',
  CODE_AUDIT = 'CODE_AUDIT',
  EMBEDDING_INGESTION = 'EMBEDDING_INGESTION',
}

export interface BackgroundJob {
  id: string;
  title: string;
  type: JobType;
  status: JobStatus;
  progressPercent: number;
  priority: 'LOW' | 'NORMAL' | 'HIGH';
  modelId: string;
  createdAt: number;
  completedAt?: number;
  resultOutput?: string;
  error?: string;
}

// Vault & Encryption
export interface EncryptedExportRecord {
  id: string;
  title: string;
  timestamp: number;
  algorithm: string;
  cipherTextBase64: string;
  ivHex: string;
  saltHex: string;
  itemCount: number;
  category: 'CHAT_SESSION' | 'VECTOR_MEMORY' | 'SYSTEM_CONFIG';
}

// Voice
export interface VoiceProfile {
  id: string;
  name: string;
  pitch: number;
  rate: number;
  timbre: string;
  sampleName?: string;
  isDefault?: boolean;
}

// Billing / Pro Allocations
export enum SubscriptionTier {
  FREE_STARTER = 'FREE_STARTER',
  PRO_CREATOR = 'PRO_CREATOR',
}

export interface UserSubscriptionProfile {
  tier: SubscriptionTier;
  monthlyTokensAllocated: number;
  tokensUsedThisMonth: number;
  billingCycleResetDate: string;
}
