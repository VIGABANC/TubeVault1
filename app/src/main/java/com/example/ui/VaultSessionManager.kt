package com.example.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LockTimeout(val durationMs: Long, val displayName: String) {
    IMMEDIATE(0L, "Lock immediately"),
    SECONDS_30(30_000L, "After 30 seconds"),
    MINUTE_1(60_000L, "After 1 minute"),
    MINUTES_5(300_000L, "After 5 minutes")
}

class VaultSessionManager private constructor() {
    companion object {
        @Volatile
        private var INSTANCE: VaultSessionManager? = null

        fun getInstance(): VaultSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VaultSessionManager().also { INSTANCE = it }
            }
        }
    }

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var lastActiveTimestamp: Long = System.currentTimeMillis()
    private var backgroundedAt: Long = 0L
    private var currentTimeout: LockTimeout = LockTimeout.MINUTE_1

    fun unlockSession() {
        _isUnlocked.value = true
        lastActiveTimestamp = System.currentTimeMillis()
        backgroundedAt = 0L
    }

    fun lockSession() {
        _isUnlocked.value = false
        backgroundedAt = 0L
    }

    fun setLockTimeout(timeout: LockTimeout) {
        currentTimeout = timeout
    }

    fun getLockTimeout(): LockTimeout = currentTimeout

    fun recordActivity(now: Long = System.currentTimeMillis()) {
        if (_isUnlocked.value) {
            lastActiveTimestamp = now
        }
    }

    fun checkTimeout(now: Long = System.currentTimeMillis()): Boolean {
        if (!_isUnlocked.value) return true
        if (currentTimeout == LockTimeout.IMMEDIATE) {
            lockSession()
            return true
        }
        if (backgroundedAt > 0L) {
            val elapsedBackground = now - backgroundedAt
            if (elapsedBackground >= currentTimeout.durationMs) {
                lockSession()
                return true
            }
        } else {
            val elapsedActive = now - lastActiveTimestamp
            if (elapsedActive >= currentTimeout.durationMs) {
                lockSession()
                return true
            }
        }
        return false
    }

    fun onAppBackgrounded(now: Long = System.currentTimeMillis()) {
        if (!_isUnlocked.value) return
        if (currentTimeout == LockTimeout.IMMEDIATE) {
            lockSession()
        } else {
            backgroundedAt = now
        }
    }

    fun onAppForegrounded(now: Long = System.currentTimeMillis()) {
        if (!_isUnlocked.value) return
        if (currentTimeout == LockTimeout.IMMEDIATE) {
            lockSession()
            return
        }
        if (backgroundedAt > 0L) {
            val elapsed = now - backgroundedAt
            backgroundedAt = 0L
            if (elapsed >= currentTimeout.durationMs) {
                lockSession()
            } else {
                lastActiveTimestamp = now
            }
        }
    }
}
