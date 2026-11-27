import React from 'react';

export interface PhoneFrameProps {
  children: React.ReactNode;
  showNotch?: boolean;
  notchTime?: string;
}

export const PhoneFrame: React.FC<PhoneFrameProps> = ({
  children,
  showNotch = false,
  notchTime = "7:26",
}) => {
  return (
    <div className="relative flex items-center justify-center p-4 sm:p-8">
      {/* Outer Phone Hardware Body (iPhone Titanium/Aluminum frame) */}
      <div 
        className="relative w-[390px] h-[844px] rounded-[54px] bg-[#1a1a1c] p-[10px] shadow-[0_25px_60px_-15px_rgba(0,0,0,0.7),0_0_0_1px_rgba(255,255,255,0.12)] ring-1 ring-black/40 flex flex-col justify-between overflow-hidden"
        style={{
          boxShadow: '0 30px 80px rgba(0, 0, 0, 0.75), inset 0 0 4px rgba(255, 255, 255, 0.25), 0 0 0 1px #333336',
        }}
      >
        {/* Hardware side button accents */}
        <div className="absolute -left-[13px] top-[140px] w-[3px] h-[32px] bg-[#3a3a3c] rounded-l-sm" />
        <div className="absolute -left-[13px] top-[190px] w-[3px] h-[55px] bg-[#3a3a3c] rounded-l-sm" />
        <div className="absolute -left-[13px] top-[255px] w-[3px] h-[55px] bg-[#3a3a3c] rounded-l-sm" />
        <div className="absolute -right-[13px] top-[180px] w-[3px] h-[80px] bg-[#3a3a3c] rounded-r-sm" />

        {/* Screen Bezel & Display Viewport (Exact 390x844 active content area) */}
        <div className="relative w-full h-full rounded-[44px] overflow-hidden bg-black flex flex-col">
          {/* Optional Classic Notch Header for Wallpaper view (Images 2 and 3) */}
          {showNotch && (
            <div className="absolute top-0 inset-x-0 z-40 pt-2 px-7 flex items-center justify-between text-white pointer-events-none">
              <span className="text-[15px] font-semibold tracking-tight">{notchTime}</span>
              
              {/* iPhone Notch */}
              <div className="absolute left-1/2 -translate-x-1/2 top-0 w-[140px] h-[28px] bg-black rounded-b-[18px] flex items-center justify-center">
                {/* Speaker slit & camera */}
                <div className="w-[50px] h-[4px] bg-[#222] rounded-full mr-2" />
                <div className="w-[10px] h-[10px] rounded-full bg-[#111] border border-[#222]" />
              </div>

              {/* Status Icons */}
              <div className="flex items-center space-x-1.5">
                <svg className="w-4 h-3.5 fill-white" viewBox="0 0 17 12">
                  <rect x="0" y="9" width="3" height="3" rx="0.5" />
                  <rect x="4.5" y="6" width="3" height="6" rx="0.5" />
                  <rect x="9" y="3" width="3" height="9" rx="0.5" />
                  <rect x="13.5" y="0" width="3" height="12" rx="0.5" />
                </svg>
                <svg className="w-4 h-3.5 fill-white" viewBox="0 0 16 12">
                  <path d="M8 9.6a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 0 0 0-3.2zM3.4 7a6.5 6.5 0 0 1 9.2 0 .9.9 0 0 0 1.2-1.3 8.3 8.3 0 0 0-11.6 0 .9.9 0 0 0 1.2 1.3zm-2.6-3a10.2 10.2 0 0 1 14.4 0 .9.9 0 0 0 1.2-1.3 12 12 0 0 0-16.8 0 .9.9 0 0 0 1.2 1.3z" />
                </svg>
                <div className="w-6 h-3 rounded-[4px] border border-white p-[1.5px] flex items-center">
                  <div className="w-full h-full bg-white rounded-[2px]" />
                </div>
              </div>
            </div>
          )}

          {/* Screen Content */}
          <div className="relative w-full h-full flex flex-col overflow-hidden">
            {children}
          </div>
        </div>
      </div>
    </div>
  );
};
