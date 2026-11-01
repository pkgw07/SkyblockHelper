package sbhelper.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sbhelper.core.AppState
import sbhelper.core.calc.AccessoryCalc
import sbhelper.core.model.ProfileSummary
import sbhelper.core.nbt.ItemParser
import sbhelper.core.nbt.SbItem
import sbhelper.core.util.formatCoins
import sbhelper.core.util.formatNumber
import sbhelper.ui.AppController
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.McPanel
import sbhelper.ui.components.McSlotGrid
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.PixelIcon
import sbhelper.ui.components.RarityName
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.Segmented
import sbhelper.ui.components.WikiIcons
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

private val TABS = listOf("인벤토리·장비", "엔더 상자", "백팩", "옷장", "장신구", "빠진 장신구", "자루", "기타 가방")

/** 인벤토리 뷰어: 게임처럼 칸에 아이템을 놓고, 마우스를 올리면 툴팁을 보여 준다 */
@Composable
fun InventoryScreen(controller: AppController, state: AppState, wide: Boolean) {
    val tab = controller.inventoryTab
    val p = state.selectedProfile
    ScreenColumn(wide) {
        if (p == null) {
            Panel { EmptyState("계정을 연결해 주세요", "인벤토리를 보려면 계정 연결과 API 키가 필요해요.", icon = Icons.Rounded.Inventory2) }
            return@ScreenColumn
        }
        Segmented(TABS, tab, { controller.inventoryTab = it })
        if (p.inventories.isEmpty()) {
            Panel { EmptyState("인벤토리 API 가 꺼져 있어요", "게임에서 /profile → API Settings 에서 Inventory 를 켜 주세요.", icon = Icons.Rounded.Inventory2) }
            return@ScreenColumn
        }
        Text(if (wide) "칸에 마우스를 올리면 게임과 같은 아이템 정보가 떠요." else "칸을 누르면 아이템 정보가 떠요.",
            style = SbText.body(12, Sb.TextFaint))
        val slot = if (wide) 44.dp else 34.dp
        when (tab) {
            0 -> MainInventory(state, p, slot)
            1 -> Pages(state, p.inventories["ender_chest_contents"], 45, "엔더 상자", slot)
            2 -> Backpacks(state, p, slot)
            3 -> WardrobeView(state, p, slot)
            4 -> Bag(state, p.inventories["talisman_bag"], "장신구 가방", slot)
            5 -> MissingAccessories(controller, state, p)
            6 -> Sacks(state, p, slot)
            else -> {
                Bag(state, p.inventories["personal_vault_contents"], "개인 금고", slot)
                Bag(state, p.inventories["potion_bag"], "포션 가방", slot)
                Bag(state, p.inventories["fishing_bag"], "낚시 가방", slot)
                Bag(state, p.inventories["quiver"], "화살통", slot)
            }
        }
    }
}

@Composable
private fun MainInventory(state: AppState, p: ProfileSummary, slot: androidx.compose.ui.unit.Dp) {
    val inv = remember(p.inventories["inv_contents"]) { ItemParser.parse(p.inventories["inv_contents"]) }
    val armor = remember(p.inventories["inv_armor"]) { ItemParser.parse(p.inventories["inv_armor"]).reversed() } // 투구가 위
    val equip = remember(p.inventories["equipment_contents"]) { ItemParser.parse(p.inventories["equipment_contents"]) }
    McPanel("인벤토리") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("갑옷", style = SbText.pixel(11, androidx.compose.ui.graphics.Color(0xFF404040)))
                McSlotGrid(state, armor, 1, slot, minRows = 4)
            }
            Column {
                Text("장비", style = SbText.pixel(11, androidx.compose.ui.graphics.Color(0xFF404040)))
                McSlotGrid(state, equip, 1, slot, minRows = 4)
            }
            p.activePet?.let { pet ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("펫", style = SbText.pixel(11, androidx.compose.ui.graphics.Color(0xFF404040)))
                    val name = pet.type.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
                    PixelIcon(WikiIcons.url("$name Pet"), name, pet.tier, slot)
                    Text(name, style = SbText.pixel(10, androidx.compose.ui.graphics.Color(0xFF404040)))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // 게임처럼: 위 3줄 = 9~35번 칸, 맨 아래 = 단축바(0~8번)
        McSlotGrid(state, (9 until 36).map { inv.getOrNull(it) }, 9, slot, minRows = 3)
        Spacer(Modifier.height(8.dp))
        McSlotGrid(state, (0 until 9).map { inv.getOrNull(it) }, 9, slot, minRows = 1)
    }
}

@Composable
private fun Pages(state: AppState, data: String?, perPage: Int, title: String, slot: androidx.compose.ui.unit.Dp) {
    val items = remember(data) { ItemParser.parse(data) }
    if (items.isEmpty()) { Panel { EmptyState("$title 이 비어 있거나 API 가 꺼져 있어요", "") }; return }
    items.chunked(perPage).forEachIndexed { i, page ->
        if (page.any { it != null }) McPanel("$title ${i + 1}") { McSlotGrid(state, page, 9, slot) }
    }
}

@Composable
private fun Backpacks(state: AppState, p: ProfileSummary, slot: androidx.compose.ui.unit.Dp) {
    val keys = p.inventories.keys.filter { it.startsWith("backpack_") }.sortedBy { it.removePrefix("backpack_").toIntOrNull() ?: 0 }
    if (keys.isEmpty()) { Panel { EmptyState("백팩이 없어요", "") }; return }
    keys.forEach { k ->
        val items = remember(p.inventories[k]) { ItemParser.parse(p.inventories[k]) }
        McPanel("백팩 ${(k.removePrefix("backpack_").toIntOrNull() ?: 0) + 1}") { McSlotGrid(state, items, 9, slot) }
    }
}

@Composable
private fun Bag(state: AppState, data: String?, title: String, slot: androidx.compose.ui.unit.Dp) {
    val items = remember(data) { ItemParser.parse(data) }
    if (items.isEmpty()) return
    McPanel("$title · ${items.count { it != null }}개") { McSlotGrid(state, items, 9, slot) }
}

/** 자루: 자루 가방(Sack of Sacks) + 자루 속 아이템 개수 */
@Composable
private fun Sacks(state: AppState, p: ProfileSummary, slot: androidx.compose.ui.unit.Dp) {
    val sacks = state.gameData.sources.sacks
    val counts = p.sacks.orEmpty()
    val bag = remember(p.inventories["sacks_bag"]) { ItemParser.parse(p.inventories["sacks_bag"]) }
    if (bag.isNotEmpty()) {
        McPanel("자루 가방 (Sack of Sacks)") {
            McSlotGrid(state, bag, 9, slot) { item ->
                val sack = sacks[item.skyblockId ?: ""] ?: return@McSlotGrid emptyList()
                listOf("", "§e자루 속 아이템") + sack.items.map { id -> "§7${state.itemName(id)}: §a${formatNumber(counts[id] ?: 0)}" }
            }
        }
    }
    if (counts.isEmpty()) {
        Panel { EmptyState("자루 정보가 없어요", "게임 API 설정에서 Inventory 를 켜 주세요.") }
        return
    }
    val items = counts.entries.sortedByDescending { it.value }.mapIndexed { i, (id, n) ->
        SbItem(i, id, state.itemName(id), listOf("§7자루에 §a${formatNumber(n)}§7개",
            state.items[id]?.npcSellPrice?.let { "§7NPC 판매가 §6${formatCoins(it * n)}" } ?: ""),
            n.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtMost(999), 0, 0, null, state.itemTier(id), null, 0, false, 0, emptyMap(), null)
    }
    McPanel("자루 속 아이템 · ${items.size}종") { McSlotGrid(state, items, 9, slot) }
}

/** 장신구 가방에 없는 장신구 (위키 Accessories 목록 + 업그레이드 단계 기준) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingAccessories(controller: AppController, state: AppState, p: ProfileSummary) {
    var sort by remember { mutableStateOf(0) }
    val owned = remember(p.inventories) {
        AccessoryCalc.owned(listOf("talisman_bag", "inv_contents", "ender_chest_contents").flatMap { ItemParser.parse(p.inventories[it]).filterNotNull() } +
            p.inventories.keys.filter { it.startsWith("backpack_") }.flatMap { ItemParser.parse(p.inventories[it]).filterNotNull() })
    }
    val list = AccessoryCalc.missing(state.gameData, owned, state.priceBook)
    val sorted = when (sort) {
        0 -> list.sortedWith(compareBy({ it.price == null }, { it.price ?: 0.0 }))
        else -> list.sortedWith(compareBy({ it.ease }, { it.price ?: Double.MAX_VALUE }))
    }
    Panel(Modifier.fillMaxWidth(), caption = "Missing Accessories · Wiki", title = "빠진 장신구 ${list.size}개", accent = Sb.Gold,
        trailing = { Segmented(listOf("싼 순", "얻기 쉬운 순"), sort, { sort = it }) }) {
        Text("하위 단계를 가지고 있으면 다음 단계를 보여 줘요. " +
            "가격: NPC 가격 → 바자 즉시 구매가 → 경매장 최저 BIN.", style = SbText.body(12, Sb.TextFaint))
        if (state.lowestBin.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            SbButton(if (state.loadingPrices) "경매장 시세 불러오는 중…" else "경매장 시세 불러오기 (약 30초)",
                { controller.scope.launch { controller.repo.refreshLowestBins(force = true) } }, enabled = !state.loadingPrices, outlined = true)
        }
        Spacer(Modifier.height(10.dp))
        sorted.take(150).forEach { m ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                PixelIcon(WikiIcons.url(m.acc.name), m.acc.name, state.itemTier(m.acc.id), 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RarityName(m.acc.name, state.itemTier(m.acc.id), size = 14, bold = true)
                        if (m.upgrade) { Spacer(Modifier.width(6.dp)); Pill("업그레이드", Sb.Aqua) }
                    }
                    Text("${m.acc.category} · ${m.acc.npc ?: m.acc.source}", style = SbText.body(12, Sb.TextFaint), maxLines = 1)
                }
                Text(m.price?.let { formatCoins(it) } ?: "가격 정보 없음", style = SbText.pixel(13, if (m.price != null) Sb.Gold else Sb.TextFaint))
            }
        }
    }
}


/**
 * 옷장: 게임처럼 칸 번호마다 투구·갑옷·바지·신발을 세로로 (한 쪽 9칸).
 * 공식 API 의 loadout(갑옷 세트·장비 세트·로드아웃 이름) 기준. 예전 wardrobe_contents 가 있으면 그것도 보여 준다.
 */
@Composable
private fun WardrobeView(state: AppState, p: ProfileSummary, slot: androidx.compose.ui.unit.Dp) {
    val w = p.wardrobe
    if (w == null) {
        Pages(state, p.inventories["wardrobe_contents"], 36, "옷장", slot)
        return
    }
    val armor = remember(w) { w.armor.mapValues { (_, list) -> list.map { d -> d?.let { ItemParser.parse(it).firstOrNull() } } } }
    val equip = remember(w) { w.equipment.mapValues { (_, list) -> list.map { d -> d?.let { ItemParser.parse(it).firstOrNull() } } } }
    val maxSlot = (armor.keys.maxOrNull() ?: 0).coerceAtLeast(9)
    val pages = (1..maxSlot).chunked(9)
    pages.forEachIndexed { pi, nums ->
        McPanel("옷장 ${pi + 1}쪽") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                nums.forEach { n ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (n == w.equippedArmor) "▶$n" else "$n",
                            style = SbText.pixel(10, if (n == w.equippedArmor) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color(0xFF404040), bold = n == w.equippedArmor))
                        McSlotGrid(state, armor[n] ?: List(4) { null }, 1, slot, minRows = 4)
                    }
                }
            }
        }
    }
    if (equip.isNotEmpty()) {
        McPanel("장비 세트") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                equip.keys.sorted().forEach { n ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (n == w.equippedEquipment) "▶$n" else "$n",
                            style = SbText.pixel(10, if (n == w.equippedEquipment) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color(0xFF404040), bold = n == w.equippedEquipment))
                        McSlotGrid(state, equip[n] ?: List(4) { null }, 1, slot, minRows = 4)
                    }
                }
            }
        }
    }
    val named = w.loadouts.filter { it.armorSet != null || it.equipmentSet != null }
    if (named.isNotEmpty()) {
        Panel(Modifier.fillMaxWidth(), caption = "Loadouts", title = "로드아웃", accent = Sb.Aqua) {
            named.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l.name?.replace(Regex("§."), "")?.ifBlank { null } ?: "로드아웃 ${l.id}", style = SbText.body(14, Sb.Text), modifier = Modifier.weight(1f))
                    Text(listOfNotNull(l.armorSet?.let { "옷장 ${it}번" }, l.equipmentSet?.let { "장비 ${it}번" }).joinToString(" · "),
                        style = SbText.pixel(12, Sb.TextDim))
                }
            }
        }
    }
}
