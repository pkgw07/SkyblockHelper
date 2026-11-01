package sbhelper.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.api.WikiApi
import sbhelper.core.data.CraftTarget
import sbhelper.core.util.fixed
import sbhelper.core.util.formatCoins
import sbhelper.core.util.formatDuration
import sbhelper.ui.AppController
import sbhelper.ui.LocalPlatform
import sbhelper.ui.Screen
import sbhelper.ui.components.ItemGlyph
import sbhelper.ui.components.McText
import sbhelper.ui.components.McTooltip
import sbhelper.ui.components.RarityName
import sbhelper.ui.components.SbButton
import sbhelper.ui.theme.McColors
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/** 아이템 정보: 공식 위키 제작법·포지·드롭·미니언 + 공식 API NPC 판매가 */
@Composable
fun ItemInfo(controller: AppController, state: AppState, id: String, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    val platform = LocalPlatform.current
    val data = state.gameData
    val name = state.itemName(id)
    val tier = state.itemTier(id)
    val isPet = id.startsWith("pet:")
    McTooltip(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (isPet) "🐾 " else "") + name, style = SbText.pixel(15, McColors.rarity(tier), bold = true), modifier = Modifier.weight(1f))
            if (onClose != null) Icon(Icons.Rounded.Close, "닫기", tint = Sb.Gray, modifier = Modifier.clickable(onClick = onClose).size(18.dp))
        }
        if (tier != null) Text(McColors.rarityLabel(tier), style = SbText.pixel(11, McColors.rarity(tier)))
        Spacer(Modifier.height(8.dp))

        val pet = if (isPet) data.pets.values.flatten().firstOrNull { it.name == id.removePrefix("pet:") } else null
        pet?.lore?.take(16)?.forEach { McText("§7$it", size = 12) }
        if (pet != null) Spacer(Modifier.height(6.dp))
        state.items[id]?.npcSellPrice?.let { Line("NPC 판매가", formatCoins(it), Sb.Gold) }
        val acc = data.accessories.firstOrNull { it.id == id }
        val npc = data.npcBuy[id]
        if (npc != null) {
            Line("NPC 구매", npc.npc + if (npc.coins > 0) " · ${formatCoins(npc.coins)}" else "", Sb.Yellow)
            if (npc.cost.isNotBlank()) Line("", npc.cost, Sb.Yellow)
        }
        data.upgrades[id]?.let { Line("이전 단계", state.itemName(it), Sb.Aqua) }
        else if (acc?.npc != null) Line("NPC 구매", acc.npc + (acc.coins?.let { " · ${formatCoins(it)}" } ?: ""), Sb.Yellow)
        if (acc != null && acc.source.isNotBlank() && acc.npc == null) Line("얻는 법", acc.source, Sb.Purple)
        data.forge[id]?.firstOrNull()?.let { f ->
            Line("포지", "${formatDuration(f.sec * 1000)} · HotM ${f.hotm}", Sb.Aqua)
            Inputs(state, f.`in`)
        }
        data.crafting[id]?.firstOrNull()?.let { r ->
            Line("제작" + if (r.count > 1) " (${r.count}개)" else "", "", Sb.Green)
            Inputs(state, r.`in`)
        }
        data.sources.drops[id]?.let { mobs -> Line("드롭", mobs.take(5).joinToString(", "), Sb.Red) }
        data.sources.minions[id]?.let { Line("미니언", it, Sb.Yellow) }
        if (data.forge[id] == null && data.crafting[id] == null && data.sources.drops[id] == null && data.sources.minions[id] == null &&
            npc == null && acc == null && pet == null) {
            Text("위키 데이터에 제작·드롭 정보가 없어요. 위키 문서를 확인해 주세요.", style = SbText.body(12, Sb.Gray))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (data.forge.containsKey(id) || data.crafting.containsKey(id)) {
                SbButton("제작 플래너", {
                    controller.repo.updateSynced { s -> s.copy(craftTargets = (listOf(CraftTarget(id, 1)) + s.craftTargets.filterNot { it.itemId == id }).take(12)) }
                    controller.go(Screen.IRONMAN)
                }, icon = Icons.Rounded.Handyman, outlined = true)
            }
            SbButton("위키", { platform.openUrl(WikiApi.pageUrl(name)) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, outlined = true, color = Sb.Aqua)
        }
    }
}

@Composable
private fun Line(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = SbText.pixel(12, color), modifier = Modifier.width(76.dp))
        Text(value, style = SbText.pixel(12, Sb.Gray))
    }
}

@Composable
private fun Inputs(state: AppState, inputs: Map<String, Double>) {
    Column(Modifier.padding(start = 76.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        inputs.forEach { (k, q) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${fixed(q, if (q % 1.0 == 0.0) 0 else 1)}× ", style = SbText.pixel(11, Sb.Gray))
                Text(state.itemName(k), style = SbText.pixel(11, McColors.rarity(state.itemTier(k))))
            }
        }
    }
}

/** 아이템 칩 (테크트리·가이드 목록용) */
@Composable
fun ItemChip(state: AppState, id: String, selected: Boolean, checked: Boolean? = null, onClick: () -> Unit) {
    val name = state.itemName(id)
    Row(
        Modifier.clip(RoundedCornerShape(10.dp))
            .let { if (selected) it.then(Modifier.padding(0.dp)) else it }
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) {
            Text(if (checked) "☑" else "☐", style = SbText.body(15, if (checked) Sb.Green else Sb.TextFaint))
            Spacer(Modifier.width(4.dp))
        }
        if (id.startsWith("pet:")) Text("🐾", style = SbText.body(16)) else ItemGlyph(name, state.itemTier(id), 24.dp)
        Spacer(Modifier.width(7.dp))
        RarityName(name, state.itemTier(id), size = 13, bold = selected)
    }
}
