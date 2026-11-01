package sbhelper.core.data

/**
 * 스킬별 장비 진행 가이드 (사용자 제공 문서 "Hypixel SkyBlock 스킬별 장비 진행 가이드", 2026-11-01 기준).
 * 공식 출처가 아니므로 화면에 출처를 함께 표시한다. 아이템 그림·보유 여부·조건 달성은 공식 API 로 판단한다.
 *
 * 이름은 공식 아이템 이름 그대로 (화면에서 공식 ID 로 찾음). 펫은 "pet:이름".
 * 조건 글(cond)은 "HotM 5", "Fishing 24", "F7 클리어", "Cata 27", "Enderman Slayer VII", "Lily Pad VII" 처럼 쓰면 내 프로필로 자동 판정한다.
 */
object SkillGuide {
    const val SOURCE = "사용자 제공 스킬 가이드 (2026-11-01 기준)"

    data class Step(val name: String, val cond: String = "", val note: String = "", val optional: Boolean = false, val value: Int? = null)
    data class Stage(val label: String, val from: Int?, val to: Int?, val cond: String, val goal: String)

    sealed interface Sec { val title: String? }
    data class Text(override val title: String?, val lines: List<String>, val tip: Boolean = false) : Sec
    /** 진행 단계. metric 으로 지금 내 단계를 강조 (HOTM, SKILL:FARMING …) */
    data class Stages(override val title: String, val metric: String?, val rows: List<Stage>, val condLabel: String = "조건") : Sec
    /** 화살표로 이어지는 진행 */
    data class Flow(override val title: String, val steps: List<Step>, val notes: List<String> = emptyList()) : Sec
    /** 해금 단계별 묶음 사다리 (채광 곡괭이처럼 같은 단계에 여러 선택지) */
    data class Ladder(override val title: String, val groups: List<Pair<String, List<Step>>>, val notes: List<String> = emptyList()) : Sec
    /** 나란한 여러 갈래 (사냥 도구처럼) */
    data class Columns(override val title: String, val lines: List<Pair<String, List<Step>>>, val notes: List<String> = emptyList()) : Sec
    /** 단계 × 슬롯 격자. 칸은 아이템 이름 또는 설명 글 */
    data class SlotGrid(override val title: String, val slots: List<String>, val rows: List<Pair<String, List<String>>>, val notes: List<String> = emptyList()) : Sec
    /** 슬롯별 초중반 → 후반 */
    data class Compare(override val title: String, val headers: Pair<String, String>, val rows: List<Triple<String, String, String>>, val notes: List<String> = emptyList()) : Sec
    /** 카드 (펫처럼 '언제·왜' 가 중요한 것) */
    data class Cards(override val title: String, val cards: List<Step>, val notes: List<String> = emptyList()) : Sec
    /** 막대 비교 (value) */
    data class Bars(override val title: String, val unit: String, val steps: List<Step>, val notes: List<String> = emptyList()) : Sec
    /** 일반 표. "@이름" 칸은 아이템 그림과 함께 */
    data class Table(override val title: String, val headers: List<String>, val rows: List<List<String>>, val notes: List<String> = emptyList()) : Sec
    data class Bullets(override val title: String, val items: List<String>) : Sec
    /** 최종 목표 세팅 */
    data class Final(override val title: String, val lines: List<Pair<String, String>>) : Sec

    data class Tab(val label: String, val sections: List<Sec>)
    /** @param metric 머리 카드에 보여 줄 내 진행도 (HOTM, SKILL:MINING …) */
    data class Skill(val key: String, val label: String, val metric: String?, val intro: List<String>, val tabs: List<Tab>)

    private fun s(name: String, cond: String = "", note: String = "", optional: Boolean = false, value: Int? = null) = Step(name, cond, note, optional, value)
    private fun opt(name: String, cond: String = "", note: String = "") = Step(name, cond, note, optional = true)

    // ================================================================ 채광
    private val MINING = Skill("MINING", "채광", "HOTM", listOf(
        "채광은 좋은 곡괭이만 사는 것보다 Heart of the Mountain(HotM), Powder, Mining Speed, Mining Fortune, Pristine 을 함께 올리는 것이 중요해요.",
        "투자 규모가 커서 초반에 비싼 장비를 무리해서 사면 손해를 보기 쉬워요. HotM 진행과 장비를 같이 올리세요.",
    ), listOf(Tab("가이드", listOf(
        Stages("진행 순서", "HOTM", listOf(
            Stage("초반", 0, 1, "Dwarven Mines 입장 · Commission", "기본 곡괭이, Fractured Mithril Pickaxe"),
            Stage("초중반", 2, 4, "HotM 2~4", "Mithril/Titanium Pickaxe, 초기 Drill"),
            Stage("중반", 5, 6, "HotM 5~6", "Sorrow Armor, Gemstone Gauntlet 또는 Titanium Drill"),
            Stage("후반", 7, 9, "HotM 7~10 · Crystal Hollows · Glacite Tunnels", "Divan Armor, 상위 Drill"),
            Stage("엔드게임", 10, null, "Powder·능력치 최적화", "완성형 Divan Armor, Divan's Drill, 상황별 펫"),
        ), "HotM"),
        Text(null, listOf("가장 먼저: Dwarven Mines 에서 Commission 을 꾸준히 완료해 HotM 을 올리세요. Crystal Hollows 와 여러 채광 방식은 HotM 진행이 상당히 필요해요. HotM VII 을 주요 목표로 두세요."), tip = true),
        Ladder("곡괭이 · Drill (해금 단계별)", listOf(
            "시작" to listOf(s("Promising Pickaxe", note = "광산 입장 전후 기본 채광")),
            "HotM 0~1" to listOf(s("Fractured Mithril Pickaxe", "HotM 1", "Mithril 채광 시작")),
            "HotM 2" to listOf(s("Bandaged Mithril Pickaxe", "HotM 2"), s("Mithril Pickaxe", "HotM 2", "Mithril 채광 향상")),
            "HotM 3" to listOf(s("Titanium Pickaxe", "HotM 3", "Titanium 채광"), s("Mithril Drill SX-R226", "HotM 3", "초기 Drill"), s("Ruby Drill TX-15", "HotM 3", "Gemstone 입문")),
            "HotM 4" to listOf(s("Polished Titanium Pickaxe", "HotM 4", "Titanium Pickaxe 업그레이드"), s("Gemstone Drill LT-522", "HotM 4", "Gemstone 채광"), s("Pickonimbus 2000", "HotM 4", "저렴한 임시 Gemstone 도구")),
            "HotM 5" to listOf(s("Titanium Drill DR-X355", "HotM 5", "가성비 비보석 Drill"), s("Titanium Drill DR-X555", "HotM 5", "상위 비보석"), s("Topaz Drill KGR-12", "HotM 5", "Gemstone 용")),
            "HotM 6" to listOf(s("Gemstone Gauntlet", "HotM 6", "Gemstone 대표 가성비"), s("Titanium Drill DR-X655", "HotM 6", "상위 비보석")),
            "HotM 7~10" to listOf(s("Divan's Drill", "HotM 7", "고투자 최종급")),
        ), listOf(
            "모든 Drill 을 차례대로 살 필요는 없어요.",
            "Gemstone 위주라면 Gemstone Gauntlet 을 오래 써도 좋아요. 비보석 채광은 DR-X355 나 DR-X655 가 가성비가 좋아요.",
            "Divan's Drill 은 강력하지만 비싸서, 나머지 장비와 Powder 를 갖춘 다음 투자하세요.",
            "Pickonimbus 2000 은 초기 Gemstone 에 유용하지만 내구도와 캘 수 있는 블록 제한이 있어요.",
        )),
        Flow("방어구", listOf(
            s("Prospecting Armor", note = "초기 채광"),
            s("Flamebreaker Armor", "Hard Stone V", "초기 Mining Fortune 보충"),
            opt("Yog Armor", "Hard Stone V", "Magma Fields 의 Heat 대응"),
            opt("Glacite Armor", "Mining 31", "초중반 선택지"),
            s("Sorrow Armor", "HotM 5", "후반으로 넘어가는 주요 방어구"),
            s("Armor of Divan", "HotM 6", "최종급 채광 방어구"),
        ), listOf("Flamebreaker 와 Yog 는 대체품이 아니에요. Flamebreaker 는 Fortune 보충, Yog 는 Magma Fields Heat 대응용이에요.",
            "Sorrow 는 Divan 을 마련하기 전까지 쓰고, 이후 Divan 에 Gemstone 을 장착하며 발전시키세요.")),
        SlotGrid("Equipment (HotM 단계별)", listOf("Necklace", "Cloak", "Belt", "Gloves"), listOf(
            "초기" to listOf("기본 장비", "기본 장비", "기본 장비", "기본 장비"),
            "HotM 2" to listOf("Mithril Necklace", "Mithril Cloak", "Mithril Belt", "Mithril Gauntlet"),
            "HotM 3" to listOf("Titanium Necklace", "Titanium Cloak", "Titanium Belt", "Titanium Gauntlet"),
            "HotM 5" to listOf("Amber Necklace", "Sapphire Cloak", "Jade Belt", "Amethyst Gauntlet"),
            "HotM 8" to listOf("기존 Necklace 유지", "기존 Cloak 유지", "기존 Belt 유지", "Dwarven Handwarmers"),
            "HotM 10" to listOf("Pendant of Divan", "지역별 선택", "Jade Belt 등", "Dwarven Handwarmers"),
        ), listOf("Dwarven Mines: Titanium Equipment 를 쓰다가 HotM 10 이후 Necklace 를 Pendant of Divan 으로.",
            "Crystal Hollows: Mining Fortune 과 Mining Speed 를 목적에 맞게.",
            "Glacite Tunnels: Pendant of Divan / Ancient 또는 Sapphire Cloak / Jade Belt / Dwarven Handwarmers.")),
        Flow("Accessory", listOf(
            s("Mineral Talisman", note = "초기 Mining Fortune"), s("Glossy Mineral Talisman", note = "Mining Fortune 향상"),
            s("Ring of Power", note = "Power 계열 채광 보강"), s("Artifact of Power", note = "Ring 상위"), s("Relic of Power", "HotM 10", "최종 단계"),
        ), listOf("Power 계열은 Gemstone 슬롯으로 추가 능력치를 얻어요.")),
        Cards("펫 (장소마다 바꿔 쓰기)", listOf(
            s("pet:Silverfish", "초반부터", "Mining XP · Mining Wisdom — 레벨 올릴 때"),
            s("pet:Mithril Golem", "Mithril 채광", "Mithril 관련 Mining Speed"),
            s("pet:Scatha", "Gemstone · 일반 채광", "Mining Fortune, 보물 상자, Gemstone Powder"),
            s("pet:Bal", "Magma Fields", "Magma Fields 특화"),
            s("pet:Glacite Golem", "Glacite Mineshaft", "Mineshaft 안 Mining Fortune · Pristine"),
            s("pet:Mammoth", "Mineshaft 후반", "Cold 감소 대응 보조"),
        ), listOf("Mineshaft 에서는 Cold Resistance 가 중요하고, Glacite Golem 은 희귀도에 따라 보너스가 달라져요.")),
        Bullets("그 밖의 필수 업그레이드", listOf(
            "Drill 부품: Drill Engine · Fuel Tank · Upgrade · Goblin Omelette (고투자: Amber-Polished Drill Engine, Blue Cheese Goblin Omelette, Efficiency X)",
            "Powder 와 HotM: Mithril·Gemstone Powder 를 모으고 HotM Perk 최적화 — 높은 단계에서는 장비보다 Powder·Perk 가 더 큰 차이",
        )),
        Final("채광 최종 목표 세팅", listOf(
            "Armor" to "Armor of Divan", "Tool" to "Divan's Drill 또는 목적별 Drill / Gemstone Gauntlet",
            "Equipment" to "Pendant of Divan 중심 지역별 조합", "Accessory" to "Relic of Power · Mining Fortune 계열",
            "Pet" to "장소별 Scatha / Bal / Glacite Golem / Silverfish",
        )),
    ))))

    // ================================================================ 농사
    private val FARMING = Skill("FARMING", "농사", "SKILL:FARMING", listOf(
        "농사는 Garden 중심으로 진행해요. Farming Fortune 뿐 아니라 Crop Milestones, Visitors, Garden Chips, Pest Farming, Greenhouse 까지 함께 키워야 해요.",
        "Garden 은 SkyBlock Level 5 에서 Sam 을 통해 해금돼요.",
    ), listOf(Tab("가이드", listOf(
        Stages("진행 순서", "SKILL:FARMING", listOf(
            Stage("초반", 1, 19, "Farming 1~19", "Garden 해금, Rookie 도구, 전문 농사 도구 시작"),
            Stage("초중반", 20, 29, "Farming 20~29", "Tater Armor, 주력 작물 도구 육성"),
            Stage("중반", 30, 39, "Farming 30~39", "Cropie / Squash Armor, Equipment 강화"),
            Stage("후반", 40, 49, "Farming 40~49", "Fermento Armor, Pest Farming 시작"),
            Stage("엔드게임", 50, null, "Farming 50~60", "Helianthus Armor, Garden 시스템 완성"),
        ), "Farming Level"),
        Flow("농사 도구 (주력 작물 하나에 집중)", listOf(
            s("Rookie Hoe", note = "초반 기본 괭이"), s("Rookie Farming Axe", note = "초기 작물 채집"),
            s("Basic Gardening Hoe", note = "Copper 로 사는 임시 도구"), s("Advanced Gardening Hoe", note = "Basic 의 중간 업그레이드"),
            s("Euclid's Wheat Sickle Mk. I", note = "주력 작물 전용 도구 (예: Wheat)"), s("Euclid's Wheat Sickle Mk. II", note = "도구 레벨 15"),
            s("Euclid's Wheat Sickle Mk. III", note = "도구 레벨 30"), s("Overclocker 3000", note = "레벨 40 이후 최대 레벨 확장"),
        ), listOf("Specialized Farming Tool 은 250 Copper 로 살 수 있고, 맞는 작물을 수확할수록 Tool XP 가 올라요. Mk. II·III 를 차례로 만들며 주력 하나를 먼저 키우세요.",
            "도구 레벨 50 에서 네 번째 Gemstone 슬롯이 열려요.",
            "인챈트·개조: Harvesting V→VI, Cultivating, Turbo-Crop, Dedication III, Replenish, Bountiful Reforge, Farming for Dummies, Peridot Gemstone, Crop Fever")),
        Bars("방어구 (기본 풀세트 Farming Fortune)", "Fortune", listOf(
            s("Farmhand Armor", "Farming 3", value = 20), s("Haymaker Armor", "Farming 10", value = 40), s("Sprout Armor", "Farming 15", value = 60),
            s("Tater Armor", "Farming 20", value = 70), s("Cropie Armor", "Farming 30", value = 90), s("Squash Armor", "Farming 35", value = 110),
            s("Fermento Armor", "Farming 40", value = 130), s("Helianthus Armor", "Farming 50", value = 150),
        ), listOf("중간에 다른 방어구를 사기보다 다음 농사 방어구를 준비하세요.", "Reforge: Farmhand~Squash 는 Bustling, Fermento 부터 Mossy, Pest 최적화는 Mantid.")),
        SlotGrid("Equipment", listOf("Necklace", "Cloak", "Belt", "Gloves"), listOf(
            "초기" to listOf("초기 Farming Equipment", "초기 Farming Equipment", "초기 Farming Equipment", "초기 Farming Equipment"),
            "중반" to listOf("Peony Necklace", "Peony Cloak", "Peony Belt", "Peony Bracelet"),
            "후반" to listOf("Blossom Necklace", "Blossom Cloak", "Blossom Belt", "Blossom Bracelet"),
            "Pest Farming" to listOf("Blossom Necklace", "Pest Vest", "Blossom Belt 또는 Pesthunter 장비", "Pesthunter 장비"),
        ), listOf("초반에는 Blooming Reforge, 중반부터 Peony 를 모은 뒤 Blossom 으로 업그레이드. Garden Visitor 를 많이 완료할수록 Fortune 이 더 올라가요.",
            "Pest 소환 직전에는 Pesthunter's Set 3부위 + Pest Vest + Squeaky Reforge 로 바꾸고, 평소에는 Rooted Blossom Set. 처치용으로 Thorny Blossom Set.")),
        Flow("Accessory", listOf(s("Cropie Talisman", note = "초기 목표"), s("Squash Ring", note = "업그레이드"), s("Fermento Artifact", note = "상위"), s("Helianthus Relic", note = "최종"),
            opt("Pesthunter Relic", note = "Pest Farming 전용 별도 목표"))),
        Cards("펫", listOf(
            s("pet:Rabbit", "초반부터 Farming 40 까지", "Farming XP"), s("pet:Elephant", "일반 농사", "작물 수확량 · Collection"),
            s("pet:Mooshroom Cow", "수익 중심 농사", "Strength→Farming Fortune, 추가 버섯"), s("pet:Slug", "Pest 소환", "가성비 Pest 소환"),
            s("pet:Hedgehog", "Pest 처치", "Pest 처치 특화"), s("pet:Mosquito", "후반 Pest 소환", "Brown Bandana 조합"),
            s("pet:Orchid Mantis", "후반", "Farming Tool XP · Overbloom 보조"), s("pet:Rose Dragon", "엔드게임", "높은 Fortune · Crop Milestone 보너스"),
        ), listOf("일반적으로 Rabbit → Elephant 또는 Mooshroom Cow → Slug/Mosquito → Hedgehog → Rose Dragon. Rose Dragon 은 레벨 200 + Green Bandana 가 최종 목표.")),
        Bullets("Garden · Pest · Contest", listOf(
            "Garden: Garden Level·Crop Milestones, Visitors, Copper·Crop Upgrades, Greenhouse·Mutation, Garden Chips, Pest Bestiary, Anita 의 Contest 업그레이드",
            "Farming 40 이후엔 Mushroom 만 하지 말고 Wheat·Melon·Sunflower·Moonflower 로 수익과 Contest 를 병행",
            "Pest 도구: Sprayonator → Juicy → Salty, Vacuum 업그레이드, Pesthunter 장비, Pest Bestiary, Pest Attributes",
            "Jacob's Farming Contest: Farming 10 부터. 서로 다른 작물에서 Gold 를 얻는 것이 최대 레벨 60 확장의 핵심. 보상 전 Zorro's Cape 착용",
        )),
        Final("농사 최종 목표 세팅", listOf(
            "Armor" to "Helianthus Armor", "Equipment" to "Rooted Blossom Set", "Tool" to "주력 작물 Specialized Tool 레벨 50",
            "Accessory" to "Helianthus Relic", "Pet" to "레벨 200 Rose Dragon + Green Bandana", "보조" to "Garden Chips · Crop Upgrades · Greenhouse · Pest Bestiary",
        )),
    ))))

    // ================================================================ 벌목
    private val FORAGING = Skill("FORAGING", "벌목", "SKILL:FORAGING", listOf(
        "벌목은 The Park 의 나무만 베던 예전과 달리 Galatea 의 Moonglade Marsh 와 Torrhus Canyon 중심으로 진행해요.",
        "벌목 능력치: Sweep(한 번에 부수는 블록 수), Foraging Fortune, Foraging Wisdom, Timber(나무 전체를 쓰러뜨릴 확률). 후반엔 Sweep 이 특히 중요해요.",
    ), listOf(Tab("가이드", listOf(
        Stages("진행 순서", "SKILL:FORAGING", listOf(
            Stage("초반", 1, 11, "Foraging 1~11", "The Park 에서 Park Armor, Spruce Axe"),
            Stage("초중반", 12, 14, "Foraging 12~14", "Galatea 진입, Fig Hew 획득"),
            Stage("중반", 15, 19, "Foraging 15~19", "Fig Armor, Figstone Splitter"),
            Stage("후반 진입", 20, 24, "Foraging 20~24", "Torrhus Canyon 및 Helix Chopper"),
            Stage("후반", 25, 29, "Foraging 25~29", "Helix Armor 완성"),
            Stage("엔드게임", 30, null, "Foraging 30 이상", "장비 강화, 나무별 Fortune·Sweep 극대화"),
        ), "Foraging Level"),
        Text(null, listOf("Torrhus Canyon 은 HotF Tier 4 에서 열리지만 바로 옮기진 마세요. Helix 나무는 Toughness 가 높아 Sweep 과 장비가 부족하면 효율이 낮아요."), tip = true),
        Flow("도끼", listOf(
            s("Promising Axe", note = "극초반"), s("Spruce Axe", note = "The Park 주력"), s("Treecapitator", note = "The Park 에서 특히 강력 (+100 Fortune)"),
            opt("Seriously Damaged Axe", note = "Galatea 첫 도끼, 임시용 (Ironman)"), s("Fig Hew", note = "Fig 나무 기본 주력"), s("Figstone Splitter", note = "Fig Hew 업그레이드"), s("Helix Chopper", note = "최종 도끼"),
        ), listOf("Fig Hew 는 Galatea 에서 나무 2,000개마다 Sweep +1 (최대 +20). Figstone Splitter·Helix Chopper 도 비슷한 강화 구조예요.",
            "Treecapitator 는 The Park 에서 유용하지만 Galatea 에서는 Fig Hew 계열로 바꾸세요.")),
        Flow("방어구", listOf(
            s("Park Armor", note = "The Park 퀘스트로 획득"), opt("Canopy Armor", "Foraging 12", "Sweep +24, Fortune 없음 — Ironman 대체"),
            s("Fig Armor", "Foraging 15", "Moonglade Marsh 주력"), s("Helix Armor", "Foraging 25", "현재 최종 (Citrine 슬롯 하나 더)"),
        ), listOf("추가 신발: Primordial Boots (Spider Slayer VII, 이중 점프). 그 전엔 Spider's Boots 나 Tarantula Boots.")),
        Compare("Equipment (슬롯별)", "초중반" to "후반", listOf(
            Triple("Necklace", "Mangrove Locket", "Honeycomb Necklace"), Triple("Cloak", "David's Cloak", "David's Cloak (강화)"),
            Triple("Belt", "Moonglade Belt", "Torrhus Belt"), Triple("Gloves", "Mangrove Grippers", "Veilshroom Bracelet"),
        ), listOf("Mangrove Locket / Grippers: Foraging 12 부터 준비", "Moonglade Belt: Starlyn Contest 점수 보너스 — 일찍 확보", "Torrhus Belt: Foraging 24 에서 업그레이드",
            "Honeycomb Necklace · Veilshroom Bracelet: Foraging 30 에서 업그레이드", "David's Cloak: Hunting 진행에 따라 성장 — 벌목 Fortune 도 올라요")),
        Flow("Accessory", listOf(s("Lumberjack Talisman", note = "도끼 던지기 쿨다운 감소"), s("Moonglade Talisman", note = "Fig / Mangrove Fortune"),
            s("Torrhus Talisman", note = "Helix Fortune"), opt("Abicase", note = "Blue™ but Green Abicase — Foraging Wisdom")),
            listOf("Moonglade 계열은 Agatha's Coupons, Torrhus 계열은 Miria's Coupons 로 업그레이드해요.")),
        Flow("펫", listOf(s("pet:Ocelot", note = "Foraging Wisdom · 경험치 (Epic 이면 충분)"), s("pet:Monkey", note = "The Park 보조"),
            s("pet:Frog", note = "Epic 이상에서 벌목 Fortune"), s("pet:Sloth", note = "Starlyn Contest"), s("pet:Jade Dragon", note = "후반 최종 (Winding Ivy 로 Sweep +10)"))),
        Table("인챈트와 Booster", listOf("이름", "부위", "효과"), listOf(
            listOf("First Impression", "도끼", "Sweep 증가"), listOf("Forest Pledge", "방어구", "Foraging Fortune"), listOf("Petalfall", "관련 장비", "Timber 증가"),
            listOf("Karma", "도끼", "Tree Gift 추가 보상 확률"), listOf("Absorb", "도끼", "Foraging Wisdom · Fortune"),
            listOf("Sweep Booster", "장비", "Sweep 증가"), listOf("Foraging Fortune Booster", "장비", "Fortune 증가"), listOf("Foraging Wisdom Booster", "장비", "Wisdom 증가"),
        ), listOf("Booster 는 도끼 → 방어구 → Equipment 순으로.", "강화: Helix Chopper 는 Moonglade Reforge, Helix Armor 는 Groovy Reforge, Citrine Gemstone, Wood Singularity(+25 Fortune), Star Upgrade, Recombobulator 3000")),
        Bullets("지역별 강화 요소", listOf("Heart of the Forest(HotF) 진행", "Agatha / Miria 상점 업그레이드", "Attribute Shard 수집·강화", "Tree Gifts",
            "Moonglade / Torrhus Beacon 강화", "Forest Temple · Desert Temple 버프", "Foraging 50 이후 Fig·Mangrove·Helix Log 컬렉션과 상점 업그레이드로 최대 57")),
        Final("벌목 최종 목표 세팅", listOf("Armor" to "Helix Armor", "Axe" to "Helix Chopper",
            "Equipment" to "Honeycomb Necklace / David's Cloak / Torrhus Belt / Veilshroom Bracelet", "Accessory" to "Lumberjack · Moonglade · Torrhus 계열",
            "Pet" to "Jade Dragon + Winding Ivy", "보조" to "Moonglade Reforge · Citrine · Booster · HotF · Attribute Shards")),
    ))))

    // ================================================================ 낚시
    private val FISHING = Skill("FISHING", "낚시", "SKILL:FISHING", listOf(
        "낚시는 세팅을 여러 개 만들어 두는 게 특히 중요해요. 일반 물 낚시, 용암 낚시(Crimson Isle), Trophy Fishing 은 최적 장비가 달라 Loadout 을 따로 쓰세요.",
    ), listOf(
        Tab("공통", listOf(
            Table("낚시에서 중요한 능력치", listOf("능력치", "역할"), listOf(
                listOf("Fishing Speed", "낚시 속도"), listOf("Sea Creature Chance", "Sea Creature 등장 확률"), listOf("Treasure Chance", "보물 확률"),
                listOf("Double Hook Chance", "한 번에 두 마리"), listOf("Trophy Chance", "Trophy Fish 확률"), listOf("Fishing Wisdom", "낚시 경험치"),
                listOf("Magic Find", "희귀 드롭"), listOf("Tracking", "Elusive Sea Creature 등장"),
            )),
            Bars("지역별 Fishing Speed 상한", "Speed", listOf(s("Backwater Bayou", value = 200), s("Lotus Atoll", value = 250), s("일반 지역", value = 300), s("Crimson Isle", value = 350)),
                listOf("상한에 닿았다면 Fishing Speed 대신 SCC 나 Double Hook 을 올리세요.")),
            Table("Rod 부품 (Backwater Bayou 의 Roddy)", listOf("부품", "초반", "후반 · 특수 목적"), listOf(
                listOf("Hook", "@Common Hook", "@Hotspot Hook / Trophy 관련 Hook"), listOf("Line", "@Speedy Line", "@Titan Line / @Trophy Line"),
                listOf("Sinker", "@Stingy Sinker / @Junk Sinker", "@Hotspot Sinker / @Prismarine Sinker / @Lotus Sinker"),
            ), listOf("Speedy Line: Fishing Speed +10 · Titan Line: Double Hook +2% · Trophy Line: Trophy Chance +5%", "Stingy Sinker: Bait 소비 안 할 확률 10% · Junk Sinker: Treasure 대신 Junk, Treasure +10",
                "Hotspot Hook: Hotspot Sea Creature +20% · Hotspot Sinker: Hotspot 보너스 두 배")),
            Flow("펫", listOf(s("pet:Dolphin", note = "파티 낚시 (Washed-Up Souvenir)"), s("pet:Ammonite", note = "Mining·Fishing 레벨이 높은 계정 (Hephaestus Souvenir)"),
                s("pet:Squid", note = "Fishing Wisdom · 경험치"), opt("pet:Flying Fish", note = "AutoPet 으로 잠깐 활성화"), opt("pet:Baby Yeti", note = "Jerry's Workshop 이벤트"))),
            Bullets("그 밖에", listOf("Bait: Fish Bait(속도), Whale Bait, Hotspot Bait, Shark Bait(Fishing Festival) — 지역 상한과 목표에 맞게",
                "Accessory: 전체 Accessory Power 확보 → 낚시용 Power → SCC·Double Hook·Magic Find 보충 → Trophy 관련",
                "Sea Creature 처치 무기: Soul Whip, Giant's Sword, Dark Claymore — 마법 저항 높은 몹은 Hyperion 같은 마법 무기도 어려워요")),
        )),
        Tab("일반 물 낚시", listOf(
            Flow("Fishing Rod", listOf(s("Fishing Rod", note = "기본"), s("Challenging Rod", "Fishing 5"), s("Rod of Champions", "Fishing 15, Lily Pad VII"),
                s("Rod of Legends", "Fishing 20, Lily Pad IX"), s("Rod of the Sea", "Fishing 24", "Great White Shark Tooth 4개 — 대표 최종"), opt("Giant Fishing Rod", note = "고가 Double Hook 특화")),
                listOf("Rod of the Sea: Fishing Speed +80, SCC +8%. Giant Fishing Rod 는 Double Hook +10% 대신 기본 속도·슬롯이 없어요.")),
            Flow("방어구", listOf(s("Angler Armor", note = "기본 세트"), s("Backwater Armor", "Fishing 5"), opt("Salmon Armor", "Fishing 13, Raw Salmon VI"),
                opt("Sponge Armor", "Fishing 17", "SCC · 수중 방어"), opt("Diver's Armor", "Fishing 20", "수중 Treasure"), opt("Abyssal Armor", "Fishing 22", "Diver's 업그레이드"),
                s("Shark Scale Armor", "Fishing 24", "Sponge 업그레이드, SCC +10%")),
                listOf("Salmon 은 Sponge 를 마련할 수 있으면 건너뛰어도 돼요. Diver's·Abyssal 은 Treasure·수중 생존용이에요.")),
            SlotGrid("Equipment", listOf("Necklace", "Cloak", "Belt", "Gloves"), listOf(
                "1" to listOf("Angler Necklace", "Angler Cloak", "Angler Belt", "Angler Bracelet"),
                "2" to listOf("Backwater Necklace", "Backwater Cloak", "Backwater Belt", "Backwater Gloves"),
                "3" to listOf("Backwater Necklace 유지", "Finwave Cloak", "Finwave Belt", "Finwave Gloves"),
                "4" to listOf("Backwater Necklace 유지", "Gillsplash Cloak", "Gillsplash Belt", "Gillsplash Gloves"),
                "5" to listOf("Thunderbolt Necklace 또는 Magma Lord Necklace", "지역별 최적", "지역별 최적", "지역별 최적"),
            ), listOf("Gillsplash 3부위 합계 Fishing Speed +42 — 상한을 쉽게 맞추고 나머지를 SCC·Double Hook 에.",
                "Necklace: 초반 Backwater → Fishing 36 Thunderbolt → Fishing 45 Magma Lord")),
            Final("일반 물 낚시 최종", listOf("Rod" to "Rod of the Sea 또는 목적별 Giant Fishing Rod", "Armor" to "Shark Scale Armor",
                "Equipment" to "Gillsplash 3부위 + 상황별 Necklace", "Pet" to "Ammonite 또는 파티용 Dolphin")),
        )),
        Tab("용암 낚시", listOf(
            Flow("Lava Rod", listOf(s("Starter Lava Rod", "Fishing 10", "Odger 에게 무료"), s("Topaz Rod", "Fishing 19, HotM 5"), s("Magma Rod", "Fishing 27, Magmafish IV"),
                s("Inferno Rod", "Fishing 30, Magmafish VIII"), s("Hellfire Rod", "Fishing 35, Magmafish XII", "최종 — Speed +60, SCC +14%, Trophy +25")),
                listOf("Starter Lava Rod 는 Magma Rod 로 업그레이드되지 않으니 큰돈을 투자하지 마세요. Topaz Rod 는 Magma Rod 조건이 어려울 때.")),
            Flow("방어구", listOf(s("Lava Sea Creature Armor", "Fishing 35"), s("Thunder Armor", "Fishing 36", "Thunderbolt Necklace 와 함께"), s("Magma Lord Armor", "Fishing 45", "최종 — Magma Lord Necklace 와 함께"))),
            Final("용암 낚시 최종", listOf("Rod" to "Hellfire Rod", "Armor" to "Magma Lord Armor", "Equipment" to "Magma Lord Necklace + Fishing Speed/SCC 보조", "Pet" to "목적에 맞는 Fishing Pet")),
        )),
        Tab("Trophy Fishing", listOf(
            Flow("Trophy Hunter 방어구", listOf(s("Bronze Hunter Armor"), s("Silver Hunter Armor"), s("Gold Hunter Armor"), s("Diamond Hunter Armor")),
                listOf("Trophy Fisher 마일스톤으로 업그레이드. Peace Treaty 효과로 Sea Creature 를 피해 Trophy 수집에 좋아요. Festive Reforge.")),
            SlotGrid("Fisherman Set (Equipment)", listOf("Necklace", "Cloak", "Belt", "Gloves"), listOf(
                "Fisherman" to listOf("Prismarine Necklace", "Tropical Cloak", "Sponge Belt", "Clay Bracelet"),
            ), listOf("Fishing Speed 와 Trophy Chance. Crimson Isle 에서 속도 350 상한을 맞출 수 있다면 좋은 선택이에요.")),
            Final("Trophy Fishing 최종", listOf("Armor" to "Diamond Hunter Armor", "Equipment" to "Fisherman Set", "Rod" to "Trophy Line 등 Trophy Chance Rod Part", "Pet" to "지역·목표에 맞는 Fishing Pet")),
        )),
    ))

    // ================================================================ 사냥
    private val HUNTING = Skill("HUNTING", "사냥", "SKILL:HUNTING", listOf(
        "사냥은 다른 스킬과 구조가 달라요. Attribute Shard 를 포획하고 강화해 캐릭터 전체에 영향을 주는 시스템이에요. 최대 레벨 50.",
        "공개된 Hunting Guide 가 아직 완성되지 않아, 실제로 확인되는 도구와 능력치 중심으로 정리했어요.",
    ), listOf(Tab("가이드", listOf(
        Stages("진행 순서", "SKILL:HUNTING", listOf(
            Stage("초반", null, null, "", "Moonglade Marsh 에서 Hunting 튜토리얼"),
            Stage("초중반", null, null, "", "Abysmal Lasso, Basic Fishing Net, Worn Huntaxe 확보"),
            Stage("중반", null, null, "", "Lasso / Net / Huntaxe 업그레이드, Pocket Black Hole 강화"),
            Stage("후반", null, null, "", "Huntrap 설치·업그레이드, Attribute Syphon"),
            Stage("엔드게임", null, null, "", "Hunting Fortune, Charm Chance, 희귀 Attribute, David's Cloak 강화"),
        )),
        Columns("사냥 도구 다섯 갈래", listOf(
            "Lasso · 숲" to listOf(s("Abysmal Lasso"), s("Vinesap Collection 업그레이드"), s("Everstretch Lasso")),
            "Net · 물" to listOf(s("Basic Fishing Net"), s("Sea Lumies Collection 업그레이드"), s("Gigantic Fishing Net")),
            "Huntaxe · 전투" to listOf(s("Worn Huntaxe - Genesis"), s("Vinesap Collection 업그레이드"), s("Prime Huntaxe - Nex Titanum")),
            "Black Hole" to listOf(s("Small Pocket Black Hole"), s("Medium Pocket Black Hole"), s("Large Pocket Black Hole")),
            "Huntrap · 자동" to listOf(s("Small Huntrap", note = "기본 Huntrap"), s("Tender Wood Collection 업그레이드"), s("Astral Huntrap")),
        ), listOf("Lasso: 숲 생물에 던져 연결한 뒤 Stamina 를 줄여 잡아요. Vinesap Collection 으로 Hunting Fortune ↑",
            "Net: Water Attribute Shard 생물. 등급이 높으면 더 멀리 던지고 Pull 이 늘어요.",
            "Huntaxe: 몹을 죽이지 않고 체력을 낮추는 포획 무기. 안에 넣은 무기의 능력치를 일부 흡수해요.",
            "Black Hole: Small·Medium 은 Albert, Large 는 Ruby Veilshroom Collection. 체력 10% 미만 몹을 잡아 Shard 획득.",
            "Huntrap: 설치 위치의 Shard 를 자동 획득, 최대 5개")),
        Table("방어구 방향 (전용 계열 없음)", listOf("사냥 종류", "방어구 방향"), listOf(
            listOf("Forest Hunting", "이동과 생존을 고려한 일반 장비"), listOf("Water Hunting", "수중 생존, Respiration / Pressure Resistance"),
            listOf("Combat Hunting", "몹을 빠르게 약화시키며 생존 가능한 Combat 방어구"), listOf("Huntrap", "설치 후 다른 활동을 할 수 있는 범용 장비"),
        ), listOf("사냥만을 위해 초반부터 고가 방어구를 맞출 필요는 없어요. Lasso / Net / Huntaxe / Black Hole 업그레이드가 우선.")),
        Table("Equipment", listOf("슬롯", "추천"), listOf(
            listOf("Necklace", "사냥 전용 직선 업그레이드는 확인되지 않음"), listOf("Cloak", "@David's Cloak"), listOf("Belt", "Safari Belt 등 Safari 진행용"), listOf("Gloves", "일반 능력치·생존용 범용 장비"),
        ), listOf("David's Cloak: David Hunterborough 의 훈련 후 획득. Attribute 메뉴, Shard Syphon, Combat/Water/Forest/Huntrap/Salt Hunts 마일스톤으로 성장해요.")),
        Table("Attribute 능력치 우선순위", listOf("능력치", "주요 용도"), listOf(
            listOf("Hunting Fortune", "추가 Attribute Shard 획득 — 100 이면 1개 보장"), listOf("Charm Chance", "몹 처치 시 Shard 획득"),
            listOf("Respiration", "수중 체류 시간"), listOf("Pressure Resistance", "깊은 수중 압력 억제"),
        ), listOf("Shard 는 /huntingbox 에 저장. Syphon 하거나 Fusion Machine 으로 Fusion. Attribute 는 레벨 I~X, 희귀할수록 필요 Shard 가 적어요.",
            "펫: 모든 사냥에 공통 최종 펫은 없어요. Combat 은 피해·생존 보완 펫, Water 는 수중 생존 장비가 먼저.")),
        Bullets("그 밖의 필수 업그레이드", listOf("David's Cloak Milestone", "Attribute Shard 수집 · Syphon · Fusion", "Vinesap · Sea Lumies · Ruby Veilshroom · Tender Wood Collection",
            "Hunting Toolkit 확보", "Critter Safari 및 Critter Capsule", "Starborn Temple 의 Hunting 버프", "Safari 능력치 · Hunting Fortune")),
        Final("사냥 최종 목표", listOf("Lasso" to "Everstretch Lasso", "Fishing Net" to "Gigantic Fishing Net", "Huntaxe" to "Prime Huntaxe - Nex Titanum",
            "Black Hole" to "Large Pocket Black Hole", "Huntrap" to "Astral Huntrap", "Equipment" to "성장시킨 David's Cloak + 활동별 장비",
            "Attribute" to "필요한 능력치 레벨 X", "보조" to "Hunting Fortune · Charm Chance · Hunting Toolkit")),
    ))))

    // ================================================================ 전투
    private val COMBAT = Skill("COMBAT", "전투", "SKILL:COMBAT", listOf(
        "전투는 하나의 세트만 맞추면 끝나지 않아요. 일반 필드 전투 / Catacombs(Berserk·Archer·Mage·Tank·Healer) / Slayer 6종 / Crimson Isle·Kuudra / 엔드게임으로 나뉘어요.",
        "초보자라면 Berserk 로 기본 전투력을 올리고, 자금과 Catacombs 진행이 쌓이면 다른 직업으로 넓히세요.",
    ), listOf(
        Tab("진행·무기", listOf(
            Stages("일반 전투 진행 순서", "SKILL:COMBAT", listOf(
                Stage("초반", 1, 11, "Combat 1~11", "Mercenary / Rosetta / Hardened Diamond"),
                Stage("초중반", 12, 15, "Combat 12~15", "Ender Armor + Void Sword"),
                Stage("중반", 16, 19, "Combat 16~19", "Strong Dragon / Unstable Dragon"),
                Stage("중후반", 20, 21, "Combat 20~21", "Superior Dragon 선택"),
                Stage("후반", 22, 41, "Combat 22 이상", "Crimson Armor 진입"),
                Stage("엔드게임", 42, null, "Combat 42~47 이상", "상위 Kuudra Armor 및 최종 장비"),
            ), "Combat Level"),
            Flow("근접 무기", listOf(
                s("Undead Sword", note = "초반 Slayer · 언데드"), s("Void Sword", "Combat 12", "Ender Armor 와 조합"), s("Aspect of the End", note = "이동용으로 반드시 보유"),
                s("Aspect of the Dragons", "Combat 18", "중반 일반 전투"), s("Super Cleaver", note = "Dungeon 초반 가성비"), s("Hyper Cleaver", "F3 클리어"),
                s("Livid Dagger", note = "중반 근접 — 빠른 공격·치명타"), s("Flower of Truth", "F6 클리어", "방 청소용"), s("Bouquet of Lies", note = "FoT 업그레이드"),
                s("Felthorn Reaper", note = "중후반 던전 근접"), s("Giant's Sword", "F6 클리어", "강력한 단일 대상"), s("Dark Claymore", note = "Master Mode F7 등 엔드게임"),
            ), listOf("무기를 전부 순서대로 사면 안 돼요. AOTD 는 일반 필드용, Giant's Sword 는 한 방 피해를 원할 때.")),
            Flow("활 · 원거리 (Shortbow 중심)", listOf(
                s("Artisanal Shortbow", note = "극초반 (또는 Undead Bow)"), s("Dragon Shortbow", "Combat 15"), s("Machine Gun Shortbow", "F2 클리어", "초기 Dungeon"),
                s("Spirit Shortbow", "F4 클리어"), s("Juju Shortbow", "Enderman Slayer V, Cata 15", "원거리 핵심 — 450 AP 이상 권장"),
                s("Mosquito Shortbow", "Spider Slayer VII, Cata 13"), s("Terminator", "Enderman Slayer VII, Cata 27", "최종 — Crit Chance 400% 목표, Overload"),
            ), listOf("Mosquito 는 2026년 8월 버프로 단일 화살 피해가 높지만, Terminator 처럼 여러 화살을 쏘지 않아요.")),
            Flow("마법 무기 (Mage)", listOf(
                s("Dreadlord Sword", "Cata 3", "매우 저렴한 초반"), s("Bonzo's Staff", "F1 클리어", "방 청소·이동"), s("Spirit Sceptre", "F4 클리어", "넓은 범위"),
                s("Yeti Sword", "Cata 22", "Baby Yeti 와 조합"), s("Midas Staff", "Cata 25", "고가 대체"), s("Hyperion", "F7 클리어", "Wither Impact 최종급"),
                opt("Astraea", note = "생존 중심 Wither Impact"),
            ), listOf("Mage 는 Intelligence 와 Ability Damage 중심. 비용이 커서 F4 클리어 전까진 Berserk 를 권하기도 해요.")),
            Table("필수 보조 아이템", listOf("아이템", "용도", "우선순위"), listOf(
                listOf("@Aspect of the End", "순간이동", "매우 높음"), listOf("@Aspect of the Void", "고급 순간이동", "높음"), listOf("@Wand of Atonement", "체력 회복", "매우 높음"),
                listOf("@Florid Zombie Sword", "위급 상황 회복", "높음"), listOf("@Mana Flux Power Orb / @Overflux Power Orb", "회복·전투 능력치", "매우 높음"),
                listOf("@Wither Cloak Sword", "강력한 피해 방어", "고급"), listOf("@Gloomlock Grimoire", "생존 · 마나 전환", "고급"), listOf("@Necromancer Sword / @Reaper Scythe", "소환수", "상황별"),
                listOf("@Ice Spray Wand", "적 약화·제어", "Dungeon"), listOf("@Gyrokinetic Wand", "몬스터 제어·방 청소", "Dungeon"), listOf("@Ragnarock", "피해량 보조", "고급"),
            )),
        )),
        Tab("방어구", listOf(
            Flow("일반 전투용", listOf(s("Mercenary Armor", note = "초반"), s("Rosetta's Armor", note = "초반 기본"), s("Hardened Diamond Armor", note = "방어력"),
                s("Ender Armor", "Combat 12", "The End"), s("Unstable Dragon Armor", "Combat 16", "치명타"), s("Strong Dragon Armor", "Combat 16", "Strength · 근접"),
                opt("Superior Dragon Armor", "Combat 20", "모든 능력치 +5%, 비쌈"), s("Crimson Armor", "Combat 22", "Kuudra 입문 (Hot → Burning → Fiery → Infernal)")),
                listOf("Strong Dragon 은 매우 좋은 중간 목표. Superior 는 비싸서 Shadow Assassin 까지 가깝다면 건너뛰어도 좋아요.")),
            Flow("Dungeon Berserk / Archer", listOf(s("Rotten Armor", note = "Dungeon 초기"), s("Zombie Soldier Armor", note = "초반 (또는 Skeleton Master)"), s("Adaptive Armor", "F2 클리어"),
                s("Shadow Assassin Armor", "F5 클리어", "대표 중반"), s("Necron's Armor", "F7 클리어", "최종급"), opt("Maxor's Boots", note = "Necron 세팅 이동 속도 교체"), opt("Diamond Necron Head", note = "해당 보스 층에서")),
                listOf("Necron 3부위 + Diamond Head 조합, 이동 속도가 필요하면 Maxor's Boots. Warden Helmet 은 비싸고 이동 속도 패널티가 있어 초반 구매 금지.")),
            Flow("Mage", listOf(s("Wise Dragon Armor", note = "초반 Intelligence"), s("Zombie Knight Armor", note = "Dungeon 초반 (또는 Zombie Soldier)"), s("Adaptive Armor", note = "중간"),
                s("Shadow Goggles", "F5 클리어", "+ 높은 Intelligence 방어구"), s("Wither Goggles", "F7 클리어", "마법 피해 중심"), s("Storm's Armor", note = "최종급 Dungeon Mage"), s("Aurora Armor", note = "Kuudra · 필드 마법")),
                listOf("Intelligence 만이 전부는 아니에요. Necrotic Reforge 로 Intelligence 를 챙기며 Effective Health 도 보세요.")),
            Table("Tank / Healer", listOf("직업", "초반", "후반"), listOf(
                listOf("Tank", "@Heavy Armor", "@Goldor's Armor"), listOf("Healer", "Mender Helmet 계열 + 적절한 방어구", "Goldor / Necron 혼합 · 회복 보조"), listOf("공통", "각 층의 Dungeon 장비", "Diamond Head · 생존 장비"),
            )),
            Table("Slayer · Kuudra 전용", listOf("방어구", "목적"), listOf(
                listOf("@Revenant Armor", "Zombie Slayer"), listOf("@Tarantula Armor", "Spider Slayer"), listOf("@Mastiff Armor", "Wolf Slayer"), listOf("@Final Destination Armor", "Enderman Slayer"),
                listOf("@Blaze Armor", "Blaze Slayer"), listOf("@Crimson Armor", "Crimson Isle 일반 전투"), listOf("@Aurora Armor", "Mage · Kuudra"), listOf("@Terror Armor", "Kuudra 원거리"), listOf("@Magma Lord Armor", "낚시 · 용암 콘텐츠"),
            ), listOf("Final Destination 은 Voidgloom 의 높은 피해와 패턴 때문에 중요해요.")),
        )),
        Tab("Equipment·AP", listOf(
            Compare("공통 중반 진행", "초반" to "중반 / 후반", listOf(
                Triple("Necklace", "저렴한 Combat Necklace", "Bone Necklace / 클래스별 Molten 장비"), Triple("Cloak", "일반 Cloak", "Shadow Assassin Cloak"),
                Triple("Belt", "저렴한 Belt", "Adaptive Belt / Implosion Belt"), Triple("Gloves", "저렴한 Gloves", "Soulweaver Gloves / Gauntlet of Contagion"),
            ), listOf("Bone Necklace: F4 클리어 · Adaptive Belt: F2 클리어 · Shadow Assassin Cloak: F5 클리어 · Soulweaver Gloves: F6 클리어 · Gauntlet of Contagion: Combat 22 · Implosion Belt: Sulphur IX")),
            SlotGrid("클래스별 Equipment", listOf("Necklace", "Cloak", "Belt", "Gloves"), listOf(
                "Berserk" to listOf("Bone Necklace", "Shadow Assassin Cloak", "Adaptive Belt", "Soulweaver Gloves"),
                "Archer" to listOf("Bone Necklace", "Shadow Assassin Cloak", "Adaptive Belt", "Soulweaver Gloves"),
                "Mage" to listOf("지능 중심 장비", "Shadow Assassin Cloak", "Implosion Belt", "Gauntlet of Contagion"),
                "Tank" to listOf("Bone Necklace", "Shadow Assassin Cloak", "Adaptive Belt", "Soulweaver 또는 생존 장비"),
            ), listOf("Kuudra 에서는 Molten 계열 Equipment 와 속성을 갖춘 별도 세팅.")),
            Stages("Accessory Power(AP) 목표", "AP", listOf(
                Stage("초반", 0, 299, "", "150~200 AP"), Stage("Catacombs F1~F3", 300, 549, "", "약 300 AP"), Stage("F5~F6", 550, 699, "", "약 550 AP"),
                Stage("F7", 700, 949, "", "약 700 AP 이상"), Stage("Master Mode 진입", 950, 1499, "", "950~1,200 AP"), Stage("Master Mode 후반", 1500, null, "", "1,500 AP 이상"),
            )),
            Bullets("Accessory 수집 순서", listOf("1. 저렴한 Talisman · Ring 을 최대한", "2. Collection·퀘스트의 Soulbound Accessory", "3. Elizabeth 계정 업그레이드로 Bag 슬롯 확장",
                "4. 같은 계열은 상위 버전으로 업그레이드", "5. Recombobulator 로 희귀도 상승", "6. 고가 Legendary / Mythic 을 장기적으로", "7. Enrichment, Power Stones, Tuning 최적화",
                "같은 계열은 가장 높은 단계만 인정돼요 — 중복 구매하지 마세요.")),
            Table("클래스별 Power", listOf("클래스", "Power 방향"), listOf(listOf("Berserk", "Strength + Crit Damage (Bloody / Itchy)"), listOf("Archer", "Crit Chance + Crit Damage + Attack Speed (Fortuitous / Bloody)"),
                listOf("Mage", "Intelligence"), listOf("Tank", "Defense / Health → 이후 피해"), listOf("Healer", "생존력 · 지원"))),
            Table("Reforge", listOf("장비", "초중반", "후반"), listOf(listOf("Armor", "Fierce", "Ancient"), listOf("Mage Armor", "Wise / Necrotic", "Necrotic"),
                listOf("Weapon", "Spicy / Fabled", "Withered / Fabled"), listOf("Equipment", "Blended", "Blaze Wax / 클래스별"), listOf("Accessory", "Maxwell Power", "Maxwell Power"))),
        )),
        Tab("펫", listOf(
            Flow("Berserk / 근접", listOf(s("pet:Griffin", note = "초반 저렴한 대체 (Common)"), s("pet:Lion", note = "근접 피해 · Strength"), s("pet:Wither Skeleton", note = "Dungeon Wither"),
                s("pet:Baby Yeti", note = "Strength→Defense 생존"), s("pet:Ender Dragon", note = "고급 범용"), s("pet:Golden Dragon", note = "최고급 (10억+ 투자)"))),
            Flow("Archer", listOf(s("pet:Skeleton", note = "초반 활"), s("pet:Wither Skeleton", note = "Dungeon Wither"), s("pet:Tiger", note = "Ferocity · 피해량"), s("pet:Ender Dragon"), s("pet:Golden Dragon"))),
            Flow("Mage", listOf(s("pet:Sheep", note = "Intelligence · Ability"), s("pet:Baby Yeti", note = "Yeti Sword 세팅"), s("pet:Blue Whale", note = "생존력·체력"), s("pet:Ender Dragon"), s("pet:Golden Dragon"))),
            Cards("Tank / Healer", listOf(s("pet:Blue Whale", note = "높은 체력 · 생존 — Tank 대표"), s("pet:Baby Yeti", note = "Strength 기반 Defense"), s("pet:Turtle", note = "저렴한 생존력"),
                s("pet:Jellyfish", note = "회복 · 파티 지원 — Healer"), s("pet:Ender Dragon", note = "고급 전투 능력치"))),
            Cards("Slayer 전용", listOf(s("pet:Zombie", "Zombie Slayer", "언데드에 유리"), s("pet:Ghoul", "Zombie Slayer", "Revenant 소환"), s("pet:Wolf", "Wolf Slayer", "Combat XP"),
                s("pet:Enderman", "Enderman Slayer", "Voidgloom 소환"), s("pet:Blaze", "Blaze 전투"), s("pet:Subzero Wisp", "Blaze Slayer"))),
        )),
        Tab("Slayer", listOf(
            Table("Slayer 진행 순서", listOf("순서", "Slayer", "보스", "해금 조건"), listOf(
                listOf("1", "Zombie", "Revenant Horror", "기본"), listOf("2", "Spider", "Tarantula Broodfather", "Zombie Slayer II"), listOf("3", "Wolf", "Sven Packmaster", "Spider Slayer II"),
                listOf("4", "Enderman", "Voidgloom Seraph", "Wolf Slayer IV"), listOf("5", "Blaze", "Inferno Demonlord", "Enderman Slayer III"), listOf("별도", "Vampire", "Riftstalker Bloodfiend", "Globulate Timecharm"),
            ), listOf("이 순서가 장비 구매 순서와 같지는 않아요. 보스마다 맞는 무기·방어구·펫·보조 아이템을 준비하세요.")),
            Flow("Zombie Slayer — 무기", listOf(s("Undead Sword"), s("Revenant Falchion"), s("Reaper Falchion")),
                listOf("더 좋은 일반 무기가 있으면 Revenant Falchion 은 건너뛰어도 돼요.", "방어구: 초반 Lapis / Park / Hardened Diamond → 중반 Strong / Unstable Dragon. 생존: Zombie's Heart → Crystallized Heart → Revived Heart. 선택: Revenant Armor",
                    "보조: Wand of Healing → Mending → Restoration → Atonement, Reaper Mask. 펫: Zombie / Ghoul / Lion")),
            Flow("Zombie Slayer — 회복 지팡이", listOf(s("Wand of Healing"), s("Wand of Mending"), s("Wand of Restoration"), s("Wand of Atonement"))),
            Bullets("Spider Slayer", listOf("일반 근접 무기로 시작, Spider Slayer IV 이후 Tarantula 전용 장비", "방어구: Strong / Unstable Dragon, Tarantula Armor (Spider Slayer IV)",
                "Spider Slayer VII: Mosquito Shortbow 해금 조건 중 하나", "Zombie Slayer II 완료로 해금")),
            Flow("Wolf Slayer — 무기", listOf(s("Shaman Sword"), s("Pooch Sword", "Wolf Slayer VI")),
                listOf("방어구: Strong Dragon 등 + Mastiff Armor (Wolf Slayer IV), 선택 Armor of the Pack. 2026년 6월 Mastiff·Pooch 효과가 바뀌어 옛 체력 세팅은 따르지 마세요.")),
            Flow("Wolf Slayer — Power Orb (가장 중요)", listOf(s("Radiant Power Orb", "Wolf Slayer II"), s("Mana Flux Power Orb", "Wolf Slayer VI"),
                s("Overflux Power Orb", "Wolf Slayer VII"), s("Plasmaflux Power Orb", "Wolf Slayer VIII", "엔드게임 목표"))),
            Flow("Enderman Slayer — Katana", listOf(s("Voidedge Katana", note = "입문 — Soulcry 로 Ferocity"), s("Vorpal Katana", "Enderman Slayer V"), s("Atomsplit Katana", note = "최종급")),
                listOf("방어구: Ender / Dragon → Shadow Assassin + 생존 Helmet → Final Destination (각 부위 처치 기록 5,000~10,000)",
                    "보조: Wand of Atonement, Mana Flux / Overflux, Gloomlock Grimoire (Enderman Slayer V), Enderman 펫",
                    "Enderman Slayer V = Juju Shortbow, VII = Terminator 사용 조건 (던전 레벨도 필요)")),
            Flow("Blaze Slayer — Dagger", listOf(s("Twilight Dagger"), s("Mawdredge Dagger", "Blaze Slayer IV"), s("Deathripper Dagger", "Blaze Slayer VI")),
                listOf("Mawdredge·Deathripper 는 Hellion Shield 에 맞춰 Attunement 를 바꿔요.", "Blaze Armor 는 Blaze Rod VII·Combat 22 일반 방어구 — 최종 전용 방어구는 아니에요. Subzero Wisp 펫, Blaze 전용 Attribute")),
            Bullets("Vampire Slayer (The Rift)", listOf("1. Rift 의 시간·화폐 이해", "2. Stillgore Château 진입", "3. Rift 전용 방어구·무기", "4. Vampire 특수 효과·회복 준비",
                "5. Riftstalker Bloodfiend 패턴 숙달", "6. Coven Seal / Bloodbadge 보상 — 일반 Combat 장비와 섞지 마세요")),
        )),
    ))

    // ================================================================ 공통 · 우선순위
    private val COMMON = Skill("COMMON", "공통·우선순위", null, listOf(
        "일반(Classic) 프로필 기준이에요. Ironman 은 거래소에서 못 사는 아이템이 있어 순서가 더 길어질 수 있어요.",
    ), listOf(Tab("가이드", listOf(
        Table("장비 슬롯은 총 8개", listOf("구분", "슬롯"), listOf(listOf("방어구", "Helmet / Chestplate / Leggings / Boots"), listOf("Equipment", "Necklace / Cloak / Belt / Gloves")),
            listOf("Bracelet 은 다섯 번째 슬롯이 아니라 Gloves 와 같은 슬롯이에요. /equipment 로 확인, /loadouts 로 방어구·Equipment·펫·HotM/HotF·Power·Tuning 을 한 번에 전환.",
                "추천: Mining / Farming / Foraging / Fishing / Hunting / Combat 용 Loadout 을 따로 만들어 두세요.")),
        Text("Accessory 와 Equipment 는 달라요", listOf("Accessory(Talisman·Ring·Artifact·Relic)는 Accessory Bag 에 넣으면 Accessory Power(AP)가 쌓이고, Maxwell 에서 Power 를 골라 능력치를 얻어요.",
            "스킬마다 새 Accessory 를 살 필요는 없어요. 공통으로 모으고 Loadout 에서 Power 와 Tuning 만 바꾸세요. 같은 계열은 가장 높은 단계만 인정돼요.")),
        Table("여섯 스킬 투자 우선순위", listOf("스킬", "먼저", "중간 목표", "최종 목표"), listOf(
            listOf("채광", "HotM / Powder / 기본 Pickaxe", "Sorrow + Gemstone Gauntlet", "Divan Armor + Divan's Drill"),
            listOf("농사", "Garden / 주력 작물 도구", "Fermento + Mooshroom Cow", "Helianthus + Rose Dragon"),
            listOf("벌목", "HotF / Fig Hew", "Fig Armor + Figstone Splitter", "Helix Armor + Helix Chopper"),
            listOf("낚시", "Rod / Fishing Speed / SCC", "Rod of the Sea / Shark Scale", "Hellfire Rod + Magma Lord"),
            listOf("사냥", "Lasso / Net / Huntaxe", "Black Hole / Attribute", "Astral Huntrap + 속성 최적화"),
            listOf("전투", "기본 전투력 / AP / 회복 아이템", "Shadow Assassin / Juju / Slayer", "Necron / Terminator / Hyperion 등"),
        )),
        Table("별도 Loadout 으로 보관할 세팅", listOf("Loadout", "세부"), listOf(listOf("Mining", "Crystal Hollows / Glacite Mineshaft / Mithril"), listOf("Farming", "일반 작물 / Pest 소환 / Pest 처치"),
            listOf("Foraging", "Moonglade / Torrhus"), listOf("Fishing", "일반 물 / 용암 / Trophy"), listOf("Hunting", "Forest / Water / Combat"), listOf("Combat", "일반 전투 / Dungeon 클래스 / Slayer / Kuudra"))),
        Bullets("마지막으로 알아둘 점", listOf("Mining 은 HotM 개편에 따라 Perk 배분이 바뀔 수 있어요.", "Farming 은 2026년 10월 말 Accessory Bag·작물 판매 변경안이 Alpha 에 있어요 — 라이브 적용 여부 확인.",
            "Foraging 과 Hunting 은 신규 콘텐츠가 계속 추가되는 분야예요.", "Combat 의 Slayer Guide 는 2026년 10월 31일에도 WIP 상태였어요.", "커뮤니티 위키의 장비 추천은 공식 운영진이 보증하는 단일 최적 세팅이 아니에요.")),
    ))))

    val skills: List<Skill> = listOf(MINING, FARMING, FORAGING, FISHING, HUNTING, COMBAT, COMMON)

    /** 화면에서 아이템을 찾아볼 모든 이름 (검증·미리 찾기용) */
    fun allNames(): Set<String> = buildSet {
        fun step(st: Step) { add(st.name) }
        skills.flatMap { it.tabs }.flatMap { it.sections }.forEach { sec ->
            when (sec) {
                is Flow -> sec.steps.forEach(::step)
                is Ladder -> sec.groups.flatMap { it.second }.forEach(::step)
                is Columns -> sec.lines.flatMap { it.second }.forEach(::step)
                is Cards -> sec.cards.forEach(::step)
                is Bars -> sec.steps.forEach(::step)
                is SlotGrid -> sec.rows.flatMap { it.second }.forEach(::add)
                is Compare -> sec.rows.forEach { add(it.second); add(it.third) }
                is Table -> sec.rows.flatten().forEach { c -> Regex("""@([^/@]+)""").findAll(c).forEach { add(it.groupValues[1].trim()) } }
                else -> Unit
            }
        }
    }
}
