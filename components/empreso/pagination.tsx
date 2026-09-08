"use client";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { Button } from "./ui/primitives";

export function Pagination({ page, hasNext }: { page: number; hasNext: boolean }) {
  const pathname = usePathname();
  const sp = useSearchParams();
  const make = (p: number) => {
    const params = new URLSearchParams(sp.toString());
    params.set("page", String(p));
    return `${pathname}?${params.toString()}`;
  };
  return (
    <div className="flex items-center justify-end gap-2 text-sm">
      <span className="text-muted-foreground">Page {page}</span>
      <Link href={make(Math.max(1, page - 1))} aria-disabled={page <= 1}>
        <Button disabled={page <= 1}>Prev</Button>
      </Link>
      <Link href={make(page + 1)} aria-disabled={!hasNext}>
        <Button disabled={!hasNext}>Next</Button>
      </Link>
    </div>
  );
}