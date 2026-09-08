/** @type {import('next').NextConfig} */
const nextConfig = {
  eslint: {
    ignoreDuringBuilds: true,
  },
  images: {
    unoptimized: true,
    remotePatterns: [
      { protocol: 'https', hostname: '0ec90b57d6e95fcbda19832f.supabase.co' },
      { protocol: 'https', hostname: 'supabase.co' },
      { protocol: 'https', hostname: 'images.pexels.com' },
    ],
  },
};

module.exports = nextConfig;
