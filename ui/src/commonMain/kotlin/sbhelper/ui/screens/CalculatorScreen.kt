package sbhelper.ui.screens

import androidx.compose.runtime.Composable
import sbhelper.core.AppState
import sbhelper.ui.AppController
import sbhelper.ui.components.Segmented

/** 계산기: 제작 플래너 · 미니언 수익 계산 */
@Composable
fun CalculatorScreen(controller: AppController, state: AppState, wide: Boolean) {
    ScreenColumn(wide) {
        Segmented(listOf("제작 플래너", "미니언 수익"), controller.calcTab.coerceIn(0, 1), { controller.calcTab = it })
        when (controller.calcTab) {
            1 -> MinionCalcView(state, state.selectedProfile, wide)
            else -> CraftPlannerView(controller, state, wide)
        }
    }
}
