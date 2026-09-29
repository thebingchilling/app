# app

> **Code agents (e.g. Claude Code): read this file in full before making
> any changes to this repository.**

A collection of small standalone apps, each in its own folder.

## Apps

- [`battery-taskbar/`](battery-taskbar/) — Windows tray icon showing battery
  percentage.
- [`tidewall/`](tidewall/) — Material 3 Android VPN client: mihomo (Clash)
  proxy engine, Direct WireGuard/AmneziaWG and Direct OpenVPN (OpenVPN 3).
- [`pebble/`](pebble/) — FlClash's Flutter UI 1:1, rebranded as Pebble, for
  Android and Windows; imports WireGuard/AmneziaWG `.conf` and OpenVPN `.ovpn`
  as mihomo profiles. Read [`pebble/HANDOFF.md`](pebble/HANDOFF.md) first.

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
