import Razorpay from 'razorpay';
import crypto from 'node:crypto';

const keyId = process.env.RAZORPAY_KEY_ID;
const keySecret = process.env.RAZORPAY_KEY_SECRET;

if (!keyId || !keySecret) {
  // We don't throw at module load — the app may run in contexts where Razorpay
  // isn't configured yet (build, lint). The server actions throw at call time.
  console.warn('[razorpay] RAZORPAY_KEY_ID or RAZORPAY_KEY_SECRET not set');
}

export const razorpay = new Razorpay({
  key_id: keyId || 'rzp_test_placeholder',
  key_secret: keySecret || 'placeholder',
});

export function verifyRazorpaySignature(
  razorpayOrderId: string,
  razorpayPaymentId: string,
  signature: string,
): boolean {
  const secret = process.env.RAZORPAY_KEY_SECRET;
  if (!secret) {
    throw new Error('RAZORPAY_KEY_SECRET is not configured');
  }
  const expected = crypto
    .createHmac('sha256', secret)
    .update(`${razorpayOrderId}|${razorpayPaymentId}`)
    .digest('hex');
  // Use timing-safe compare to avoid timing attacks.
  const a = Buffer.from(expected, 'hex');
  const b = Buffer.from(signature, 'hex');
  if (a.length !== b.length) return false;
  return crypto.timingSafeEqual(a, b);
}
