import { NextResponse } from "next/server";
import { auth } from "@clerk/nextjs/server";
import { supabaseAdmin } from "@/lib/supabase/server";

export async function GET() {
  const { userId } = await auth();

  if (!userId) {
    return NextResponse.json({ role: null }, { status: 401 });
  }

  const { data, error } = await supabaseAdmin
    .from("profiles")
    .select("role")
    .eq("id", userId)
    .maybeSingle();

  if (error) {
    console.error("Failed to load profile:", error);
    return NextResponse.json(
      { error: "Failed to load profile" },
      { status: 500 }
    );
  }

  return NextResponse.json({
    role: data?.role ?? "customer",
  });
}
