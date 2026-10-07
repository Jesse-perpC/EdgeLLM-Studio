import React from 'react';
import { useEdgeLLM } from '../context/EdgeLLMContext';
import { SubscriptionTier } from '../types';
import { X, CreditCard, Sparkles, Check, RefreshCw } from 'lucide-react';

interface BillingAndAllocationsSheetProps {
  isOpen: boolean;
  onClose: () => void;
}

export const BillingAndAllocationsSheet: React.FC<BillingAndAllocationsSheetProps> = ({
  isOpen,
  onClose,
}) => {
  const { userProfile, upgradeSubscription, resetAllocations, accentPalette } = useEdgeLLM();

  if (!isOpen) return null;

  const isPro = userProfile.tier === SubscriptionTier.PRO_CREATOR;
  const usagePercent = Math.min(
    100,
    Math.round((userProfile.tokensUsedThisMonth / userProfile.monthlyTokensAllocated) * 100)
  );

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
            <div className="p-2 rounded-xl bg-amber-500/15">
              <CreditCard className="w-5 h-5 text-amber-400" />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">Plan & Monthly Allocations</h2>
              <p className="text-xs text-slate-400">Manage Local Token Quotas & Pro Tier</p>
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
          {/* Token Usage Meter */}
          <div
            className="p-4 rounded-xl border space-y-3"
            style={{
              backgroundColor: accentPalette.surfaceDark,
              borderColor: `${accentPalette.borderDark}60`,
            }}
          >
            <div className="flex justify-between items-center text-xs">
              <span className="text-slate-400 font-mono">Monthly Compute Allocation</span>
              <span className="font-bold text-white font-mono">
                {userProfile.tokensUsedThisMonth.toLocaleString()} /{' '}
                {userProfile.monthlyTokensAllocated.toLocaleString()} Tokens
              </span>
            </div>

            <div className="w-full h-2.5 bg-slate-800 rounded-full overflow-hidden">
              <div
                className="h-full rounded-full transition-all duration-500"
                style={{
                  width: `${usagePercent}%`,
                  backgroundColor: usagePercent > 85 ? '#EF4444' : accentPalette.primary,
                }}
              />
            </div>

            <div className="flex justify-between items-center text-[10px] text-slate-500 font-mono">
              <span>Quota resets on {userProfile.billingCycleResetDate}</span>
              <button
                onClick={resetAllocations}
                className="flex items-center gap-1 text-cyan-400 hover:underline"
              >
                <RefreshCw className="w-3 h-3" />
                Reset Counter
              </button>
            </div>
          </div>

          {/* Tier Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {/* Free Starter */}
            <div
              className={`p-4 rounded-xl border flex flex-col justify-between ${
                !isPro ? 'border-cyan-500 bg-cyan-950/20' : 'border-slate-800 bg-slate-900/40'
              }`}
            >
              <div>
                <div className="text-xs font-bold text-white">Free Starter</div>
                <div className="text-xl font-extrabold text-white mt-1">$0 / mo</div>
                <ul className="text-[11px] text-slate-300 space-y-1.5 mt-3">
                  <li className="flex items-center gap-1.5">
                    <Check className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />
                    50,000 Local Tokens / mo
                  </li>
                  <li className="flex items-center gap-1.5">
                    <Check className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />
                    All 6 On-Device Engines
                  </li>
                  <li className="flex items-center gap-1.5">
                    <Check className="w-3.5 h-3.5 text-emerald-400 flex-shrink-0" />
                    100% Air-Gapped Zero-Egress
                  </li>
                </ul>
              </div>

              {!isPro ? (
                <div className="mt-4 py-1.5 px-3 rounded-lg text-center text-xs font-bold text-cyan-400 bg-cyan-500/10 border border-cyan-500/30">
                  Current Plan
                </div>
              ) : (
                <button
                  onClick={() => upgradeSubscription(SubscriptionTier.FREE_STARTER)}
                  className="mt-4 py-1.5 px-3 rounded-lg text-xs font-semibold text-slate-400 border border-slate-700 hover:text-white"
                >
                  Downgrade
                </button>
              )}
            </div>

            {/* Pro Creator */}
            <div
              className={`p-4 rounded-xl border flex flex-col justify-between ${
                isPro ? 'border-amber-500 bg-amber-950/20' : 'border-slate-800 bg-slate-900/40'
              }`}
            >
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-amber-400 flex items-center gap-1">
                    <Sparkles className="w-3.5 h-3.5" />
                    Pro Creator
                  </span>
                  <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-300">
                    POPULAR
                  </span>
                </div>
                <div className="text-xl font-extrabold text-white mt-1">
                  $19 <span className="text-xs text-slate-400 font-normal">/ mo</span>
                </div>
                <ul className="text-[11px] text-slate-300 space-y-1.5 mt-3">
                  <li className="flex items-center gap-1.5">
                    <Check className="w-3.5 h-3.5 text-amber-400 flex-shrink-0" />
                    200,000 Monthly Tokens
                  </li>
                  <li className="flex items-center gap-1.5">
                    <Check className="w-3.5 h-3.5 text-amber-400 flex-shrink-0" />
                    Gemini Cloud Assist Bridge
                  </li>
                  <li className="flex items-center gap-1.5">
                    <Check className="w-3.5 h-3.5 text-amber-400 flex-shrink-0" />
                    Unlimited RAG Document Embeddings
                  </li>
                  <li className="flex items-center gap-1.5">
                    <Check className="w-3.5 h-3.5 text-amber-400 flex-shrink-0" />
                    Priority Vulkan & NPU Shaders
                  </li>
                </ul>
              </div>

              {isPro ? (
                <div className="mt-4 py-1.5 px-3 rounded-lg text-center text-xs font-bold text-amber-400 bg-amber-500/10 border border-amber-500/30">
                  Active Pro
                </div>
              ) : (
                <button
                  onClick={() => upgradeSubscription(SubscriptionTier.PRO_CREATOR)}
                  className="mt-4 py-1.5 px-3 rounded-lg text-xs font-bold text-black bg-amber-400 hover:bg-amber-300 transition-colors"
                >
                  Upgrade to Pro
                </button>
              )}
            </div>
          </div>
        </div>

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
