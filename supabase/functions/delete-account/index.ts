import { createClient, type SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2.119.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return json(405, { error: "METHOD_NOT_ALLOWED" });

  const authorization = request.headers.get("Authorization") ?? "";
  const token = authorization.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!token) return json(401, { error: "AUTH_REQUIRED" });

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) return json(503, { error: "SERVICE_NOT_CONFIGURED" });

  const userClient = createClient(url, anonKey, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: { user }, error: userError } = await userClient.auth.getUser(token);
  if (userError || !user) return json(401, { error: "SESSION_INVALID" });

  const admin = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });
  try {
    for (const bucket of ["pace-private-tracks", "pace-private-backups"]) {
      const files = await listUserObjects(admin, bucket, user.id);
      for (let offset = 0; offset < files.length; offset += 100) {
        const { error } = await admin.storage.from(bucket).remove(files.slice(offset, offset + 100));
        if (error) return json(503, { error: "STORAGE_DELETE_FAILED" });
      }
    }
    const { error } = await admin.auth.admin.deleteUser(user.id);
    if (error) return json(503, { error: "ACCOUNT_DELETE_FAILED" });
    return json(200, { deleted: true });
  } catch {
    return json(503, { error: "ACCOUNT_DELETE_FAILED" });
  }
});

async function listUserObjects(
  admin: SupabaseClient,
  bucket: string,
  userId: string,
): Promise<string[]> {
  const files: string[] = [];
  const folders = [userId];
  let visited = 0;
  while (folders.length > 0) {
    const folder = folders.shift()!;
    for (let offset = 0; ; offset += 1000) {
      const { data, error } = await admin.storage.from(bucket).list(folder, { limit: 1000, offset });
      if (error) throw new Error("Storage inventory failed");
      for (const item of data ?? []) {
        visited++;
        if (visited > 20_000) throw new Error("Storage inventory limit exceeded");
        const path = `${folder}/${item.name}`;
        if (item.id == null && item.metadata == null) folders.push(path);
        else files.push(path);
      }
      if ((data?.length ?? 0) < 1000) break;
    }
  }
  return files;
}
