# Liste les couches composées de la WebView (taille et raison), via le protocole devtools
import json, sys
sys.argv = [sys.argv[0], "1"]
exec(open(__file__.replace("layers.py", "cdp.py")).read().split("send({\"id\": 1")[0])
send({"id": 1, "method": "DOM.enable"}); send({"id": 2, "method": "LayerTree.enable"})
layers = None
while layers is None:
    m = recv()
    if m.get("method") == "LayerTree.layerTreeDidChange" and m["params"].get("layers"): layers = m["params"]["layers"]
send({"id": 3, "method": "DOM.getDocument", "params": {"depth": -1}})
nodes = {}
while True:
    m = recv()
    if m.get("id") == 3:
        def walk(n):
            nodes[n["backendNodeId"]] = n.get("localName", "") + ("#" + n["attributes"][n["attributes"].index("id") + 1] if "id" in n.get("attributes", []) else "") + ("." + n["attributes"][n["attributes"].index("class") + 1] if "class" in n.get("attributes", []) else "")
            for c in n.get("children", []): walk(c)
        walk(m["result"]["root"]); break
tot = 0; rid = 10
for L in layers:
    if not L.get("drawsContent"): continue
    mb = L["width"] * L["height"] * 4 / 1e6; tot += mb
    rid += 1; send({"id": rid, "method": "LayerTree.compositingReasons", "params": {"layerId": L["layerId"]}})
    while True:
        m = recv()
        if m.get("id") == rid: reasons = m.get("result", {}).get("compositingReasonIds", m.get("result", {}).get("compositingReasons", [])); break
    print(f'{L["width"]:5}x{L["height"]:<5} {mb:5.1f} Mo  {nodes.get(L.get("backendNodeId"), "?")[:40]:40} {",".join(reasons)[:60]}')
print(f"total ~{tot:.0f} Mo")
