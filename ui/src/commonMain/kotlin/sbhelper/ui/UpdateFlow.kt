package sbhelper.ui

/**
 * 새 버전 확인 → 내려받기 → 적용 (데스크톱·Android 공통)
 *
 * 1. 앱 안 업데이트가 되면: 바뀐 파일만 받아 두고 "다시 시작하면 적용"
 * 2. 안 되면(런타임이 바뀐 버전 등): 설치 파일을 받아 설치
 */
object UpdateFlow {
    /** @return 업데이트를 받아 두었거나 설치를 시작했으면 true */
    suspend fun run(controller: AppController, platform: Platform): Boolean {
        val repo = controller.repo
        val release = repo.state.value.availableUpdate ?: repo.checkForUpdate() ?: return false

        platform.inAppUpdater?.let { u ->
            if (u.stagedVersion == release.version) {
                repo.setUpdateStaged(release.version)
                repo.setUpdateMessage("준비 완료! 다시 시작하면 바로 적용돼요.")
                return true
            }
            repo.setUpdateMessage("업데이트를 내려받는 중…")
            when (u.stage(release) { url -> repo.downloadUpdate(url) }) {
                InAppUpdater.Result.STAGED -> {
                    repo.setUpdateStaged(release.version)
                    repo.setUpdateMessage("준비 완료! 다시 시작하면 바로 적용돼요.")
                    return true
                }
                InAppUpdater.Result.FAILED -> {
                    repo.setUpdateMessage("⚠ 업데이트를 받지 못했어요. 잠시 후 다시 시도해 주세요.")
                    return false
                }
                InAppUpdater.Result.NEEDS_INSTALLER -> Unit // 아래 설치 파일 방식으로
            }
        }

        val suffix = platform.updateAssetSuffix ?: run {
            repo.setUpdateMessage("이 버전은 Releases 페이지에서 새로 받아 설치해 주세요.")
            return false
        }
        val asset = release.assets.firstOrNull { it.name.endsWith(suffix, ignoreCase = true) }
        if (asset == null) {
            repo.setUpdateMessage("이 기기용 설치 파일(${suffix})이 릴리스에 없어요.")
            return false
        }
        val bytes = repo.downloadUpdate(asset.url) ?: return false
        val ok = platform.installUpdate(bytes, asset.name)
        repo.setUpdateMessage(
            if (ok) (if (platform.isDesktop) "설치 프로그램을 열었어요." else "업데이트를 설치하는 중이에요. 앱이 잠시 닫혔다가 다시 열 수 있어요.")
            else "⚠ 설치를 시작하지 못했어요. Releases 페이지에서 직접 받아 주세요.",
        )
        return ok
    }
}
