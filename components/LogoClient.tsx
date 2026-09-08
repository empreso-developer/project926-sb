"use client";

import Image from "next/image";

export default function Logo() {
  return (
    <div className="flex items-center justify-start rounded-xl overflow-hidden transition-transform group-hover:scale-105">
        <Image
            src="/logo-uncropped.png"
            alt="Project926 Logo"
            width={200}
            height={50}
            className="object-contain"
            priority
            onContextMenu={(e) => e.preventDefault()}
        />
    </div>
  );
}

