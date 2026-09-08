'use client';
import React, { useState } from "react";
import { RiChatSmileAiLine } from "react-icons/ri";
import { ArrowUpRight } from "lucide-react";

const FloatingButton = () => {
  const [hovered, setHovered] = useState(false);

  return (
    <div className="fixed bottom-6 right-6 z-[999]">
      
      {/* Container */}
      <div
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        className="group relative flex items-center"
      >
        
        {/* Expandable Label */}
        <div
          className={`absolute right-full mr-3 flex items-center gap-2 whitespace-nowrap rounded-full border border-white/[0.1] bg-background/80 backdrop-blur-md px-4 py-2 text-sm text-foreground shadow-lg transition-all duration-300 ${
            hovered
              ? "opacity-100 translate-x-0"
              : "opacity-0 translate-x-4 pointer-events-none"
          }`}
        >
          <span>Ask Empreso AI</span>
          <ArrowUpRight className="w-4 h-4 opacity-70" />
        </div>

        {/* Button */}
        <button
          onClick={() => window.open("https://ai.empreso.in", "_blank")}
          className="relative flex items-center justify-center w-14 h-14 rounded-full border border-white/[0.1] bg-background/80 backdrop-blur-md shadow-xl transition-all duration-300 group-hover:scale-105"
        >
          {/* subtle inner background */}
          <div className="absolute inset-0 rounded-full bg-white/[0.03]" />

          {/* icon */}
          <RiChatSmileAiLine className="text-white relative z-10" size={22} />

          {/* notification dot */}
          <span className="absolute top-2 right-2 flex h-2.5 w-2.5">
            <span className="absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75 animate-ping"></span>
            <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
          </span>
        </button>
      </div>
    </div>
  );
};

export default FloatingButton;