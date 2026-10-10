import React, { useState, useRef, useEffect } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { MessageSender, InferenceMessage } from '../types';
import { BUILT_IN_PERSONAS } from '../data/constants';
import {
  Send,
  Square,
  Sparkles,
  Bot,
  User,
  Copy,
  Volume2,
  ThumbsUp,
  ThumbsDown,
  RotateCcw,
  Trash2,
  ChevronDown,
  ChevronUp,
  Cpu,
  Zap,
  Mic,
  MicOff,
  Image as ImageIcon,
  X,
  Lock,
  Share2,
} from 'lucide-react';

interface InferenceScreenProps {
  onNavigateToExport: (text: string) => void;
}

export const InferenceScreen: React.FC<InferenceScreenProps> = ({ onNavigateToExport }) => {
  const {
    chatMessages,
    isGenerating,
    streamingChunk,
    sendPrompt,
    regenerateResponse,
    deleteChatMessage,
    clearChat,
    cancelGeneration,
    rateMessageFeedback,
    activeModel,
    models,
    setActiveModel,
    activePersona,
    selectPersona,
    generationParams,
    updateGenerationParams,
    speakText,
    isSpeaking,
    stopSpeaking,
    accentPalette,
    attachImage,
    activeAttachedImage,
    detachImage,
  } = useEdgeLLM();

  const [inputPrompt, setInputPrompt] = useState<string>('');
  const [expandedThoughts, setExpandedThoughts] = useState<Record<string, boolean>>({});
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [isListening, setIsListening] = useState<boolean>(false);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatMessages, streamingChunk]);

  const handleSend = () => {
    if (!inputPrompt.trim() || isGenerating) return;
    sendPrompt(inputPrompt);
    setInputPrompt('');
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const toggleThought = (id: string) => {
    setExpandedThoughts((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  // Speech Recognition (Web Speech API)
  const toggleSpeechRecognition = () => {
    if (isListening) {
      setIsListening(false);
      return;
    }

    const SpeechRecognition =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

    if (!SpeechRecognition) {
      setInputPrompt('What are objects in Java?');
      return;
    }

    try {
      const recognition = new SpeechRecognition();
      recognition.continuous = false;
      recognition.interimResults = false;
      recognition.lang = 'en-US';

      recognition.onstart = () => setIsListening(true);
      recognition.onend = () => setIsListening(false);
      recognition.onerror = () => setIsListening(false);

      recognition.onresult = (event: any) => {
        const transcript = event.results[0][0].transcript;
        setInputPrompt((prev) => (prev ? prev + ' ' + transcript : transcript));
        setIsListening(false);
      };

      recognition.start();
    } catch {
      setIsListening(false);
    }
  };

  // Handle Mock Image Attachment
  const handleAttachImage = () => {
    attachImage(
      'https://images.unsplash.com/photo-1620712943543-bcc4688e7485?auto=format&fit=crop&w=400&q=80',
      'Silicon Neural Die'
    );
  };

  return (
    <div className="flex flex-col h-[calc(100vh-60px)] pb-16 max-w-4xl mx-auto">
      {/* Studio Header Settings Strip */}
      <div
        className="px-4 py-2.5 border-b flex flex-wrap items-center justify-between gap-2 text-xs backdrop-blur-md sticky top-0 z-10"
        style={{
          backgroundColor: `${accentPalette.surfaceDark}F0`,
          borderColor: `${accentPalette.borderDark}60`,
        }}
      >
        {/* Model Selector Pill */}
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5 bg-slate-900/80 border border-slate-700/80 rounded-lg px-2.5 py-1">
            <Cpu className="w-3.5 h-3.5 text-cyan-400" />
            <select
              value={activeModel.id}
              onChange={(e) => setActiveModel(e.target.value)}
              className="bg-transparent text-white font-medium text-xs focus:outline-none cursor-pointer"
            >
              {models.map((m) => (
                <option key={m.id} value={m.id} className="bg-slate-900 text-white">
                  {m.name} ({m.quantization})
                </option>
              ))}
            </select>
          </div>

          {/* Persona Selector Pill */}
          <div className="flex items-center gap-1.5 bg-slate-900/80 border border-slate-700/80 rounded-lg px-2.5 py-1">
            <span>{activePersona.emoji}</span>
            <select
              value={activePersona.id}
              onChange={(e) => {
                const found = BUILT_IN_PERSONAS.find((p) => p.id === e.target.value);
                if (found) selectPersona(found);
              }}
              className="bg-transparent text-white font-medium text-xs focus:outline-none cursor-pointer"
            >
              {BUILT_IN_PERSONAS.map((p) => (
                <option key={p.id} value={p.id} className="bg-slate-900 text-white">
                  {p.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Thinking & Export Actions */}
        <div className="flex items-center gap-2">
          {/* Thinking CoT Toggle */}
          <button
            onClick={() =>
              updateGenerationParams({ enableThinking: !generationParams.enableThinking })
            }
            className={`flex items-center gap-1 px-2.5 py-1 rounded-lg border text-xs font-mono transition-colors ${
              generationParams.enableThinking
                ? 'bg-purple-950/50 border-purple-500 text-purple-300'
                : 'bg-slate-900/80 border-slate-700 text-slate-400 hover:text-white'
            }`}
            title="Toggle Chain-of-Thought (CoT) Deep Reasoning Trace"
          >
            <Sparkles className="w-3 h-3 text-purple-400" />
            <span>Thinking Mode</span>
          </button>

          {/* Clear Chat */}
          {chatMessages.length > 0 && (
            <button
              onClick={clearChat}
              className="p-1.5 rounded-lg text-slate-400 hover:text-red-400 hover:bg-slate-800 transition-colors"
              title="Clear Chat Conversation"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}

          {/* Export to Vault */}
          <button
            onClick={() => {
              const fullText = chatMessages
                .map((m) => `${m.sender}: ${m.text}`)
                .join('\n\n');
              onNavigateToExport(fullText);
            }}
            className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-slate-900/80 border border-slate-700 text-xs font-mono text-slate-300 hover:text-white"
            title="Save encrypted session to Zero-Knowledge Vault"
          >
            <Lock className="w-3 h-3 text-amber-400" />
            <span className="hidden sm:inline">Vault Export</span>
          </button>
        </div>
      </div>

      {/* Messages Feed */}
      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
        {chatMessages.length === 0 && (
          <div className="h-full flex flex-col items-center justify-center text-center p-8 space-y-3">
            <div
              className="p-4 rounded-2xl"
              style={{ backgroundColor: `${accentPalette.primary}18` }}
            >
              <Bot className="w-8 h-8" style={{ color: accentPalette.primary }} />
            </div>
            <h3 className="text-base font-bold text-white">EdgeLLM Direct Inference Studio</h3>
            <p className="text-xs text-slate-400 max-w-sm">
              All models execute 100% locally on your silicon hardware. Zero conversational fluff,
              zero latency leaks, pure straight-to-the-point answers.
            </p>
            <div className="flex flex-wrap justify-center gap-1.5 max-w-md pt-2">
              {activePersona.samplePrompts.map((prompt) => (
                <button
                  key={prompt}
                  onClick={() => {
                    setInputPrompt(prompt);
                    sendPrompt(prompt);
                  }}
                  className="text-xs bg-slate-900 border border-slate-800 rounded-full px-3 py-1.5 text-slate-300 hover:border-cyan-500 hover:text-white text-left transition-colors"
                >
                  "{prompt}"
                </button>
              ))}
            </div>
          </div>
        )}

        {chatMessages.map((msg) => {
          const isUser = msg.sender === MessageSender.USER;
          const isThoughtExpanded = expandedThoughts[msg.id] ?? false;

          return (
            <div
              key={msg.id}
              className={`flex gap-3 max-w-[90%] sm:max-w-[85%] ${
                isUser ? 'ml-auto flex-row-reverse' : 'mr-auto'
              }`}
            >
              {/* Avatar */}
              <div
                className={`w-7 h-7 rounded-xl flex items-center justify-center flex-shrink-0 mt-0.5 ${
                  isUser ? 'bg-cyan-600 text-white' : 'bg-slate-800 text-slate-300'
                }`}
              >
                {isUser ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
              </div>

              {/* Message Bubble Container */}
              <div className="space-y-1.5 flex-1 min-w-0">
                {/* Image attachment if user message */}
                {msg.imageUri && (
                  <div className="w-48 h-32 rounded-xl overflow-hidden border border-slate-700 shadow-md">
                    <img
                      src={msg.imageUri}
                      alt={msg.imageLabel || 'Attached frame'}
                      className="w-full h-full object-cover"
                    />
                  </div>
                )}

                {/* Thought Trace Collapsible Box (if CoT was used) */}
                {msg.thoughtTrace && (
                  <div className="rounded-xl border border-purple-500/30 bg-purple-950/20 overflow-hidden text-xs">
                    <button
                      onClick={() => toggleThought(msg.id)}
                      className="w-full px-3 py-1.5 flex items-center justify-between text-purple-300 font-mono text-[11px] hover:bg-purple-900/20"
                    >
                      <span className="flex items-center gap-1.5">
                        <Sparkles className="w-3 h-3 text-purple-400" />
                        Internal Chain-of-Thought Trace
                      </span>
                      {isThoughtExpanded ? (
                        <ChevronUp className="w-3.5 h-3.5" />
                      ) : (
                        <ChevronDown className="w-3.5 h-3.5" />
                      )}
                    </button>
                    {isThoughtExpanded && (
                      <div className="p-3 border-t border-purple-500/20 font-mono text-[11px] text-purple-200/80 whitespace-pre-wrap leading-relaxed">
                        {msg.thoughtTrace}
                      </div>
                    )}
                  </div>
                )}

                {/* Bubble Text */}
                <div
                  className={`p-3.5 rounded-2xl text-xs sm:text-sm leading-relaxed whitespace-pre-wrap shadow-md ${
                    isUser
                      ? 'bg-cyan-600 text-white font-medium rounded-tr-sm'
                      : 'border text-slate-100 rounded-tl-sm'
                  }`}
                  style={
                    !isUser
                      ? {
                          backgroundColor: accentPalette.surfaceDark,
                          borderColor: `${accentPalette.borderDark}90`,
                        }
                      : undefined
                  }
                >
                  {msg.text}
                </div>

                {/* Assistant Telemetry Chips & Message Actions */}
                {!isUser && (
                  <div className="flex flex-wrap items-center justify-between gap-2 px-1 pt-0.5 text-[10px] text-slate-400 font-mono">
                    {/* Telemetry Chips */}
                    <div className="flex flex-wrap items-center gap-1.5">
                      {msg.tokensPerSecond && (
                        <span className="px-1.5 py-0.5 rounded bg-slate-800 text-emerald-400 border border-slate-700 flex items-center gap-1">
                          <Zap className="w-2.5 h-2.5" />
                          {msg.tokensPerSecond} tok/s
                        </span>
                      )}
                      {msg.timeToFirstTokenMs && (
                        <span className="px-1.5 py-0.5 rounded bg-slate-800 text-cyan-400 border border-slate-700">
                          TTFT: {msg.timeToFirstTokenMs}ms
                        </span>
                      )}
                      {msg.tokensGenerated && (
                        <span className="px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                          {msg.tokensGenerated} tokens
                        </span>
                      )}
                      {msg.executionBackend && (
                        <span className="px-1.5 py-0.5 rounded bg-slate-800 text-slate-400 border border-slate-700 hidden sm:inline">
                          {msg.executionBackend}
                        </span>
                      )}
                    </div>

                    {/* Action Buttons */}
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => handleCopy(msg.text, msg.id)}
                        className="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-white"
                        title="Copy text"
                      >
                        <Copy className="w-3.5 h-3.5" />
                      </button>

                      <button
                        onClick={() => speakText(msg.text)}
                        className="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-cyan-400"
                        title="Read aloud with neural TTS"
                      >
                        <Volume2 className="w-3.5 h-3.5" />
                      </button>

                      <button
                        onClick={() => rateMessageFeedback(msg.id, 1)}
                        className={`p-1 rounded hover:bg-slate-800 ${
                          msg.userRating === 1 ? 'text-emerald-400' : 'text-slate-400 hover:text-white'
                        }`}
                        title="Helpful output"
                      >
                        <ThumbsUp className="w-3.5 h-3.5" />
                      </button>

                      <button
                        onClick={() => rateMessageFeedback(msg.id, -1)}
                        className={`p-1 rounded hover:bg-slate-800 ${
                          msg.userRating === -1 ? 'text-red-400' : 'text-slate-400 hover:text-white'
                        }`}
                        title="Poor output"
                      >
                        <ThumbsDown className="w-3.5 h-3.5" />
                      </button>

                      <button
                        onClick={() => deleteChatMessage(msg.id)}
                        className="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-red-400"
                        title="Delete message"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          );
        })}

        {/* Live Streaming Bubble */}
        {isGenerating && streamingChunk && (
          <div className="flex gap-3 mr-auto max-w-[85%]">
            <div className="w-7 h-7 rounded-xl bg-slate-800 text-slate-300 flex items-center justify-center flex-shrink-0 mt-0.5 animate-pulse">
              <Bot className="w-4 h-4 text-cyan-400" />
            </div>
            <div className="space-y-1.5 flex-1 min-w-0">
              <div
                className="p-3.5 rounded-2xl text-xs sm:text-sm leading-relaxed whitespace-pre-wrap border rounded-tl-sm shadow-md"
                style={{
                  backgroundColor: accentPalette.surfaceDark,
                  borderColor: `${accentPalette.primary}60`,
                }}
              >
                {streamingChunk.accumulatedText}
                <span className="inline-block w-1.5 h-4 ml-1 bg-cyan-400 animate-pulse align-middle" />
              </div>

              {/* Streaming Speedometer Bar */}
              <div className="flex items-center gap-2 px-1 text-[10px] text-slate-400 font-mono">
                <span className="px-1.5 py-0.5 rounded bg-emerald-950/60 text-emerald-400 border border-emerald-800 flex items-center gap-1 font-bold">
                  <Zap className="w-2.5 h-2.5" />
                  {streamingChunk.tokensPerSecond} tok/s
                </span>
                <span>{streamingChunk.tokenCount} tokens</span>
                <span className="hidden sm:inline">• {streamingChunk.backendUsed}</span>
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input Composer Dock */}
      <div
        className="p-3 border-t backdrop-blur-xl"
        style={{
          backgroundColor: `${accentPalette.surfaceDark}F5`,
          borderColor: `${accentPalette.borderDark}80`,
        }}
      >
        {/* Attached image preview tag */}
        {activeAttachedImage && (
          <div className="flex items-center gap-2 mb-2 p-1.5 rounded-lg bg-slate-900 border border-slate-700 w-fit">
            <img
              src={activeAttachedImage.uri}
              alt="Attached"
              className="w-6 h-6 rounded object-cover"
            />
            <span className="text-xs text-white">{activeAttachedImage.label}</span>
            <button
              onClick={detachImage}
              className="p-0.5 rounded hover:bg-slate-800 text-slate-400 hover:text-white"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        <div className="flex items-end gap-2 bg-slate-900/90 border border-slate-700/80 rounded-2xl p-2 focus-within:border-cyan-500 transition-colors shadow-inner">
          {/* Attach Image button */}
          <button
            onClick={handleAttachImage}
            className="p-2 rounded-xl text-slate-400 hover:text-cyan-400 hover:bg-white/5 transition-colors"
            title="Attach multimodal image"
          >
            <ImageIcon className="w-4 h-4" />
          </button>

          {/* Voice Input */}
          <button
            onClick={toggleSpeechRecognition}
            className={`p-2 rounded-xl transition-colors ${
              isListening
                ? 'bg-red-500/20 text-red-400 animate-pulse'
                : 'text-slate-400 hover:text-cyan-400 hover:bg-white/5'
            }`}
            title="Voice Speech Input"
          >
            {isListening ? <MicOff className="w-4 h-4" /> : <Mic className="w-4 h-4" />}
          </button>

          {/* Prompt Textarea */}
          <textarea
            value={inputPrompt}
            onChange={(e) => setInputPrompt(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={
              isGenerating
                ? 'Model generating...'
                : 'Message EdgeLLM (e.g. "What are objects in Java?")...'
            }
            rows={1}
            disabled={isGenerating}
            className="flex-1 max-h-32 bg-transparent text-white text-xs sm:text-sm resize-none focus:outline-none placeholder-slate-500 py-1.5 font-sans"
          />

          {/* Send or Stop Generation */}
          {isGenerating ? (
            <button
              onClick={cancelGeneration}
              className="p-2 rounded-xl bg-red-600 text-white hover:bg-red-500 transition-colors flex items-center justify-center"
              title="Stop Generation"
            >
              <Square className="w-4 h-4 fill-current" />
            </button>
          ) : (
            <button
              onClick={handleSend}
              disabled={!inputPrompt.trim()}
              className="p-2 rounded-xl text-black transition-all disabled:opacity-30 disabled:cursor-not-allowed"
              style={{ backgroundColor: accentPalette.primary }}
              title="Send prompt"
            >
              <Send className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
