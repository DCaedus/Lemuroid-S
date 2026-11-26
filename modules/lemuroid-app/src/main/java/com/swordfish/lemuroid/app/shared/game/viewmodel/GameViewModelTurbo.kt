package com.swordfish.lemuroid.app.shared.game.viewmodel

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Lemuroid-S turbo. While a player's turbo button is held, pressing a button turns turbo on or off for
 * it. A button with turbo on is pressed and released repeatedly for as long as it's held.
 */
class GameViewModelTurbo(
    private val retroGameView: GameViewModelRetroGameView,
    private val scope: CoroutineScope,
) {
    private data class PortKey(val port: Int, val keyCode: Int)

    private val turboHeld = mutableSetOf<Int>()
    private val turboKeys = MutableStateFlow<Map<Int, Set<Int>>>(emptyMap())
    private val toggleKeys = mutableSetOf<PortKey>()
    private val repeaters = mutableMapOf<PortKey, Job>()

    /** The first player's buttons with turbo on, highlighted on the touch controls. */
    val firstPlayerTurboKeys: Flow<Set<Int>> = turboKeys.map { it[0].orEmpty() }.distinctUntilChanged()

    fun setTurboHeld(
        port: Int,
        held: Boolean,
    ) {
        if (held) turboHeld.add(port) else turboHeld.remove(port)
    }

    /** Handles a button of [port]. Returns true if the event is taken care of here and must not reach the core. */
    fun onButton(
        port: Int,
        keyCode: Int,
        pressed: Boolean,
    ): Boolean {
        val key = PortKey(port, keyCode)

        if (!pressed) {
            if (toggleKeys.remove(key)) return true
            val repeater = repeaters.remove(key) ?: return false
            repeater.cancel()
            send(KeyEvent.ACTION_UP, key)
            return true
        }

        if (port in turboHeld && keyCode in TURBO_BUTTONS) {
            val keys = turboKeys.value[port].orEmpty()
            turboKeys.value += port to (if (keyCode in keys) keys - keyCode else keys + keyCode)
            toggleKeys.add(key)
            return true
        }

        if (keyCode !in turboKeys.value[port].orEmpty()) return false

        repeaters.remove(key)?.cancel()
        repeaters[key] =
            scope.launch {
                while (true) {
                    send(KeyEvent.ACTION_DOWN, key)
                    delay(TURBO_PRESS_MILLIS)
                    send(KeyEvent.ACTION_UP, key)
                    delay(TURBO_RELEASE_MILLIS)
                }
            }
        return true
    }

    private fun send(
        action: Int,
        key: PortKey,
    ) {
        retroGameView.retroGameView?.sendKeyEvent(action, key.keyCode, key.port)
    }

    companion object {
        // About 10 presses per second: each press lasts a few frames, so every game notices it.
        private const val TURBO_PRESS_MILLIS = 50L
        private const val TURBO_RELEASE_MILLIS = 50L

        /** Action buttons only: not the d-pad, sticks, Start, Select or the menu. */
        private val TURBO_BUTTONS =
            setOf(
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BUTTON_C,
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_BUTTON_Z,
                KeyEvent.KEYCODE_BUTTON_L1,
                KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_L2,
                KeyEvent.KEYCODE_BUTTON_R2,
            )
    }
}
