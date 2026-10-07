import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import {
  Server,
  Play,
  Square,
  Copy,
  RotateCw,
  Send,
  Terminal,
  Activity,
  CheckCircle2,
  Lock,
} from 'lucide-react';

export const ApiServerScreen: React.FC = () => {
  const {
    apiConfig,
    apiStats,
    apiLogs,
    toggleApiServer,
    rotateApiToken,
    updateApiPort,
    testApiCall,
    accentPalette,
  } = useEdgeLLM();

  const [copiedToken, setCopiedToken] = useState<boolean>(false);
  const [testPrompt, setTestPrompt] = useState<string>('What are objects in Java?');
  const [testOutput, setTestOutput] = useState<string>('');
  const [isTesting, setIsTesting] = useState<boolean>(false);

  const copyToken = () => {
    navigator.clipboard.writeText(apiConfig.authToken);
    setCopiedToken(true);
    setTimeout(() => setCopiedToken(false), 2000);
  };

  const handleTest = async () => {
    if (!testPrompt.trim() || isTesting) return;
    setIsTesting(true);
    const resp = await testApiCall(testPrompt.trim());
    setTestOutput(resp);
    setIsTesting(false);
  };

  const curlSnippet = `curl -X POST http://127.0.0.1:${apiConfig.port}/v1/chat/completions \\
  -H "Authorization: Bearer ${apiConfig.authToken}" \\
  -H "Content-Type: application/json" \\
  -d '{"messages": [{"role": "user", "content": "${testPrompt.slice(0, 30)}..."}]}'`;

  return (
    <div className="max-w-4xl mx-auto px-4 py-5 space-y-6 pb-24">
      {/* Header Card */}
      <div
        className="p-5 rounded-2xl border flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-xl"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}90`,
        }}
      >
        <div className="flex items-center gap-3">
          <div className="p-3 rounded-xl bg-amber-500/20 shadow-lg border border-amber-500/30">
            <Server className="w-6 h-6 text-amber-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-white tracking-tight">
                OpenAI & Ollama Local HTTP Server
              </h2>
              <span
                className={`text-[10px] font-mono px-2 py-0.5 rounded border ${
                  apiStats.isRunning
                    ? 'bg-emerald-950 text-emerald-300 border-emerald-700'
                    : 'bg-slate-800 text-slate-400 border-slate-700'
                }`}
              >
                {apiStats.isRunning ? `PORT ${apiConfig.port} ACTIVE` : 'STOPPED'}
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Connect External Web Apps, IDEs, or Python Scripts Directly to Your On-Device Model
            </p>
          </div>
        </div>

        <button
          onClick={toggleApiServer}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all ${
            apiStats.isRunning
              ? 'bg-red-600 hover:bg-red-500 text-white'
              : 'bg-emerald-500 hover:bg-emerald-400 text-black'
          }`}
        >
          {apiStats.isRunning ? <Square className="w-3.5 h-3.5 fill-current" /> : <Play className="w-3.5 h-3.5 fill-current" />}
          <span>{apiStats.isRunning ? 'Stop Daemon' : 'Start Daemon'}</span>
        </button>
      </div>

      {/* Security & Config */}
      <div
        className="p-5 rounded-2xl border space-y-4 shadow-lg"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}80`,
        }}
      >
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {/* Bearer Token */}
          <div>
            <div className="flex justify-between items-center text-xs font-mono text-slate-400 mb-1">
              <span className="flex items-center gap-1.5">
                <Lock className="w-3.5 h-3.5 text-amber-400" />
                Bearer Auth API Key:
              </span>
              <button
                onClick={rotateApiToken}
                className="text-cyan-400 hover:underline flex items-center gap-1 text-[11px]"
              >
                <RotateCw className="w-3 h-3" />
                Rotate Key
              </button>
            </div>
            <div className="flex items-center gap-2 bg-slate-900 border border-slate-700 rounded-xl px-3 py-2 font-mono text-xs text-slate-200">
              <span className="truncate flex-1">{apiConfig.authToken}</span>
              <button
                onClick={copyToken}
                className="p-1 rounded hover:bg-slate-800 text-slate-400 hover:text-white"
                title="Copy token"
              >
                {copiedToken ? <CheckCircle2 className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {/* Port Config */}
          <div>
            <label className="text-xs font-mono text-slate-400 block mb-1">Port Selection</label>
            <div className="flex gap-2">
              {[8080, 11434].map((p) => (
                <button
                  key={p}
                  onClick={() => updateApiPort(p)}
                  className={`flex-1 py-2 rounded-xl border text-xs font-mono font-bold transition-all ${
                    apiConfig.port === p
                      ? 'bg-amber-500/20 border-amber-500 text-amber-300'
                      : 'bg-slate-900 border-slate-700 text-slate-400 hover:text-white'
                  }`}
                >
                  Port {p} {p === 8080 ? '(OpenAI standard)' : '(Ollama port)'}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Server Metrics Strip */}
        <div className="grid grid-cols-3 gap-3 pt-2 text-center border-t border-slate-800">
          <div>
            <div className="text-[10px] font-mono text-slate-400 uppercase">Requests Served</div>
            <div className="text-sm font-bold text-white mt-0.5">{apiStats.totalRequestsServed}</div>
          </div>
          <div>
            <div className="text-[10px] font-mono text-slate-400 uppercase">Tokens Generated</div>
            <div className="text-sm font-bold text-cyan-400 mt-0.5">
              {apiStats.totalTokensGenerated.toLocaleString()}
            </div>
          </div>
          <div>
            <div className="text-[10px] font-mono text-slate-400 uppercase">Average Latency</div>
            <div className="text-sm font-bold text-emerald-400 mt-0.5">
              {apiStats.averageLatencyMs} ms
            </div>
          </div>
        </div>
      </div>

      {/* Interactive Curl & Test Console */}
      <div
        className="p-5 rounded-2xl border space-y-4 shadow-lg"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}80`,
        }}
      >
        <div className="flex items-center gap-2">
          <Terminal className="w-4 h-4 text-cyan-400" />
          <h3 className="text-sm font-bold text-white font-mono uppercase">
            In-Browser Daemon Testing Console
          </h3>
        </div>

        <div className="bg-slate-950 p-3 rounded-xl border border-slate-800 font-mono text-[11px] text-cyan-300/80 overflow-x-auto whitespace-pre">
          {curlSnippet}
        </div>

        <div className="flex gap-2">
          <input
            type="text"
            value={testPrompt}
            onChange={(e) => setTestPrompt(e.target.value)}
            placeholder="Test prompt through HTTP daemon..."
            className="flex-1 bg-slate-900 border border-slate-700 rounded-xl px-3 py-2 text-xs text-white focus:outline-none"
          />
          <button
            onClick={handleTest}
            disabled={isTesting || !testPrompt.trim()}
            className="px-4 py-2 rounded-xl text-xs font-bold text-black flex items-center gap-1.5 transition-all disabled:opacity-50"
            style={{ backgroundColor: accentPalette.primary }}
          >
            <Send className="w-3.5 h-3.5" />
            <span>{isTesting ? 'Dispatching...' : 'Dispatch POST'}</span>
          </button>
        </div>

        {testOutput && (
          <div className="p-3 rounded-xl bg-slate-900 border border-slate-700 space-y-1 font-mono text-xs">
            <div className="text-slate-400 text-[10px]">HTTP 200 OK Response:</div>
            <div className="text-white whitespace-pre-wrap">{testOutput}</div>
          </div>
        )}
      </div>

      {/* Live Request Audit Logs */}
      <div>
        <div className="flex items-center justify-between mb-3 px-1">
          <h3 className="text-sm font-bold text-white uppercase tracking-wider font-mono">
            Request Audit Logger ({apiLogs.length})
          </h3>
          <span className="text-xs text-slate-400 font-mono">Live Telemetry</span>
        </div>

        <div className="space-y-2">
          {apiLogs.map((log) => (
            <div
              key={log.id}
              className="p-3 rounded-xl border flex items-center justify-between text-xs font-mono"
              style={{
                backgroundColor: accentPalette.surfaceDark,
                borderColor: `${accentPalette.borderDark}60`,
              }}
            >
              <div className="flex items-center gap-3">
                <span className="px-1.5 py-0.5 rounded bg-emerald-950 text-emerald-400 border border-emerald-800 text-[10px] font-bold">
                  {log.statusCode}
                </span>
                <span className="text-white font-bold">{log.method}</span>
                <span className="text-slate-400">{log.path}</span>
                <span className="text-slate-500 hidden sm:inline">"{log.promptPreview}"</span>
              </div>
              <div className="text-slate-400 text-[11px]">
                {log.tokensGenerated} tokens • {log.durationMs}ms
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
