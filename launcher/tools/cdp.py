# Évalue une expression JS dans la WebView de la box (via adb forward tcp:9333)
import base64, json, os, socket, struct, sys, urllib.request
page = json.load(urllib.request.urlopen("http://127.0.0.1:9333/json"))[0]
path = page["webSocketDebuggerUrl"].split("9333", 1)[1]
s = socket.create_connection(("127.0.0.1", 9333))
key = base64.b64encode(os.urandom(16)).decode()
s.sendall(f"GET {path} HTTP/1.1\r\nHost: 127.0.0.1:9333\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n".encode())
buf = b""
while b"\r\n\r\n" not in buf: buf += s.recv(1024)
def send(obj):
    data = json.dumps(obj).encode(); mask = os.urandom(4); n = len(data)
    hdr = bytes([0x81]) + (bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + struct.pack(">H", n) if n < 65536 else bytes([0x80 | 127]) + struct.pack(">Q", n))
    s.sendall(hdr + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))
def read(n):
    global buf
    while len(buf) < n: buf += s.recv(65536)
    out, buf = buf[:n], buf[n:]; return out
buf = buf.split(b"\r\n\r\n", 1)[1]
def recv():
    b1, b2 = read(2); n = b2 & 0x7F
    if n == 126: n = struct.unpack(">H", read(2))[0]
    elif n == 127: n = struct.unpack(">Q", read(8))[0]
    return json.loads(read(n))
send({"id": 1, "method": "Runtime.evaluate", "params": {"expression": sys.argv[1], "returnByValue": True, "awaitPromise": True}})
while True:
    m = recv()
    if m.get("id") == 1:
        r = m["result"]; print(json.dumps(r.get("result", {}).get("value", r), ensure_ascii=False, indent=1)); break
