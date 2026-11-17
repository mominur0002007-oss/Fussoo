package com.tuplayer.app.settings

/** Light, dark, or follow the phone. */
enum class ThemeMode(val label: String) {
    SYSTEM("Auto"),
    DARK("Dark"),
    LIGHT("Light")
}

/** Accent colour used for buttons, highlights and the player controls. Red is the default. */
enum class AccentColor(val label: String, val argb: Long) {
    RED("Red", 0xFFE53935),
    BLUE("Blue", 0xFF3D9BFF),
    GREEN("Green", 0xFF2E9E5B),
    ORANGE("Orange", 0xFFFF8A00),
    PURPLE("Purple", 0xFF8E5CFF),
    TEAL("Teal", 0xFF14B8A6)
}
