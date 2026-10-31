#!/usr/bin/env python3
"""
게임 데이터 생성기 — 공식 Hypixel SkyBlock 위키(hypixelskyblock.minecraft.wiki)만 사용.

위키의 구조화 데이터(Bucket)와 표를 읽어 앱이 쓰는 gamedata.json 을 만든다.
  - 제작법          : Bucket:Recipe trees
  - 포지 레시피/시간 : The Forge/Table
  - 경험치표        : Catacombs/Levels, Slayer, Template:Heart of the Mountain/Leveling Table
  - 이벤트 일정      : Events (위키 LiveCountdown 타이머)
  - 진행 단계        : 지역 정보 상자(해금 조건), Slayer(해금 순서), HotM 표(포지 슬롯), The Catacombs(층)
  - 규칙            : Bazaar(수수료), The Forge(시간 감소), Heart of the Mountain(Quick Forge 공식), Bank(이자), Cole
아이템 이름 → ID 매칭에는 Hypixel 공식 API(/v2/resources/skyblock/items)를 쓴다.

GitHub Actions(.github/workflows/data.yml)가 매일 실행해서 최신 패치를 반영한다.
사용법: python3 tools/wiki_data.py [출력 경로]
"""
import datetime
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

WIKI = "https://hypixelskyblock.minecraft.wiki"
API = WIKI + "/api.php"
ITEMS = "https://api.hypixel.net/v2/resources/skyblock/items"
UA = "SkyblockHelper-data/1.0 (+https://github.com/pkgw07/SkyblockHelper)"
DEFAULT_OUT = os.path.join(os.path.dirname(__file__), "..", "ui", "src", "commonMain",
                           "composeResources", "files", "gamedata.json")

SB_HOUR = 50            # 실제 초
SB_DAY = 24 * SB_HOUR
SB_MONTH = 31 * SB_DAY
SB_YEAR = 12 * SB_MONTH
MONTH_CODES = ["ESP", "SP", "LSP", "ESU", "SU", "LSU", "EAU", "AU", "LAU", "EWI", "WI", "LWI"]

PAGES_USED = []


# ------------------------------------------------------------------ 네트워크
def get_json(url, params=None):
    if params:
        url += "?" + urllib.parse.urlencode(params)
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:  # 일시적 오류는 재시도
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)


def wikitext(page):
    d = get_json(API, {"action": "parse", "page": page, "prop": "wikitext|revid", "redirects": 1, "format": "json"})
    if "error" in d:
        raise RuntimeError(f"wiki page not found: {page}: {d['error']}")
    PAGES_USED.append({"page": d["parse"]["title"], "revid": d["parse"].get("revid")})
    return d["parse"]["wikitext"]["*"]


def bucket(query):
    d = get_json(API, {"action": "bucket", "query": query, "format": "json"})
    if "bucket" not in d:
        raise RuntimeError(f"bucket query failed: {d}")
    return d["bucket"]


# ------------------------------------------------------------------ 위키 문법 정리
def clean(text):
    """위키 문법을 사람이 읽는 글자로: [[A|B]]→B, {{Green|10%}}→10%, 태그 제거"""
    t = text
    t = re.sub(r"<ref[^>]*>.*?</ref>|<ref[^/]*/>", "", t, flags=re.S)
    t = re.sub(r"\[\[File:[^\]]*\]\]", "", t)
    for _ in range(4):
        t = re.sub(r"\{\{(?:[^{}|]+)\|([^{}]*)\}\}", lambda m: m.group(1).split("|")[-1], t)
        t = re.sub(r"\{\{[^{}|]*\}\}", "", t)
    t = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", t)
    t = re.sub(r"<br\s*/?>", " ", t)
    t = re.sub(r"<[^>]+>", "", t)
    t = t.replace("'''", "").replace("''", "")
    return re.sub(r"\s+", " ", t).strip()


def parse_tables(text):
    """위키 표(wikitable)를 2차원 목록으로. rowspan/colspan 을 펼친다."""
    tables = []
    for raw in re.findall(r"\{\|(.*?)\n\|\}", text, flags=re.S):
        rows, pending = [], {}  # pending[col] = (남은 줄 수, 값)
        for chunk in re.split(r"\n\|-[^\n]*", raw)[1:] if "\n|-" in raw else []:
            cells = []
            for line in chunk.strip("\n").split("\n"):
                if not line or line[0] not in "|!":
                    if cells:
                        cells[-1] += "\n" + line
                    continue
                for part in re.split(r"\|\||!!", line[1:]):
                    cells.append(part)
            row, col = [], 0
            parsed = []
            for c in cells:
                attrs, value = "", c
                m = re.match(r"^\s*([a-z\-]+\s*=\s*\"?[^|\"]*\"?\s*)+\|(?!\|)(.*)$", c, flags=re.S)
                if m and "[[" not in c.split("|")[0] and "{{" not in c.split("|")[0]:
                    attrs, value = c.split("|", 1)
                rs = int((re.search(r'rowspan\s*=\s*"?(\d+)', attrs) or [0, 1])[1])
                cs = int((re.search(r'colspan\s*=\s*"?(\d+)', attrs) or [0, 1])[1])
                parsed.append((value.strip(), rs, cs))
            for value, rs, cs in parsed:
                while col in pending:
                    left, v = pending[col]
                    row.append(v)
                    pending[col] = (left - 1, v) if left > 1 else None
                    if pending[col] is None:
                        del pending[col]
                    col += 1
                for _ in range(cs):
                    row.append(value)
                    if rs > 1:
                        pending[col] = (rs - 1, value)
                    col += 1
            while col in pending:
                left, v = pending[col]
                row.append(v)
                pending[col] = (left - 1, v) if left > 1 else None
                if pending[col] is None:
                    del pending[col]
                col += 1
            if row:
                rows.append(row)
        tables.append(rows)
    return tables


def num(text):
    t = clean(text).lower().replace(",", "").replace("xp", "").strip()
    m = re.match(r"^([\d.]+)\s*([km]?)", t)
    if not m:
        return None
    v = float(m.group(1)) * {"": 1, "k": 1e3, "m": 1e6}[m.group(2)]
    return int(v)


# ------------------------------------------------------------------ 아이템 이름 → ID
class Ids:
    def __init__(self):
        items = get_json(ITEMS)["items"]
        self.by_name = {}
        self.names = {}
        for i in items:
            name = re.sub("§.", "", i.get("name", "")).strip()
            self.by_name.setdefault(name.lower(), i["id"])

    def id(self, name):
        """공식 ID 가 있으면 ID, 없으면 'name:이름'"""
        name = name.strip()
        found = self.by_name.get(name.lower())
        if found:
            return found
        key = "name:" + name
        self.names[key] = name
        return key


# ------------------------------------------------------------------ 각 데이터
def crafting(ids):
    out = {}
    for row in bucket("bucket('recipe_trees').select('item','json').limit(10000).run()"):
        try:
            tree = json.loads(row["json"])
        except ValueError:
            continue
        mats = {}
        for m in tree.get("materials", []):
            if m.get("name") and m.get("quantity"):
                k = ids.id(m["name"])
                mats[k] = mats.get(k, 0) + float(m["quantity"])
        if mats:
            out.setdefault(ids.id(tree["name"]), []).append({"in": mats, "count": int(tree.get("quantity") or 1)})
    PAGES_USED.append({"page": "Bucket:Recipe trees", "revid": None})
    return out


def duration_seconds(text):
    t = clean(text).lower()
    total = 0
    for value, unit in re.findall(r"([\d.]+)\s*(day|hour|minute|second)s?", t):
        total += float(value) * {"day": 86400, "hour": 3600, "minute": 60, "second": 1}[unit]
    return int(total)


def forge(ids):
    text = wikitext("The Forge/Table")
    out = {}
    for table in parse_tables(text):
        for row in table:
            if len(row) < 6 or "{{BZC" not in row[5]:
                continue
            name_m = re.search(r"\[\[([^\]|]+)", row[1])
            if not name_m:
                continue
            sec = duration_seconds(row[3])
            mats, coins = {}, 0
            for qty, item in re.findall(r"\*([\d,]+)\s+([^*}]+)", row[5]):
                q = float(qty.replace(",", ""))
                item = item.strip()
                if item.lower() == "coins":
                    coins += q
                else:
                    k = ids.id(item)
                    mats[k] = mats.get(k, 0) + q
            hotm = clean(row[4]).split(" ")[0]
            out.setdefault(ids.id(name_m.group(1)), []).append(
                {"in": mats, "count": 1, "sec": sec, "coins": coins, "hotm": hotm})
    if len(out) < 50:
        raise RuntimeError(f"forge table looks wrong ({len(out)} rows)")
    return out


def leveling():
    # 카타콤: 레벨별 필요 XP
    cata = []
    for table in parse_tables(wikitext("Catacombs/Levels")):
        for row in table:
            if len(row) >= 3 and clean(row[0]).isdigit():
                v = num(row[1])
                if v is not None:
                    cata.append(v)
    # 슬레이어: 누적 XP (표: 슬레이어 이름 + LVL 1..9)
    slayer = {}
    name_map = {"zombie": "zombie", "spider": "spider", "wolf": "wolf", "enderman": "enderman",
                "blaze": "blaze", "vampire": "vampire"}
    for table in parse_tables(wikitext("Slayer")):
        for row in table:
            key = clean(row[0]).lower() if row else ""
            if key in name_map:
                vals = [num(c) for c in row[1:]]
                slayer[name_map[key]] = [v for v in vals if v is not None]
    # HotM: 단계별 필요 XP (To Upgrade 열)
    hotm = []
    for table in parse_tables(wikitext("Template:Heart of the Mountain/Leveling Table")):
        for row in table:
            if len(row) >= 3 and clean(row[0]).isdigit():
                v = num(row[1])
                if v is not None:
                    hotm.append(v)
    if len(cata) < 50 or len(slayer) < 5 or len(hotm) < 10:
        raise RuntimeError(f"leveling tables look wrong: cata={len(cata)} slayer={list(slayer)} hotm={len(hotm)}")
    return {"catacombs": cata, "slayer_xp": slayer, "HOTM": hotm}


def parse_routine(routine):
    """위키 LiveCountdown routine → (기준 시각 dict, 주기 목록[초])"""
    a = re.search(r"A\[([^\]]*)\]", routine).group(1)
    c = re.search(r"C\[([^\]]*)\]", routine).group(1)
    anchor = {"year": 1, "month": 0, "day": 1, "hour": 0, "minute": 0}
    for tok in a.replace("-S", "").split():
        if tok.startswith("Y") and tok[1:].isdigit():
            anchor["year"] = int(tok[1:])
        elif tok.startswith("M") and tok[1:].isdigit():
            anchor["month"] = int(tok[1:]) - 1
        elif tok.startswith("D") and tok[1:].isdigit():
            anchor["day"] = int(tok[1:])
        elif tok in MONTH_CODES:
            anchor["month"] = MONTH_CODES.index(tok)
        elif re.match(r"^\d{1,2}:\d{2}$", tok):
            h, m = tok.split(":")
            anchor["hour"], anchor["minute"] = int(h), int(m)
    cycle = []
    for part in c.split("/"):
        secs = 0
        for value, unit in re.findall(r"(\d+)(mo|y|d|h)", part.replace("-S", "")):
            secs += int(value) * {"y": SB_YEAR, "mo": SB_MONTH, "d": SB_DAY, "h": SB_HOUR}[unit]
        cycle.append(secs)
    return anchor, cycle


def events():
    text = wikitext("Events")
    out = []
    for row_text in re.split(r"\n\|-", text):
        m = re.search(r"\{\{LiveCountdown\|routine=([^}]*)\}\}", row_text)
        if not m:
            continue
        # 표의 한 줄만 본다: 타이머 바로 앞의 [[File:...]] 부터 타이머까지
        row_text = row_text[max(0, row_text.rfind("[[File:", 0, m.start())):m.end()]
        names = re.findall(r"'''(.+?)'''", row_text)
        if not names:
            continue
        raw_name = names[-1]
        npc = re.search(r"\{\{NPCSprite\|([^}|]+)", raw_name)
        name = clean(raw_name).replace("’", "'").strip()
        link = re.search(r"link=([^\]|]+)", row_text)
        anchor, cycle = parse_routine(m.group(1))
        ev = {"name": name, "page": link.group(1).strip() if link else name, "anchor": anchor, "cycle": cycle}
        if npc:
            ev["requiresNpc"] = npc.group(1).strip()
        out.append(ev)
    # 은행 이자: "At the end of every in-game season (31 hours)" — 계절이 시작될 때 = 3달마다
    bank = wikitext("Bank")
    if "end of every in-game season" in bank:
        out.append({"name": "Bank Interest", "page": "Bank",
                    "anchor": {"year": 1, "month": 0, "day": 1, "hour": 0, "minute": 0},
                    "cycle": [SB_HOUR, 3 * SB_MONTH - SB_HOUR]})
    if len(out) < 10:
        raise RuntimeError(f"events look wrong ({len(out)})")
    return out


def rules():
    r = {}
    bazaar = wikitext("Bazaar")
    m = re.search(r"There is a \{\{Green\|([\d.]+)%\}\} tax on all '''Bazaar''' sales", bazaar)
    if not m:
        raise RuntimeError("bazaar tax sentence not found")
    r["bazaarTaxPercent"] = float(m.group(1))
    if "abused to avoid {{NPCSprite|Derpy}}'s {{Green|4x}} taxes" in bazaar:
        r["taxMultiplierByMayor"] = {"Derpy": 4}

    forge_page = wikitext("The Forge")
    r["forgeReductionStacking"] = "additive" if "stacking {{aqua|'''additively'''}}" in forge_page else "unknown"
    mf = re.search(r"\{\{green\|-([\d.]+)%\}\}\s*\n\|\[\[Mayor\]\] perk\s*\n\|\{\{NPCSprite\|(\w+)\}\}'s \{\{AN\|([^}]+)\}\}", forge_page)
    if mf:
        r["mayorForgePerks"] = [{"mayor": mf.group(2), "perk": mf.group(3), "percent": float(mf.group(1)),
                                 "onlyStartedDuringTerm": "started after the beginning of his term" in forge_page}]
    tier3 = wikitext("Template:Heart of the Mountain/List/Tier 3")
    q = re.search(r"Quick Forge=.*?10 \+ \\text\{Level\} \\times 0\.5 \+ \\left\\lfloor \\text\{Level\} \\times 0\.05 \\right\\rfloor \\times 10", tier3, flags=re.S)
    if not q:
        raise RuntimeError("Quick Forge formula not found on wiki")
    r["quickForge"] = {"base": 10, "perLevel": 0.5, "bonusFactor": 0.05, "bonus": 10, "max": 30}

    ts = wikitext("Time Systems")
    ep = re.search(r"unix time\]\] of \{\{Code\|(\d+)\}\}", ts)
    if not ep:
        raise RuntimeError("SkyBlock epoch not found on Time Systems page")
    r["skyblockEpochSeconds"] = int(ep.group(1))

    cole = wikitext("Cole")
    r["miningFiestaWholeTerm"] = "Mining Fiestas''' now last for the whole mayorship" in cole
    return r


AREA_PAGES = ["Spider's Den", "The End", "Dwarven Mines", "Crystal Hollows", "Glacite Tunnels",
              "Crimson Isle", "Garden", "The Rift"]


def requirement_list(text):
    """위키 정보 상자의 level 값 → 조건 목록"""
    reqs = []
    for skill, level in re.findall(r"\{\{(?:Skill|Skl)\|([A-Za-z]+)[| ](\d+)\}\}", text):
        reqs.append({"type": "skill", "skill": skill.upper(), "level": int(level)})
    for level in re.findall(r"\{\{SkyBlock Level\|(\d+)\}\}", text):
        reqs.append({"type": "sblevel", "level": int(level)})
    for tier in re.findall(r"Heart of the Mountain\]\]\s*Tier\s*(\d+)", text):
        reqs.append({"type": "hotm", "tier": int(tier)})
    return reqs


def progression():
    areas = []
    for page in AREA_PAGES:
        text = wikitext(page)
        m = re.search(r"^\|\s*level\s*=(.*)$", text, flags=re.M)
        reqs = requirement_list(m.group(1)) if m else []
        if page == "Glacite Tunnels":  # 정보 상자 대신 본문: "requires Heart of the Mountain VII"
            g = re.search(r"requires \[\[Heart of the Mountain\|Heart of the Mountain (\w+)\]\]", text)
            if g:
                reqs.append({"type": "hotm", "tier": roman(g.group(1))})
        if reqs:
            areas.append({"name": page, "page": page, "requires": reqs})

    # HotM 단계별 포지 슬롯 (위키 Leveling Table 의 Forge Slots 열)
    slots = {}
    for table in parse_tables(wikitext("Template:Heart of the Mountain/Leveling Table")):
        for row in table:
            if len(row) >= 6 and clean(row[0]).isdigit():
                v = clean(row[5])
                if v.isdigit():
                    slots[clean(row[0])] = int(v)

    # 슬레이어 해금 순서 (위키 Slayer: "Defeat a Tier N 보스")
    unlocks = []
    keys = {"Revenant Horror": "zombie", "Tarantula Broodfather": "spider", "Sven Packmaster": "wolf",
            "Voidgloom Seraph": "enderman", "Inferno Demonlord": "blaze", "Riftstalker Bloodfiend": "vampire"}
    slayer = wikitext("Slayer")
    start = slayer.index("== Slayer Types and Requirements ==")
    slayer = slayer[start:slayer.index("\n== ", start + 5)]
    for boss, req in re.findall(r"\|\{\{MobSprite\|([^}]+)\}\}\n\|([^\n]+)", slayer):
        if boss not in keys:
            continue
        t = re.search(r"\{\{Stier\|(\d+)\}\}\s*\{\{MobSprite\|([^}]+)\}\}", req)
        unlocks.append({"boss": keys[boss], "name": boss,
                        "requires": {"boss": keys.get(t.group(2)), "tier": int(t.group(1))} if t else None,
                        "note": clean(req) if not t else ""})

    cata = wikitext("The Catacombs")
    floors = ["Entrance"] + [f"Floor {r}" for r in ["I", "II", "III", "IV", "V", "VI", "VII"]]
    master = "unlock" in cata and "Necron" in cata
    return {"areas": areas, "hotmForgeSlots": slots, "slayerUnlocks": unlocks,
            "dungeonFloors": floors if "beating a floor unlocks the one succeeding it" in cata else [],
            "masterModeAfterFloorVII": master}


def roman(text):
    vals = {"I": 1, "V": 5, "X": 10}
    total, prev = 0, 0
    for ch in reversed(text.upper()):
        v = vals.get(ch, 0)
        total = total - v if v < prev else total + v
        prev = max(prev, v)
    return total


def main(out_path):
    ids = Ids()
    data = {
        "source": {
            "name": "Hypixel SkyBlock Wiki",
            "url": WIKI,
            "license": "CC BY-NC-SA 3.0 (위키 콘텐츠)",
            "generated": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC"),
        },
        "crafting": crafting(ids),
        "forge": forge(ids),
        "leveling": leveling(),
        "events": events(),
        "rules": rules(),
        "progression": progression(),
    }
    data["names"] = ids.names
    data["source"]["pages"] = PAGES_USED
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    print(f"wrote {out_path}: crafting={len(data['crafting'])} forge={len(data['forge'])} "
          f"events={len(data['events'])} cata={len(data['leveling']['catacombs'])} "
          f"rules={data['rules']} ({os.path.getsize(out_path) // 1024} KB)")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT)
