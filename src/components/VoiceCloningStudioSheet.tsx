import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { VoiceProfile } from '../types';
import { X, Mic, Volume2, Square, Plus, Trash2, CheckCircle2 } from 'lucide-react';

interface VoiceCloningStudioSheetProps {
  isOpen: boolean;
  onClose: () => void;
}

export const VoiceCloningStudioSheet: React.FC<VoiceCloningStudioSheetProps> = ({
  isOpen,
  onClose,
}) => {
  const {
    voiceProfiles,
    activeVoiceProfile,
    selectVoiceProfile,
    createClonedVoiceProfile,
    deleteVoiceProfile,
    speakText,
    stopSpeaking,
    isSpeaking,
    autoVoiceReadout,
    toggleAutoVoiceReadout,
    accentPalette,
  } = useEdgeLLM();

  const [testPhrase, setTestPhrase] = useState<string>(
    'EdgeLLM Studio is operating locally in silicon with zero network transmission.'
  );
  const [showNewProfileModal, setShowNewProfileModal] = useState<boolean>(false);
  const [newName, setNewName] = useState<string>('');
  const [newPitch, setNewPitch] = useState<number>(1.0);
  const [newRate, setNewRate] = useState<number>(1.0);
  const [newTimbre, setNewTimbre] = useState<string>('Custom Synthesized Profile');

  if (!isOpen) return null;

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newName.trim()) return;
    createClonedVoiceProfile(newName.trim(), newPitch, newRate, newTimbre);
    setNewName('');
    setShowNewProfileModal(false);
  };

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
            <div className="p-2 rounded-xl bg-pink-500/15">
              <Mic className="w-5 h-5 text-pink-400" />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">Voice Cloning & Neural TTS</h2>
              <p className="text-xs text-slate-400">Zero-Latency Speech Synthesis & Profiles</p>
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
          {/* Auto Readout Toggle */}
          <div
            className="flex items-center justify-between p-3.5 rounded-xl border"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}60`,
            }}
          >
            <div>
              <div className="text-xs font-bold text-white">Auto Voice Readout</div>
              <div className="text-[11px] text-slate-400">
                Automatically read out assistant completions aloud
              </div>
            </div>
            <button
              onClick={toggleAutoVoiceReadout}
              className={`w-12 h-6 flex items-center rounded-full p-1 transition-colors ${
                autoVoiceReadout ? 'bg-cyan-500 justify-end' : 'bg-slate-700 justify-start'
              }`}
            >
              <div className="bg-white w-4 h-4 rounded-full shadow-md" />
            </button>
          </div>

          {/* Voice Profiles List */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-xs font-mono uppercase tracking-wider text-slate-400">
                Acoustic Speaker Profiles
              </label>
              <button
                onClick={() => setShowNewProfileModal(true)}
                className="flex items-center gap-1 text-xs font-semibold text-cyan-400 hover:underline"
              >
                <Plus className="w-3.5 h-3.5" />
                Clone New Voice
              </button>
            </div>

            <div className="space-y-2">
              {voiceProfiles.map((p) => {
                const isSelected = activeVoiceProfile.id === p.id;

                return (
                  <div
                    key={p.id}
                    onClick={() => selectVoiceProfile(p)}
                    className="flex items-center justify-between p-3 rounded-xl border cursor-pointer transition-all"
                    style={{
                      backgroundColor: isSelected
                        ? `${accentPalette.primary}18`
                        : accentPalette.surfaceDark,
                      borderColor: isSelected ? accentPalette.primary : `${accentPalette.borderDark}60`,
                    }}
                  >
                    <div className="flex items-center gap-3">
                      <div
                        className="p-2 rounded-lg"
                        style={{
                          backgroundColor: isSelected
                            ? `${accentPalette.primary}25`
                            : 'rgba(255,255,255,0.05)',
                        }}
                      >
                        <Volume2
                          className="w-4 h-4"
                          style={{ color: isSelected ? accentPalette.primary : '#94A3B8' }}
                        />
                      </div>
                      <div>
                        <div className="text-xs font-bold text-white flex items-center gap-2">
                          <span>{p.name}</span>
                          {isSelected && (
                            <span
                              className="text-[9px] font-mono px-1.5 py-0.2 rounded border"
                              style={{
                                color: accentPalette.primary,
                                borderColor: `${accentPalette.primary}50`,
                              }}
                            >
                              Active
                            </span>
                          )}
                        </div>
                        <div className="text-[10px] text-slate-400">
                          {p.timbre} • Pitch: {p.pitch}x • Speed: {p.rate}x
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      {voiceProfiles.length > 1 && !p.isDefault && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            deleteVoiceProfile(p.id);
                          }}
                          className="p-1 text-slate-500 hover:text-red-400 transition-colors"
                          title="Delete voice profile"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                      {isSelected && (
                        <CheckCircle2
                          className="w-4 h-4"
                          style={{ color: accentPalette.primary }}
                        />
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Audition & Testing Player */}
          <div
            className="p-4 rounded-xl border space-y-3"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}80`,
            }}
          >
            <div className="text-xs font-mono uppercase tracking-wider text-slate-400">
              Interactive Audition Player
            </div>

            <textarea
              value={testPhrase}
              onChange={(e) => setTestPhrase(e.target.value)}
              rows={2}
              className="w-full text-xs bg-slate-900 border border-slate-700 rounded-lg p-2.5 text-white focus:outline-none focus:border-cyan-500 font-sans"
              placeholder="Enter phrase to test speaker timbre..."
            />

            <div className="flex gap-2">
              <button
                onClick={() => speakText(testPhrase)}
                disabled={isSpeaking}
                className="flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-lg text-xs font-bold text-black transition-all disabled:opacity-50"
                style={{ backgroundColor: accentPalette.primary }}
              >
                <Volume2 className="w-4 h-4" />
                Synthesize & Play Sample
              </button>

              {isSpeaking && (
                <button
                  onClick={stopSpeaking}
                  className="px-3 py-2 rounded-lg text-xs font-bold bg-red-600 text-white flex items-center gap-1.5"
                >
                  <Square className="w-3.5 h-3.5 fill-current" />
                  Stop
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Modal for Creating New Voice Profile */}
        {showNewProfileModal && (
          <div className="fixed inset-0 z-60 flex items-center justify-center p-4 bg-black/85">
            <form
              onSubmit={handleCreate}
              className="w-full max-w-sm rounded-xl p-5 border space-y-4"
              style={{
                backgroundColor: accentPalette.surfaceDark,
                borderColor: accentPalette.borderDark,
              }}
            >
              <h3 className="text-sm font-bold text-white">Create Cloned Voice Profile</h3>
              <div>
                <label className="text-[11px] text-slate-400 block mb-1">Profile Name</label>
                <input
                  type="text"
                  required
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  placeholder="e.g. Cyber Narrator"
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-xs text-white"
                />
              </div>

              <div>
                <label className="text-[11px] text-slate-400 block mb-1">
                  Pitch Modulation ({newPitch}x)
                </label>
                <input
                  type="range"
                  min="0.5"
                  max="1.8"
                  step="0.05"
                  value={newPitch}
                  onChange={(e) => setNewPitch(parseFloat(e.target.value))}
                  className="w-full"
                />
              </div>

              <div>
                <label className="text-[11px] text-slate-400 block mb-1">
                  Speaking Rate ({newRate}x)
                </label>
                <input
                  type="range"
                  min="0.5"
                  max="1.8"
                  step="0.05"
                  value={newRate}
                  onChange={(e) => setNewRate(parseFloat(e.target.value))}
                  className="w-full"
                />
              </div>

              <div>
                <label className="text-[11px] text-slate-400 block mb-1">Timbre Resonance</label>
                <input
                  type="text"
                  value={newTimbre}
                  onChange={(e) => setNewTimbre(e.target.value)}
                  placeholder="Acoustic description"
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-xs text-white"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowNewProfileModal(false)}
                  className="px-3 py-1.5 rounded-lg text-xs text-slate-400 hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-lg text-xs font-bold text-black"
                  style={{ backgroundColor: accentPalette.primary }}
                >
                  Save Profile
                </button>
              </div>
            </form>
          </div>
        )}

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
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
