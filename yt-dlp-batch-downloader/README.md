# yt-dlp Batch Downloader

A simple local web page for downloading multiple videos at once using
[yt-dlp](https://github.com/yt-dlp/yt-dlp), without touching the command line
yourself.

A plain web page can't run yt-dlp on its own — it needs a program running on
your machine with network access. This is a tiny local server (Python +
Flask) that does the downloading, with a one-page browser UI in front of it.

## Setup

1. Install [Python 3.9+](https://www.python.org/downloads/).
2. Install [ffmpeg](https://ffmpeg.org/download.html) (needed to merge
   video+audio and to convert to mp3). On macOS: `brew install ffmpeg`. On
   Windows: `winget install ffmpeg` or download a build and add it to PATH.
   On Linux: use your package manager, e.g. `sudo apt install ffmpeg`.
3. In this folder, install the Python dependencies:

   ```
   pip install -r requirements.txt
   ```

## Run

```
python app.py
```

Then open **http://127.0.0.1:5000** in your browser.

## Use

1. Paste one or more video URLs into the box, one per line.
2. Check "Audio only (mp3)" if you just want the audio.
3. Click **Download**.
4. Watch progress per URL; click **Save** next to each finished item to save
   it to your computer.

Downloaded files are also saved on disk in this folder's `downloads/`
subfolder while the server is running.

## Notes

- Only download content you have the right to download.
- Leave the terminal running `python app.py` open while using the page —
  closing it stops the server.
- If a download fails, hover over its row to see the error message.
