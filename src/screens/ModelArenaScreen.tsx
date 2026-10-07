import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { ArenaWinner } from '../types';
import { ARENA_CHALLENGES } from '../data/constants';
import {
  Swords,
  Trophy,
  Shield,
  Zap,
  Play,
  RotateCcw,
  Check,
  Award,
  ChevronRight,
  Flame,
  ArrowRight,
  Eye,
  EyeOff,
} from 'lucide-react';

interface ModelArenaScreenProps {
  onNavigateToChat: () => void;
}

export const ModelArenaScreen: React.FC<ModelArenaScreenProps> = ({ onNavigateToChat }) => {
  const {
    activeArenaMatch,
    arenaHistory,
    arenaLeaderboard,
    startArenaBattle,
    voteArenaMatch,
    models,
    accentPalette,
  } = useEdgeLLM();

  const [promptInput, setPromptInput] = useState<string>(
    "Resolve the Grandfather paradox using the Many-Worlds interpretation and Novikov's self-consistency principle."
  );
  const [isBlindMode, setIsBlindMode] = useState<boolean>(true);
  const [selectedModelA, setSelectedModelA] = useState<string>(models[0]?.id || 'gemma_2b_it');
  const [selectedModelB, setSelectedModelB] = useState<string>(
    models[1]?.id || models[0]?.id || 'qwen_2_5_1_5b_instruct'
  );
  const [activeTab, setActiveTab] = useState<'arena' | 'leaderboard'>('arena');

  const handleLaunchBattle = () => {
    if (!promptInput.trim()) return;
    startArenaBattle(promptInput, isBlindMode, selectedModelA, selectedModelB);
  };

  const selectChallenge = (p: string) => {
    setPromptInput(p);
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-5 space-y-6 pb-24">
      {/* Header Banner */}
      <div
        className="p-5 rounded-2xl border flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-xl relative overflow-hidden"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}90`,
        }}
      >
        <div className="flex items-center gap-3">
          <div className="p-3 rounded-xl bg-violet-500/20 shadow-lg border border-violet-500/30">
            <Swords className="w-6 h-6 text-violet-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-white tracking-tight">
                Neural Model Arena & Tensor Clash
              </h2>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-violet-950 text-violet-300 border border-violet-700">
                LMSYS ELO
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Side-by-Side Model A/B Battles with Grounded Local Intelligence
            </p>
          </div>
        </div>

        {/* Tab Switcher */}
        <div className="flex items-center bg-slate-900 border border-slate-700/80 rounded-xl p-1 self-start sm:self-auto">
          <button
            onClick={() => setActiveTab('arena')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'arena' ? 'bg-cyan-500 text-black shadow-md' : 'text-slate-400 hover:text-white'
            }`}
          >
            Battle Arena
          </button>
          <button
            onClick={() => setActiveTab('leaderboard')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'leaderboard'
                ? 'bg-violet-500 text-white shadow-md'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            Hall of Elo
          </button>
        </div>
      </div>

      {activeTab === 'arena' ? (
        <div className="space-y-6">
          {/* Arena Setup Card */}
          <div
            className="p-5 rounded-2xl border space-y-4 shadow-lg"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}80`,
            }}
          >
            {/* Fighter Selector Strip */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="text-xs font-mono uppercase tracking-wider text-cyan-400 block mb-1">
                  Alpha Corner (Model A)
                </label>
                <select
                  value={selectedModelA}
                  onChange={(e) => setSelectedModelA(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-700 rounded-xl p-2.5 text-xs text-white"
                >
                  {models.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name} ({m.format} • {m.quantization})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="text-xs font-mono uppercase tracking-wider text-violet-400 block mb-1">
                  Beta Corner (Model B)
                </label>
                <select
                  value={selectedModelB}
                  onChange={(e) => setSelectedModelB(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-700 rounded-xl p-2.5 text-xs text-white"
                >
                  {models.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name} ({m.format} • {m.quantization})
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Prompt Input */}
            <div>
              <div className="flex justify-between items-center mb-1">
                <label className="text-xs font-mono uppercase tracking-wider text-slate-400">
                  Arena Challenge Prompt
                </label>
                <button
                  onClick={() => setIsBlindMode(!isBlindMode)}
                  className="flex items-center gap-1.5 text-xs font-mono text-slate-400 hover:text-cyan-400"
                >
                  {isBlindMode ? <EyeOff className="w-3.5 h-3.5 text-cyan-400" /> : <Eye className="w-3.5 h-3.5" />}
                  <span>{isBlindMode ? 'Blind Mode (Shields On)' : 'Open Identities'}</span>
                </button>
              </div>
              <textarea
                value={promptInput}
                onChange={(e) => setPromptInput(e.target.value)}
                rows={2}
                placeholder="Enter question, algorithm, or reasoning test to clash both models..."
                className="w-full bg-slate-900 border border-slate-700 rounded-xl p-3 text-xs sm:text-sm text-white focus:outline-none focus:border-cyan-500"
              />
            </div>

            {/* Challenge Decks Pills */}
            <div className="space-y-1.5">
              <div className="text-[11px] font-mono uppercase text-slate-400">
                Curated Challenge Decks:
              </div>
              <div className="flex flex-wrap gap-1.5">
                {ARENA_CHALLENGES.map((ch) => (
                  <button
                    key={ch.id}
                    onClick={() => selectChallenge(ch.prompt)}
                    className="flex items-center gap-1 text-[11px] px-2.5 py-1 rounded-full bg-slate-800/80 border border-slate-700 text-slate-300 hover:text-white hover:border-slate-500 transition-colors"
                  >
                    <span>{ch.iconEmoji}</span>
                    <span>{ch.title}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Battle Launch Button */}
            <div className="flex justify-end pt-2">
              <button
                onClick={handleLaunchBattle}
                disabled={activeArenaMatch?.isBattling}
                className="px-6 py-2.5 rounded-xl text-xs font-bold text-black flex items-center gap-2 transition-all disabled:opacity-50"
                style={{ backgroundColor: accentPalette.primary }}
              >
                <Play className="w-4 h-4 fill-current" />
                <span>Commence Clash Battle</span>
              </button>
            </div>
          </div>

          {/* Active Battle Colosseum Arena Display */}
          {activeArenaMatch && (
            <div className="space-y-4">
              <div className="flex items-center justify-between text-xs font-mono text-slate-400 px-1">
                <span>Prompt: "{activeArenaMatch.prompt.slice(0, 60)}..."</span>
                <span className="text-cyan-400">
                  {activeArenaMatch.isBattling
                    ? '⚡ Live Stream Velocity Clash...'
                    : activeArenaMatch.isFinished
                    ? '🏆 Battle Decided'
                    : 'Awaiting User Jury Verdict'}
                </span>
              </div>

              {/* Side-by-Side Dual Fighter Cards */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Fighter Alpha Card */}
                <div className="p-4 rounded-2xl border bg-slate-900/90 border-cyan-500/40 shadow-xl flex flex-col justify-between space-y-3">
                  <div>
                    <div className="flex items-center justify-between border-b border-slate-800 pb-2 mb-2">
                      <div className="flex items-center gap-2">
                        <span className="w-2.5 h-2.5 rounded-full bg-cyan-400" />
                        <h4 className="text-sm font-bold text-white">
                          {activeArenaMatch.isBlindMode && !activeArenaMatch.isFinished
                            ? 'Fighter Alpha (Shielded)'
                            : activeArenaMatch.modelAName}
                        </h4>
                      </div>
                      <span className="text-[10px] font-mono text-cyan-400">
                        {activeArenaMatch.tokPerSecA} tok/s
                      </span>
                    </div>

                    <div className="text-xs text-slate-200 leading-relaxed min-h-[140px] whitespace-pre-wrap">
                      {activeArenaMatch.responseA || (
                        <span className="text-slate-500 animate-pulse font-mono">
                          Generating response tokens...
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Alpha Vote Action */}
                  {!activeArenaMatch.isBattling && !activeArenaMatch.isFinished && (
                    <button
                      onClick={() => voteArenaMatch(ArenaWinner.MODEL_A)}
                      className="w-full py-2 rounded-xl text-xs font-bold bg-cyan-500 hover:bg-cyan-400 text-black transition-colors"
                    >
                      Vote Model A as Winner
                    </button>
                  )}
                </div>

                {/* Fighter Beta Card */}
                <div className="p-4 rounded-2xl border bg-slate-900/90 border-violet-500/40 shadow-xl flex flex-col justify-between space-y-3">
                  <div>
                    <div className="flex items-center justify-between border-b border-slate-800 pb-2 mb-2">
                      <div className="flex items-center gap-2">
                        <span className="w-2.5 h-2.5 rounded-full bg-violet-400" />
                        <h4 className="text-sm font-bold text-white">
                          {activeArenaMatch.isBlindMode && !activeArenaMatch.isFinished
                            ? 'Fighter Beta (Shielded)'
                            : activeArenaMatch.modelBName}
                        </h4>
                      </div>
                      <span className="text-[10px] font-mono text-violet-400">
                        {activeArenaMatch.tokPerSecB} tok/s
                      </span>
                    </div>

                    <div className="text-xs text-slate-200 leading-relaxed min-h-[140px] whitespace-pre-wrap">
                      {activeArenaMatch.responseB || (
                        <span className="text-slate-500 animate-pulse font-mono">
                          Generating response tokens...
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Beta Vote Action */}
                  {!activeArenaMatch.isBattling && !activeArenaMatch.isFinished && (
                    <button
                      onClick={() => voteArenaMatch(ArenaWinner.MODEL_B)}
                      className="w-full py-2 rounded-xl text-xs font-bold bg-violet-500 hover:bg-violet-400 text-white transition-colors"
                    >
                      Vote Model B as Winner
                    </button>
                  )}
                </div>
              </div>

              {/* Tie / Both Bad Options */}
              {!activeArenaMatch.isBattling && !activeArenaMatch.isFinished && (
                <div className="flex justify-center gap-3 pt-1">
                  <button
                    onClick={() => voteArenaMatch(ArenaWinner.TIE)}
                    className="px-4 py-1.5 rounded-xl border border-slate-700 bg-slate-800 text-xs text-slate-300 hover:text-white"
                  >
                    Declare Tie (Both Equal)
                  </button>
                  <button
                    onClick={() => voteArenaMatch(ArenaWinner.BOTH_BAD)}
                    className="px-4 py-1.5 rounded-xl border border-slate-700 bg-slate-800 text-xs text-slate-300 hover:text-red-400"
                  >
                    Both Responses Bad
                  </button>
                </div>
              )}

              {/* Holographic Reveal Banner on Finish */}
              {activeArenaMatch.isFinished && (
                <div className="p-4 rounded-xl border border-emerald-500/40 bg-emerald-950/20 text-center space-y-1">
                  <div className="text-sm font-bold text-emerald-300 flex items-center justify-center gap-2">
                    <Trophy className="w-4 h-4 text-amber-400" />
                    Verdict Recorded: {activeArenaMatch.userVote}
                  </div>
                  <p className="text-xs text-slate-300">
                    Model A: {activeArenaMatch.modelAName} ({activeArenaMatch.eloDeltaA! >= 0 ? '+' : ''}
                    {activeArenaMatch.eloDeltaA} Elo) vs Model B: {activeArenaMatch.modelBName} (
                    {activeArenaMatch.eloDeltaB! >= 0 ? '+' : ''}
                    {activeArenaMatch.eloDeltaB} Elo)
                  </p>
                </div>
              )}
            </div>
          )}
        </div>
      ) : (
        /* Hall of Elo Leaderboard */
        <div
          className="p-5 rounded-2xl border space-y-4 shadow-xl"
          style={{
            backgroundColor: accentPalette.surfaceDark,
            borderColor: `${accentPalette.borderDark}80`,
          }}
        >
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-white flex items-center gap-2">
              <Award className="w-4 h-4 text-amber-400" />
              Classified Hall of Elo Leaderboard
            </h3>
            <span className="text-xs text-slate-400 font-mono">Ranked by Head-to-Head Battles</span>
          </div>

          <div className="divide-y divide-slate-800">
            {arenaLeaderboard.map((entry, idx) => (
              <div key={entry.id} className="py-3 flex items-center justify-between text-xs">
                <div className="flex items-center gap-3">
                  <span
                    className={`w-6 h-6 rounded-full flex items-center justify-center font-bold font-mono text-[11px] ${
                      idx === 0
                        ? 'bg-amber-400 text-black'
                        : idx === 1
                        ? 'bg-slate-300 text-black'
                        : idx === 2
                        ? 'bg-amber-700 text-white'
                        : 'bg-slate-800 text-slate-400'
                    }`}
                  >
                    #{idx + 1}
                  </span>
                  <div>
                    <div className="font-bold text-white text-sm">{entry.modelName}</div>
                    <div className="text-slate-400 text-[11px] mt-0.5">
                      {entry.format} • {entry.siliconCore} • {entry.averageTokSec} tok/s peak
                    </div>
                  </div>
                </div>

                <div className="text-right">
                  <div className="font-mono font-bold text-cyan-400 text-sm">
                    {entry.eloRating} ELO
                  </div>
                  <div className="text-slate-400 text-[10px] font-mono">
                    {entry.wins}W - {entry.losses}L - {entry.ties}T ({entry.rankTier})
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
