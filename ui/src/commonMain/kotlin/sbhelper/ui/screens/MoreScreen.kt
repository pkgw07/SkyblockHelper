package sbhelper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sbhelper.ui.AppController
import sbhelper.ui.Screen
import sbhelper.ui.theme.Sb
import sbhelper.ui.theme.SbText

/** 휴대폰 "더보기" 메뉴 */
@Composable
fun MoreScreen(controller: AppController) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Screen.PROFILE to "스킬 · 슬레이어 · 던전 · 컬렉션",
            Screen.SEARCH to "모든 아이템 · NPC · 지역 · 이벤트",
            Screen.EVENTS to "이벤트 일정 · 정보 · 제이콥 농사대회 작물",
            Screen.INVENTORY to "인벤토리 · 엔더 상자 · 백팩 · 옷장 · 장신구 · 자루",
            Screen.STATS to "순자산 · HotM 트리 · 도감 · 컬렉션 · 미니언",
            Screen.GEAR to "스킬별 장비 진행",
            Screen.IRONMAN to "진행 가이드 · 제작 플래너 · 재료 출처 · 자루 가치",
            Screen.PATCH to "최신 패치노트",
            Screen.SETTINGS to "계정 · 알림 · 백그라운드 · 기기 연동 · 업데이트",
        ).forEach { (screen, desc) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sb.Surface)
                    .border(1.dp, Sb.Border, RoundedCornerShape(14.dp)).clickable { controller.go(screen) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(screen.icon, null, tint = Sb.Gold, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(screen.title, style = SbText.body(15, weight = FontWeight.SemiBold))
                    Text(desc, style = SbText.body(12, Sb.TextFaint))
                }
            }
        }
    }
}
