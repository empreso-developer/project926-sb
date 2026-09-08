'use client'
import React, { useEffect, useState } from 'react';

const terminalLines = [
  'visit empreso.in',
  'empreso --service overview',
  '✓ Resume Optimization',
  '✓ Interview Prep',
  '✓ AI-powered Job Matching',
  '✓ Career Coaching',
  '✓ Skills Assessment',
];

const TYPING_SPEED = 45;
const LINE_DELAY = 350;

// OPTIMIZATION: Removed useRef and requestAnimationFrame which cause performance issues
const EmpresoTerminal = () => {
  const [currentLine, setCurrentLine] = useState(0);
  const [text, setText] = useState('');

  useEffect(() => {
    if (currentLine >= terminalLines.length) return;

    const line = terminalLines[currentLine];
    let cancelled = false;
    let charIndex = 0;

    const interval = setInterval(() => {
      if (cancelled) return;

      charIndex += 1;
      setText(line.slice(0, charIndex));

      if (charIndex >= line.length) {
        clearInterval(interval);

        const timeoutId = setTimeout(() => {
          if (cancelled) return;
          setCurrentLine((prev) => prev + 1);
          setText('');
        }, LINE_DELAY);

        return () => clearTimeout(timeoutId);
      }
    }, TYPING_SPEED);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [currentLine]);

  const completed = terminalLines.slice(0, currentLine);

  return (
    <div className="terminal-wrapper">
      <div className="terminal">

        {/* HEADER */}
        <div className="head">
          <div className="title">
            <span className="dot red" />
            <span className="dot yellow" />
            <span className="dot green" />
            <span className="ml-2 text-xs opacity-70">terminal</span>
          </div>
        </div>

        {/* BODY */}
        <div className="body">
          <pre className="pre">

            {completed.map((line, i) => (
              <div key={i} className="line">
                {line.startsWith('✓') ? (
                  <>
                    <span className="indent" />
                    <span className="success">{line}</span>
                  </>
                ) : (
                  <>
                    <span className="prompt">$</span>
                    <span className="command">{line}</span>
                  </>
                )}
              </div>
            ))}

            {currentLine < terminalLines.length && (
              <div className="line">
                {terminalLines[currentLine].startsWith('✓') ? (
                  <>
                    <span className="indent" />
                    <span className="success">{text}</span>
                  </>
                ) : (
                  <>
                    <span className="prompt">$</span>
                    <span className="command">
                      {text}
                      <span className="cursor" />
                    </span>
                  </>
                )}
              </div>
            )}

          </pre>
        </div>
      </div>

      <style jsx>{`
        .terminal-wrapper {
          width: 100%;
          max-width: 420px;
        }

        .terminal {
          display: flex;
          flex-direction: column;
          height: 260px;
          border-radius: 12px;
          overflow: hidden;
          background: rgba(0,0,0,0.65);
          backdrop-filter: blur(12px);
          border: 1px solid rgba(255,255,255,0.08);
          box-shadow: 0 10px 40px rgba(0,0,0,0.4);
        }

        .head {
          display: flex;
          align-items: center;
          padding: 6px 10px;
          background: rgba(255,255,255,0.03);
        }

        .title {
          display: flex;
          align-items: center;
          gap: 6px;
        }

        .dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
        }

        .red { background: #ff5f56; }
        .yellow { background: #ffbd2e; }
        .green { background: #27c93f; }

        .body {
          flex: 1;
          overflow-y: auto;
          padding: 10px;
          font-size: 12px;
          line-height: 1.4;
          color: white;
          scrollbar-width: none;
        }

        .body::-webkit-scrollbar {
          display: none;
        }

        .pre {
          display: flex;
          flex-direction: column;
          gap: 2px;
        }

        .line {
          display: flex;
          align-items: center;
          height: 18px;
          white-space: pre;
        }

        .prompt {
          color: #6b7280;
          margin-right: 6px;
        }

        .command {
          color: #e5e7eb;
        }

        .success {
          color: #4ade80;
        }

        .indent {
          width: 14px;
        }

        /* CSS-only cursor (IMPORTANT OPTIMIZATION) */
        .cursor {
          display: inline-block;
          width: 2px;
          height: 14px;
          margin-left: 2px;
          background: #e34ba9;
          animation: blink 1s infinite;
        }

        @keyframes blink {
          50% { opacity: 0; }
        }
      `}</style>
    </div>
  );
};

export default EmpresoTerminal;