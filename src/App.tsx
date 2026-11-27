import React, { useState, useEffect } from 'react';
import { AnimatePresence } from 'motion/react';
import { PhoneFrame } from './components/PhoneFrame';
import { ActiveCallScreen } from './components/ActiveCallScreen';
import { CallBanner } from './components/CallBanner';
import { IncomingFullScreenCall } from './components/IncomingFullScreenCall';

export type DemoState = 'active' | 'ongoing-banner' | 'incoming-banner' | 'incoming-fullscreen';

export default function App() {
  const [activeTab, setActiveTab] = useState<DemoState>('active');
  const [isCallActive, setIsCallActive] = useState<boolean>(true);
  const [timerSeconds, setTimerSeconds] = useState<number>(739); // 12:19 default
  const [isMuted, setIsMuted] = useState<boolean>(false);
  const [isVideoOn, setIsVideoOn] = useState<boolean>(false);
  const [isIncomingDismissed, setIsIncomingDismissed] = useState<boolean>(false);

  // Shared timer ticking every second when call is active
  useEffect(() => {
    if (!isCallActive) return;
    const interval = setInterval(() => {
      setTimerSeconds((prev) => prev + 1);
    }, 1000);
    return () => clearInterval(interval);
  }, [isCallActive]);

  const handleEndCall = () => {
    setIsCallActive(false);
    setTimeout(() => {
      setTimerSeconds(0);
      setIsCallActive(true);
      setActiveTab('incoming-fullscreen');
      setIsIncomingDismissed(false);
    }, 1200);
  };

  const handleAcceptIncoming = () => {
    setIsCallActive(true);
    setTimerSeconds(0);
    setActiveTab('active');
  };

  const handleDeclineIncoming = () => {
    setIsIncomingDismissed(true);
    setTimeout(() => {
      setIsIncomingDismissed(false);
    }, 1500);
  };

  return (
    <div className="min-h-screen bg-[#0E131F] text-white flex flex-col items-center justify-start py-8 px-4 font-[-apple-system,BlinkMacSystemFont,'SF_Pro_Display','SF_Pro_Text','SF_Pro','Inter',sans-serif]">
      {/* Header Info & Switcher Bar */}
      <header className="w-full max-w-2xl flex flex-col items-center mb-6">
        <div className="flex items-center space-x-2 mb-2">
          <span className="w-2.5 h-2.5 rounded-full bg-[#19D3F0] animate-pulse" />
          <h1 className="text-xl font-bold tracking-tight text-white/95">
            Mio Mobile Call UI
          </h1>
          <span className="text-xs px-2 py-0.5 rounded-full bg-white/10 text-white/70 font-medium">
            Pixel-Accurate Replica
          </span>
        </div>
        <p className="text-sm text-white/50 text-center mb-5">
          Exact replicas of all 4 reference designs: Active voice call, ongoing mini-banner (when app is minimized/in background), incoming mini-banner (notification overlay), and full-screen incoming call UI (when phone is locked/idle or on chat screen).
        </p>

        {/* 4 Buttons to Switch Between States */}
        <div className="bg-[#1C2333] p-1.5 rounded-2xl flex flex-wrap items-center justify-center gap-1.5 border border-white/10 shadow-lg">
          <button
            type="button"
            onClick={() => {
              setActiveTab('active');
              setIsCallActive(true);
            }}
            className={`px-3 py-2 rounded-xl text-xs sm:text-sm font-semibold transition-all duration-150 cursor-pointer ${
              activeTab === 'active'
                ? 'bg-[#2A75E6] text-white shadow-md'
                : 'text-white/70 hover:text-white hover:bg-white/5'
            }`}
          >
            1. Active Voice Call (Img 1)
          </button>

          <button
            type="button"
            onClick={() => {
              setActiveTab('ongoing-banner');
              setIsCallActive(true);
            }}
            className={`px-3 py-2 rounded-xl text-xs sm:text-sm font-semibold transition-all duration-150 cursor-pointer ${
              activeTab === 'ongoing-banner'
                ? 'bg-[#2A75E6] text-white shadow-md'
                : 'text-white/70 hover:text-white hover:bg-white/5'
            }`}
          >
            2. Ongoing Mini Banner (Img 2)
          </button>

          <button
            type="button"
            onClick={() => {
              setActiveTab('incoming-banner');
              setIsIncomingDismissed(false);
            }}
            className={`px-3 py-2 rounded-xl text-xs sm:text-sm font-semibold transition-all duration-150 cursor-pointer ${
              activeTab === 'incoming-banner'
                ? 'bg-[#2A75E6] text-white shadow-md'
                : 'text-white/70 hover:text-white hover:bg-white/5'
            }`}
          >
            3. Incoming Mini Banner (Img 3)
          </button>

          <button
            type="button"
            onClick={() => {
              setActiveTab('incoming-fullscreen');
            }}
            className={`px-3 py-2 rounded-xl text-xs sm:text-sm font-semibold transition-all duration-150 cursor-pointer ${
              activeTab === 'incoming-fullscreen'
                ? 'bg-[#2A75E6] text-white shadow-md'
                : 'text-white/70 hover:text-white hover:bg-white/5'
            }`}
          >
            4. Incoming Full Screen (Img 4)
          </button>
        </div>
      </header>

      {/* Interactive Phone Frame (390 x 844) */}
      <main className="flex flex-col items-center">
        <PhoneFrame 
          showNotch={activeTab !== 'active' && activeTab !== 'incoming-fullscreen'}
          notchTime="7:26"
        >
          {activeTab === 'active' && (
            <ActiveCallScreen
              name="Ervin Smith"
              timerSeconds={timerSeconds}
              isMuted={isMuted}
              onToggleMute={() => setIsMuted((prev) => !prev)}
              onEndCall={handleEndCall}
              onMinimize={() => setActiveTab('ongoing-banner')}
            />
          )}

          {activeTab === 'ongoing-banner' && (
            <div className="relative w-full h-full bg-[#050B14] overflow-hidden flex flex-col justify-between">
              {/* Wallpaper Background matching image 2 */}
              <div 
                className="absolute inset-0 bg-cover bg-center opacity-95 pointer-events-none"
                style={{ backgroundImage: 'url(/wallpaper_clean.png)' }}
              />

              {/* Floating Ongoing Banner */}
              <div className="relative z-30 pt-[58px]">
                <AnimatePresence>
                  <CallBanner
                    variant="ongoing"
                    name="Charles Cat"
                    relationship="My wife"
                    timerSeconds={timerSeconds}
                    avatarUrl="/avatar.png"
                    isMuted={isMuted}
                    isVideoOn={isVideoOn}
                    onToggleMute={() => setIsMuted((prev) => !prev)}
                    onToggleVideo={() => setIsVideoOn((prev) => !prev)}
                    onEndCall={handleEndCall}
                    onExpand={() => setActiveTab('active')}
                  />
                </AnimatePresence>
              </div>

              {/* Tap to return helper hint */}
              <div 
                onClick={() => setActiveTab('active')}
                className="relative z-10 flex-1 flex items-center justify-center cursor-pointer group"
              >
                <div className="px-4 py-2 rounded-full bg-black/40 backdrop-blur-md border border-white/10 text-white/50 text-xs group-hover:text-white/80 transition-colors">
                  Tap anywhere on screen to return to Active Call
                </div>
              </div>

              {/* Bottom Home Indicator Bar */}
              <div className="relative z-20 pb-3 flex justify-center pointer-events-none">
                <div className="w-[134px] h-[5px] bg-white/70 rounded-full" />
              </div>
            </div>
          )}

          {activeTab === 'incoming-banner' && (
            <div className="relative w-full h-full bg-[#050B14] overflow-hidden flex flex-col justify-between">
              {/* Wallpaper Background matching image 3 */}
              <div 
                className="absolute inset-0 bg-cover bg-center opacity-95 pointer-events-none"
                style={{ backgroundImage: 'url(/wallpaper_clean.png)' }}
              />

              {/* Floating Incoming Banner */}
              <div className="relative z-30 pt-[58px]">
                <AnimatePresence>
                  {!isIncomingDismissed ? (
                    <CallBanner
                      variant="incoming"
                      name="Charles Cat"
                      relationship="My wife"
                      avatarUrl="/avatar.png"
                      onAccept={handleAcceptIncoming}
                      onDecline={handleDeclineIncoming}
                      onExpand={handleAcceptIncoming}
                    />
                  ) : (
                    <div className="mx-4 h-[64px] rounded-full bg-black/40 backdrop-blur-md border border-white/10 flex items-center justify-center text-white/50 text-xs">
                      Call declined • Dismissed
                    </div>
                  )}
                </AnimatePresence>
              </div>

              <div className="relative z-10 flex-1 flex items-center justify-center pointer-events-none">
                <div className="px-4 py-2 rounded-full bg-black/40 backdrop-blur-md border border-white/10 text-white/40 text-xs">
                  Incoming call notification ringing...
                </div>
              </div>

              <div className="relative z-20 pb-3 flex justify-center pointer-events-none">
                <div className="w-[134px] h-[5px] bg-white/70 rounded-full" />
              </div>
            </div>
          )}

          {activeTab === 'incoming-fullscreen' && (
            <IncomingFullScreenCall
              name="Ervin Smith"
              subtitle="Incoming Call"
              onAccept={handleAcceptIncoming}
              onDecline={handleDeclineIncoming}
              onMessage={() => alert("Quick Message action triggered")}
              onRemindMe={() => alert("Remind Me action triggered")}
            />
          )}
        </PhoneFrame>

        {/* Quick Tips */}
        <div className="mt-4 flex flex-wrap items-center justify-center gap-3 text-xs text-white/40">
          <span>• Tapping ongoing banner expands to Active Call</span>
          <span>• Full-screen incoming UI replicates Image 4</span>
          <span>• Shared timer persists across states</span>
        </div>
      </main>
    </div>
  );
}
