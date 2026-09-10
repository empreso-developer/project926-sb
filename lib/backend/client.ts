import 'server-only';
import { auth } from '@clerk/nextjs/server';

/**
 * Phase H: the ONLY place that talks to the Spring Boot backend. Server
 * Components, Server Actions, and Route Handlers call this — the browser
 * never does (see the Phase H report's "Frontend Architecture" section for
 * why: no CORS, no bearer-token handling in client code, secrets never
 * reach the browser, and the existing same-origin relative-fetch calls in
 * booking-widget.tsx / ticket-scanner.tsx / site-header.tsx keep working
 * completely unchanged).
 *
 * `server-only` makes any accidental import from a 'use client' component
 * fail the build loudly instead of silently leaking this into browser code.
 */

const RAW_BACKEND_URL = process.env.PROJECT926_BACKEND_URL ?? 'http://localhost:8080';
const BACKEND_URL = RAW_BACKEND_URL.replace(/\/+$/, '');

export class BackendApiError extends Error {
  readonly status: number;
  readonly body: unknown;

  constructor(status: number, body: unknown, message: string) {
    super(message);
    this.name = 'BackendApiError';
    this.status = status;
    this.body = body;
  }
}

function extractErrorMessage(status: number, data: unknown): string {
  if (data && typeof data === 'object' && 'error' in data) {
    const value = (data as { error?: unknown }).error;
    if (typeof value === 'string' && value.length > 0) return value;
  }
  return `Backend request failed (${status})`;
}

/**
 * Returns the current request's Clerk session token, or null if signed
 * out. This is the SAME session JWT Clerk already issues for the existing
 * frontend session — Spring's SecurityConfig validates it via Clerk's
 * standard JWKS endpoint (Phase A), so no second token/auth system is
 * introduced.
 */
async function getBearerToken(): Promise<string | null> {
  const { getToken } = await auth();
  return getToken();
}

interface BackendFetchOptions {
  method?: string;
  body?: unknown;
  /** Default true. Set false only for the two endpoints SecurityConfig permits without a token (GET /api/v1/events, /api/v1/events/{id}). */
  authenticated?: boolean;
}

/**
 * For Server Components/Server Actions: throws BackendApiError on any
 * non-2xx response, mirroring the `throw new Error(...)` pattern the
 * direct-Supabase code this replaces already used — callers that already
 * catch/report errors that way need no change to their catch blocks.
 */
export async function backendFetch<T>(path: string, options: BackendFetchOptions = {}): Promise<T> {
  const { method = 'GET', body, authenticated = true } = options;
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  if (authenticated) {
    const token = await getBearerToken();
    if (!token) {
      throw new BackendApiError(401, null, 'Not authenticated');
    }
    headers.Authorization = `Bearer ${token}`;
  }

  const res = await fetch(`${BACKEND_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
    cache: 'no-store',
  });

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;

  if (!res.ok) {
    throw new BackendApiError(res.status, data, extractErrorMessage(res.status, data));
  }
  return data as T;
}

/**
 * For the three browser-facing proxy Route Handlers (create-order, verify,
 * check-in): never throws on a non-2xx response — returns Spring's exact
 * status and parsed JSON body so the route handler can forward them
 * verbatim. These three endpoints represent many business outcomes as HTTP
 * 200 with a `status`/`success` field rather than an HTTP error (see e.g.
 * CheckInResponse's contract), so "non-2xx" is not the same thing as
 * "business failure" here — only the route handler's caller (the browser
 * code already written in Phase D/F) knows how to interpret the body.
 */
export async function backendFetchRaw(
  path: string,
  options: { method?: string; body?: unknown; token: string | null },
): Promise<{ status: number; data: unknown }> {
  const { method = 'POST', body, token } = options;
  // FormData (e.g. the banner-upload proxy) is forwarded as-is — fetch
  // sets its own multipart Content-Type with the correct boundary, which
  // must NOT be overridden here, unlike every other (JSON) caller.
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData;
  const headers: Record<string, string> = {};
  if (!isFormData) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${BACKEND_URL}${path}`, {
    method,
    headers,
    body: isFormData ? (body as FormData) : body !== undefined ? JSON.stringify(body) : undefined,
    cache: 'no-store',
  });

  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  return { status: res.status, data };
}

export { getBearerToken };
