package sbhelper.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sbhelper.core.AppState
import sbhelper.core.calc.Compressor
import sbhelper.core.util.fixed
import sbhelper.core.util.formatNumber
import sbhelper.ui.components.Segmented
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/** 재료 표기 고르기: 일반(기본 재료) / Enchanted (제작 플래너 · 미니언 계산 같이 씀) */
@Composable
internal fun MaterialFormToggle(enchanted: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("재료 표기", style = SbText.caption(Sb.TextDim))
        Spacer(Modifier.width(10.dp))
        Segmented(listOf("일반", "Enchanted"), if (enchanted) 1 else 0, { onChange(it == 1) })
    }
}

/** 기본 재료를 어떤 모양으로 보여 줄지: 대표 아이템, 대표 하나에 드는 기본 재료 수, 큰 형태부터 나눈 목록 */
internal data class ShownMaterial(val id: String, val ratio: Double, val parts: List<Pair<String, Double>>)

/** Enchanted 로 볼 때: 필요한 양 이상인 가장 큰 형태를 대표로 (예: Diamond 2,000 → Enchanted Diamond 12.5) */
internal fun shownMaterial(c: Compressor, raw: String, amount: Double, enchanted: Boolean): ShownMaterial {
    if (!enchanted) return ShownMaterial(raw, 1.0, listOf(raw to amount))
    val chain = c.chain(raw)
    if (chain.isEmpty()) return ShownMaterial(raw, 1.0, listOf(raw to amount))
    val pick = chain.lastOrNull { amount >= it.second } ?: chain.first()
    return ShownMaterial(pick.first, pick.second, c.split(raw, amount))
}

/** 개수: 정수면 그대로, 소수면 소수 첫째 자리까지 (예: 12.5) */
internal fun amountText(v: Double): String =
    if (v >= 1000 || kotlin.math.abs(v - kotlin.math.round(v)) < 1e-6) formatNumber(kotlin.math.round(v).toLong()) else fixed(v, 1)

/** "1 Enchanted Diamond Block + 12 Enchanted Diamond + 40 Diamond" */
internal fun partsText(state: AppState, parts: List<Pair<String, Double>>): String =
    parts.joinToString(" + ") { (id, n) -> "${amountText(kotlin.math.ceil(n - 1e-6))} ${state.itemName(id)}" }
