'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Send, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useToast } from '@/hooks/use-toast';
import { publishEventAction } from '@/lib/actions/events';

export function PublishEventButton({ eventId }: { eventId: string }) {
  const router = useRouter();
  const { toast } = useToast();
  const [loading, setLoading] = useState(false);

  async function publish() {
    setLoading(true);
    try {
      await publishEventAction(eventId);
      toast({
        title: 'Event submitted for approval',
        description: 'An admin will review and approve your event.',
      });
      router.refresh();
    } catch (err) {
      toast({
        title: 'Could not publish',
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <Button onClick={publish} disabled={loading} className="shadow-soft">
      {loading ? <Loader2 className="mr-1.5 h-4 w-4 animate-spin" /> : <Send className="mr-1.5 h-4 w-4" />}
      Submit for approval
    </Button>
  );
}
