import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import {
  Puzzle,
  Shield,
  Activity,
  FileText,
  Code,
  Play,
  CheckCircle2,
  Lock,
  Layers,
  Terminal,
} from 'lucide-react';

interface PluginPipelineScreenProps {
  onNavigateToExport: (text: string) => void;
}

export const PluginPipelineScreen: React.FC<PluginPipelineScreenProps> = ({
  onNavigateToExport,
}) => {
  const { plugins, togglePlugin, executePlugin, mcpServers, toggleMcpServer, accentPalette } =
    useEdgeLLM();

  const [activePluginId, setActivePluginId] = useState<string>(plugins[0]?.id || 'plugin_pii_redactor');
  const [testInput, setTestInput] = useState<string>(
    'Contact admin at security@edgellm.local or phone (555) 234-5678 from IP 192.168.1.42 with private token.'
  );
  const [outputReport, setOutputReport] = useState<string>('');
  const [isExecuting, setIsExecuting] = useState<boolean>(false);

  const handleExecute = async () => {
    if (!testInput.trim() || isExecuting) return;
    setIsExecuting(true);
    const res = await executePlugin(activePluginId, testInput.trim());
    setOutputReport(res.processedOutput);
    setIsExecuting(false);
  };

  const getIcon = (key: string) => {
    switch (key) {
      case 'Shield':
        return Shield;
      case 'Activity':
        return Activity;
      case 'FileText':
        return FileText;
      case 'Code':
        return Code;
      default:
        return Puzzle;
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
          <div className="p-3 rounded-xl bg-purple-500/20 shadow-lg border border-purple-500/30">
            <Puzzle className="w-6 h-6 text-purple-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-white tracking-tight">
                Sandboxed Plugins & MCP Ecosystem
              </h2>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-purple-950 text-purple-300 border border-purple-700">
                Local-Only
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              PII Sanitizer, Threat Scanners & Model Context Protocol (MCP) Daemon Tools
            </p>
          </div>
        </div>
      </div>

      {/* Plugins Grid */}
      <div className="space-y-3">
        <div className="text-xs font-mono uppercase tracking-wider text-slate-400 px-1">
          Installed Edge Plugins
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
          {plugins.map((plugin) => {
            const Icon = getIcon(plugin.iconKey);
            const isSelected = activePluginId === plugin.id;

            return (
              <div
                key={plugin.id}
                onClick={() => setActivePluginId(plugin.id)}
                className="p-4 rounded-2xl border cursor-pointer transition-all flex flex-col justify-between space-y-3 shadow-md"
                style={{
                  backgroundColor: isSelected
                    ? `${accentPalette.primary}12`
                    : accentPalette.surfaceDark,
                  borderColor: isSelected ? accentPalette.primary : `${accentPalette.borderDark}80`,
                }}
              >
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <div
                        className="p-2 rounded-xl"
                        style={{ backgroundColor: `${accentPalette.primary}20` }}
                      >
                        <Icon className="w-4 h-4" style={{ color: accentPalette.primary }} />
                      </div>
                      <div>
                        <h4 className="text-sm font-bold text-white">{plugin.name}</h4>
                        <span className="text-[10px] font-mono text-slate-400">
                          {plugin.category} • v{plugin.version}
                        </span>
                      </div>
                    </div>

                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        togglePlugin(plugin.id);
                      }}
                      className={`w-10 h-5 flex items-center rounded-full p-0.5 transition-colors ${
                        plugin.isEnabled ? 'bg-cyan-500 justify-end' : 'bg-slate-700 justify-start'
                      }`}
                    >
                      <div className="bg-white w-4 h-4 rounded-full shadow-md" />
                    </button>
                  </div>

                  <p className="text-xs text-slate-300 leading-relaxed">{plugin.description}</p>
                </div>

                <div className="flex items-center justify-between text-[11px] font-mono text-slate-400 pt-2 border-t border-slate-800">
                  <span className="text-emerald-400">100% On-Device</span>
                  <span className={isSelected ? 'text-cyan-400 font-bold' : ''}>
                    {isSelected ? 'Selected for Test' : 'Click to Select'}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Interactive Plugin Execution Sandbox */}
      <div
        className="p-5 rounded-2xl border space-y-4 shadow-lg"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}80`,
        }}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Terminal className="w-4 h-4 text-cyan-400" />
            <h3 className="text-sm font-bold text-white font-mono uppercase">
              Plugin Execution Sandbox
            </h3>
          </div>
          <span className="text-xs text-slate-400 font-mono">
            Active: {plugins.find((p) => p.id === activePluginId)?.name}
          </span>
        </div>

        <div>
          <label className="text-xs text-slate-400 block mb-1">Input Payload</label>
          <textarea
            value={testInput}
            onChange={(e) => setTestInput(e.target.value)}
            rows={3}
            className="w-full bg-slate-900 border border-slate-700 rounded-xl p-3 text-xs text-white font-mono focus:outline-none"
          />
        </div>

        <div className="flex justify-end gap-2">
          <button
            onClick={handleExecute}
            disabled={isExecuting || !testInput.trim()}
            className="px-5 py-2 rounded-xl text-xs font-bold text-black flex items-center gap-1.5 transition-all disabled:opacity-50"
            style={{ backgroundColor: accentPalette.primary }}
          >
            <Play className="w-3.5 h-3.5 fill-current" />
            <span>{isExecuting ? 'Running in Sandbox...' : 'Run Local Plugin'}</span>
          </button>
        </div>

        {outputReport && (
          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 font-mono text-xs text-slate-200 whitespace-pre-wrap leading-relaxed space-y-2">
            <div className="flex justify-between items-center text-slate-400 text-[10px] border-b border-slate-800 pb-1">
              <span>Sanitized / Processed Result:</span>
              <button
                onClick={() => onNavigateToExport(outputReport)}
                className="text-cyan-400 hover:underline flex items-center gap-1"
              >
                <Lock className="w-3 h-3" />
                Vault Export
              </button>
            </div>
            <div>{outputReport}</div>
          </div>
        )}
      </div>

      {/* Model Context Protocol (MCP) Servers */}
      <div>
        <div className="flex items-center justify-between mb-3 px-1">
          <h3 className="text-sm font-bold text-white uppercase tracking-wider font-mono">
            Model Context Protocol (MCP) Servers
          </h3>
          <span className="text-xs text-slate-400 font-mono">Stdio / SSE</span>
        </div>

        <div className="space-y-2.5">
          {mcpServers.map((server) => (
            <div
              key={server.id}
              className="p-3.5 rounded-xl border flex items-center justify-between text-xs font-mono"
              style={{
                backgroundColor: accentPalette.surfaceDark,
                borderColor: `${accentPalette.borderDark}60`,
              }}
            >
              <div className="flex items-center gap-3">
                <span
                  className={`w-2.5 h-2.5 rounded-full ${
                    server.isConnected ? 'bg-emerald-400' : 'bg-slate-600'
                  }`}
                />
                <div>
                  <div className="font-bold text-white">{server.name}</div>
                  <div className="text-[11px] text-slate-400 mt-0.5">
                    {server.endpointUrl} • {server.toolsCount} Tools Available
                  </div>
                </div>
              </div>

              <button
                onClick={() => toggleMcpServer(server.id)}
                className={`px-3 py-1 rounded-lg border text-xs font-bold transition-all ${
                  server.isConnected
                    ? 'bg-emerald-950 text-emerald-300 border-emerald-700'
                    : 'bg-slate-800 text-slate-400 border-slate-700'
                }`}
              >
                {server.isConnected ? 'Connected' : 'Connect'}
              </button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
