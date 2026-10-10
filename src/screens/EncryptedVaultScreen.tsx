import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { EncryptedExportRecord } from '../types';
import {
  Lock,
  Key,
  Unlock,
  Trash2,
  Copy,
  Plus,
  ShieldCheck,
  CheckCircle2,
  AlertTriangle,
  FileText,
} from 'lucide-react';

interface EncryptedVaultScreenProps {
  prefilledExportContent?: string;
}

export const EncryptedVaultScreen: React.FC<EncryptedVaultScreenProps> = ({
  prefilledExportContent = '',
}) => {
  const {
    vaultRecords,
    createEncryptedVaultExport,
    decryptVaultRecord,
    deleteVaultRecord,
    accentPalette,
  } = useEdgeLLM();

  const [showExportModal, setShowExportModal] = useState<boolean>(Boolean(prefilledExportContent));
  const [exportTitle, setExportTitle] = useState<string>('Chat Session Export');
  const [exportContent, setExportContent] = useState<string>(prefilledExportContent);
  const [exportPassphrase, setExportPassphrase] = useState<string>('');

  const [selectedRecord, setSelectedRecord] = useState<EncryptedExportRecord | null>(null);
  const [decryptPassphrase, setDecryptPassphrase] = useState<string>('');
  const [decryptedText, setDecryptedText] = useState<string | null>(null);
  const [decryptError, setDecryptError] = useState<string | null>(null);

  const handleExport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!exportPassphrase.trim() || !exportContent.trim()) return;
    await createEncryptedVaultExport(
      exportTitle.trim() || 'Vault Item',
      exportContent.trim(),
      exportPassphrase.trim(),
      'CHAT_SESSION'
    );
    setExportTitle('');
    setExportContent('');
    setExportPassphrase('');
    setShowExportModal(false);
  };

  const handleDecrypt = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRecord || !decryptPassphrase.trim()) return;
    setDecryptError(null);
    try {
      const text = await decryptVaultRecord(selectedRecord, decryptPassphrase.trim());
      setDecryptedText(text);
      setDecryptPassphrase('');
    } catch (err: any) {
      setDecryptError(err.message || 'Decryption failed.');
    }
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-5 space-y-6 pb-24">
      {/* Header Banner */}
      <div
        className="p-5 rounded-2xl border flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-xl"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}90`,
        }}
      >
        <div className="flex items-center gap-3">
          <div className="p-3 rounded-xl bg-amber-500/20 shadow-lg border border-amber-500/30">
            <Lock className="w-6 h-6 text-amber-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-white tracking-tight">
                Zero-Knowledge Encrypted Vault
              </h2>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-amber-950 text-amber-300 border border-amber-700">
                AES-256-GCM
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Hardware-Backed PBKDF2 Authenticated Payloads with Zero Data Egress
            </p>
          </div>
        </div>

        <button
          onClick={() => setShowExportModal(true)}
          className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-bold text-black self-start sm:self-auto transition-transform hover:scale-[1.02]"
          style={{ backgroundColor: accentPalette.primary }}
        >
          <Plus className="w-4 h-4" />
          <span>Encrypt New Item</span>
        </button>
      </div>

      {/* Vault Records List */}
      <div>
        <div className="flex items-center justify-between mb-3 px-1">
          <h3 className="text-sm font-bold text-white uppercase tracking-wider font-mono">
            Encrypted Records ({vaultRecords.length})
          </h3>
          <span className="text-xs text-slate-400 font-mono">Zero-Knowledge Storage</span>
        </div>

        <div className="space-y-3">
          {vaultRecords.map((record) => (
            <div
              key={record.id}
              className="p-4 rounded-2xl border space-y-2.5 shadow-md"
              style={{
                backgroundColor: accentPalette.surfaceDark,
                borderColor: `${accentPalette.borderDark}80`,
              }}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Key className="w-4 h-4 text-amber-400" />
                  <h4 className="text-sm font-bold text-white">{record.title}</h4>
                  <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-300">
                    {record.category}
                  </span>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => {
                      setSelectedRecord(record);
                      setDecryptedText(null);
                      setDecryptError(null);
                    }}
                    className="px-3 py-1 rounded-lg bg-amber-500/20 text-amber-300 border border-amber-500/40 text-xs font-semibold hover:bg-amber-500/30 flex items-center gap-1"
                  >
                    <Unlock className="w-3.5 h-3.5" />
                    Decrypt
                  </button>
                  <button
                    onClick={() => deleteVaultRecord(record.id)}
                    className="p-1 text-slate-500 hover:text-red-400"
                    title="Delete record"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              {/* Ciphertext snippet */}
              <div className="p-2.5 rounded-xl bg-slate-950 border border-slate-800 font-mono text-[11px] text-slate-400 truncate">
                Ciphertext: {record.cipherTextBase64}
              </div>

              <div className="flex items-center justify-between text-[10px] font-mono text-slate-500 pt-1">
                <span>{record.algorithm}</span>
                <span>{new Date(record.timestamp).toLocaleDateString()}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Decrypt Dialog */}
      {selectedRecord && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-sm animate-in fade-in">
          <div
            className="w-full max-w-md rounded-2xl border p-6 space-y-4 shadow-2xl"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: accentPalette.borderDark,
            }}
          >
            <div className="flex justify-between items-center">
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <Unlock className="w-4 h-4 text-amber-400" />
                Decrypt Record
              </h3>
              <button
                onClick={() => setSelectedRecord(null)}
                className="text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>

            <p className="text-xs text-slate-300">
              Enter your zero-knowledge PBKDF2 passphrase to decrypt{' '}
              <span className="text-white font-bold">"{selectedRecord.title}"</span>.
            </p>

            <form onSubmit={handleDecrypt} className="space-y-3">
              <div>
                <input
                  type="password"
                  required
                  value={decryptPassphrase}
                  onChange={(e) => setDecryptPassphrase(e.target.value)}
                  placeholder="Enter vault encryption passphrase..."
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2.5 text-xs text-white"
                />
              </div>

              {decryptError && (
                <div className="text-xs text-red-400 font-mono flex items-center gap-1.5">
                  <AlertTriangle className="w-3.5 h-3.5" />
                  {decryptError}
                </div>
              )}

              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setSelectedRecord(null)}
                  className="px-4 py-2 rounded-xl text-xs text-slate-400 hover:text-white"
                >
                  Close
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl text-xs font-bold text-black"
                  style={{ backgroundColor: accentPalette.primary }}
                >
                  Decrypt Payload
                </button>
              </div>
            </form>

            {decryptedText && (
              <div className="p-3.5 rounded-xl bg-slate-950 border border-emerald-500/40 space-y-2 mt-4 font-mono text-xs">
                <div className="flex justify-between text-[11px] text-emerald-400 font-bold">
                  <span>Decrypted Plaintext:</span>
                  <button
                    onClick={() => navigator.clipboard.writeText(decryptedText)}
                    className="flex items-center gap-1 hover:underline text-cyan-400"
                  >
                    <Copy className="w-3 h-3" />
                    Copy
                  </button>
                </div>
                <div className="text-white whitespace-pre-wrap max-h-48 overflow-y-auto">
                  {decryptedText}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Export / Create Modal */}
      {showExportModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-sm animate-in fade-in">
          <form
            onSubmit={handleExport}
            className="w-full max-w-md rounded-2xl border p-6 space-y-4 shadow-2xl"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: accentPalette.borderDark,
            }}
          >
            <div className="flex justify-between items-center">
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <Lock className="w-4 h-4 text-amber-400" />
                Encrypt & Export Payload
              </h3>
              <button
                type="button"
                onClick={() => setShowExportModal(false)}
                className="text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Record Title</label>
              <input
                type="text"
                required
                value={exportTitle}
                onChange={(e) => setExportTitle(e.target.value)}
                placeholder="e.g. Encrypted Audit Notes"
                className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2.5 text-xs text-white"
              />
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Plaintext Content</label>
              <textarea
                required
                rows={4}
                value={exportContent}
                onChange={(e) => setExportContent(e.target.value)}
                placeholder="Content to seal into AES-256-GCM vault..."
                className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2.5 text-xs text-white font-mono"
              />
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">
                Passphrase (PBKDF2 100,000 Iterations)
              </label>
              <input
                type="password"
                required
                value={exportPassphrase}
                onChange={(e) => setExportPassphrase(e.target.value)}
                placeholder="Set secure encryption passphrase..."
                className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2.5 text-xs text-white"
              />
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowExportModal(false)}
                className="px-4 py-2 rounded-xl text-xs text-slate-400 hover:text-white"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-5 py-2 rounded-xl text-xs font-bold text-black"
                style={{ backgroundColor: accentPalette.primary }}
              >
                Seal & Encrypt Record
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
