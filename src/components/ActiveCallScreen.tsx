import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';

export interface ActiveCallScreenProps {
  name?: string;
  timerSeconds?: number;
  isMuted?: boolean;
  onToggleMute?: () => void;
  onEndCall?: () => void;
  onOpenKeypad?: () => void;
  onMinimize?: () => void;
}

export function formatCallTimer(totalSeconds: number): string {
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
}

export const ActiveCallScreen: React.FC<ActiveCallScreenProps> = ({
  name = "Ervin Smith",
  timerSeconds = 739, // 12:19 default
  isMuted = false,
  onToggleMute,
  onEndCall,
  onOpenKeypad,
  onMinimize,
}) => {
  const [showInternalKeypad, setShowInternalKeypad] = useState(false);
  const [dialedNumbers, setDialedNumbers] = useState("");

  const handleKeypadPress = (digit: string) => {
    setDialedNumbers((prev) => (prev + digit).slice(-12));
  };

  const handleKeypadClick = () => {
    if (onOpenKeypad) {
      onOpenKeypad();
    } else {
      setShowInternalKeypad(true);
    }
  };

  return (
    <div className="relative w-full h-full bg-[#F5F5F7] flex flex-col justify-between overflow-hidden select-none font-[-apple-system,BlinkMacSystemFont,'SF_Pro_Display','SF_Pro_Text','SF_Pro','Inter',sans-serif]">
      {/* Top Status Bar & Dynamic Island */}
      <div className="relative z-30 pt-3 px-7 flex items-center justify-between text-[#1C2B33]">
        {/* Time */}
        <span className="text-[16px] font-semibold tracking-tight">9:41</span>

        {/* Dynamic Island */}
        <div 
          onClick={onMinimize}
          title="Tap to minimize"
          className="absolute left-1/2 -translate-x-1/2 top-3 w-[124px] h-[35px] bg-black rounded-full flex items-center justify-between px-3 cursor-pointer shadow-sm hover:scale-[1.02] transition-transform"
        >
          {/* Subtle sensor camera on right */}
          <div className="w-2 h-2 rounded-full bg-[#1A1A1A] ml-auto mr-1 flex items-center justify-center">
            <div className="w-1 h-1 rounded-full bg-[#080820]" />
          </div>
        </div>

        {/* Right Status Icons */}
        <div className="flex items-center space-x-1.5">
          {/* Signal bars */}
          <svg className="w-4 h-3.5 fill-[#1C2B33]" viewBox="0 0 17 12">
            <rect x="0" y="9" width="3" height="3" rx="0.5" />
            <rect x="4.5" y="6" width="3" height="6" rx="0.5" />
            <rect x="9" y="3" width="3" height="9" rx="0.5" />
            <rect x="13.5" y="0" width="3" height="12" rx="0.5" />
          </svg>
          {/* Wi-Fi */}
          <svg className="w-4 h-3.5 fill-[#1C2B33]" viewBox="0 0 16 12">
            <path d="M8 9.6a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0-3.2zM3.4 7a6.5 6.5 0 0 1 9.2 0 .9.9 0 0 0 1.2-1.3 8.3 8.3 0 0 0-11.6 0 .9.9 0 0 0 1.2 1.3zm-2.6-3a10.2 10.2 0 0 1 14.4 0 .9.9 0 0 0 1.2-1.3 12 12 0 0 0-16.8 0 .9.9 0 0 0 1.2 1.3z" />
          </svg>
          {/* Battery */}
          <div className="w-6 h-3 rounded-[4px] border border-[#1C2B33] p-[1.5px] flex items-center">
            <div className="w-full h-full bg-[#1C2B33] rounded-[2px]" />
          </div>
        </div>
      </div>

      {/* Main Soft Gradient Card */}
      <div className="relative mx-6 mt-4 mb-3 flex-1 flex flex-col items-center justify-between">
        {/* Feathered outer ambient glow matching reference image */}
        <div 
          className="absolute -inset-2 rounded-[52px] pointer-events-none opacity-80 blur-[26px]"
          style={{
            background: 'linear-gradient(180deg, #8FDDFB 0%, #4FB4F2 28%, #1E6FE0 65%, #0A2A9E 100%)',
          }}
        />

        {/* The Card with soft feathered borders and heavy blur */}
        <div className="relative w-full h-full rounded-[44px] overflow-hidden flex flex-col items-center justify-between py-10 shadow-lg">
          {/* Moving background gradient layers */}
          <div 
            className="absolute inset-0 w-full h-full animate-gradient-drift pointer-events-none"
            style={{
              background: 'linear-gradient(180deg, #8FDDFB 0%, #4FB4F2 30%, #1E6FE0 65%, #0A2A9E 100%)',
              filter: 'blur(36px)',
              transformOrigin: 'center center',
            }}
          />

          {/* Secondary depth layer to create the dreamy melting look */}
          <div 
            className="absolute inset-0 w-full h-full pointer-events-none opacity-85"
            style={{
              background: 'radial-gradient(ellipse 90% 60% at 50% 18%, rgba(143, 221, 251, 0.9) 0%, transparent 75%), radial-gradient(ellipse 100% 80% at 50% 90%, rgba(10, 42, 158, 0.95) 0%, transparent 80%)',
              filter: 'blur(20px)',
            }}
          />

          {/* Top text block: Contact Name & Call Timer */}
          <div className="relative z-10 flex flex-col items-center text-center mt-2 px-4">
            <h1 className="text-[34px] font-bold text-[#1C2B33] tracking-[-0.5px] leading-tight">
              {name}
            </h1>
            <p className="text-[32px] font-normal text-[#4A6A7C]/75 mt-1 tracking-tight tabular-nums">
              {formatCallTimer(timerSeconds)}
            </p>
          </div>

          {/* Lower Third: Glowing Animated Orb */}
          <div className="relative z-10 w-full flex items-center justify-center mt-auto mb-10">
            {/* Outer soft halo */}
            <div 
              className="absolute w-[180px] h-[180px] rounded-full pointer-events-none opacity-60 blur-[38px]"
              style={{
                background: 'radial-gradient(circle, #19D3F0 0%, #1456D8 65%, transparent 100%)',
              }}
            />

            {/* Glowing Orb with gentle breathing pulse */}
            <div 
              className="relative w-[130px] h-[130px] rounded-full animate-orb-breathe shadow-2xl flex items-center justify-center"
              style={{
                background: 'radial-gradient(circle at 40% 35%, #58E4F7 0%, #19D3F0 32%, #1456D8 78%, #0C369B 100%)',
                boxShadow: '0 0 35px rgba(25, 211, 240, 0.65), 0 0 70px rgba(20, 86, 216, 0.45)',
              }}
            >
              {/* Inner ambient light sheen */}
              <div 
                className="w-[84px] h-[84px] rounded-full opacity-70 blur-[8px]"
                style={{
                  background: 'radial-gradient(circle at 35% 30%, #FFFFFF 0%, #3BE2F9 50%, transparent 80%)',
                }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Control Bar Container */}
      <div className="relative z-20 mx-6 mb-7">
        <div className="w-full bg-[#EAEAEC] rounded-[52px] p-2.5 flex items-center justify-between shadow-sm">
          {/* 1. Mute Button: 76px circle, #E1E1E4, mic-off icon in #6B6B70 */}
          <button
            type="button"
            onClick={onToggleMute}
            aria-label={isMuted ? "Unmute microphone" : "Mute microphone"}
            className={`w-[76px] h-[76px] rounded-full flex items-center justify-center transition-all duration-100 active:scale-[0.94] cursor-pointer ${
              isMuted ? 'bg-[#1C1C1E] text-white shadow-md' : 'bg-[#E1E1E4] text-[#6B6B70] hover:bg-[#DCDCE0]'
            }`}
          >
            <svg 
              className="w-8 h-8" 
              viewBox="0 0 24 24" 
              fill="none" 
              stroke="currentColor" 
              strokeWidth="2.2" 
              strokeLinecap="round" 
              strokeLinejoin="round"
            >
              <line x1="2" y1="2" x2="22" y2="22" />
              <path d="M18.89 13.23A7.12 7.12 0 0 0 19 12v-2" />
              <path d="M5 10v2a7 7 0 0 0 10.15 6.27" />
              <line x1="12" y1="19" x2="12" y2="23" />
              <line x1="8" y1="23" x2="16" y2="23" />
              <path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V5a3 3 0 0 0-5.94-.6" />
            </svg>
          </button>

          {/* 2. End Call Button: 96px circle, red #F0483E, white phone-down icon */}
          <button
            type="button"
            onClick={onEndCall}
            aria-label="End call"
            className="w-[96px] h-[96px] rounded-full bg-[#F0483E] text-white flex items-center justify-center transition-all duration-100 active:scale-[0.94] hover:bg-[#E03A30] shadow-md cursor-pointer"
          >
            <svg className="w-11 h-11 fill-current" viewBox="0 0 24 24">
              <path d="M12 9c-3.14 0-6.07.96-8.52 2.61a1.5 1.5 0 0 0-.48 1.76l1.32 3.09c.27.63.92 1.02 1.6 1.02.3 0 .6-.08.87-.23 1.62-.94 3.42-1.55 5.21-1.81V13.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5v1.94c1.79.26 3.59.87 5.21 1.81.27.15.57.23.87.23.68 0 1.33-.39 1.6-1.02l1.32-3.09a1.5 1.5 0 0 0-.48-1.76C18.07 9.96 15.14 9 12 9z" />
            </svg>
          </button>

          {/* 3. Keypad Button: 76px circle, #E1E1E4, 3x3 grid of dots icon in #6B6B70 */}
          <button
            type="button"
            onClick={handleKeypadClick}
            aria-label="Open keypad"
            className="w-[76px] h-[76px] rounded-full bg-[#E1E1E4] text-[#6B6B70] flex items-center justify-center transition-all duration-100 active:scale-[0.94] hover:bg-[#DCDCE0] cursor-pointer"
          >
            <svg className="w-7 h-7 fill-current" viewBox="0 0 24 24">
              <circle cx="5" cy="5" r="2.2" />
              <circle cx="12" cy="5" r="2.2" />
              <circle cx="19" cy="5" r="2.2" />
              <circle cx="5" cy="12" r="2.2" />
              <circle cx="12" cy="12" r="2.2" />
              <circle cx="19" cy="12" r="2.2" />
              <circle cx="5" cy="19" r="2.2" />
              <circle cx="12" cy="19" r="2.2" />
              <circle cx="19" cy="19" r="2.2" />
            </svg>
          </button>
        </div>
      </div>

      {/* Bottom Home Indicator */}
      <div className="relative z-20 pb-2 flex justify-center">
        <div className="w-[134px] h-[5px] bg-black/40 rounded-full" />
      </div>

      {/* Slide-up Dialpad Sheet */}
      <AnimatePresence>
        {showInternalKeypad && (
          <motion.div
            initial={{ y: "100%" }}
            animate={{ y: 0 }}
            exit={{ y: "100%" }}
            transition={{ type: "spring", stiffness: 320, damping: 30 }}
            className="absolute inset-x-0 bottom-0 z-40 bg-[#1C1C1E]/95 backdrop-blur-xl rounded-t-[36px] p-6 text-white shadow-2xl flex flex-col items-center border-t border-white/10"
          >
            <div className="w-10 h-1 bg-white/20 rounded-full mb-4" />
            
            <div className="h-10 text-2xl font-semibold tracking-wider text-white tabular-nums mb-3">
              {dialedNumbers || <span className="text-white/40 text-base font-normal">Dial a number...</span>}
            </div>

            <div className="grid grid-cols-3 gap-x-6 gap-y-4 w-full max-w-[280px]">
              {[
                { digit: "1", sub: "" },
                { digit: "2", sub: "ABC" },
                { digit: "3", sub: "DEF" },
                { digit: "4", sub: "GHI" },
                { digit: "5", sub: "JKL" },
                { digit: "6", sub: "MNO" },
                { digit: "7", sub: "PQRS" },
                { digit: "8", sub: "TUV" },
                { digit: "9", sub: "WXYZ" },
                { digit: "*", sub: "" },
                { digit: "0", sub: "+" },
                { digit: "#", sub: "" },
              ].map(({ digit, sub }) => (
                <button
                  key={digit}
                  onClick={() => handleKeypadPress(digit)}
                  className="w-[72px] h-[72px] rounded-full bg-[#2C2C2E] hover:bg-[#3A3A3C] active:scale-95 transition-all flex flex-col items-center justify-center text-white"
                >
                  <span className="text-2xl font-semibold leading-none">{digit}</span>
                  {sub && <span className="text-[10px] tracking-wider text-white/50 mt-0.5">{sub}</span>}
                </button>
              ))}
            </div>

            <button
              onClick={() => setShowInternalKeypad(false)}
              className="mt-6 text-base font-medium text-[#4FB4F2] hover:text-white transition-colors"
            >
              Hide Keypad
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};
