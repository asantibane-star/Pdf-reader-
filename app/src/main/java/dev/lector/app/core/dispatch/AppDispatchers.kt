package dev.lector.app.core.dispatch

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Despachadores inyectables. `pdf` es de un solo hilo: `PdfRenderer` no es seguro entre hilos. */
interface AppDispatchers {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
    val pdf: CoroutineDispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAppDispatchers : AppDispatchers {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val pdf: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
}
