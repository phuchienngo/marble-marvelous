package com.phuchienngo.marblemarvelous.wallpaper.listener

interface UserPresenceListener {
    fun onUserPresenceChanged(
        userPresence: String,
        animate: Boolean
    )
}
