'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Check, X, Trash2, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useToast } from '@/hooks/use-toast';
import {
  approveEventAction,
  rejectEventAction,
  removeEventAction,
} from '@/lib/actions/admin';

interface AdminEventActionsProps {
  eventId: string;
  compact?: boolean;
}

export function AdminEventActions({ eventId, compact }: AdminEventActionsProps) {
  const router = useRouter();
  const { toast } = useToast();
  const [loading, setLoading] = useState<string | null>(null);

  async function run(
    action: 'approve' | 'reject' | 'remove',
    fn: () => Promise<void>,
  ) {
    setLoading(action);
    try {
      await fn();
      toast({ title: `Event ${action}d` });
      router.refresh();
    } catch (err) {
      toast({
        title: 'Action failed',
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setLoading(null);
    }
  }

  if (compact) {
    return (
      <div className="flex justify-end gap-1">
        <Button
          size="icon"
          variant="ghost"
          className="h-8 w-8 text-success hover:bg-success/10 hover:text-success"
          disabled={loading !== null}
          onClick={() => run('approve', () => approveEventAction(eventId))}
          title="Approve"
        >
          {loading === 'approve' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
        </Button>
        <Button
          size="icon"
          variant="ghost"
          className="h-8 w-8 text-warning hover:bg-warning/10 hover:text-warning"
          disabled={loading !== null}
          onClick={() => run('reject', () => rejectEventAction(eventId))}
          title="Reject"
        >
          {loading === 'reject' ? <Loader2 className="h-4 w-4 animate-spin" /> : <X className="h-4 w-4" />}
        </Button>
        <Button
          size="icon"
          variant="ghost"
          className="h-8 w-8 text-destructive hover:bg-destructive/10 hover:text-destructive"
          disabled={loading !== null}
          onClick={() => run('remove', () => removeEventAction(eventId))}
          title="Remove"
        >
          {loading === 'remove' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
        </Button>
      </div>
    );
  }

  return (
    <div className="flex gap-2">
      <Button
        size="sm"
        className="bg-success text-success-foreground hover:bg-success/90"
        disabled={loading !== null}
        onClick={() => run('approve', () => approveEventAction(eventId))}
      >
        {loading === 'approve' ? <Loader2 className="mr-1.5 h-4 w-4 animate-spin" /> : <Check className="mr-1.5 h-4 w-4" />}
        Approve
      </Button>
      <Button
        size="sm"
        variant="outline"
        disabled={loading !== null}
        onClick={() => run('reject', () => rejectEventAction(eventId))}
      >
        {loading === 'reject' ? <Loader2 className="mr-1.5 h-4 w-4 animate-spin" /> : <X className="mr-1.5 h-4 w-4" />}
        Reject
      </Button>
    </div>
  );
}
