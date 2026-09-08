"use client";

import { useEffect, useRef, useState } from "react";
import { CaretDownIcon, CheckIcon } from "@phosphor-icons/react";

export default function SelectField({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const selectedLabel = value || `Select ${label}`;

  // close on outside click
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="space-y-2" ref={ref}>
      <label className="text-sm font-medium text-foreground">
        {label}
      </label>

      {/* Trigger */}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="
          flex h-10 w-full items-center justify-between
          rounded-md border border-input bg-background
          px-3 text-sm text-foreground shadow-sm
          transition-all
          hover:bg-accent/40
          focus:outline-none focus:ring-2 focus:ring-ring/20
        "
      >
        <span
          className={
            value ? "text-foreground" : "text-muted-foreground"
          }
        >
          {selectedLabel}
        </span>

        <CaretDownIcon
          className={`h-4 w-4 text-muted-foreground transition-transform ${
            open ? "rotate-180" : ""
          }`}
        />
      </button>

      {/* Dropdown */}
      {open && (
        <div
          className="
            mt-1 max-h-60 overflow-auto
            rounded-md border border-input bg-popover
            p-1 shadow-md
            animate-in fade-in-0 zoom-in-95
          "
        >
          {options.map((option) => {
            const isSelected = option === value;

            return (
              <button
                key={option}
                type="button"
                onClick={() => {
                  onChange(option);
                  setOpen(false);
                }}
                className={`
                  flex w-full items-center justify-between
                  rounded-sm px-3 py-2 text-sm
                  transition-colors

                  ${
                    isSelected
                      ? "bg-accent text-accent-foreground"
                      : "text-foreground hover:bg-accent/60"
                  }
                `}
              >
                <span>{option}</span>

                {isSelected && (
                  <CheckIcon className="h-4 w-4" />
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}