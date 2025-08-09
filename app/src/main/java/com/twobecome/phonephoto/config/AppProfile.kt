package com.twobecome.phonephoto.config

object AppProfile {
    // Build switch
    const val IS_TEST_BUILD = true
    const val IS_RELEASE_BUILD = !IS_TEST_BUILD

    // Runtime-tunable params
    val LRU_DIVISOR = if (IS_TEST_BUILD) 4 else 6          // heap 1/N
    val PREFETCH_MAX = if (IS_TEST_BUILD) 60 else 40       // concurrent prefetch cap
    val DISK_QUALITY = if (IS_TEST_BUILD) 80 else 88       // JPEG quality
    val THUMB_SIZE = 256                                    // px

    // Disk cache cap (bytes) — simple LRU by lastModified
    val DISK_MAX_BYTES: Long = if (IS_TEST_BUILD) 150L * 1024 * 1024 else 300L * 1024 * 1024

    // Animations
    val STAGGER_LIMIT_MS = if (IS_TEST_BUILD) 60 else 160
    val FADE_DISK_MS = if (IS_TEST_BUILD) 100 else 140
    val FADE_DECODE_MS = if (IS_TEST_BUILD) 220 else 260

    // Logging
    val LOG_HIT = IS_TEST_BUILD
}
