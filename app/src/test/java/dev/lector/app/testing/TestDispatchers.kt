package dev.lector.app.testing

import dev.lector.app.core.dispatch.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher

class TestDispatchers(private val dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val io = dispatcher
    override val default = dispatcher
    override val main = dispatcher
    override val pdf = dispatcher
}
