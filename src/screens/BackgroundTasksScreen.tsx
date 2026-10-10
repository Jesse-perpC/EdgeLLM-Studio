import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { JobType, JobStatus } from '../types';
import {
  ListOrdered,
  Plus,
  Play,
  CheckCircle2,
  AlertCircle,
  Clock,
  Trash2,
  Download,
  Lock,
} from 'lucide-react';

interface BackgroundTasksScreenProps {
  onNavigateToExport: (text: string) => void;
}

export const BackgroundTasksScreen: React.FC<BackgroundTasksScreenProps> = ({
  onNavigateToExport,
}) => {
  const {
    backgroundJobs,
    addBackgroundJob,
    cancelJob,
    clearCompletedJobs,
    activeModel,
    accentPalette,
  } = useEdgeLLM();

  const [showAddModal, setShowAddModal] = useState<boolean>(false);
  const [jobTitle, setJobTitle] = useState<string>('');
  const [jobType, setJobType] = useState<JobType>(JobType.BATCH_SUMMARIZATION);
  const [jobPriority, setJobPriority] = useState<'LOW' | 'NORMAL' | 'HIGH'>('NORMAL');

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    if (!jobTitle.trim()) return;
    addBackgroundJob(jobTitle.trim(), jobType, activeModel.name, jobPriority);
    setJobTitle('');
    setShowAddModal(false);
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
          <div className="p-3 rounded-xl bg-cyan-500/20 shadow-lg border border-cyan-500/30">
            <ListOrdered className="w-6 h-6 text-cyan-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-white tracking-tight">
                Asynchronous Background Queue
              </h2>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-cyan-950 text-cyan-300 border border-cyan-700">
                Screen-Off Safe
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Run Batch Summaries, Document Analysis, and Security Audits Without Blocking UI
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          <button
            onClick={clearCompletedJobs}
            className="px-3 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:text-white border border-slate-700"
          >
            Clear Completed
          </button>
          <button
            onClick={() => setShowAddModal(true)}
            className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-bold text-black transition-transform hover:scale-[1.02]"
            style={{ backgroundColor: accentPalette.primary }}
          >
            <Plus className="w-4 h-4" />
            <span>Enqueue Job</span>
          </button>
        </div>
      </div>

      {/* Jobs List */}
      <div className="space-y-3">
        {backgroundJobs.map((job) => (
          <div
            key={job.id}
            className="p-4 rounded-2xl border space-y-2.5 shadow-md"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}80`,
            }}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                {job.status === JobStatus.COMPLETED ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                ) : job.status === JobStatus.RUNNING ? (
                  <div className="w-4 h-4 rounded-full border-2 border-cyan-400 border-t-transparent animate-spin" />
                ) : (
                  <Clock className="w-4 h-4 text-amber-400" />
                )}
                <h3 className="text-sm font-bold text-white">{job.title}</h3>
                <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-slate-300">
                  {job.priority} Priority
                </span>
              </div>

              <span
                className={`text-xs font-mono font-bold ${
                  job.status === JobStatus.COMPLETED
                    ? 'text-emerald-400'
                    : job.status === JobStatus.RUNNING
                    ? 'text-cyan-400'
                    : 'text-amber-400'
                }`}
              >
                {job.status} ({job.progressPercent}%)
              </span>
            </div>

            {/* Progress Bar */}
            {job.status === JobStatus.RUNNING && (
              <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
                <div
                  className="h-full bg-cyan-400 transition-all duration-300"
                  style={{ width: `${job.progressPercent}%` }}
                />
              </div>
            )}

            {job.resultOutput && (
              <div className="p-2.5 rounded-xl bg-slate-900 border border-slate-800 text-xs text-slate-300 font-mono">
                {job.resultOutput}
              </div>
            )}

            <div className="flex items-center justify-between pt-1 text-[11px] font-mono text-slate-400 border-t border-slate-800">
              <span>Model: {job.modelId}</span>
              <div className="flex items-center gap-2">
                {job.resultOutput && (
                  <button
                    onClick={() => onNavigateToExport(job.resultOutput!)}
                    className="flex items-center gap-1 text-cyan-400 hover:underline"
                  >
                    <Lock className="w-3 h-3" />
                    Vault Export
                  </button>
                )}
                {job.status === JobStatus.RUNNING && (
                  <button
                    onClick={() => cancelJob(job.id)}
                    className="text-red-400 hover:underline"
                  >
                    Cancel
                  </button>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Add Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-sm animate-in fade-in">
          <form
            onSubmit={handleAdd}
            className="w-full max-w-md rounded-2xl border p-6 space-y-4 shadow-2xl"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: accentPalette.borderDark,
            }}
          >
            <div className="flex justify-between items-center">
              <h3 className="text-base font-bold text-white">Enqueue Background Task</h3>
              <button
                type="button"
                onClick={() => setShowAddModal(false)}
                className="text-slate-400 hover:text-white"
              >
                ✕
              </button>
            </div>

            <div>
              <label className="text-xs text-slate-400 block mb-1">Task Title / Goal</label>
              <input
                type="text"
                required
                value={jobTitle}
                onChange={(e) => setJobTitle(e.target.value)}
                placeholder="e.g. Audit Repository Cryptography"
                className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2.5 text-xs text-white"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="text-xs text-slate-400 block mb-1">Task Type</label>
                <select
                  value={jobType}
                  onChange={(e) => setJobType(e.target.value as JobType)}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-xs text-white"
                >
                  <option value={JobType.BATCH_SUMMARIZATION}>Batch Summary</option>
                  <option value={JobType.CODE_AUDIT}>Code Audit</option>
                  <option value={JobType.DOCUMENT_ANALYSIS}>Doc Analysis</option>
                  <option value={JobType.EMBEDDING_INGESTION}>Embeddings</option>
                </select>
              </div>

              <div>
                <label className="text-xs text-slate-400 block mb-1">Priority</label>
                <select
                  value={jobPriority}
                  onChange={(e) => setJobPriority(e.target.value as any)}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-xs text-white"
                >
                  <option value="LOW">Low</option>
                  <option value="NORMAL">Normal</option>
                  <option value="HIGH">High</option>
                </select>
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowAddModal(false)}
                className="px-4 py-2 rounded-xl text-xs text-slate-400 hover:text-white"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-5 py-2 rounded-xl text-xs font-bold text-black"
                style={{ backgroundColor: accentPalette.primary }}
              >
                Enqueue & Start
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
