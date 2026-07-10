package com.hrdcoreee.lighty.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** Supported in-app languages. The user can switch at runtime in Settings. */
enum class Language(val code: String, val label: String) {
    EN("en", "English"),
    RU("ru", "Русский");

    companion object {
        fun fromCode(code: String?): Language =
            entries.firstOrNull { it.code == code } ?: default()

        /** Pick a sensible default from the system locale. */
        fun default(): Language =
            if (Locale.getDefault().language == "ru") RU else EN
    }
}

/**
 * Every user-facing string in the app. Add a field here, then fill it in for
 * both [RuStrings] and [EnStrings]; screens read them via [LocalStrings].
 */
data class Strings(
    // Scan screen
    val scanSubtitle: String,
    val scan: String,
    val stopScan: String,
    val searching: String,
    val devicesFound: (Int) -> String,
    val readyToScan: String,
    val unknownDevice: String,
    val bluetoothOff: String,
    val enable: String,
    val searchingNearby: String,
    val holdCloser: String,
    val emptyTitle: String,
    val emptyHint: String,
    val settingsCd: String,
    // Control screen
    val connected: String,
    val disconnectCd: String,
    val quickColors: String,
    val customColor: String,
    val brightness: String,
    val powerLabel: String,
    val stripOn: String,
    val stripOff: String,
    val onLabel: String,
    val offLabel: String,
    val connectFailed: String,
    val deviceFallback: String,
    val offline: String,
    val connecting: String,
    val offlineHint: String,
    // Permission screen
    val permTitle: String,
    val permMessage: String,
    val allow: String,
    // Settings screen
    val settingsTitle: String,
    val languageSection: String,
    val showAllDevices: String,
    val showAllDevicesDesc: String,
    val aboutSection: String,
    val versionLabel: String,
    val openGithub: String,
    val aboutDescription: String,
    val deviceSection: String,
    val unbind: String,
    val unbindDesc: String,
)

val EnStrings = Strings(
    scanSubtitle = "Find your light strip",
    scan = "Scan",
    stopScan = "Stop",
    searching = "Searching for devices…",
    devicesFound = { count -> "Devices found: $count" },
    readyToScan = "Ready to scan",
    unknownDevice = "Unknown device",
    bluetoothOff = "Bluetooth is off",
    enable = "Enable",
    searchingNearby = "Looking for nearby devices…",
    holdCloser = "Hold your phone closer to the strip",
    emptyTitle = "Nothing yet",
    emptyHint = "Tap “Scan” to find\ndevices nearby",
    settingsCd = "Settings",
    connected = "Connected",
    disconnectCd = "Disconnect",
    quickColors = "Quick colors",
    customColor = "Custom color",
    brightness = "Brightness",
    powerLabel = "Power",
    stripOn = "Strip is on",
    stripOff = "Strip is off",
    onLabel = "ON",
    offLabel = "OFF",
    connectFailed = "Failed to connect",
    deviceFallback = "Device",
    offline = "Offline",
    connecting = "Connecting…",
    offlineHint = "The strip is unavailable. Check its power and that it is nearby.",
    permTitle = "Bluetooth access needed",
    permMessage = "To find your light strip and control it, the app needs permission to scan for nearby devices.",
    allow = "Allow",
    settingsTitle = "Settings",
    languageSection = "Language",
    showAllDevices = "Show all devices",
    showAllDevicesDesc = "Scan for any BLE device, not only strip controllers",
    aboutSection = "About",
    versionLabel = "Version",
    openGithub = "Open on GitHub",
    aboutDescription = "Bluetooth LED strip control",
    deviceSection = "Strip",
    unbind = "Unbind",
    unbindDesc = "Unbind and choose another strip",
)

val RuStrings = Strings(
    scanSubtitle = "Найдите вашу подсветку",
    scan = "Сканировать",
    stopScan = "Остановить",
    searching = "Идёт поиск устройств…",
    devicesFound = { count -> "Найдено устройств: $count" },
    readyToScan = "Готово к поиску",
    unknownDevice = "Неизвестное устройство",
    bluetoothOff = "Bluetooth выключен",
    enable = "Включить",
    searchingNearby = "Ищем устройства рядом…",
    holdCloser = "Держите телефон ближе к ленте",
    emptyTitle = "Пока пусто",
    emptyHint = "Нажмите «Сканировать», чтобы найти\nустройства поблизости",
    settingsCd = "Настройки",
    connected = "Подключено",
    disconnectCd = "Отключиться",
    quickColors = "Быстрые цвета",
    customColor = "Свой цвет",
    brightness = "Яркость",
    powerLabel = "Питание",
    stripOn = "Лента включена",
    stripOff = "Лента выключена",
    onLabel = "ВКЛ",
    offLabel = "ВЫКЛ",
    connectFailed = "Не удалось подключиться",
    deviceFallback = "Устройство",
    offline = "Не в сети",
    connecting = "Подключение…",
    offlineHint = "Лента недоступна. Проверьте питание и что она рядом.",
    permTitle = "Нужен доступ к Bluetooth",
    permMessage = "Чтобы найти вашу подсветку и управлять ей, приложению нужно разрешение на поиск устройств поблизости.",
    allow = "Разрешить",
    settingsTitle = "Настройки",
    languageSection = "Язык",
    showAllDevices = "Показывать все устройства",
    showAllDevicesDesc = "Искать любые BLE-устройства, а не только контроллеры лент",
    aboutSection = "О программе",
    versionLabel = "Версия",
    openGithub = "Открыть на GitHub",
    aboutDescription = "Управление светодиодной подсветкой по Bluetooth",
    deviceSection = "Лента",
    unbind = "Отвязать",
    unbindDesc = "Отвязать и выбрать другую ленту",
)

fun stringsFor(language: Language): Strings = when (language) {
    Language.EN -> EnStrings
    Language.RU -> RuStrings
}

/** Provided at the app root; read with `LocalStrings.current` inside composables. */
val LocalStrings = staticCompositionLocalOf { EnStrings }
