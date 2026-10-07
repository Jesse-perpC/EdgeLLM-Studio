import React, { useState } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { ImageTaskMode } from '../types';
import {
  Sparkles,
  Paintbrush,
  Maximize2,
  Trash2,
  Download,
  Sliders,
  Image as ImageIcon,
  RotateCcw,
  Zap,
} from 'lucide-react';
import { MaskPainterDialog } from '../components/MaskPainterDialog';

export const ImageTaskScreen: React.FC = () => {
  const {
    imageItems,
    isGeneratingImage,
    generateImage,
    upscaleImage,
    deleteImageItem,
    accentPalette,
  } = useEdgeLLM();

  const [prompt, setPrompt] = useState<string>(
    'Cybernetic neural processor chip with glowing neon cyan bus pathways, photorealistic 8k'
  );
  const [negativePrompt, setNegativePrompt] = useState<string>('low quality, blurry, deformed');
  const [mode, setMode] = useState<ImageTaskMode>(ImageTaskMode.TEXT_TO_IMAGE);
  const [modelName, setModelName] = useState<string>('Stable Diffusion 1.5 (Q4)');
  const [steps, setSteps] = useState<number>(20);
  const [cfgScale, setCfgScale] = useState<number>(7.5);
  const [showMaskDialog, setShowMaskDialog] = useState<boolean>(false);
  const [selectedImageForMask, setSelectedImageForMask] = useState<string | null>(null);

  const handleGenerate = async () => {
    if (!prompt.trim() || isGeneratingImage) return;
    await generateImage({
      prompt: prompt.trim(),
      negativePrompt: negativePrompt.trim(),
      mode,
      modelName,
      steps,
      cfgScale,
      seed: Math.floor(Math.random() * 888888),
    });
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
          <div className="p-3 rounded-xl bg-pink-500/20 shadow-lg border border-pink-500/30">
            <Sparkles className="w-6 h-6 text-pink-400" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-white tracking-tight">
                Stable Diffusion Studio (:ai_sd)
              </h2>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-pink-950 text-pink-300 border border-pink-700">
                On-Device
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Latent Diffusion Text-to-Image, Inpainting Mask Brush & 4× Super-Resolution
            </p>
          </div>
        </div>

        {/* Mode Selector */}
        <div className="flex items-center bg-slate-900 border border-slate-700/80 rounded-xl p-1 self-start sm:self-auto">
          {[
            { id: ImageTaskMode.TEXT_TO_IMAGE, label: 'Text2Img' },
            { id: ImageTaskMode.IMAGE_TO_IMAGE, label: 'Img2Img' },
            { id: ImageTaskMode.INPAINTING, label: 'Inpaint' },
          ].map((item) => (
            <button
              key={item.id}
              onClick={() => setMode(item.id)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                mode === item.id ? 'bg-pink-500 text-white shadow-md' : 'text-slate-400 hover:text-white'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      {/* Generator Prompt Box */}
      <div
        className="p-5 rounded-2xl border space-y-4 shadow-lg"
        style={{
          backgroundColor: accentPalette.surfaceDark,
          borderColor: `${accentPalette.borderDark}80`,
        }}
      >
        <div>
          <label className="text-xs font-mono uppercase tracking-wider text-slate-400 block mb-1">
            Prompt Description
          </label>
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            rows={2}
            placeholder="Describe visual scene to render on mobile GPU/NPU..."
            className="w-full bg-slate-900 border border-slate-700 rounded-xl p-3 text-xs sm:text-sm text-white focus:outline-none focus:border-pink-500"
          />
        </div>

        <div>
          <label className="text-xs font-mono uppercase tracking-wider text-slate-400 block mb-1">
            Negative Prompt
          </label>
          <input
            type="text"
            value={negativePrompt}
            onChange={(e) => setNegativePrompt(e.target.value)}
            className="w-full bg-slate-900 border border-slate-700 rounded-xl p-2.5 text-xs text-white"
          />
        </div>

        {/* Hyperparameters Strip */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-1">
          <div>
            <div className="flex justify-between text-[11px] font-mono text-slate-400 mb-1">
              <span>Model Architecture</span>
            </div>
            <select
              value={modelName}
              onChange={(e) => setModelName(e.target.value)}
              className="w-full bg-slate-900 border border-slate-700 rounded-lg p-2 text-xs text-white"
            >
              <option value="Stable Diffusion 1.5 (Q4)">SD 1.5 (Q4 MNN)</option>
              <option value="SD Turbo (1-Step Realtime)">SD Turbo (1-Step NPU)</option>
              <option value="Stable Diffusion XS (Edge)">SD-XS (Vulkan GPU)</option>
              <option value="LCM Latent Consistency 4-Step">LCM Dream (4-Step)</option>
            </select>
          </div>

          <div>
            <div className="flex justify-between text-[11px] font-mono text-slate-400 mb-1">
              <span>Sampling Steps: {steps}</span>
            </div>
            <input
              type="range"
              min="4"
              max="35"
              value={steps}
              onChange={(e) => setSteps(parseInt(e.target.value, 10))}
              className="w-full accent-pink-500"
            />
          </div>

          <div>
            <div className="flex justify-between text-[11px] font-mono text-slate-400 mb-1">
              <span>CFG Guidance: {cfgScale}</span>
            </div>
            <input
              type="range"
              min="1"
              max="15"
              step="0.5"
              value={cfgScale}
              onChange={(e) => setCfgScale(parseFloat(e.target.value))}
              className="w-full accent-pink-500"
            />
          </div>
        </div>

        {/* Inpaint Mask Brush Launch if Inpaint Mode */}
        {mode === ImageTaskMode.INPAINTING && (
          <div className="p-3.5 rounded-xl border border-pink-500/30 bg-pink-950/20 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Paintbrush className="w-4 h-4 text-pink-400" />
              <span className="text-xs text-pink-200">Interactive Canvas Mask Painter</span>
            </div>
            <button
              onClick={() => {
                setSelectedImageForMask(imageItems[0]?.imageUrl || null);
                setShowMaskDialog(true);
              }}
              className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-pink-500 text-white hover:bg-pink-400"
            >
              Open Mask Brush Canvas
            </button>
          </div>
        )}

        {/* Generate Action */}
        <div className="flex justify-end pt-2">
          <button
            onClick={handleGenerate}
            disabled={isGeneratingImage || !prompt.trim()}
            className="px-6 py-2.5 rounded-xl text-xs font-bold text-black flex items-center gap-2 transition-all disabled:opacity-50"
            style={{ backgroundColor: accentPalette.primary }}
          >
            <Sparkles className="w-4 h-4 fill-current" />
            <span>{isGeneratingImage ? 'Synthesizing Latents...' : 'Generate On-Device Image'}</span>
          </button>
        </div>
      </div>

      {/* Generated Gallery Feed */}
      <div>
        <div className="flex items-center justify-between mb-3 px-1">
          <h3 className="text-sm font-bold text-white uppercase tracking-wider font-mono">
            Local Generation Gallery ({imageItems.length})
          </h3>
          <span className="text-xs text-slate-400 font-mono">512×512 Standard / 4× Upscaled</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {imageItems.map((item) => (
            <div
              key={item.id}
              className="rounded-2xl border overflow-hidden shadow-lg flex flex-col justify-between"
              style={{
                backgroundColor: accentPalette.surfaceDark,
                borderColor: `${accentPalette.borderDark}80`,
              }}
            >
              <div className="relative aspect-square overflow-hidden bg-slate-950">
                <img
                  src={item.imageUrl}
                  alt={item.prompt}
                  className="w-full h-full object-cover transition-transform duration-300 hover:scale-105"
                />
                {item.isUpscaled && (
                  <span className="absolute top-2 left-2 text-[10px] font-mono px-2 py-0.5 rounded bg-black/75 text-emerald-400 border border-emerald-500/40">
                    4× ESRGAN Upscaled
                  </span>
                )}
              </div>

              <div className="p-4 space-y-2">
                <p className="text-xs text-white line-clamp-2 leading-relaxed">{item.prompt}</p>
                <div className="flex items-center justify-between text-[10px] font-mono text-slate-400 pt-1 border-t border-slate-800">
                  <span>{item.modelName}</span>
                  <span>{item.generationTimeMs}ms Latency</span>
                </div>

                <div className="flex items-center justify-between pt-1">
                  <div className="flex items-center gap-1.5">
                    {!item.isUpscaled && (
                      <button
                        onClick={() => upscaleImage(item.id)}
                        className="px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700 text-[11px] font-semibold text-slate-200 hover:text-white flex items-center gap-1"
                      >
                        <Maximize2 className="w-3 h-3 text-cyan-400" />
                        4× Upscale
                      </button>
                    )}
                    <button
                      onClick={() => {
                        setSelectedImageForMask(item.imageUrl);
                        setShowMaskDialog(true);
                      }}
                      className="px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700 text-[11px] font-semibold text-slate-200 hover:text-white flex items-center gap-1"
                    >
                      <Paintbrush className="w-3 h-3 text-pink-400" />
                      Inpaint Mask
                    </button>
                  </div>

                  <button
                    onClick={() => deleteImageItem(item.id)}
                    className="p-1.5 text-slate-500 hover:text-red-400 rounded-lg hover:bg-slate-800"
                    title="Delete image"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Mask Dialog */}
      <MaskPainterDialog
        isOpen={showMaskDialog}
        baseImageUrl={selectedImageForMask}
        onClose={() => setShowMaskDialog(false)}
        onConfirmMask={(mask) => {
          setMode(ImageTaskMode.INPAINTING);
          handleGenerate();
        }}
      />
    </div>
  );
};
