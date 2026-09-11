"""Simple local web UI for batch-downloading videos with yt-dlp.

Run with `python app.py`, then open http://127.0.0.1:5000 in a browser.
"""

import threading
import uuid
from pathlib import Path

from flask import Flask, jsonify, render_template, request, send_from_directory
import yt_dlp

BASE_DIR = Path(__file__).resolve().parent
DOWNLOAD_DIR = BASE_DIR / "downloads"
DOWNLOAD_DIR.mkdir(exist_ok=True)

app = Flask(__name__)

jobs = {}
jobs_lock = threading.Lock()


def run_job(job_id, audio_only):
    for entry in jobs[job_id]["items"]:
        entry["status"] = "downloading"

        def hook(d, entry=entry):
            if d["status"] == "downloading":
                total = d.get("total_bytes") or d.get("total_bytes_estimate")
                downloaded = d.get("downloaded_bytes", 0)
                if total:
                    entry["progress"] = round(downloaded / total * 100, 1)
            elif d["status"] == "finished":
                entry["progress"] = 100

        ydl_opts = {
            "outtmpl": str(DOWNLOAD_DIR / "%(title).200B [%(id)s].%(ext)s"),
            "progress_hooks": [hook],
            "quiet": True,
            "no_warnings": True,
        }
        if audio_only:
            ydl_opts["format"] = "bestaudio/best"
            ydl_opts["postprocessors"] = [
                {
                    "key": "FFmpegExtractAudio",
                    "preferredcodec": "mp3",
                    "preferredquality": "192",
                }
            ]
        else:
            ydl_opts["format"] = "bestvideo+bestaudio/best"
            ydl_opts["merge_output_format"] = "mp4"

        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(entry["url"], download=True)
                filename = ydl.prepare_filename(info)
                if audio_only:
                    filename = str(Path(filename).with_suffix(".mp3"))
                entry["filename"] = Path(filename).name
                entry["status"] = "done"
                entry["progress"] = 100
        except Exception as exc:
            entry["status"] = "error"
            entry["error"] = str(exc)

    jobs[job_id]["done"] = True


@app.route("/")
def index():
    return render_template("index.html")


@app.route("/api/download", methods=["POST"])
def api_download():
    data = request.get_json(force=True)
    urls = [u.strip() for u in data.get("urls", []) if u.strip()]
    audio_only = bool(data.get("audio_only"))

    if not urls:
        return jsonify({"error": "No URLs provided"}), 400

    job_id = uuid.uuid4().hex
    with jobs_lock:
        jobs[job_id] = {
            "items": [
                {"url": u, "status": "queued", "progress": 0, "filename": None, "error": None}
                for u in urls
            ],
            "done": False,
        }

    thread = threading.Thread(target=run_job, args=(job_id, audio_only), daemon=True)
    thread.start()

    return jsonify({"job_id": job_id})


@app.route("/api/status/<job_id>")
def api_status(job_id):
    job = jobs.get(job_id)
    if not job:
        return jsonify({"error": "Unknown job"}), 404
    return jsonify(job)


@app.route("/files/<path:filename>")
def files(filename):
    return send_from_directory(DOWNLOAD_DIR, filename, as_attachment=True)


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5000, debug=False)
