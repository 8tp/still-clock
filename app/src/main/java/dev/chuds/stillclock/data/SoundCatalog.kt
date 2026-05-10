package dev.chuds.stillclock.data

import dev.chuds.stillclock.R

/**
 * The four bundled CC0 tones shipped in res/raw. URI scheme is `still://<slug>` —
 * the picker writes these strings as alarm/timer soundUri values, AlarmFiresActivity
 * resolves them back to a raw resource id at fire time.
 */
data class BundledTone(
    val slug: String,
    val label: String,
    val description: String,
    val rawResId: Int,
) {
    val uri: String = "$URI_SCHEME$slug"
}

const val URI_SCHEME = "still://"

val BUNDLED_TONES: List<BundledTone> = listOf(
    BundledTone(
        slug = "chime",
        label = "chime",
        description = "rising arpeggio · c5–c6 · gentle wake",
        rawResId = R.raw.still_chime,
    ),
    BundledTone(
        slug = "pulse",
        label = "pulse",
        description = "urgent triple beep · 880 hz · for heavy sleepers",
        rawResId = R.raw.still_pulse,
    ),
    BundledTone(
        slug = "bell",
        label = "bell",
        description = "warm two-tone bell · long reverb tail",
        rawResId = R.raw.still_bell,
    ),
    BundledTone(
        slug = "wood",
        label = "wood",
        description = "syncopated woodblock · percussive rhythm",
        rawResId = R.raw.still_wood,
    ),
)

fun bundledToneFor(uri: String?): BundledTone? {
    if (uri.isNullOrBlank() || !uri.startsWith(URI_SCHEME)) return null
    val slug = uri.removePrefix(URI_SCHEME)
    return BUNDLED_TONES.firstOrNull { it.slug == slug }
}
