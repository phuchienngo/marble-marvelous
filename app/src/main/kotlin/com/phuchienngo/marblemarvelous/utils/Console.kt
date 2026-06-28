package com.phuchienngo.marblemarvelous.utils

// Logging was stripped in the release build; kept as no-ops.
object Console {
    @JvmStatic fun log(
        tag: String,
        vararg messages: String
    ) {}

    @JvmStatic fun verbose(
        tag: String,
        vararg messages: String
    ) {}

    @JvmStatic fun error(
        tag: String,
        vararg messages: String
    ) {}

    @JvmStatic fun warn(
        tag: String,
        vararg messages: String
    ) {}

    @JvmStatic fun info(
        tag: String,
        vararg messages: String
    ) {}
}
