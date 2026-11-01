package sbhelper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 화면 공통 스크롤 컨테이너
 *  - 화면(탭)마다 스크롤 위치를 기억한다 (다른 화면에 갔다 와도 그대로)
 *  - 가운데 내용 바깥의 빈 공간에서 굴려도 스크롤된다
 */
@Composable
fun ScreenColumn(wide: Boolean, scrollKey: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val controller = sbhelper.ui.LocalController.current
    val key = controller.screen.name + (scrollKey?.let { ":$it" } ?: controller.tabKey())
    val state = controller.scrollState(key)
    Column(
        Modifier.fillMaxSize().verticalScroll(state),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 1280.dp).fillMaxWidth()
                .padding(horizontal = if (wide) 28.dp else 14.dp)
                .padding(bottom = 28.dp, top = if (wide) 4.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (wide) 18.dp else 12.dp),
            content = content,
        )
    }
}

/** Responsive 안의 한 칸 */
class Cell(val weight: Float, val content: @Composable (Modifier) -> Unit)

fun cell(weight: Float, content: @Composable (Modifier) -> Unit) = Cell(weight, content)

/** 넓은 화면에서는 가로로, 좁은 화면에서는 세로로 배치 */
@Composable
fun Responsive(wide: Boolean, vararg cells: Cell) {
    if (wide) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            cells.forEach { c -> c.content(Modifier.weight(c.weight).fillMaxHeight()) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cells.forEach { c -> c.content(Modifier.fillMaxWidth()) }
        }
    }
}
