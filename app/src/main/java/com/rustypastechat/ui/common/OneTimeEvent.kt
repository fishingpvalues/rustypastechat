package com.rustypastechat.ui.common

import java.util.concurrent.atomic.AtomicBoolean

class OneTimeEvent<out T>(private val content: T) {
    private val hasBeenHandled = AtomicBoolean(false)

    fun getContentIfNotHandled(): T? =
        if (hasBeenHandled.compareAndSet(false, true)) content else null

    fun peekContent(): T = content
}
