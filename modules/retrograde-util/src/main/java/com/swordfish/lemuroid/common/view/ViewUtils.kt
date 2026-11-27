package com.swordfish.lemuroid.common.view

import android.animation.ObjectAnimator
import android.view.View
import android.widget.ProgressBar
import androidx.core.animation.addListener
import androidx.core.view.isVisible

fun View.disableTouchEvents() {
    this.setOnTouchListener { _, _ -> true }
}
