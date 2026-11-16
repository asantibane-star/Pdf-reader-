package dev.lector.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** La `Activity` que envuelve a este contexto (los contextos de Compose suelen ser `ContextWrapper`). */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
