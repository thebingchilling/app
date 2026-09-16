// Cloudflare Worker: password-gated backend for browsing/uploading/downloading
// files in one Google Drive account from anywhere, without re-doing the
// Google OAuth dance every time.
//
// Flow:
//   1. Owner logs in with a shared password -> gets a long-lived signed
//      session cookie. That's the only "sign in" anyone does day to day.
//   2. Once, the owner (while logged in) visits /oauth/start to grant this
//      app offline access to their Drive. The resulting refresh token is
//      stored in KV and used forever after to mint short-lived access
//      tokens server-side. The browser never sees Google credentials.
//   3. /api/* endpoints list, download, and upload Drive files using that
//      stored refresh token.

const SESSION_COOKIE = "session";
const SESSION_MAX_AGE_SECONDS = 60 * 60 * 24 * 30; // 30 days
const DRIVE_SCOPE = "https://www.googleapis.com/auth/drive";
const LOGIN_ATTEMPT_LIMIT = 10;
const LOGIN_LOCKOUT_SECONDS = 15 * 60;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const origin = request.headers.get("Origin");
    const isAllowedOrigin = origin && origin === env.ALLOWED_ORIGIN;

    if (request.method === "OPTIONS") {
      return corsResponse(new Response(null, { status: 204 }), origin, isAllowedOrigin);
    }

    // CSRF guard: any state-changing request must come from the known
    // frontend origin. Reads (GET) are also gated so a leaked link can't be
    // used to exfiltrate data from a third-party page (CORS blocks the
    // response from being read, but this stops the request from being
    // treated as authenticated in the first place for browser navigations).
    if (origin !== null && !isAllowedOrigin) {
      return new Response("Forbidden origin", { status: 403 });
    }

    try {
      let response;
      if (url.pathname === "/api/login" && request.method === "POST") {
        response = await handleLogin(request, env);
      } else if (url.pathname === "/api/logout" && request.method === "POST") {
        response = handleLogout();
      } else if (url.pathname === "/oauth/start" && request.method === "GET") {
        response = await requireSession(request, env, () => handleOAuthStart(env));
      } else if (url.pathname === "/oauth/callback" && request.method === "GET") {
        response = await handleOAuthCallback(url, env);
      } else if (url.pathname === "/api/status" && request.method === "GET") {
        response = await requireSession(request, env, () => handleStatus(env));
      } else if (url.pathname === "/api/files" && request.method === "GET") {
        response = await requireSession(request, env, () => handleListFiles(url, env));
      } else if (url.pathname === "/api/download" && request.method === "GET") {
        response = await requireSession(request, env, () => handleDownload(url, env));
      } else if (url.pathname === "/api/upload" && request.method === "POST") {
        response = await requireSession(request, env, () => handleUpload(request, env));
      } else {
        response = new Response("Not found", { status: 404 });
      }
      return corsResponse(response, origin, isAllowedOrigin);
    } catch (err) {
      console.error(err);
      return corsResponse(
        jsonResponse({ error: err.message || "Internal error" }, 500),
        origin,
        isAllowedOrigin
      );
    }
  },
};

// ---------- auth ----------

async function handleLogin(request, env) {
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const lockKey = `login_lock:${ip}`;
  const attemptsKey = `login_attempts:${ip}`;

  if (await env.DRIVE_KV.get(lockKey)) {
    return jsonResponse({ error: "Too many attempts, try again later" }, 429);
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return jsonResponse({ error: "Bad request" }, 400);
  }

  const provided = typeof body.password === "string" ? body.password : "";
  const ok = await timingSafeEqualStrings(provided, env.SITE_PASSWORD);

  if (!ok) {
    const attempts = parseInt((await env.DRIVE_KV.get(attemptsKey)) || "0", 10) + 1;
    if (attempts >= LOGIN_ATTEMPT_LIMIT) {
      await env.DRIVE_KV.put(lockKey, "1", { expirationTtl: LOGIN_LOCKOUT_SECONDS });
      await env.DRIVE_KV.delete(attemptsKey);
    } else {
      await env.DRIVE_KV.put(attemptsKey, String(attempts), { expirationTtl: 3600 });
    }
    return jsonResponse({ error: "Invalid password" }, 401);
  }

  await env.DRIVE_KV.delete(attemptsKey);
  const token = await signSession(env, SESSION_MAX_AGE_SECONDS);
  const res = jsonResponse({ ok: true });
  res.headers.append("Set-Cookie", sessionCookie(token, SESSION_MAX_AGE_SECONDS));
  return res;
}

function handleLogout() {
  const res = jsonResponse({ ok: true });
  res.headers.append("Set-Cookie", sessionCookie("", 0));
  return res;
}

async function requireSession(request, env, handler) {
  const cookie = getCookie(request, SESSION_COOKIE);
  if (!cookie || !(await verifySession(env, cookie))) {
    return jsonResponse({ error: "Not authenticated" }, 401);
  }
  return handler();
}

function sessionCookie(token, maxAgeSeconds) {
  // SameSite=None + Secure because the frontend lives on a different origin
  // (GitHub Pages) than this Worker.
  return `${SESSION_COOKIE}=${token}; Path=/; HttpOnly; Secure; SameSite=None; Max-Age=${maxAgeSeconds}`;
}

function getCookie(request, name) {
  const header = request.headers.get("Cookie") || "";
  for (const part of header.split(";")) {
    const [k, ...v] = part.trim().split("=");
    if (k === name) return v.join("=");
  }
  return null;
}

async function signSession(env, maxAgeSeconds) {
  const payload = JSON.stringify({ exp: Math.floor(Date.now() / 1000) + maxAgeSeconds });
  const payloadB64 = base64UrlEncode(payload);
  const sig = await hmac(env.SESSION_SECRET, payloadB64);
  return `${payloadB64}.${sig}`;
}

async function verifySession(env, token) {
  const [payloadB64, sig] = token.split(".");
  if (!payloadB64 || !sig) return false;
  const expected = await hmac(env.SESSION_SECRET, payloadB64);
  if (!(await timingSafeEqualStrings(sig, expected))) return false;
  try {
    const payload = JSON.parse(base64UrlDecode(payloadB64));
    return payload.exp > Math.floor(Date.now() / 1000);
  } catch {
    return false;
  }
}

// ---------- Google OAuth (one-time authorization, then stored refresh token) ----------

async function handleOAuthStart(env) {
  const params = new URLSearchParams({
    client_id: env.GOOGLE_CLIENT_ID,
    redirect_uri: env.GOOGLE_REDIRECT_URI,
    response_type: "code",
    scope: DRIVE_SCOPE,
    access_type: "offline",
    prompt: "consent",
  });
  return Response.redirect(`https://accounts.google.com/o/oauth2/v2/auth?${params}`, 302);
}

async function handleOAuthCallback(url, env) {
  const code = url.searchParams.get("code");
  if (!code) return jsonResponse({ error: "Missing code" }, 400);

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      code,
      client_id: env.GOOGLE_CLIENT_ID,
      client_secret: env.GOOGLE_CLIENT_SECRET,
      redirect_uri: env.GOOGLE_REDIRECT_URI,
      grant_type: "authorization_code",
    }),
  });
  const tokens = await tokenRes.json();
  if (!tokenRes.ok) {
    return jsonResponse({ error: "OAuth exchange failed", detail: tokens }, 502);
  }
  if (!tokens.refresh_token) {
    return jsonResponse({
      error:
        "Google did not return a refresh token. Revoke this app's access at " +
        "https://myaccount.google.com/permissions and try /oauth/start again " +
        "(Google only issues a refresh token on first consent).",
    }, 400);
  }

  await env.DRIVE_KV.put("refresh_token", tokens.refresh_token);
  await env.DRIVE_KV.delete("access_token");
  await env.DRIVE_KV.delete("access_token_expiry");

  return new Response("Google Drive connected. You can close this tab.", {
    status: 200,
    headers: { "Content-Type": "text/plain" },
  });
}

async function getAccessToken(env) {
  const cachedExpiry = parseInt((await env.DRIVE_KV.get("access_token_expiry")) || "0", 10);
  if (cachedExpiry > Date.now() / 1000 + 30) {
    const cached = await env.DRIVE_KV.get("access_token");
    if (cached) return cached;
  }

  const refreshToken = await env.DRIVE_KV.get("refresh_token");
  if (!refreshToken) {
    throw new Error("Google Drive is not connected yet. Visit /oauth/start first.");
  }

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      refresh_token: refreshToken,
      client_id: env.GOOGLE_CLIENT_ID,
      client_secret: env.GOOGLE_CLIENT_SECRET,
      grant_type: "refresh_token",
    }),
  });
  const tokens = await tokenRes.json();
  if (!tokenRes.ok) {
    throw new Error("Failed to refresh Google access token: " + JSON.stringify(tokens));
  }

  await env.DRIVE_KV.put("access_token", tokens.access_token);
  await env.DRIVE_KV.put(
    "access_token_expiry",
    String(Math.floor(Date.now() / 1000) + tokens.expires_in)
  );
  return tokens.access_token;
}

async function handleStatus(env) {
  const connected = !!(await env.DRIVE_KV.get("refresh_token"));
  return jsonResponse({ connected });
}

// ---------- Drive API ----------

async function handleListFiles(url, env) {
  const accessToken = await getAccessToken(env);
  const pageToken = url.searchParams.get("pageToken") || "";
  const q = url.searchParams.get("q") || "";
  const params = new URLSearchParams({
    pageSize: "50",
    fields: "nextPageToken, files(id, name, mimeType, size, modifiedTime, iconLink)",
    orderBy: "folder,modifiedTime desc",
  });
  if (pageToken) params.set("pageToken", pageToken);
  if (q) params.set("q", q);

  const res = await fetch(`https://www.googleapis.com/drive/v3/files?${params}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const data = await res.json();
  if (!res.ok) return jsonResponse({ error: "Drive list failed", detail: data }, 502);
  return jsonResponse(data);
}

async function handleDownload(url, env) {
  const fileId = url.searchParams.get("id");
  if (!fileId) return jsonResponse({ error: "Missing id" }, 400);
  const accessToken = await getAccessToken(env);

  const metaRes = await fetch(
    `https://www.googleapis.com/drive/v3/files/${encodeURIComponent(fileId)}?fields=name,mimeType`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  const meta = await metaRes.json();
  if (!metaRes.ok) return jsonResponse({ error: "Drive metadata failed", detail: meta }, 502);

  let driveUrl;
  if (meta.mimeType && meta.mimeType.startsWith("application/vnd.google-apps.")) {
    const exportMime = googleDocsExportMime(meta.mimeType);
    driveUrl = `https://www.googleapis.com/drive/v3/files/${encodeURIComponent(
      fileId
    )}/export?mimeType=${encodeURIComponent(exportMime)}`;
  } else {
    driveUrl = `https://www.googleapis.com/drive/v3/files/${encodeURIComponent(
      fileId
    )}?alt=media`;
  }

  const fileRes = await fetch(driveUrl, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (!fileRes.ok) {
    const detail = await fileRes.text();
    return jsonResponse({ error: "Drive download failed", detail }, 502);
  }

  const headers = new Headers();
  headers.set("Content-Type", fileRes.headers.get("Content-Type") || "application/octet-stream");
  headers.set(
    "Content-Disposition",
    `attachment; filename="${(meta.name || "download").replace(/"/g, "")}"`
  );
  return new Response(fileRes.body, { status: 200, headers });
}

function googleDocsExportMime(mimeType) {
  const map = {
    "application/vnd.google-apps.document": "application/pdf",
    "application/vnd.google-apps.spreadsheet":
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.google-apps.presentation":
      "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  };
  return map[mimeType] || "application/pdf";
}

async function handleUpload(request, env) {
  const fileName = decodeURIComponent(request.headers.get("X-File-Name") || "upload.bin");
  const contentType = request.headers.get("Content-Type") || "application/octet-stream";
  const fileBytes = await request.arrayBuffer();
  if (fileBytes.byteLength === 0) return jsonResponse({ error: "Empty upload" }, 400);
  if (fileBytes.byteLength > 100 * 1024 * 1024) {
    return jsonResponse({ error: "File too large for this endpoint (100MB limit)" }, 413);
  }

  const accessToken = await getAccessToken(env);
  const boundary = "drive-access-" + crypto.randomUUID();
  const metadata = JSON.stringify({ name: fileName });

  const parts = [
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${metadata}\r\n`,
    `--${boundary}\r\nContent-Type: ${contentType}\r\n\r\n`,
  ];
  const encoder = new TextEncoder();
  const head = encoder.encode(parts.join(""));
  const tail = encoder.encode(`\r\n--${boundary}--`);
  const body = new Uint8Array(head.byteLength + fileBytes.byteLength + tail.byteLength);
  body.set(head, 0);
  body.set(new Uint8Array(fileBytes), head.byteLength);
  body.set(tail, head.byteLength + fileBytes.byteLength);

  const res = await fetch(
    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,mimeType,size,modifiedTime",
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": `multipart/related; boundary=${boundary}`,
      },
      body,
    }
  );
  const data = await res.json();
  if (!res.ok) return jsonResponse({ error: "Drive upload failed", detail: data }, 502);
  return jsonResponse(data);
}

// ---------- helpers ----------

function jsonResponse(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function corsResponse(response, origin, isAllowedOrigin) {
  const res = new Response(response.body, response);
  if (isAllowedOrigin) {
    res.headers.set("Access-Control-Allow-Origin", origin);
    res.headers.set("Access-Control-Allow-Credentials", "true");
    res.headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.headers.set("Access-Control-Allow-Headers", "Content-Type, X-File-Name");
  }
  return res;
}

function base64UrlEncode(str) {
  return btoa(unescape(encodeURIComponent(str)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function base64UrlDecode(str) {
  const padded = str.replace(/-/g, "+").replace(/_/g, "/").padEnd(str.length + ((4 - (str.length % 4)) % 4), "=");
  return decodeURIComponent(escape(atob(padded)));
}

async function hmac(secret, message) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
  return base64UrlEncode(String.fromCharCode(...new Uint8Array(sig)));
}

async function timingSafeEqualStrings(a, b) {
  const enc = new TextEncoder();
  const aBytes = enc.encode(a || "");
  const bBytes = enc.encode(b || "");
  // Hash both to a fixed length first so length differences don't leak timing.
  const [aHash, bHash] = await Promise.all([sha256(aBytes), sha256(bBytes)]);
  let diff = 0;
  for (let i = 0; i < aHash.length; i++) diff |= aHash[i] ^ bHash[i];
  return diff === 0;
}

async function sha256(bytes) {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return new Uint8Array(digest);
}
