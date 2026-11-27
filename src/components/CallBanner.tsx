import React from 'react';
import { motion } from 'motion/react';
import { formatCallTimer } from './ActiveCallScreen';

export interface CallBannerProps {
  variant: 'ongoing' | 'incoming';
  name?: string;
  relationship?: string;
  timerSeconds?: number;
  avatarUrl?: string;
  isMuted?: boolean;
  isVideoOn?: boolean;
  onToggleMute?: () => void;
  onToggleVideo?: () => void;
  onEndCall?: () => void;
  onAccept?: () => void;
  onDecline?: () => void;
  onExpand?: () => void;
}

export const CallBanner: React.FC<CallBannerProps> = ({
  variant,
  name = 'Charles Cat',
  relationship = 'My wife',
  timerSeconds = 165, // 02:45 default
  avatarUrl = '/avatar.png',
  isMuted = false,
  isVideoOn = false,
  onToggleMute,
  onToggleVideo,
  onEndCall,
  onAccept,
  onDecline,
  onExpand,
}) => {
  const isOngoing = variant === 'ongoing';

  return (
    <motion.div
      initial={{ y: -80, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      exit={{ y: -80, opacity: 0 }}
      transition={{
        type: 'spring',
        stiffness: 300,
        damping: 28,
      }}
      onClick={(e) => {
        // Tapping banner body returns to ActiveCallScreen
        if (e.target === e.currentTarget || (e.target as HTMLElement).closest('.banner-body')) {
          onExpand?.();
        }
      }}
      className="relative z-30 mx-4 h-[64px] rounded-full bg-[rgba(44,44,46,0.92)] backdrop-blur-xl border border-white/5 flex items-center justify-between px-2 shadow-2xl select-none cursor-pointer font-[-apple-system,BlinkMacSystemFont,'SF_Pro_Display','SF_Pro_Text','SF_Pro','Inter',sans-serif]"
    >
      {/* Left section: Avatar & Text block */}
      <div 
        className="banner-body flex items-center min-w-0 flex-1 mr-2"
        onClick={() => onExpand?.()}
      >
        {/* Round Avatar: 48px, 8px inset */}
        <div className="w-[48px] h-[48px] rounded-full overflow-hidden shrink-0 bg-[#2C2C2E] flex items-center justify-center border border-black/10">
          {avatarUrl ? (
            <img 
              src={avatarUrl} 
              alt={name} 
              className="w-full h-full object-cover" 
              onError={(e) => {
                // Fallback to initial if image fails
                (e.currentTarget as HTMLElement).style.display = 'none';
              }}
            />
          ) : (
            <span className="text-white text-base font-semibold">{name.charAt(0)}</span>
          )}
        </div>

        {/* 2-line Text Block */}
        <div className="ml-3 flex flex-col justify-center min-w-0">
          <span className="text-white text-[17px] font-semibold tracking-tight truncate leading-tight">
            {name}
          </span>
          <span className="text-[#A1A1A6] text-[14px] font-normal tracking-tight truncate mt-0.5">
            {isOngoing ? (
              `${relationship}${relationship ? ' • ' : ''}${formatCallTimer(timerSeconds ?? 0)}`
            ) : (
              relationship
            )}
          </span>
        </div>
      </div>

      {/* Right Action Buttons */}
      <div 
        className="flex items-center space-x-2 shrink-0" 
        onClick={(e) => e.stopPropagation()}
      >
        {isOngoing ? (
          <>
            {/* Ongoing Button 1: Video (#3A3A3C with white camera icon) */}
            <button
              type="button"
              onClick={onToggleVideo}
              aria-label={isVideoOn ? "Turn off video" : "Turn on video"}
              className={`w-[44px] h-[44px] rounded-full flex items-center justify-center transition-all duration-100 active:scale-[0.94] cursor-pointer ${
                isVideoOn ? 'bg-white text-black' : 'bg-[#3A3A3C] text-white hover:bg-[#48484A]'
              }`}
            >
              <svg 
                className="w-5 h-5" 
                viewBox="0 0 24 24" 
                fill="none" 
                stroke="currentColor" 
                strokeWidth="2.2" 
                strokeLinecap="round" 
                strokeLinejoin="round"
              >
                <path d="M14.5 4h-9A2.5 2.5 0 0 0 3 6.5v11A2.5 2.5 0 0 0 5.5 20h9a2.5 2.5 0 0 0 2.5-2.5v-11A2.5 2.5 0 0 0 14.5 4z" />
                <path d="M17 9.5 21 7v10l-4-2.5" />
              </svg>
            </button>

            {/* Ongoing Button 2: Mute (#3A3A3C with white mic-off icon) */}
            <button
              type="button"
              onClick={onToggleMute}
              aria-label={isMuted ? "Unmute" : "Mute"}
              className={`w-[44px] h-[44px] rounded-full flex items-center justify-center transition-all duration-100 active:scale-[0.94] cursor-pointer ${
                isMuted ? 'bg-white text-[#1C1C1E]' : 'bg-[#3A3A3C] text-white hover:bg-[#48484A]'
              }`}
            >
              <svg 
                className="w-5 h-5" 
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

            {/* Ongoing Button 3: End Call (#FF3B30 with white phone-down icon) */}
            <button
              type="button"
              onClick={onEndCall}
              aria-label="End call"
              className="w-[44px] h-[44px] rounded-full bg-[#FF3B30] text-white flex items-center justify-center transition-all duration-100 active:scale-[0.94] hover:bg-[#E0352B] shadow-sm cursor-pointer"
            >
              <svg className="w-5 h-5 fill-current" viewBox="0 0 24 24">
                <path d="M12 9c-3.14 0-6.07.96-8.52 2.61a1.5 1.5 0 0 0-.48 1.76l1.32 3.09c.27.63.92 1.02 1.6 1.02.3 0 .6-.08.87-.23 1.62-.94 3.42-1.55 5.21-1.81V13.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5v1.94c1.79.26 3.59.87 5.21 1.81.27.15.57.23.87.23.68 0 1.33-.39 1.6-1.02l1.32-3.09a1.5 1.5 0 0 0-.48-1.76C18.07 9.96 15.14 9 12 9z" />
              </svg>
            </button>
          </>
        ) : (
          <>
            {/* Incoming Button 1: Accept (#34C759 with white phone icon) */}
            <button
              type="button"
              onClick={onAccept}
              aria-label="Accept call"
              className="w-[44px] h-[44px] rounded-full bg-[#34C759] text-white flex items-center justify-center transition-all duration-100 active:scale-[0.94] hover:bg-[#2EB34F] shadow-sm cursor-pointer"
            >
              <svg className="w-5 h-5 fill-current" viewBox="0 0 24 24">
                <path d="M6.62 10.79a15.053 15.053 0 0 0 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z" />
              </svg>
            </button>

            {/* Incoming Button 2: Decline (#FF3B30 with white phone-down icon) */}
            <button
              type="button"
              onClick={onDecline}
              aria-label="Decline call"
              className="w-[44px] h-[44px] rounded-full bg-[#FF3B30] text-white flex items-center justify-center transition-all duration-100 active:scale-[0.94] hover:bg-[#E0352B] shadow-sm cursor-pointer"
            >
              <svg className="w-5 h-5 fill-current" viewBox="0 0 24 24">
                <path d="M12 9c-3.14 0-6.07.96-8.52 2.61a1.5 1.5 0 0 0-.48 1.76l1.32 3.09c.27.63.92 1.02 1.6 1.02.3 0 .6-.08.87-.23 1.62-.94 3.42-1.55 5.21-1.81V13.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5v1.94c1.79.26 3.59.87 5.21 1.81.27.15.57.23.87.23.68 0 1.33-.39 1.6-1.02l1.32-3.09a1.5 1.5 0 0 0-.48-1.76C18.07 9.96 15.14 9 12 9z" />
              </svg>
            </button>
          </>
        )}
      </div>
    </motion.div>
  );
};
