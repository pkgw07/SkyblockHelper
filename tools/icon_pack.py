"""
아이템 아이콘 묶음 만들기 (매일 위키 데이터 작업에서 실행).

모든 아이템(공식 Hypixel 아이템 API) + 펫(위키 Pets 목록)의 그림을 공식 위키에서 받아
32×32 이하 PNG 로 줄인 뒤(움직이는 GIF 는 첫 장면), 한 파일로 이어 붙인다.

  icons.bin           PNG 들을 이어 붙인 파일
  icons.json          {"version", "items": {ID: [시작, 길이]}, "names": {소문자 이름: ID}}
  icons-version.txt   version 한 줄 (앱이 바뀌었는지만 가볍게 확인)
  icons-urls.json     ID → 위키 그림 주소 (다음 실행 때 바뀐 그림만 다시 받기 위해)

머리 모양 아이템은 위키 그림이 없으면 공식 API 의 스킨에서 얼굴을 잘라 쓴다.

사용: python tools/icon_pack.py <출력 폴더> [이전 data 폴더] [gamedata.json]
"""
import base64
import concurrent.futures
import hashlib
import io
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

WIKI = "https://hypixelskyblock.minecraft.wiki"
API = WIKI + "/api.php"
UA = {"User-Agent": "SkyblockHelper-icon-pack (github.com/pkgw07/SkyblockHelper)"}
SIZE = 32


def get(url, tries=3):
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code in (400, 403, 404):
                return None
            time.sleep(1.5 * (i + 1) if e.code == 429 else 0.5)
        except Exception:
            time.sleep(0.5)
    return None


def page_images(titles):
    """위키 PageImages: 문서 제목 → 대표 그림 주소 (50개씩)"""
    out = {}
    titles = list(dict.fromkeys(titles))
    for i in range(0, len(titles), 50):
        chunk = titles[i:i + 50]
        q = urllib.parse.urlencode({"action": "query", "titles": "|".join(chunk), "prop": "pageimages",
                                    "piprop": "thumbnail", "pithumbsize": 64, "redirects": 1, "format": "json"})
        raw = get(API + "?" + q)
        if not raw:
            continue
        d = json.loads(raw).get("query", {})
        alias = {}
        for k in ("normalized", "redirects"):
            for r in d.get(k, []):
                alias[r["to"]] = alias.get(r["from"], r["from"])
        for p in d.get("pages", {}).values():
            src = p.get("thumbnail", {}).get("source")
            if src:
                out[alias.get(p["title"], p["title"])] = src
        time.sleep(0.2)
    return out


def file_thumbs(titles):
    """아이템 이름과 같은 위키 파일(리소스팩 그림) → 64px 그림 주소 (50개씩)"""
    out = {}
    titles = list(dict.fromkeys(titles))
    for i in range(0, len(titles), 50):
        chunk = titles[i:i + 50]
        q = urllib.parse.urlencode({"action": "query", "titles": "|".join("File:" + t + ".png" for t in chunk),
                                    "prop": "imageinfo", "iiprop": "url", "iiurlwidth": 64, "format": "json"})
        raw = get(API + "?" + q)
        if not raw:
            continue
        d = json.loads(raw).get("query", {})
        alias = {}
        for r in d.get("normalized", []):
            alias[r["to"]] = r["from"]
        for p in d.get("pages", {}).values():
            info = (p.get("imageinfo") or [{}])[0]
            src = info.get("thumburl") or info.get("url")
            if src and "missing" not in p:
                title = alias.get(p["title"], p["title"])
                out[title[len("File:"):-len(".png")]] = src
        time.sleep(0.2)
    return out


def shrink(data):
    """첫 장면만, 32×32 안으로 (픽셀 그림은 뭉개지지 않게)"""
    try:
        img = Image.open(io.BytesIO(data))
        img.seek(0)
        img = img.convert("RGBA")
        bbox = img.getbbox()
        if bbox:
            img = img.crop(bbox)
        w, h = img.size
        if max(w, h) > SIZE:
            pixel_art = max(w, h) <= 64 or (w % 16 == 0 and h % 16 == 0 and max(w, h) <= 128)
            img.thumbnail((SIZE, SIZE), Image.Resampling.NEAREST if pixel_art else Image.Resampling.LANCZOS)
        buf = io.BytesIO()
        img.save(buf, "PNG", optimize=True)
        return buf.getvalue()
    except Exception:
        return None


def skull_face(skin_value):
    """공식 API 스킨 값(base64) → 얼굴 8×8 + 모자 겹 → 16×16"""
    try:
        url = re.search(r'"url"\s*:\s*"([^"]+)"', base64.b64decode(skin_value).decode()).group(1).replace("http://", "https://")
        raw = get(url)
        skin = Image.open(io.BytesIO(raw)).convert("RGBA")
        u = skin.width // 64
        face = skin.crop((8 * u, 8 * u, 16 * u, 16 * u))
        hat = skin.crop((40 * u, 8 * u, 48 * u, 16 * u))
        face.alpha_composite(hat)
        face = face.resize((16, 16), Image.Resampling.NEAREST)
        buf = io.BytesIO()
        face.save(buf, "PNG", optimize=True)
        return buf.getvalue()
    except Exception:
        return None


def main():
    out_dir = sys.argv[1]
    prev_dir = sys.argv[2] if len(sys.argv) > 2 else None
    gamedata = sys.argv[3] if len(sys.argv) > 3 else os.path.join(out_dir, "gamedata.json")
    os.makedirs(out_dir, exist_ok=True)

    items = json.loads(get("https://api.hypixel.net/v2/resources/skyblock/items"))["items"]
    entries = {}  # ID → (위키 문서 제목, 스킨 값)
    for it in items:
        name = re.sub(r"§.", "", it.get("name", it["id"])).strip()
        entries[it["id"]] = (name, (it.get("skin") or {}).get("value"))
    if os.path.exists(gamedata):
        gd = json.load(open(gamedata, encoding="utf-8"))
        for pets in gd.get("pets", {}).values():
            for p in pets:
                entries.setdefault("pet:" + p["name"], (p["name"] + " Pet", None))
        # 도감 몹 (지역 카드·몹 카드 아이콘)
        for f in gd.get("bestiary", {}).get("families", []):
            entries.setdefault("mob:" + f["family"], (f["family"], None))

    titles = [t for t, _ in entries.values()]
    # 1) 아이템 이름과 같은 파일 (게임 리소스팩 그림)  2) 없으면 문서 대표 그림
    files = file_thumbs(titles)
    images = page_images([t for t in titles if t not in files])
    print(f"items {len(entries)}, item files {len(files)}, page images {len(images)}")

    # 이전 묶음 (바뀌지 않은 그림은 다시 받지 않는다)
    prev_bytes, prev_urls = {}, {}
    if prev_dir and os.path.exists(os.path.join(prev_dir, "icons.json")):
        try:
            idx = json.load(open(os.path.join(prev_dir, "icons.json")))
            blob = open(os.path.join(prev_dir, "icons.bin"), "rb").read()
            prev_urls = json.load(open(os.path.join(prev_dir, "icons-urls.json")))
            for k, (off, ln) in idx["items"].items():
                prev_bytes[k] = blob[off:off + ln]
        except Exception as e:
            print("previous pack unreadable:", e)

    def fetch(item):
        iid, (title, skin) = item
        url = files.get(title) or images.get(title)
        if url is None and not skin:
            return iid, None, None
        if prev_urls.get(iid) == url and iid in prev_bytes:
            return iid, url, prev_bytes[iid]
        raw = get(url) if url else None
        png = shrink(raw) if raw else None
        if png is None and skin:
            png, url = skull_face(skin), "skin"
        return iid, url, png

    result = {}
    done = [0]
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        for iid, url, png in pool.map(fetch, entries.items()):
            done[0] += 1
            if done[0] % 500 == 0:
                print(f"  {done[0]}/{len(entries)}", flush=True)
            if png:
                result[iid] = (url, png)

    blob = bytearray()
    index, urls, names = {}, {}, {}
    for iid in sorted(result):
        url, png = result[iid]
        index[iid] = [len(blob), len(png)]
        blob += png
        urls[iid] = url
        names[entries[iid][0].lower()] = iid
    version = hashlib.sha1(bytes(blob)).hexdigest()[:16]
    open(os.path.join(out_dir, "icons.bin"), "wb").write(blob)
    json.dump({"version": version, "items": index, "names": names}, open(os.path.join(out_dir, "icons.json"), "w"), separators=(",", ":"))
    json.dump(urls, open(os.path.join(out_dir, "icons-urls.json"), "w"), separators=(",", ":"))
    open(os.path.join(out_dir, "icons-version.txt"), "w").write(version)
    print(f"icons {len(index)}/{len(entries)}, {len(blob) // 1024} KB, version {version}")


if __name__ == "__main__":
    main()
