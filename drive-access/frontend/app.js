// Fill this in after you deploy the Worker, e.g.
// "https://drive-access.yoursubdomain.workers.dev"
const API_BASE = "https://REPLACE_WITH_YOUR_WORKER_URL";

const loginPanel = document.getElementById("login-panel");
const appPanel = document.getElementById("app-panel");
const loginError = document.getElementById("login-error");
const uploadError = document.getElementById("upload-error");
const fileList = document.getElementById("file-list");
const connectBanner = document.getElementById("connect-banner");
const connectLink = document.getElementById("connect-link");
const loadMoreBtn = document.getElementById("load-more-btn");

let nextPageToken = null;

async function api(path, options = {}) {
  const res = await fetch(API_BASE + path, {
    ...options,
    credentials: "include",
  });
  if (res.status === 401) {
    showLogin();
    throw new Error("Not authenticated");
  }
  return res;
}

function showLogin() {
  loginPanel.classList.remove("hidden");
  appPanel.classList.add("hidden");
}

function showApp() {
  loginPanel.classList.add("hidden");
  appPanel.classList.remove("hidden");
}

document.getElementById("login-btn").addEventListener("click", async () => {
  loginError.textContent = "";
  const password = document.getElementById("password").value;
  try {
    const res = await fetch(API_BASE + "/api/login", {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ password }),
    });
    const data = await res.json();
    if (!res.ok) {
      loginError.textContent = data.error || "Login failed";
      return;
    }
    document.getElementById("password").value = "";
    await init();
  } catch (err) {
    loginError.textContent = "Network error";
  }
});

document.getElementById("logout-btn").addEventListener("click", async () => {
  await fetch(API_BASE + "/api/logout", { method: "POST", credentials: "include" });
  showLogin();
});

document.getElementById("upload-btn").addEventListener("click", async () => {
  uploadError.textContent = "";
  const input = document.getElementById("file-input");
  const file = input.files[0];
  if (!file) {
    uploadError.textContent = "Choose a file first";
    return;
  }
  const btn = document.getElementById("upload-btn");
  btn.disabled = true;
  btn.textContent = "Uploading...";
  try {
    const res = await api("/api/upload", {
      method: "POST",
      headers: {
        "X-File-Name": encodeURIComponent(file.name),
        "Content-Type": file.type || "application/octet-stream",
      },
      body: file,
    });
    const data = await res.json();
    if (!res.ok) {
      uploadError.textContent = data.error || "Upload failed";
    } else {
      input.value = "";
      nextPageToken = null;
      await loadFiles(true);
    }
  } catch (err) {
    uploadError.textContent = err.message;
  } finally {
    btn.disabled = false;
    btn.textContent = "Upload";
  }
});

loadMoreBtn.addEventListener("click", () => loadFiles(false));

async function loadFiles(reset) {
  const qs = nextPageToken ? `?pageToken=${encodeURIComponent(nextPageToken)}` : "";
  const res = await api("/api/files" + qs);
  const data = await res.json();
  if (!res.ok) {
    fileList.textContent = data.error || "Failed to load files";
    return;
  }
  if (reset) fileList.innerHTML = "";
  for (const file of data.files || []) {
    fileList.appendChild(renderFileRow(file));
  }
  nextPageToken = data.nextPageToken || null;
  loadMoreBtn.classList.toggle("hidden", !nextPageToken);
}

function renderFileRow(file) {
  const row = document.createElement("div");
  row.className = "row";

  const info = document.createElement("div");
  info.style.overflow = "hidden";
  const name = document.createElement("div");
  name.className = "file-name";
  name.textContent = file.name;
  const meta = document.createElement("div");
  meta.className = "file-meta";
  meta.textContent = file.size ? `${formatBytes(file.size)} · ${file.mimeType}` : file.mimeType;
  info.appendChild(name);
  info.appendChild(meta);

  const downloadBtn = document.createElement("button");
  downloadBtn.className = "secondary";
  downloadBtn.textContent = "Download";
  downloadBtn.addEventListener("click", () => downloadFile(file));

  row.appendChild(info);
  row.appendChild(downloadBtn);
  return row;
}

async function downloadFile(file) {
  const res = await api(`/api/download?id=${encodeURIComponent(file.id)}`);
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    alert(data.error || "Download failed");
    return;
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = file.name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function formatBytes(bytes) {
  const n = Number(bytes);
  if (!n) return "";
  const units = ["B", "KB", "MB", "GB"];
  let i = 0;
  let val = n;
  while (val >= 1024 && i < units.length - 1) {
    val /= 1024;
    i++;
  }
  return `${val.toFixed(val < 10 && i > 0 ? 1 : 0)} ${units[i]}`;
}

async function init() {
  try {
    const statusRes = await api("/api/status");
    const status = await statusRes.json();
    showApp();
    if (!status.connected) {
      connectBanner.classList.remove("hidden");
      connectLink.href = API_BASE + "/oauth/start";
    } else {
      connectBanner.classList.add("hidden");
      await loadFiles(true);
    }
  } catch {
    showLogin();
  }
}

init();
