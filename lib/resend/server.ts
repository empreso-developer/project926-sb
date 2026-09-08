import { Resend } from 'resend';

let client: Resend | null = null;

export function getResendClient(): Resend {
  const apiKey = process.env.RESEND_API_KEY;
  if (!apiKey) {
    throw new Error('RESEND_API_KEY is not configured');
  }
  if (!client) {
    client = new Resend(apiKey);
  }
  return client;
}

export function getResendFromAddress(): string {
  return process.env.RESEND_FROM_EMAIL || 'Project926 Tickets <onboarding@resend.dev>';
}

export function getResendReplyTo(): string | undefined {
  return process.env.RESEND_REPLY_TO || undefined;
}
