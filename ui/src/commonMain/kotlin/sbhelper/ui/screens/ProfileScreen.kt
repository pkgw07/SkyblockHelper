package sbhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Carpenter
import androidx.compose.material.icons.rounded.Castle
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.calc.CollectionCalc
import sbhelper.core.calc.LevelInfo
import sbhelper.core.calc.Leveling
import sbhelper.core.model.GameMode
import sbhelper.core.model.ProfileSummary
import sbhelper.core.util.formatCoins
import sbhelper.core.util.formatNumber
import sbhelper.core.util.prettifyId
import sbhelper.core.util.toRoman
import sbhelper.ui.AppController
import sbhelper.ui.Screen
import sbhelper.ui.components.EmptyState
import sbhelper.ui.components.LevelRow
import sbhelper.ui.components.Panel
import sbhelper.ui.components.Pill
import sbhelper.ui.components.PlayerHead
import sbhelper.ui.components.SbButton
import sbhelper.ui.components.SbTextField
import sbhelper.ui.components.XpBar
import sbhelper.ui.theme.McColors
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

private data class SkillStyle(val label: String, val icon: ImageVector, val color: Color)

private val SKILL_STYLES = mapOf(
    "FARMING" to SkillStyle("농사", Icons.Rounded.Agriculture, Color(0xFFB5E655)),
    "MINING" to SkillStyle("채광", Icons.Rounded.Construction, Color(0xFF8FB8DE)),
    "COMBAT" to SkillStyle("전투", Icons.Rounded.SportsMartialArts, Color(0xFFFF6B6B)),
    "FORAGING" to SkillStyle("벌목", Icons.Rounded.Park, Color(0xFF4CC38A)),
    "FISHING" to SkillStyle("낚시", Icons.Rounded.Phishing, Color(0xFF5EC8F2)),
    "ENCHANTING" to SkillStyle("마법부여", Icons.Rounded.AutoFixHigh, Color(0xFFC77DFF)),
    "ALCHEMY" to SkillStyle("연금술", Icons.Rounded.Science, Color(0xFFFFD166)),
    "TAMING" to SkillStyle("길들이기", Icons.Rounded.Pets, Color(0xFFFF9F68)),
    "HUNTING" to SkillStyle("사냥", Icons.Rounded.GpsFixed, Color(0xFFE2A55B)),
    "CARPENTRY" to SkillStyle("목공", Icons.Rounded.Carpenter, Color(0xFFC49A6C)),
    "RUNECRAFTING" to SkillStyle("룬 제작", Icons.Rounded.AutoAwesome, Color(0xFFFF7EDB)),
    "SOCIAL" to SkillStyle("사교", Icons.Rounded.Groups, Color(0xFF7EE8FA)),
)

private data class SlayerStyle(val name: String, val icon: ImageVector, val color: Color)

private val SLAYER_STYLES = mapOf(
    "zombie" to SlayerStyle("Revenant Horror", Icons.Rounded.Healing, Color(0xFF55C46B)),
    "spider" to SlayerStyle("Tarantula Broodfather", Icons.Rounded.BugReport, Color(0xFFB0544A)),
    "wolf" to SlayerStyle("Sven Packmaster", Icons.Rounded.Pets, Color(0xFFD9D9D9)),
    "enderman" to SlayerStyle("Voidgloom Seraph", Icons.Rounded.Whatshot, Color(0xFFB57BFF)),
    "blaze" to SlayerStyle("Inferno Demonlord", Icons.Rounded.LocalFireDepartment, Color(0xFFFF9A3C)),
    "vampire" to SlayerStyle("Riftstalker Bloodfiend", Icons.Rounded.WaterDrop, Color(0xFFE0405A)),
)

private val CLASS_STYLES = mapOf(
    "healer" to SlayerStyle("Healer", Icons.Rounded.Healing, Color(0xFFFF7EDB)),
    "mage" to SlayerStyle("Mage", Icons.Rounded.AutoFixHigh, Color(0xFF7EE8FA)),
    "berserk" to SlayerStyle("Berserk", Icons.Rounded.Bolt, Color(0xFFFF6B6B)),
    "archer" to SlayerStyle("Archer", Icons.Rounded.GpsFixed, Color(0xFFB5E655)),
    "tank" to SlayerStyle("Tank", Icons.Rounded.Shield, Color(0xFF8FB8DE)),
)

private fun xpText(info: LevelInfo): String = if (info.maxed) "총 ${formatCoins(info.totalXp)} XP"
else "${formatCoins(info.xpIntoLevel)} / ${formatCoins(info.xpForNext)} XP"

@Composable
fun ProfileScreen(controller: AppController, state: AppState, wide: Boolean) {
    val p = state.selectedProfile
    ScreenColumn(wide) {
        if (p == null) {
            Panel {
                EmptyState(
                    if (state.accountLinked) "프로필을 불러오는 중이에요" else "계정이 연결되지 않았어요",
                    if (state.accountLinked) state.profileError ?: "잠시만 기다려 주세요." else "설정에서 닉네임과 API 키를 입력해 주세요.",
                    action = if (!state.accountLinked) ({ SbButton("설정으로 이동", { controller.go(Screen.SETTINGS) }) }) else null,
                )
            }
            return@ScreenColumn
        }
        ProfileHeader(controller, state, p)
        Responsive(
            wide,
            cell(1.25f) { m -> SkillsPanel(state, p, m, wide) },
            cell(1f) { m ->
                Column(m, verticalArrangement = Arrangement.spacedBy(if (wide) 18.dp else 12.dp)) {
                    SlayerPanel(state, p, Modifier.fillMaxWidth())
                    DungeonPanel(state, p, Modifier.fillMaxWidth())
                }
            },
        )
        Responsive(
            wide,
            cell(1f) { m -> MiningPanel(state, p, m) },
            cell(1.4f) { m -> CollectionsPanel(state, p, m) },
        )
    }
}

@Composable
private fun ProfileHeader(controller: AppController, state: AppState, p: ProfileSummary) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Sb.Surface)
            .border(1.dp, Sb.Border, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerHead(state.synced.playerUuid, state.synced.username, 64.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(state.synced.username, style = SbText.pixel(22, bold = true))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.profiles.forEach { other ->
                    val active = other.profileId == p.profileId
                    Text(
                        (if (other.gameMode != GameMode.NORMAL) other.gameMode.badge + " " else "") + other.cuteName,
                        style = SbText.body(13, if (active) Color(0xFF15100A) else Sb.TextDim, FontWeight.SemiBold),
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (active) Sb.Gold else Sb.SurfaceHigh)
                            .clickable { controller.repo.selectProfile(other.profileId) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            p.activePet?.let { pet ->
                Text("활성 펫", style = SbText.caption())
                Text(prettifyId(pet.type), style = SbText.body(14, McColors.rarity(pet.tier), FontWeight.SemiBold))
            }
            p.fairySouls?.let { Text("요정 영혼 $it", style = SbText.pixel(12, Sb.Pink)) }
        }
    }
}

@Composable
private fun SkillsPanel(state: AppState, p: ProfileSummary, modifier: Modifier, wide: Boolean) {
    Panel(modifier, caption = "Skills · 공식 API 경험치표", title = "스킬", accent = Sb.Green) {
        val defs = state.skills?.skills
        val skillXp = p.skillXp
        when {
            skillXp == null -> EmptyState("스킬 API 가 꺼져 있어요", "게임에서 /profile → Settings → API Settings 에서 Skills 를 켜 주세요.")
            defs == null -> EmptyState("스킬 정보 불러오는 중", "")
            else -> {
                val entries = defs.entries.filter { it.key in SKILL_STYLES }
                val rows = entries.map { (key, def) ->
                    val cap = if (key == "FARMING") (50 + p.farmingCapBonus).coerceAtMost(def.maxLevel) else null
                    key to Leveling.skill(skillXp[key] ?: 0.0, def, cap)
                }
                val columns = if (wide) 2 else 1
                Column {
                    rows.chunked(columns).forEach { chunk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            chunk.forEach { (key, info) ->
                                val style = SKILL_STYLES.getValue(key)
                                LevelRow(style.label, info.level, info.maxLevel, info.progress, xpText(info), style.color, style.icon,
                                    Modifier.weight(1f))
                            }
                            if (chunk.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlayerPanel(state: AppState, p: ProfileSummary, modifier: Modifier) {
    Panel(modifier, caption = "Slayer", title = "슬레이어", accent = Sb.Red) {
        val tables = state.gameData.leveling.slayerXp
        if (p.slayerXp.isEmpty()) {
            EmptyState("슬레이어 기록 없음", "슬레이어 퀘스트를 시작하면 여기에 표시돼요.")
            return@Panel
        }
        SLAYER_STYLES.forEach { (key, style) ->
            val xp = p.slayerXp[key] ?: return@forEach
            val table = tables[key] ?: return@forEach
            val info = Leveling.slayer(xp, table)
            LevelRow(style.name, info.level, info.maxLevel, info.progress, xpText(info), style.color, style.icon)
        }
    }
}

@Composable
private fun DungeonPanel(state: AppState, p: ProfileSummary, modifier: Modifier) {
    Panel(modifier, caption = "Dungeons", title = "던전", accent = Sb.DarkRed) {
        val table = state.gameData.leveling.catacombs
        val xp = p.catacombsXp
        if (xp == null || table.isEmpty()) {
            EmptyState("던전 기록 없음", "카타콤에 입장하면 여기에 표시돼요.")
            return@Panel
        }
        val cata = Leveling.catacombs(xp, table)
        LevelRow("Catacombs", cata.level, cata.maxLevel, cata.progress, xpText(cata), Color(0xFFFF5555), Icons.Rounded.Castle)
        CLASS_STYLES.forEach { (key, style) ->
            val cxp = p.classXp[key] ?: return@forEach
            val info = Leveling.catacombs(cxp, table)
            val label = if (p.selectedClass == key) "${style.name} · 선택됨" else style.name
            LevelRow(label, info.level, info.maxLevel, info.progress, xpText(info), style.color, style.icon)
        }
    }
}

@Composable
private fun MiningPanel(state: AppState, p: ProfileSummary, modifier: Modifier) {
    Panel(modifier, caption = "Mining", title = "채광 & 재화", accent = Sb.Aqua) {
        val hotmTable = state.gameData.leveling.hotm
        p.hotmXp?.takeIf { hotmTable.isNotEmpty() }?.let { xp ->
            val info = Leveling.hotm(xp, hotmTable)
            LevelRow("Heart of the Mountain", info.level, info.maxLevel, info.progress, xpText(info), Sb.Aqua, Icons.Rounded.Diamond)
        }
        Text("Quick Forge ${if (p.quickForgeLevel > 0) "레벨 ${p.quickForgeLevel}" else "없음"}", style = SbText.body(13, Sb.TextDim))
        Spacer(Modifier.height(12.dp))
        if (p.essence.isNotEmpty()) {
            Text("에센스", style = SbText.caption())
            Spacer(Modifier.height(6.dp))
            p.essence.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(prettifyId(k), style = SbText.body(13, Sb.TextDim), modifier = Modifier.weight(1f))
                    Text(formatNumber(v), style = SbText.pixel(12, Sb.Pink))
                }
            }
        }
    }
}

@Composable
private fun CollectionsPanel(state: AppState, p: ProfileSummary, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    Panel(modifier, caption = "Collections", title = "컬렉션 — 다음 단계가 가까운 순", accent = Sb.Yellow) {
        val resource = state.collections
        val amounts = p.collections
        when {
            amounts == null -> EmptyState("컬렉션 API 가 꺼져 있어요", "게임에서 API Settings → Collections 를 켜 주세요.")
            resource == null -> EmptyState("불러오는 중", "")
            else -> {
                SbTextField(query, { query = it }, Modifier.fillMaxWidth(), "컬렉션 검색", Icons.Rounded.Search)
                Spacer(Modifier.height(10.dp))
                val list = CollectionCalc.progress(resource, amounts)
                    .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                    .sortedWith(compareBy({ it.maxed }, { -it.progress }))
                    .take(if (query.isBlank()) 12 else 40)
                list.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, style = SbText.body(14, weight = FontWeight.Medium), modifier = Modifier.weight(1f))
                                Pill(if (c.maxed) "MAX" else toRoman(c.tier), if (c.maxed) Sb.Gold else Sb.TextDim)
                            }
                            Spacer(Modifier.height(4.dp))
                            XpBar(c.progress, Modifier.fillMaxWidth(), if (c.maxed) Sb.Gold else Sb.Yellow, 6.dp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (c.maxed) "${formatNumber(c.amount)} 개 · 최대 단계"
                                else "${formatNumber(c.amount)} / ${formatNumber(c.nextRequired!!)} · 다음: ${c.nextUnlocks.firstOrNull() ?: ""}",
                                style = SbText.body(11, Sb.TextFaint), maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
