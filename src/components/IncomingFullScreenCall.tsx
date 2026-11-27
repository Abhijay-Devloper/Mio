import React, { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';

export interface IncomingFullScreenCallProps {
  name?: string;
  subtitle?: string;
  onAccept?: () => void;
  onDecline?: () => void;
  onMessage?: () => void;
  onRemindMe?: () => void;
}

export const IncomingFullScreenCall: React.FC<IncomingFullScreenCallProps> = ({
  name = "Ervin Smith",
  subtitle = "Incoming Call",
  onAccept,
  onDecline,
  onMessage,
  onRemindMe,
}) => {
  return (
    <div className="relative w-full h-full bg-[#F5F5F7] flex flex-col justify-between overflow-hidden select-none font-[-apple-system,BlinkMacSystemFont,'SF_Pro_Display','SF_Pro_Text','SF_Pro','Inter',sans-serif]">
      {/* Top Status Bar & Dynamic Island */}
      <div className="relative z-30 pt-3 px-7 flex items-center justify-between text-[#1C2B33]">
        <span className="text-[16px] font-semibold tracking-tight">9:41</span>

        {/* Dynamic Island */}
        <div className="absolute left-1/2 -translate-x-1/2 top-3 w-[124px] h-[35px] bg-black rounded-full flex items-center justify-between px-3 shadow-sm">
          <div className="w-2 h-2 rounded-full bg-[#1A1A1A] ml-auto mr-1 flex items-center justify-center">
            <div className="w-1 h-1 rounded-full bg-[#080820]" />
          </div>
        </div>

        {/* Right Status Icons */}
        <div className="flex items-center space-x-1.5">
          <svg className="w-4 h-3.5 fill-[#1C2B33]" viewBox="0 0 17 12">
            <rect x="0" y="9" width="3" height="3" rx="0.5" />
            <rect x="4.5" y="6" width="3" height="6" rx="0.5" />
            <rect x="9" y="3" width="3" height="9" rx="0.5" />
            <rect x="13.5" y="0" width="3" height="12" rx="0.5" />
          </svg>
          <svg className="w-4 h-3.5 fill-[#1C2B33]" viewBox="0 0 16 12">
            <path d="M8 9.6a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0-3.2zM3.4 7a6.5 6.5 0 0 1 9.2 0 .9.9 0 0 0 1.2-1.3 8.3 8.3 0 0 0-11.6 0 .9.9 0 0 0 1.2 1.3zm-2.6-3a10.2 10.2 0 0 1 14.4 0 .9.9 0 0 0 1.2-1.3 12 12 0 0 0-16.8 0 .9.9 0 0 0 1.2 1.3z" />
          </svg>
          <div className="w-6 h-3 rounded-[4px] border border-[#1C2B33] p-[1.5px] flex items-center">
            <div className="w-full h-full bg-[#1C2B33] rounded-[2px]" />
          </div>
        </div>
      </div>

      {/* Main Soft Gradient Card */}
      <div className="relative mx-6 mt-4 mb-3 flex-1 flex flex-col items-center justify-between">
        {/* Feathered outer glow */}
        <div 
          className="absolute -inset-2 rounded-[52px] pointer-events-none opacity-80 blur-[26px]"
          style={{
            background: 'linear-gradient(180deg, #8FDDFB 0%, #4FB4F2 28%, #1E6FE0 65%, #0A2A9E 100%)',
          }}
        />

        {/* The Card */}
        <div className="relative w-full h-full rounded-[44px] overflow-hidden flex flex-col items-center justify-between py-10 shadow-lg">
          <div 
            className="absolute inset-0 w-full h-full animate-gradient-drift pointer-events-none"
            style={{
              background: 'linear-gradient(180deg, #8FDDFB 0%, #4FB4F2 30%, #1E6FE0 65%, #0A2A9E 100%)',
              filter: 'blur(36px)',
              transformOrigin: 'center center',
            }}
          />

          <div 
            className="absolute inset-0 w-full h-full pointer-events-none opacity-85"
            style={{
              background: 'radial-gradient(ellipse 90% 60% at 50% 18%, rgba(143, 221, 251, 0.9) 0%, transparent 75%), radial-gradient(ellipse 100% 80% at 50% 90%, rgba(10, 42, 158, 0.95) 0%, transparent 80%)',
              filter: 'blur(20px)',
            }}
          />

          {/* Top text block: Name & "Incoming Call" */}
          <div className="relative z-10 flex flex-col items-center text-center mt-2 px-4">
            <h1 className="text-[34px] font-bold text-[#1C2B33] tracking-[-0.5px] leading-tight">
              {name}
            </h1>
            <p className="text-[20px] font-normal text-[#1C2B33]/80 mt-1 tracking-tight">
              {subtitle}
            </p>
          </div>

          {/* Lower Third: Glowing Animated Orb */}
          <div className="relative z-10 w-full flex items-center justify-center my-auto">
            <div 
              className="absolute w-[180px] h-[180px] rounded-full pointer-events-none opacity-60 blur-[38px]"
              style={{
                background: 'radial-gradient(circle, #19D3F0 0%, #1456D8 65%, transparent 100%)',
              }}
            />
            <div 
              className="relative w-[130px] h-[130px] rounded-full animate-orb-breathe shadow-2xl flex items-center justify-center"
              style={{
                background: 'radial-gradient(circle at 40% 35%, #58E4F7 0%, #19D3F0 32%, #1456D8 78%, #0C369B 100%)',
                boxShadow: '0 0 35px rgba(25, 211, 240, 0.65), 0 0 70px rgba(20, 86, 216, 0.45)',
              }}
            >
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

      {/* Bottom Incoming Action Panel (Matching Image 4) */}
      <div className="relative z-20 mx-6 mb-7">
        <div className="w-full bg-[#EAEAEC]/90 backdrop-blur-md rounded-[36px] p-6 flex flex-col space-y-6 shadow-sm border border-white/40">
          {/* Top row of secondary actions: Message & Remind Me */}
          <div className="flex items-center justify-around px-4">
            {/* Message */}
            <button
              type="button"
              onClick={onMessage}
              className="flex flex-col items-center space-y-1.5 group cursor-pointer active:scale-95 transition-transform"
            >
              <div className="w-12 h-12 rounded-full bg-[#D8D8DC] flex items-center justify-center text-[#1C2B33] group-hover:bg-[#CECECE]">
                <svg className="w-5 h-5 fill-current" viewBox="0 0 24 24">
                  <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z" />
                </svg>
              </div>
              <span className="text-[13px] font-medium text-[#1C2B33]/80">Message</span>
            </button>

            {/* Remind Me */}
            <button
              type="button"
              onClick={onRemindMe}
              className="flex flex-col items-center space-y-1.5 group cursor-pointer active:scale-95 transition-transform"
            >
              <div className="w-12 h-12 rounded-full bg-[#D8D8DC] flex items-center justify-center text-[#1C2B33] group-hover:bg-[#CECECE]">
                <svg className="w-5 h-5 fill-current" viewBox="0 0 24 24">
                  <path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10 10-4.5 10-10S17.5 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z" />
                </svg>
              </div>
              <span className="text-[13px] font-medium text-[#1C2B33]/80">Remind Me</span>
            </button>
          </div>

          {/* Bottom row of primary actions: Decline & Accept */}
          <div className="flex items-center justify-between px-6">
            {/* Decline (Red) */}
            <div className="flex flex-col items-center space-y-2">
              <button
                type="button"
                onClick={onDecline}
                aria-label="Decline"
                className="w-[72px] h-[72px] rounded-full bg-[#FF3B30] text-white flex items-center justify-center shadow-md active:scale-[0.94] hover:bg-[#E0352B] cursor-pointer"
              >
                <svg className="w-8 h-8 fill-current" viewBox="0 0 24 24">
                  <path d="M12 9c-3.14 0-6.07.96-8.52 2.61a1.5 1.5 0 0 0-.48 1.76l1.32 3.09c.27.63.92 1.02 1.6 1.02.3 0 .6-.08.87-.23 1.62-.94 3.42-1.55 5.21-1.81V13.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5v1.94c1.79.26 3.59.87 5.21 1.81.27.15.57.23.87.23.68 0 1.33-.39 1.6-1.02l1.32-3.09a1.5 1.5 0 0 0-.48-1.76C18.07 9.96 15.14 9 12 9z" />
                </svg>
              </button>
              <span className="text-[14px] font-medium text-[#1C2B33]">Decline</span>
            </div>

            {/* Accept (Green) */}
            <div className="flex flex-col items-center space-y-2">
              <button
                type="button"
                onClick={onAccept}
                aria-label="Accept"
                className="w-[72px] h-[72px] rounded-full bg-[#34C759] text-white flex items-center justify-center shadow-md active:scale-[0.94] hover:bg-[#2EB34F] cursor-pointer"
              >
                <svg className="w-8 h-8 fill-current" viewBox="0 0 24 24">
                  <path d="M6.62 10.79a15.053 15.053 0 0 0 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z" />
                </svg>
              </button>
              <span className="text-[14px] font-medium text-[#1C2B33]">Accept</span>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Home Indicator */}
      <div className="relative z-20 pb-2 flex justify-center">
        <div className="w-[134px] h-[5px] bg-black/40 rounded-full" />
      </div>
    </div>
  );
};
