'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { CheckCircle2, Camera, Ticket, XCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { formatDateTime } from '@/lib/utils';

const SCANNER_ELEMENT_ID = 'ticket-qr-reader';

type CheckInStatus =
  | 'checked_in'
  | 'already_checked_in'
  | 'invalid'
  | 'wrong_event'
  | 'payment_not_confirmed'
  | 'cancelled'
  | 'unauthorized'
  | 'error';

interface CheckInResult {
  success: boolean;
  status: CheckInStatus;
  attendee?: { name: string; email: string; ticketTypes: Array<{ name: string; quantity: number }> };
  booking?: { reference: string };
  checked_in_at?: string | null;
  error?: string;
}

const STATUS_PRESENTATION: Record<
  CheckInStatus,
  { title: string; message: string; tone: 'success' | 'error' | 'warning' }
> = {
  checked_in: { title: 'VALID TICKET', message: 'Ticket confirmed', tone: 'success' },
  already_checked_in: {
    title: 'ALREADY CHECKED IN',
    message: 'This ticket has already been used.',
    tone: 'warning',
  },
  invalid: { title: 'INVALID TICKET', message: 'This QR code is not valid for this event.', tone: 'error' },
  wrong_event: { title: 'WRONG EVENT', message: 'This ticket belongs to another event.', tone: 'error' },
  payment_not_confirmed: {
    title: 'PAYMENT NOT CONFIRMED',
    message: 'This ticket has not been confirmed.',
    tone: 'error',
  },
  cancelled: { title: 'BOOKING CANCELLED', message: 'This booking was cancelled.', tone: 'error' },
  unauthorized: { title: 'NOT AUTHORIZED', message: "You can't scan tickets for this event.", tone: 'error' },
  error: { title: 'SCAN FAILED', message: 'Something went wrong. Try again.', tone: 'error' },
};

export function TicketScanner({ eventId }: { eventId: string }) {
  const [phase, setPhase] = useState<'starting' | 'scanning' | 'submitting' | 'result' | 'camera-error'>(
    'starting',
  );
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [result, setResult] = useState<CheckInResult | null>(null);
  const [manualReference, setManualReference] = useState('');
  const [manualOpen, setManualOpen] = useState(false);
  const scannerRef = useRef<InstanceType<typeof import('html5-qrcode').Html5Qrcode> | null>(null);
  const submittingRef = useRef(false);

  const submitCheckIn = useCallback(
    async (payload: { booking_id?: string; reference?: string; event_id?: string }) => {
      if (submittingRef.current) return;
      submittingRef.current = true;
      setPhase('submitting');
      try {
        const res = await fetch(`/project926/api/organizer/events/${eventId}/check-in`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = (await res.json()) as CheckInResult;
        setResult(data);
      } catch {
        setResult({ success: false, status: 'error' });
      } finally {
        submittingRef.current = false;
        setPhase('result');
      }
    },
    [eventId],
  );

  const handleDecoded = useCallback(
    (decodedText: string) => {
      let payload: { booking_id?: string; reference?: string; event_id?: string } | null = null;
      try {
        const parsed = JSON.parse(decodedText);
        if (parsed && typeof parsed.booking_id === 'string') {
          payload = { booking_id: parsed.booking_id, reference: parsed.ref, event_id: parsed.event_id };
        }
      } catch {
        payload = null;
      }

      if (!payload) {
        setResult({ success: false, status: 'invalid' });
        setPhase('result');
        return;
      }
      void submitCheckIn(payload);
    },
    [submitCheckIn],
  );

  const stopCamera = useCallback(async () => {
    const scanner = scannerRef.current;
    if (!scanner) return;
    try {
      await scanner.stop();
    } catch {
      // already stopped
    }
  }, []);

  const startCamera = useCallback(async () => {
    setCameraError(null);
    setPhase('starting');
    try {
      const { Html5Qrcode } = await import('html5-qrcode');
      if (!scannerRef.current) {
        scannerRef.current = new Html5Qrcode(SCANNER_ELEMENT_ID);
      }
      await scannerRef.current.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 250, height: 250 } },
        (decodedText) => {
          // Pause immediately so one decode never fires twice.
          void stopCamera();
          handleDecoded(decodedText);
        },
        () => {
          // Per-frame "no QR found" — expected constantly while aiming, ignore.
        },
      );
      setPhase('scanning');
    } catch (err) {
      setCameraError(
        err instanceof Error && /permission/i.test(err.message)
          ? 'Allow camera access to scan tickets.'
          : 'Could not start the camera. Allow camera access to scan tickets.',
      );
      setPhase('camera-error');
    }
  }, [handleDecoded, stopCamera]);

  useEffect(() => {
    void startCamera();
    return () => {
      void stopCamera();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const scanNext = () => {
    setResult(null);
    void startCamera();
  };

  const presentation = result ? STATUS_PRESENTATION[result.status] : null;

  return (
    <div className="space-y-4">
      <Card className="overflow-hidden shadow-soft">
        <div className="relative aspect-square w-full bg-black">
          <div id={SCANNER_ELEMENT_ID} className="h-full w-full [&_video]:h-full [&_video]:w-full [&_video]:object-cover" />
          {phase === 'starting' && (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-black/80 text-white">
              <Camera className="h-8 w-8 animate-pulse" />
              <p className="text-sm">Starting camera...</p>
            </div>
          )}
          {phase === 'camera-error' && (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-black/90 px-6 text-center text-white">
              <Camera className="h-8 w-8 text-destructive" />
              <p className="text-sm">{cameraError}</p>
              <Button size="sm" onClick={() => void startCamera()}>
                Try again
              </Button>
            </div>
          )}
          {(phase === 'submitting' || phase === 'result') && (
            <div className="absolute inset-0 bg-black/60" />
          )}
        </div>
        {phase === 'scanning' && (
          <p className="p-4 text-center text-sm text-muted-foreground">
            Point your camera at a ticket QR code.
          </p>
        )}
        {phase === 'submitting' && (
          <p className="p-4 text-center text-sm text-muted-foreground">Checking ticket...</p>
        )}
      </Card>

      {phase === 'result' && result && presentation && (
        <Card
          className={`p-6 shadow-soft ${
            presentation.tone === 'success'
              ? 'border-success/40 bg-success/5'
              : presentation.tone === 'warning'
              ? 'border-warning/40 bg-warning/5'
              : 'border-destructive/40 bg-destructive/5'
          }`}
        >
          <div className="flex items-center gap-2">
            {presentation.tone === 'success' ? (
              <CheckCircle2 className="h-6 w-6 text-success" />
            ) : (
              <XCircle className={`h-6 w-6 ${presentation.tone === 'warning' ? 'text-warning' : 'text-destructive'}`} />
            )}
            <h2 className="font-display text-xl font-bold">{presentation.title}</h2>
          </div>
          <p className="mt-1 text-sm text-muted-foreground">{presentation.message}</p>

          {result.attendee && (
            <div className="mt-4 space-y-1 border-t pt-4">
              <div className="flex items-center gap-2 font-medium">
                <Ticket className="h-4 w-4 text-muted-foreground" />
                {result.attendee.name}
              </div>
              {result.attendee.ticketTypes.map((t, i) => (
                <div key={i} className="text-sm text-muted-foreground">
                  {t.name} &times; {t.quantity}
                </div>
              ))}
              {result.booking && (
                <div className="pt-1 font-mono text-xs text-muted-foreground">{result.booking.reference}</div>
              )}
              {result.checked_in_at && (
                <div className="pt-1 text-xs text-muted-foreground">
                  Checked in at {formatDateTime(result.checked_in_at)}
                </div>
              )}
            </div>
          )}

          <Button className="mt-5 w-full shadow-soft" size="lg" onClick={scanNext}>
            Scan Next Ticket
          </Button>
        </Card>
      )}

      <Card className="p-4 shadow-soft">
        <button
          type="button"
          className="text-sm font-medium text-muted-foreground underline-offset-2 hover:underline"
          onClick={() => setManualOpen((v) => !v)}
        >
          {manualOpen ? 'Hide manual entry' : 'Enter booking reference manually'}
        </button>
        {manualOpen && (
          <form
            className="mt-3 flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              if (!manualReference.trim()) return;
              void submitCheckIn({ reference: manualReference.trim() });
            }}
          >
            <Input
              value={manualReference}
              onChange={(e) => setManualReference(e.target.value)}
              placeholder="BK-XXXXXXXX-XXXXXX"
              className="flex-1"
            />
            <Button type="submit" disabled={phase === 'submitting'}>
              Check in
            </Button>
          </form>
        )}
      </Card>
    </div>
  );
}
