#!/usr/bin/env python3
"""
게임 데이터 생성기 — 공식 Hypixel SkyBlock 위키(hypixelskyblock.minecraft.wiki)만 사용.

위키의 구조화 데이터(Bucket)와 표를 읽어 앱이 쓰는 gamedata.json 을 만든다.
  - 제작법          : Bucket:Recipe trees
  - 포지 레시피/시간 : The Forge/Table
  - 경험치표        : Catacombs/Levels, Slayer, Template:Heart of the Mountain/Leveling Table
  - 이벤트 일정      : Events (위키 LiveCountdown 타이머)
  - 진행 단계        : 지역 정보 상자(해금 조건), Slayer(해금 순서), HotM 표(포지 슬롯), The Catacombs(층)
  - 장비·펫 테크트리 : 위키 가이드(Tutorial:Mining/Farming/Foraging/Fishing/Hunting Guide, Armor/Melee Weapon Guide)
  - 아이언맨 가이드   : Tutorial:Ironman Guide
  - 재료 출처        : Bucket:Mob drops, Bucket:Minions, 아이템 정보 상자(merchant/buy)
  - 펫              : Module:Pet/UI (종류별 펫, 게임 툴팁)
  - HotM 배치도      : Template:Heart of the Mountain/UI
  - 장신구          : Accessories/* 하위 문서 + 정보 상자(upgrades_from/to)
  - 도감            : Bucket:Mob bestiary + Bestiary(단계별 처치 수)
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
        self.category = {}
        self.names_by_id = {}
        for i in items:
            name = re.sub("§.", "", i.get("name", "")).strip()
            self.names_by_id[i["id"]] = name
            self.by_name.setdefault(name.lower(), i["id"])
            if i.get("category"):
                self.category[i["id"]] = i["category"]

    def name_of(self, key):
        return self.names_by_id.get(key)

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
            # 업그레이드 재료: {{BZC|...}} 밖의 {{Item|이전 아이템|amount=n}}
            outside = re.sub(r"\{\{BZC\|[^}]*\}\}", "", row[5])
            for it in re.finditer(r"\{\{Item\|([^|}]+)(?:\|amount=(\d+))?[^}]*\}\}", outside):
                k = ids.id(it.group(1).strip())
                mats[k] = mats.get(k, 0) + float(it.group(2) or 1)
            hotm = clean(row[4]).split(" ")[0]
            out.setdefault(ids.id(name_m.group(1)), []).append(
                {"in": mats, "count": 1, "sec": sec, "coins": coins, "hotm": hotm})
    if len(out) < 50:
        raise RuntimeError(f"forge table looks wrong ({len(out)} rows)")
    return out


def module_text(title):
    """위키 Module: 문서의 원문"""
    d = get_json(API, {"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                       "titles": title, "format": "json"})
    for page in d.get("query", {}).get("pages", {}).values():
        revs = page.get("revisions")
        if revs:
            return revs[0]["slots"]["main"]["*"]
    return ""


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
    # HotF: 같은 형식의 표 (Template:Heart of the Forest/Leveling Table)
    hotf = []
    for table in parse_tables(wikitext("Template:Heart of the Forest/Leveling Table")):
        for row in table:
            if len(row) >= 3 and clean(row[0]).isdigit():
                v = num(row[1])
                if v is not None:
                    hotf.append(v)
    if len(cata) < 50 or len(slayer) < 5 or len(hotm) < 10:
        raise RuntimeError(f"leveling tables look wrong: cata={len(cata)} slayer={list(slayer)} hotm={len(hotm)}")
    # 펫: 레벨별 경험치 + 등급별 시작 위치 (위키 Module:Pet/LevelingData)
    pet_src = module_text("Module:Pet/LevelingData")
    pet = {}
    m = re.search(r"pet_levels\s*=\s*\{([^}]*)\}", pet_src)
    o = re.search(r"pet_rarity_offset\s*=\s*\{([^}]*)\}", pet_src)
    if m and o:
        pet = {"xp": [int(x) for x in re.findall(r"\d+", m.group(1))],
               "offset": {k.upper(): int(v) for k, v in re.findall(r"(\w+)\s*=\s*(\d+)", o.group(1))}}
    # 가든: 레벨별 Garden XP (위키 The Garden/Leveling Rewards)
    garden = []
    for table in parse_tables(wikitext("The Garden/Leveling Rewards")):
        for row in table:
            if len(row) >= 3 and clean(row[0]).isdigit():
                garden.append(num(row[1]) or 0)
    return {"catacombs": cata, "slayer_xp": slayer, "HOTM": hotm, "HOTF": hotf, "PET": pet, "GARDEN": garden}


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
    # Hoppity's Hunt: 문서 위쪽 LiveCountdownBox
    hop = wikitext("Hoppity's Hunt")
    m = re.search(r"\{\{LiveCountdownBox\|routine=([^|}]*)", hop)
    if m:
        anchor, cycle = parse_routine(m.group(1))
        out.append({"name": "Hoppity's Hunt", "page": "Hoppity's Hunt", "anchor": anchor, "cycle": cycle})
    if len(out) < 10:
        raise RuntimeError(f"events look wrong ({len(out)})")
    return out


GEAR_PAGES = {
    "MINING": ["Pickaxes", "Drills"],
    "FARMING": ["Farming Tools"],
    "FORAGING": ["Axes"],
    "FISHING": ["Fishing Rods"],
    "HUNTING": ["Lassos"],
}


def gear_pools(ids):
    """위키 도구 목록 문서(Pickaxes, Drills, Farming Tools …)의 {{Slot|A ; B}} → 스킬별 도구 아이템 ID (문서 순서)"""
    out = {}
    for skill, pages in GEAR_PAGES.items():
        found = []
        for page in pages:
            for slot in re.findall(r"\{\{Slot\|([^}]+)\}\}", wikitext(page)):
                for name in slot.split(";"):
                    name = name.split("|")[0].strip()
                    i = ids.id(name) if name else None
                    if i and not i.startswith("name:") and i not in found:
                        found.append(i)
        out[skill] = found
    return out


def timer_rules():
    """타이머용 위키 규칙: 초콜릿 알 등장 시각, 컴포스터, Time Tower, Fetchur·실험대 초기화"""
    r = {}
    hop = wikitext("Hoppity's Hunt")
    hours = []
    for h, ap in re.findall(r'rowspan="2"\s*\|\s*(\d{1,2})\s*(AM|PM)', hop):
        v = int(h) % 12 + (12 if ap == "PM" else 0)
        if v not in hours:
            hours.append(v)
    if hours:
        r["hoppityEggHours"] = hours
    comp = wikitext("Composter")
    m1 = re.search(r"takes (\d+) minutes to create each", comp)
    m2 = re.search(r"consuming \{\{Green\|([\d,]+)\}\} \{\{Yellow\|Organic Matter\}\} and \{\{Green\|([\d,]+)\}\}", comp)
    m3 = re.search(r"production speed by \{\{Green\|(\d+)%\}\} per tier", comp)
    m4 = re.search(r"required to produce Compost by \{\{Green\|(\d+)%\}\} per tier", comp)
    if m1 and m2 and m3 and m4:
        r["composter"] = {"secondsPerCompost": int(m1.group(1)) * 60,
                          "organicPerCompost": int(m2.group(1).replace(",", "")),
                          "fuelPerCompost": int(m2.group(2).replace(",", "")),
                          "speedPerTier": int(m3.group(1)) / 100, "costReductionPerTier": int(m4.group(1)) / 100}
    sbl = wikitext("SkyBlock Levels")
    m = re.search(r"1 SkyBlock Level\}\} for every \{\{SkyBlock XP\|(\d+)\}\}", sbl)
    if m:
        r["skyblockXpPerLevel"] = int(m.group(1))
    tower = wikitext("Chocolate Factory")
    m = re.search(r"builds up to (\d+) charges, each over an (\d+) hour period", tower)
    if m:
        r["timeTower"] = {"maxCharges": int(m.group(1)), "hoursPerCharge": int(m.group(2))}
    fet = wikitext("Fetchur")
    if "resets every night at midnight [[Hypixel Server Time]]" in fet:
        r["fetchurReset"] = "America/New_York 00:00"
    exp = wikitext("Experimentation Table")
    if "reset every day at 12:00 AM GMT" in exp:
        r["experimentBitsReset"] = "UTC 00:00"
    return r


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


# 스킬별 장비·펫 테크트리: (키, 위키 가이드 페이지, 포함할 큰 제목 정규식)
GUIDES = [
    ("MINING", "Tutorial:Mining Guide", r"Gear"),
    ("FARMING", "Tutorial:Farming Guide", r"Progression|Pets"),
    ("FORAGING", "Tutorial:Foraging Guide", r"General Progression"),
    ("FISHING", "Tutorial:Fishing Guide", r"Recommended Progression"),
    ("HUNTING", "Tutorial:Hunting Guide", r"Information"),
    ("COMBAT", "Tutorial:Armor Guide", r"."),
    ("COMBAT", "Tutorial:Melee Weapon Guide", r"."),
]
LINK = re.compile(r"\{\{(?:Item|ItemSprite|Slot)\|([^}|]+)|\[\[([^\]|#]+)")


def guide_stages(page, include, ids, keep):
    """위키 가이드에서 (단계 제목, 설명 한 줄, 아이템 목록) 을 순서대로 뽑는다."""
    text = re.sub(r"<ref[^>]*>.*?</ref>|<ref[^/]*/>", "", wikitext(page), flags=re.S)
    stages, take, top, stage = [], False, None, None
    for line in text.split("\n"):
        h = re.match(r"^(=+)\s*(.*?)\s*=+\s*$", line)
        if h:
            level, title = len(h.group(1)), clean(h.group(2))
            if level == 2:
                take, top = bool(re.search(include, title, re.I)), title
            if take:
                stage = {"group": top, "title": title, "desc": "", "items": []}
                stages.append(stage)
            continue
        if not take or stage is None:
            continue
        if not stage["desc"] and line.strip() and not line.startswith(("{|", "|", "!", "[[File", "{{")):
            stage["desc"] = clean(line)[:240]
        for a, b in LINK.findall(line):
            name = (a or b).strip()
            if name.endswith(" Pet"):
                key = "pet:" + name[:-4]
            else:
                key = ids.by_name.get(name.lower())
                if not key or not keep(key):
                    continue
            if key not in stage["items"]:
                stage["items"].append(key)
    return [s for s in stages if s["items"]]


def tech_trees(ids):
    trees = {}
    gear = lambda k: k in ids.category  # 장비(카테고리가 있는 아이템)만
    for skill, page, include in GUIDES:
        stages = guide_stages(page, include, ids, gear)
        for s in stages:
            s["page"] = page
        trees.setdefault(skill, []).extend(stages)
    return trees


def ironman_guide(ids):
    stages = guide_stages("Tutorial:Ironman Guide", r"Getting Started|Next Steps", ids, lambda k: True)
    for s in stages:
        s["page"] = "Tutorial:Ironman Guide"
    return stages


def sources(ids):
    """재료 출처: 드롭하는 몹(Bucket:Mob drops), 미니언(Bucket:Minions)"""
    drops = {}
    for row in bucket("bucket('mob_drops').select('mob','drops').limit(5000).run()"):
        mob = row.get("mob") or ""
        for d in row.get("drops") or []:
            name = re.sub(r"^\$\w\$\s*", "", d).strip()
            key = ids.by_name.get(name.lower())
            if key and mob and mob not in drops.setdefault(key, []) and len(drops[key]) < 8:
                drops[key].append(mob)
    PAGES_USED.append({"page": "Bucket:Mob drops", "revid": None})
    minions = {}
    for row in bucket("bucket('minions').select('page_name').limit(500).run()"):
        page = row.get("page_name", "")
        key = ids.by_name.get(page.replace(" Minion", "").lower())
        if key and page.endswith(" Minion"):
            minions[key] = page
    PAGES_USED.append({"page": "Bucket:Minions", "revid": None})
    sacks = {}
    for row in bucket("bucket('sacks').select('sack','capacity','items').limit(500).run()"):
        name = row.get("sack") or ""
        key = ids.by_name.get(name.lower())
        items = [ids.by_name.get(i.lower()) for i in row.get("items") or []]
        if key:
            sacks[key] = {"name": name, "capacity": row.get("capacity") or 0, "items": [i for i in items if i]}
    PAGES_USED.append({"page": "Bucket:Sacks", "revid": None})
    return {"drops": drops, "minions": minions, "sacks": sacks}


def mc_clean(text):
    """&a/§a 색 코드 제거"""
    return re.sub(r"[&§][0-9a-fk-or]", "", text).strip()


def infoboxes(titles):
    """여러 위키 문서의 정보 상자(Infobox/...) 값을 한 번에 읽는다 → {제목: {키: 값}}"""
    out = {}
    titles = list(dict.fromkeys(t for t in titles if t))
    for i in range(0, len(titles), 50):
        chunk = titles[i:i + 50]
        d = get_json(API, {"action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
                           "redirects": 1, "titles": "|".join(chunk), "format": "json"})
        alias = {r["to"]: r["from"] for r in d.get("query", {}).get("redirects", [])}
        for page in d.get("query", {}).get("pages", {}).values():
            revs = page.get("revisions")
            if not revs:
                continue
            text = revs[0]["slots"]["main"]["*"]
            m = re.search(r"\{\{Infobox/[^\n]*\n(.*?)\n\}\}", text, flags=re.S)
            fields = {}
            if m:
                for k, v in re.findall(r"^\|\s*([\w_]+)\s*=\s*(.*?)(?=^\||\Z)", m.group(1) + "\n|", flags=re.M | re.S):
                    fields[k.strip()] = v.strip()
            out[alias.get(page["title"], page["title"])] = fields
    return out


def coins(text):
    m = re.search(r"\{\{c\|([\d,.]+)\s*([kKmM]?)", text or "") or re.match(r"^\s*([\d,.]+)\s*([kKmM]?)\s*$", text or "")
    if not m:
        return None
    v = float(m.group(1).replace(",", ""))
    return v * {"": 1, "k": 1e3, "m": 1e6}[m.group(2).lower()]


def npc_buy(ids, names):
    """정보 상자의 merchant/buy → NPC 구매처와 가격, upgrades_from → 이전 단계 아이템"""
    out, upgrades = {}, {}
    for title, f in infoboxes(names).items():
        key = f.get("id", "").strip() or ids.by_name.get(title.lower())
        if not key:
            continue
        raw_buy = f.get("buy", "")
        price = coins(raw_buy)
        # {{RL|1x Fractured Mithril Pickaxe|2x Enchanted Mithril}} 처럼 아이템으로 사는 경우
        cost = ""
        rl = re.search(r"\{\{RL\|([^}]*)\}\}", raw_buy)
        if rl:
            cost = ", ".join(clean(x) for x in rl.group(1).split("|") if clean(x))
        merchant = clean(f.get("merchant", "")).lstrip("*").replace(" *", ", ").strip()
        if price or merchant:
            out[key] = {"npc": merchant, "coins": price or 0, "cost": cost}
        up = clean(f.get("upgrades_from", "")).split(",")[0].strip()
        if up:
            upgrades[key] = ids.id(up)
    return out, upgrades


PET_TYPES = ["Farming", "Mining", "Combat", "Foraging", "Fishing", "Hunting", "Alchemy", "Enchanting", "Taming"]


def pets_by_type():
    """위키 펫 표 (Module:Pet/UI): 종류별 펫 이름, 등급, 게임 툴팁"""
    out = {}
    for t in PET_TYPES:
        d = get_json(API, {"action": "expandtemplates", "text": "{{#invoke:Pet/UI|petTableType|type=%s}}" % t,
                           "prop": "wikitext", "format": "json"})
        text = d["expandtemplates"]["wikitext"]
        pets = []
        for style, title, lore in re.findall(
                r'data-minetip-style="(\w+)" data-minetip-title="([^"]*)" data-minetip-text="([^"]*)"', text):
            name = mc_clean(re.sub(r"\[[^\]]*\]", "", title.replace("&amp;", "&"))).strip()
            if not name or any(p["name"] == name for p in pets):
                continue
            lines = [l for l in lore.replace("&amp;", "&").replace("&quot;", '"').split("/")]
            pets.append({"name": name, "rarity": style.upper(), "lore": lines[:30]})
        out[t.upper()] = pets
    PAGES_USED.append({"page": "Module:Pet/UI", "revid": None})
    return out


def hotm_layout(template="Template:Heart of the Mountain/UI"):
    """위키 Template:Heart of the Mountain/UI (또는 Heart of the Forest/UI) → 칸 배치 (행 1 = 가장 위)"""
    text = wikitext(template)
    cells = []
    for r, c, body in re.findall(r"^\|(\d+),\s*(\d+)=(.*)$", text, flags=re.M):
        parts = body.split(", ", 3)
        if len(parts) < 3:
            continue
        name = mc_clean(parts[2])
        if not name or name.startswith("Tier "):
            continue
        lore = parts[3] if len(parts) > 3 else ""
        mx = re.search(r"Level \d+&8\\?/(\d+)", lore)
        ability = "Pickaxe Ability" in lore or "Axe Ability" in lore
        if not mx:
            # 예시 트리에서 다 찍은 칸은 "Level 50" 처럼 최대치만 적혀 있다
            mx = re.search(r"Level (\d+)(?!&8)", lore)
        cells.append({"row": int(r), "col": int(c), "name": name, "maxLevel": int(mx.group(1)) if mx else 1,
                      "ability": ability, "lore": [mc_clean(x) for x in lore.split("/")
                                                    if x and not x.startswith("&5&o") and "Cost" not in x][:8]})
    # 맨 아래 줄은 안내 칸(트리 슬롯·지역 설명 등)이라 뺀다
    last = max((c["row"] for c in cells), default=0)
    cells = [c for c in cells if c["row"] != last]
    if len(cells) < 20:
        raise RuntimeError("hotm layout looks wrong")
    return cells


ACCESSORY_TABS = ["Obtained From Collections or Recipes", "Misc_Crafts", "Dungeons_Loot", "Mob_Drop",
                  "Obtained From NPC Shop", "Quests_and_Trials", "Slayer_Recipes", "Other"]
ACCESSORY_LABEL = {"Obtained From Collections or Recipes": "컬렉션 제작", "Misc_Crafts": "기타 제작",
                   "Dungeons_Loot": "던전", "Mob_Drop": "몹 드롭", "Obtained From NPC Shop": "NPC 상점",
                   "Quests_and_Trials": "퀘스트", "Slayer_Recipes": "슬레이어", "Other": "기타"}


def accessories(ids):
    """위키 Accessories 하위 문서 + 각 장신구 정보 상자(upgrades_from/to, merchant/buy)"""
    found = {}
    for tab in ACCESSORY_TABS:
        text = wikitext("Accessories/" + tab)
        for row in re.split(r"\n\|-", text):
            m = re.search(r"\[\[([^\]|#]+)(?:\|[^\]]*)?\]\]", row)
            if not m or "{{Slot|" not in row:
                continue
            name = m.group(1).strip()
            last = row.split("\n|")[-1] if "\n|" in row else ""
            last = re.sub(r'^\s*class="[^"]*"\s*\|', "", last)
            source = clean(last.split("----")[0])
            found.setdefault(name, {"name": name, "category": ACCESSORY_LABEL[tab], "source": source[:120],
                                    "coins": coins(row.split("\n|")[-1]) if "\n|" in row else None})
    boxes = infoboxes(list(found))
    out = []
    for name, a in found.items():
        f = boxes.get(name, {})
        key = f.get("id", "").strip() or ids.by_name.get(name.lower())
        if not key:
            continue
        up_from = clean(f.get("upgrades_from", "")) or None
        up_to = clean(f.get("upgrades_to", "")) or None
        a.update({"id": key, "from": ids.by_name.get(up_from.lower()) if up_from else None,
                  "to": ids.by_name.get(up_to.lower()) if up_to else None})
        if f.get("merchant"):
            a["npc"] = clean(f["merchant"]).lstrip("*").replace(" *", ", ")
        if coins(f.get("buy", "")):
            a["coins"] = coins(f.get("buy", ""))
        out.append(a)
    if len(out) < 100:
        raise RuntimeError(f"accessory list looks wrong ({len(out)})")
    return out


def bestiary():
    """위키 Bucket:Mob bestiary + Bestiary 페이지의 단계별 누적 처치 수 표"""
    rows = bucket("bucket('mob_bestiary').select('name','family','section','bracket','max_tier').limit(5000).run()")
    PAGES_USED.append({"page": "Bucket:Mob bestiary", "revid": None})
    fams = {}
    for r in rows:
        fam = r.get("family") or r.get("name")
        if fam and fam not in fams and r.get("max_tier"):
            b = re.match(r"\d+", str(r.get("bracket") or "1"))
            fams[fam] = {"family": fam, "section": r.get("section", ""), "bracket": int(b.group(0)) if b else 1,
                         "maxTier": int(r["max_tier"])}
    brackets = {}
    for table in parse_tables(wikitext("Bestiary")):
        rows_ok = [row for row in table if row and clean(row[0]).isdigit() and len(row) >= 9
                   and all((num(c) or 0) > 0 for c in row[1:9])
                   and [num(c) for c in row[1:9]] != list(range(1, 9))]
        if len(rows_ok) >= 20:
            for row in rows_ok[-25:]:
                for b in range(1, 9):
                    brackets.setdefault(str(b), []).append(num(row[b]) or 0)
            break
    # 몹 이름 → 가족 (API 도감 키는 가족 이름이 아니라 몹 이름인 경우가 많다: chillblade → Chill)
    mobs = {}
    for r in rows:
        fam = r.get("family") or r.get("name")
        name = r.get("name")
        if fam and name:
            mobs[re.sub(r"[^a-z0-9]+", "_", str(name).lower().replace("'", "")).strip("_")] = fam
    return {"families": list(fams.values()), "brackets": brackets, "mobs": mobs}


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
        "timerRules": timer_rules(),
        "progression": progression(),
        "techTrees": tech_trees(ids),
        "gearPools": gear_pools(ids),
        "ironmanGuide": ironman_guide(ids),
        "sources": sources(ids),
        "pets": pets_by_type(),
        "hotmLayout": hotm_layout(),
        "hotfLayout": hotm_layout("Template:Heart of the Forest/UI"),
        "accessories": accessories(ids),
        "bestiary": bestiary(),
    }
    # 테크트리·아이언맨 가이드·장신구 아이템의 NPC 구매처 (정보 상자 merchant/buy)
    item_ids = {i for st in data["ironmanGuide"] for i in st["items"]}
    for stages in data["techTrees"].values():
        item_ids.update(i for st in stages for i in st["items"])
    names = [ids.name_of(i) for i in item_ids if not i.startswith(("pet:", "name:"))]
    data["npcBuy"], data["upgrades"] = npc_buy(ids, [n for n in names if n])
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
