package com.hrdcoreee.lighty.update

/** A newer release discovered on GitHub. */
data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    /** Direct .apk asset URL, or null if the release has no APK attached. */
    val apkUrl: String?,
    /** GitHub release page, used as a fallback. */
    val pageUrl: String,
)

/** One-shot outcomes surfaced to the UI as toasts. */
enum class UpdateEvent {
    UP_TO_DATE,
    CHECK_FAILED,
    DOWNLOAD_FAILED,
}
