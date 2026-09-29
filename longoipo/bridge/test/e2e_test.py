#!/usr/bin/env python3
"""End-to-end test: Java bridge <-> independent Python rc4-md5 Shadowsocks server (TCP + UDP).

The server here is written separately from the Java code (hashlib MD5 + a pure-Python RC4), so a
round trip proves both sides agree on the protocol. RC4/key derivation are cross-checked against
OpenSSL in BridgeUnitTest.

Usage: e2e_test.py <classes_dir> <org_json_jar>
"""
import asyncio
import hashlib
import os
import socket
import struct
import subprocess
import sys
import threading
import time

PASSWORD = b"testpass"


def rc4_stream(key):
    s = list(range(256))
    j = 0
    for i in range(256):
        j = (j + s[i] + key[i % len(key)]) & 0xFF
        s[i], s[j] = s[j], s[i]
    i = j = 0

    def crypt(data):
        nonlocal i, j
        out = bytearray(len(data))
        for n, b in enumerate(data):
            i = (i + 1) & 0xFF
            j = (j + s[i]) & 0xFF
            s[i], s[j] = s[j], s[i]
            out[n] = b ^ s[(s[i] + s[j]) & 0xFF]
        return bytes(out)

    return crypt


MASTER = hashlib.md5(PASSWORD).digest()


def cipher(iv):
    return rc4_stream(hashlib.md5(MASTER + iv).digest())


def addr_header(host, port):
    try:
        return b"\x01" + socket.inet_aton(host) + struct.pack(">H", port)
    except OSError:
        h = host.encode()
        return b"\x03" + bytes([len(h)]) + h + struct.pack(">H", port)


def parse_header(buf):
    """Returns (host, port, header_len) or None if incomplete."""
    if len(buf) < 1:
        return None
    t = buf[0]
    if t == 1 and len(buf) >= 7:
        return socket.inet_ntoa(buf[1:5]), struct.unpack(">H", buf[5:7])[0], 7
    if t == 3 and len(buf) >= 2 and len(buf) >= 2 + buf[1] + 2:
        n = buf[1]
        return buf[2:2 + n].decode(), struct.unpack(">H", buf[2 + n:4 + n])[0], 4 + n
    return None


# ---------------------------------------------------------------- targets (echo servers)

def start_tcp_echo():
    srv = socket.socket()
    srv.bind(("127.0.0.1", 0))
    srv.listen(16)

    def handle(c):
        with c:
            while True:
                d = c.recv(65536)
                if not d:
                    break
                c.sendall(d)

    def loop():
        while True:
            c, _ = srv.accept()
            threading.Thread(target=handle, args=(c,), daemon=True).start()

    threading.Thread(target=loop, daemon=True).start()
    return srv.getsockname()[1]


def start_udp_echo():
    u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    u.bind(("127.0.0.1", 0))

    def loop():
        while True:
            d, a = u.recvfrom(65535)
            u.sendto(d, a)

    threading.Thread(target=loop, daemon=True).start()
    return u.getsockname()[1]


# ---------------------------------------------------------------- rc4-md5 shadowsocks server

async def ss_tcp_client(reader, writer):
    try:
        iv = await reader.readexactly(16)
        dec = cipher(iv)
        buf = b""
        parsed = None
        while parsed is None:
            chunk = await reader.read(65536)
            if not chunk:
                return
            buf += dec(chunk)
            parsed = parse_header(buf)
        host, port, hl = parsed
        rest = buf[hl:]
        tr, tw = await asyncio.open_connection(host, port)
        out_iv = os.urandom(16)
        enc = cipher(out_iv)
        writer.write(out_iv)
        if rest:
            tw.write(rest)

        async def up():
            try:
                while True:
                    d = await reader.read(65536)
                    if not d:
                        break
                    tw.write(dec(d))
                    await tw.drain()
                if tw.can_write_eof():
                    tw.write_eof()
            except Exception:
                pass

        async def down():
            try:
                while True:
                    d = await tr.read(65536)
                    if not d:
                        break
                    writer.write(enc(d))
                    await writer.drain()
                if writer.can_write_eof():
                    writer.write_eof()
            except Exception:
                pass

        await asyncio.gather(up(), down())
        tw.close()
    except Exception:
        pass
    finally:
        writer.close()


class SsUdp(asyncio.DatagramProtocol):
    def connection_made(self, transport):
        self.transport = transport

    def datagram_received(self, data, addr):
        if len(data) <= 16:
            return
        iv = data[:16]
        plain = cipher(iv)(data[16:])
        parsed = parse_header(plain)
        if not parsed:
            return
        host, port, hl = parsed
        payload = plain[hl:]
        loop = asyncio.get_event_loop()
        transport = self.transport

        class Target(asyncio.DatagramProtocol):
            def connection_made(self, t):
                self.t = t
                t.sendto(payload)

            def datagram_received(self, d, a):
                out_iv = os.urandom(16)
                body = addr_header(a[0], a[1]) + d
                transport.sendto(out_iv + cipher(out_iv)(body), addr)
                self.t.close()

        asyncio.ensure_future(loop.create_datagram_endpoint(Target, remote_addr=(host, port)))


def start_ss_server():
    ready = {}
    evt = threading.Event()

    def run():
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        tcp = loop.run_until_complete(asyncio.start_server(ss_tcp_client, "127.0.0.1", 0))
        port = tcp.sockets[0].getsockname()[1]
        loop.run_until_complete(loop.create_datagram_endpoint(SsUdp, local_addr=("127.0.0.1", port)))
        ready["port"] = port
        evt.set()
        loop.run_forever()

    threading.Thread(target=run, daemon=True).start()
    evt.wait(10)
    return ready["port"]


# ---------------------------------------------------------------- tests

def recv_all(sock, n, timeout=20):
    sock.settimeout(timeout)
    out = b""
    while len(out) < n:
        d = sock.recv(n - len(out))
        if not d:
            break
        out += d
    return out


def main():
    classes, jar = sys.argv[1], sys.argv[2]
    tcp_echo = start_tcp_echo()
    udp_echo = start_udp_echo()
    ss_port = start_ss_server()

    proc = subprocess.Popen(
        ["java", "-cp", f"{classes}:{jar}", "com.longoipo.rc4.Rc4BridgeCli", "127.0.0.1", str(ss_port), PASSWORD.decode()],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    line = proc.stdout.readline()
    assert line.startswith("PORT "), f"bridge did not start: {line!r} {proc.stderr.read() if proc.poll() else ''}"
    bport = int(line.split()[1])
    print(f"bridge on {bport} -> ss server {ss_port} -> echo tcp {tcp_echo} udp {udp_echo}")
    failures = 0

    def check(name, ok):
        nonlocal failures
        print(("ok   " if ok else "FAIL ") + name)
        if not ok:
            failures += 1

    try:
        # 1) TCP, IPv4 address header, 1 MiB
        payload = os.urandom(1 << 20)
        s = socket.create_connection(("127.0.0.1", bport))
        s.sendall(addr_header("127.0.0.1", tcp_echo) + payload)
        got = recv_all(s, len(payload))
        s.close()
        check("tcp ipv4 1MiB echo", got == payload)

        # 2) TCP, domain address header
        s = socket.create_connection(("127.0.0.1", bport))
        s.sendall(addr_header("localhost", tcp_echo) + b"hello domain")
        check("tcp domain header echo", recv_all(s, 12) == b"hello domain")
        s.close()

        # 3) TCP half-close: send, shutdown write, read to EOF
        s = socket.create_connection(("127.0.0.1", bport))
        s.sendall(addr_header("127.0.0.1", tcp_echo) + b"half close data")
        s.shutdown(socket.SHUT_WR)
        s.settimeout(20)
        buf = b""
        while True:
            d = s.recv(4096)
            if not d:
                break
            buf += d
        s.close()
        check("tcp half-close to EOF", buf == b"half close data")

        # 4) several parallel TCP connections
        results = []

        def worker(k):
            data = os.urandom(50000)
            c = socket.create_connection(("127.0.0.1", bport))
            c.sendall(addr_header("127.0.0.1", tcp_echo) + data)
            results.append(recv_all(c, len(data)) == data)
            c.close()

        ts = [threading.Thread(target=worker, args=(k,)) for k in range(8)]
        [t.start() for t in ts]
        [t.join() for t in ts]
        check("8 parallel tcp connections", len(results) == 8 and all(results))

        # 5) UDP: [addr header + payload] in, [addr header + payload] out
        u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        u.settimeout(10)
        msg = os.urandom(300)
        u.sendto(addr_header("127.0.0.1", udp_echo) + msg, ("127.0.0.1", bport))
        data, _ = u.recvfrom(65535)
        parsed = parse_header(data)
        check("udp echo payload", parsed is not None and data[parsed[2]:] == msg)
        check("udp reply source is the echo target", parsed is not None and parsed[1] == udp_echo)

        # 6) UDP: several packets on the same flow
        ok = True
        for k in range(5):
            m = os.urandom(100 + k)
            u.sendto(addr_header("127.0.0.1", udp_echo) + m, ("127.0.0.1", bport))
            d, _ = u.recvfrom(65535)
            p = parse_header(d)
            ok = ok and p is not None and d[p[2]:] == m
        check("udp 5 packets same flow", ok)
        u.close()
    finally:
        proc.kill()

    print("E2E PASSED" if failures == 0 else f"E2E FAILED: {failures}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
