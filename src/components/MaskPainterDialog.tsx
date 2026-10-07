import React, { useRef, useState, useEffect } from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { X, Check, Eraser, Paintbrush, RotateCcw } from 'lucide-react';

interface MaskPainterDialogProps {
  isOpen: boolean;
  baseImageUrl?: string | null;
  onClose: () => void;
  onConfirmMask: (maskBase64: string) => void;
}

export const MaskPainterDialog: React.FC<MaskPainterDialogProps> = ({
  isOpen,
  baseImageUrl,
  onClose,
  onConfirmMask,
}) => {
  const { accentPalette } = useEdgeLLM();
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [isDrawing, setIsDrawing] = useState<boolean>(false);
  const [brushSize, setBrushSize] = useState<number>(24);
  const [isEraser, setIsEraser] = useState<boolean>(false);

  useEffect(() => {
    if (!isOpen) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Reset canvas to transparent or blank
    ctx.clearRect(0, 0, canvas.width, canvas.height);
  }, [isOpen]);

  if (!isOpen) return null;

  const startDrawing = (e: React.MouseEvent<HTMLCanvasElement> | React.TouchEvent<HTMLCanvasElement>) => {
    setIsDrawing(true);
    draw(e);
  };

  const stopDrawing = () => {
    setIsDrawing(false);
    const canvas = canvasRef.current;
    if (canvas) {
      const ctx = canvas.getContext('2d');
      ctx?.beginPath();
    }
  };

  const draw = (e: React.MouseEvent<HTMLCanvasElement> | React.TouchEvent<HTMLCanvasElement>) => {
    if (!isDrawing) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.getBoundingClientRect();
    let clientX = 0;
    let clientY = 0;

    if ('touches' in e && e.touches.length > 0) {
      clientX = e.touches[0].clientX;
      clientY = e.touches[0].clientY;
    } else if ('clientX' in e) {
      clientX = e.clientX;
      clientY = e.clientY;
    }

    const x = ((clientX - rect.left) / rect.width) * canvas.width;
    const y = ((clientY - rect.top) / rect.height) * canvas.height;

    ctx.lineWidth = brushSize;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    if (isEraser) {
      ctx.globalCompositeOperation = 'destination-out';
      ctx.strokeStyle = 'rgba(0,0,0,1)';
    } else {
      ctx.globalCompositeOperation = 'source-over';
      ctx.strokeStyle = 'rgba(255, 42, 109, 0.75)'; // High-visibility pink/magenta mask
    }

    ctx.lineTo(x, y);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(x, y);
  };

  const handleClear = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    ctx?.clearRect(0, 0, canvas.width, canvas.height);
  };

  const handleSave = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const dataUrl = canvas.toDataURL('image/png');
    onConfirmMask(dataUrl);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-sm animate-in fade-in duration-150">
      <div
        className="w-full max-w-lg rounded-2xl border shadow-2xl overflow-hidden flex flex-col max-h-[95vh]"
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
          <div>
            <h2 className="text-base font-bold text-white flex items-center gap-2">
              <Paintbrush className="w-4 h-4 text-pink-400" />
              Inpainting Touch Mask Brush
            </h2>
            <p className="text-xs text-slate-400">Paint mask over elements you wish to replace or inpaint</p>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body Canvas */}
        <div className="p-6 flex flex-col items-center space-y-4">
          <div className="relative w-72 h-72 sm:w-80 sm:h-80 rounded-xl overflow-hidden border border-slate-700 bg-slate-950 shadow-inner flex items-center justify-center">
            {baseImageUrl ? (
              <img
                src={baseImageUrl}
                alt="Base for inpainting"
                className="absolute inset-0 w-full h-full object-cover select-none pointer-events-none"
              />
            ) : (
              <div className="absolute inset-0 flex items-center justify-center text-xs text-slate-600 font-mono">
                [Canvas Drawing Plane: 512×512]
              </div>
            )}

            <canvas
              ref={canvasRef}
              width={512}
              height={512}
              onMouseDown={startDrawing}
              onMouseUp={stopDrawing}
              onMouseLeave={stopDrawing}
              onMouseMove={draw}
              onTouchStart={startDrawing}
              onTouchEnd={stopDrawing}
              onTouchMove={draw}
              className="absolute inset-0 w-full h-full cursor-crosshair touch-none"
            />
          </div>

          {/* Tools & Brush Controls */}
          <div className="w-full space-y-3">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setIsEraser(false)}
                  className={`p-2 rounded-lg border text-xs flex items-center gap-1.5 font-semibold ${
                    !isEraser
                      ? 'bg-pink-600/30 border-pink-500 text-pink-300'
                      : 'bg-slate-800 border-slate-700 text-slate-400'
                  }`}
                >
                  <Paintbrush className="w-3.5 h-3.5" />
                  Mask Brush
                </button>
                <button
                  onClick={() => setIsEraser(true)}
                  className={`p-2 rounded-lg border text-xs flex items-center gap-1.5 font-semibold ${
                    isEraser
                      ? 'bg-slate-700 border-slate-400 text-white'
                      : 'bg-slate-800 border-slate-700 text-slate-400'
                  }`}
                >
                  <Eraser className="w-3.5 h-3.5" />
                  Eraser
                </button>
              </div>

              <button
                onClick={handleClear}
                className="p-2 rounded-lg bg-slate-800 border border-slate-700 text-xs text-slate-300 hover:text-white flex items-center gap-1.5"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                Clear
              </button>
            </div>

            <div>
              <div className="flex justify-between text-[11px] font-mono text-slate-400 mb-1">
                <span>Brush Size</span>
                <span>{brushSize}px</span>
              </div>
              <input
                type="range"
                min="8"
                max="64"
                value={brushSize}
                onChange={(e) => setBrushSize(parseInt(e.target.value, 10))}
                className="w-full"
              />
            </div>
          </div>
        </div>

        {/* Footer */}
        <div
          className="p-4 border-t flex justify-end gap-2"
          style={{
            backgroundColor: accentPalette.surfaceDark,
            borderColor: `${accentPalette.borderDark}80`,
          }}
        >
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:text-white"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            className="px-5 py-2 rounded-xl text-xs font-semibold text-black flex items-center gap-1.5"
            style={{ backgroundColor: accentPalette.primary }}
          >
            <Check className="w-4 h-4" />
            Apply Mask & Inpaint
          </button>
        </div>
      </div>
    </div>
  );
};
