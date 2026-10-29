#!/usr/bin/env python3
"""Lance une vidéo YouTube sur un appareil Cast (Android TV) sans dépendance externe.

Usage : python3 cast_youtube.py <ip> <video_id>
"""
import json, socket, ssl, struct, sys, time, urllib.parse, urllib.request

YOUTUBE_APP = "233637DE"
NS_CONN = "urn:x-cast:com.google.cast.tp.connection"
NS_HB = "urn:x-cast:com.google.cast.tp.heartbeat"
NS_RECV = "urn:x-cast:com.google.cast.receiver"
NS_MDX = "urn:x-cast:com.google.youtube.mdx"


def varint(n):
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        out += bytes([b | (0x80 if n else 0)])
        if not n:
            return out


def field_str(num, s):
    b = s.encode()
    return varint(num << 3 | 2) + varint(len(b)) + b


def encode(src, dst, ns, payload):
    msg = (varint(1 << 3) + varint(0)  # protocol_version CASTV2_1_0
           + field_str(2, src) + field_str(3, dst) + field_str(4, ns)
           + varint(5 << 3) + varint(0)  # payload_type STRING
           + field_str(6, json.dumps(payload)))
    return struct.pack(">I", len(msg)) + msg


def decode(data):
    i, fields = 0, {}
    while i < len(data):
        key, i = read_varint(data, i)
        num, wt = key >> 3, key & 7
        if wt == 0:
            val, i = read_varint(data, i)
        else:
            ln, i = read_varint(data, i)
            val, i = data[i:i + ln], i + ln
        fields[num] = val
    return fields


def read_varint(data, i):
    shift = n = 0
    while True:
        b = data[i]; i += 1
        n |= (b & 0x7F) << shift; shift += 7
        if not b & 0x80:
            return n, i


class Cast:
    def __init__(self, ip):
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        self.s = ctx.wrap_socket(socket.create_connection((ip, 8009), timeout=10))
        self.req = 0

    def send(self, dst, ns, payload):
        self.s.sendall(encode("sender-0", dst, ns, payload))

    def recv(self):
        hdr = self._read(4)
        f = decode(self._read(struct.unpack(">I", hdr)[0]))
        return f.get(4, b"").decode(), json.loads(f.get(6, b"{}").decode() or "{}")

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.s.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("connexion fermée")
            buf += chunk
        return buf

    def wait_for(self, pred, timeout=20):
        end = time.time() + timeout
        while time.time() < end:
            ns, p = self.recv()
            if ns == NS_HB and p.get("type") == "PING":
                self.send("receiver-0", NS_HB, {"type": "PONG"})
            elif pred(ns, p):
                return p
        raise TimeoutError("pas de réponse attendue")


def lounge_play(screen_id, video_id):
    def post(url, data):
        req = urllib.request.Request(url, data=urllib.parse.urlencode(data).encode())
        return urllib.request.urlopen(req, timeout=15).read().decode()

    tok = json.loads(post("https://www.youtube.com/api/lounge/pairing/get_lounge_token_batch",
                          {"screen_ids": screen_id}))["screens"][0]["loungeToken"]
    base = "https://www.youtube.com/api/lounge/bc/bind"
    common = {"device": "REMOTE_CONTROL", "id": "claude-remote", "name": "Claude",
              "app": "youtube-desktop", "mdx-version": "3", "loungeIdToken": tok,
              "VER": "8", "v": "2", "t": "1", "ui": "1", "CVER": "1"}
    r = post(base + "?" + urllib.parse.urlencode({**common, "RID": "1", "zx": "x"}), {"count": "0"})
    sid = r.split('"c","')[1].split('"')[0]
    gsess = r.split('"S","')[1].split('"')[0]
    q = {**common, "SID": sid, "gsessionid": gsess, "RID": "2", "AID": "5"}
    post(base + "?" + urllib.parse.urlencode(q),
         {"count": "1", "ofs": "0", "req0__sc": "setPlaylist", "req0_videoId": video_id,
          "req0_currentTime": "0", "req0_currentIndex": "-1", "req0_audioOnly": "false",
          "req0_videoIds": video_id, "req0_listId": ""})


def main(ip, video_id):
    c = Cast(ip)
    c.send("receiver-0", NS_CONN, {"type": "CONNECT"})
    c.send("receiver-0", NS_RECV, {"type": "LAUNCH", "appId": YOUTUBE_APP, "requestId": 1})
    st = c.wait_for(lambda ns, p: ns == NS_RECV and p.get("type") == "RECEIVER_STATUS"
                    and any(a.get("appId") == YOUTUBE_APP for a in p["status"].get("applications", [])))
    app = next(a for a in st["status"]["applications"] if a["appId"] == YOUTUBE_APP)
    tid = app["transportId"]
    c.send(tid, NS_CONN, {"type": "CONNECT"})
    c.send(tid, NS_MDX, {"type": "getMdxSessionStatus"})
    p = c.wait_for(lambda ns, p: ns == NS_MDX and p.get("type") == "mdxSessionStatus")
    lounge_play(p["data"]["screenId"], video_id)
    print("Lecture demandée :", video_id)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
