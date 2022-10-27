package taboolib.common5.util

fun String.startsWith(vararg prefix: String): Boolean {
    return prefix.any { startsWith(it) }
}

fun String.endsWith(vararg suffix: String): Boolean {
    return suffix.any { endsWith(it) }
}

fun String.substringAfter(vararg morePrefix: String): String {
    return substringAfter(morePrefix.firstOrNull { startsWith(it) } ?: return this)
}

fun String.substringBefore(vararg moreSuffix: String): String {
    return substringBefore(moreSuffix.firstOrNull { endsWith(it) } ?: return this)
}