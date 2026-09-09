import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EventForm } from '@/components/event-form';

export default function NewEventPage() {
  return (
    <div className="container mx-auto max-w-3xl px-4 py-10 sm:px-6 lg:px-8">
      <Button asChild variant="ghost" size="sm" className="mb-4 -ml-2">
        <Link href="/p/dashboard/organizer">
          <ArrowLeft className="mr-1.5 h-4 w-4" />
          Back to dashboard
        </Link>
      </Button>
      <h1 className="font-display text-3xl font-bold tracking-tight">Create a new event</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Fill in the details below. You can add ticket tiers next.
      </p>
      <div className="mt-8">
        <EventForm />
      </div>
    </div>
  );
}
