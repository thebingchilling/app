# app

> **Code agents (e.g. Claude Code): read this file in full before making
> any changes to this repository.**

A collection of small standalone apps, each in its own folder.

## Apps

- [`battery-taskbar/`](battery-taskbar/) — Windows tray icon showing battery
  percentage.
- [`pebble/`](pebble/) — FlClash v0.8.98 (Android) rebranded as Pebble; imports
  WireGuard/AmneziaWG `.conf` and OpenVPN `.ovpn` into FlClash's own engine.
  Read [`pebble/HANDOFF.md`](pebble/HANDOFF.md) first.
- [`lychee/`](lychee/) — Lychee 荔枝, an Android keyboard (HeliBoard + Rime)
  for English, Mandarin and Cantonese that shows Yale, pinyin and English under
  every Chinese candidate; optional offline voice and handwriting downloads.
  Read [`lychee/HANDOFF.md`](lychee/HANDOFF.md) first.
- [`quartz/`](quartz/) — Quartz (working name), an Android camera with a
  Pixel Camera-style UI on the phone's own Camera2 engine: full resolution/
  frame-rate/lens control in every mode, no image enhancement ever. Plan only
  so far; read [`quartz/PLAN.md`](quartz/PLAN.md) first.
- [`wren/`](wren/) — Wren (working name), a Gmail-style Material 3 email app with instant
  push for Outlook.com and Gmail, forked from Thunderbird for Android. Read
  [`wren/PLAN.md`](wren/PLAN.md) first. Plan only so far.

## Purpose

This repo is for making projects. Each project is standalone, living in its
own folder. When starting a new project, disregard previous ones — they are
independent and unrelated.

## Contribution Policy

**ALWAYS commit and push directly to `main`. There is no exception to this.**

- Every change, no matter how small, goes straight onto `main`.
- Never create a local or remote feature/topic branch for work in this repo.
- Never open a pull request against this repo — there is nothing to merge,
  because there is only ever one branch: `main`.
- If your environment or workflow defaults to creating a branch (e.g. a
  designated feature branch for a session), that default does not apply
  here: check out or switch to `main` and commit/push there instead.
