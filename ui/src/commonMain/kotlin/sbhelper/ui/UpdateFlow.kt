package sbhelper.ui

/** 새 버전 확인 → 내려받기 → 설치 시작 (데스크톱·Android 공통) */
object UpdateFlow {
    /** @return 설치를 시작했으면 true */
    suspend fun run(controller: AppController, platform: Platform): Boolean {
        val suffix = platform.updateAssetSuffix ?: return false
        val release = controller.repo.state.value.availableUpdate ?: controller.repo.checkForUpdate() ?: return false
        val asset = release.assets.firstOrNull { it.name.endsWith(suffix, ignoreCase = true) }
        if (asset == null) {
            controller.repo.setUpdateMessage("이 기기용 설치 파일(${suffix})이 릴리스에 없어요.")
            return false
        }
        val bytes = controller.repo.downloadUpdate(asset.url) ?: return false
        val ok = platform.installUpdate(bytes, asset.name)
        controller.repo.setUpdateMessage(if (ok) "설치 프로그램을 열었어요." else "⚠ 설치를 시작하지 못했어요. Releases 페이지에서 직접 받아 주세요.")
        return ok
    }
}
