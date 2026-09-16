# Drive Access

Upload/download files in one Google Drive account from a website, without
signing in to Google each time. Two pieces:

- `worker/` — a Cloudflare Worker backend. Holds a Google refresh token
  (server-side secret, never sent to the browser) and exposes a small API.
  It is the only thing that talks to Google.
- `frontend/` — a static page (login + file list + upload) you host wherever
  you like, e.g. GitHub Pages on another account. It only talks to the
  Worker's API.

Instead of Google sign-in, the site has its own login: one shared password
you set yourself. After logging in once, the browser gets a signed cookie
good for 30 days, so you stay in — that's what gives you "access from
anywhere without repeatedly signing in," without making the whole site
public to anyone who finds the URL.

## 1. Deploy the Worker

```
cd worker
npm install
npx wrangler login
npx wrangler kv namespace create DRIVE_KV
```

Copy the `id` it prints into `wrangler.toml` under `[[kv_namespaces]]`.

```
npx wrangler deploy
```

Note the URL it prints, e.g. `https://drive-access.yoursubdomain.workers.dev`.

## 2. Create a Google OAuth client

1. In [Google Cloud Console](https://console.cloud.google.com/), create a
   project (or reuse one), then enable the **Google Drive API**
   (APIs & Services → Library).
2. APIs & Services → OAuth consent screen: choose **External**, fill in the
   required fields, add yourself as a **test user**, and — importantly —
   set **Publishing status** to **In production**. Leaving it in "Testing"
   makes Google expire your refresh token after 7 days; production status
   avoids that even without going through full verification (you'll click
   through an "unverified app" warning once during setup, which is fine
   for a personal single-user app).
3. APIs & Services → Credentials → Create credentials → **OAuth client ID**,
   type **Web application**. Add an authorized redirect URI:
   `https://<your-worker-url>/oauth/callback`.
4. Note the **Client ID** and **Client secret**.

## 3. Configure the Worker's secrets

```
cd worker
npx wrangler secret put SITE_PASSWORD        # the password the website login uses
npx wrangler secret put SESSION_SECRET       # any long random string, e.g. `openssl rand -hex 32`
npx wrangler secret put GOOGLE_CLIENT_ID
npx wrangler secret put GOOGLE_CLIENT_SECRET
npx wrangler secret put GOOGLE_REDIRECT_URI  # https://<your-worker-url>/oauth/callback
```

Also edit `wrangler.toml`'s `ALLOWED_ORIGIN` to the exact origin your
frontend will be served from (e.g. `https://yourusername.github.io`), then
redeploy: `npx wrangler deploy`.

## 4. Deploy the frontend

Edit `frontend/app.js` and set `API_BASE` to your Worker's URL from step 1.
Then publish `frontend/index.html`, `app.js`, and `style.css` to your
GitHub Pages site (copy them into that repo, or point Pages at this
folder).

## 5. Connect your Drive (one-time)

1. Open your frontend site, log in with the site password you set in step 3.
2. You'll see a "Google Drive isn't connected yet — Connect it" banner.
   Click it, sign into Google, and approve access.
3. Refresh the page — your files should now list.

That Google sign-in in step 5 happens exactly once (or again only if you
ever revoke access at
[myaccount.google.com/permissions](https://myaccount.google.com/permissions)).
After that, the Worker refreshes its own access token from the stored
refresh token indefinitely.

## Security notes

- Anyone who has the site password gets full read/write access to this
  Drive account. Treat it like a real password — don't put it in a public
  place, and change `SITE_PASSWORD` (`wrangler secret put SITE_PASSWORD`)
  if you ever suspect it's leaked.
- The Google refresh token lives only in Cloudflare KV, scoped to the
  Worker; it's never sent to the browser.
- Login is rate-limited (10 attempts/hour/IP, then a 15 minute lockout) to
  slow down password guessing.
- Cross-site requests are rejected unless their `Origin` matches
  `ALLOWED_ORIGIN`, so a leaked link alone can't be used from another page
  to ride your session.
- Uploads are capped at 100MB (Cloudflare Workers' own request-body limits
  apply too, depending on your plan).
