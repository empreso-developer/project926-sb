"use client";

export function OpenResumeButton({ url }: { url: string }) {
  return (
    <button
      onClick={() => window.open(url, "_blank")}
      className="px-3 py-2 border rounded-md text-sm"
    >
      Open Resume
    </button>
  );
}