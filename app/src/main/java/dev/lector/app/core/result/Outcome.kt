package dev.lector.app.core.result

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Resultado de toda operación de `data`: éxito con valor o fallo tipado. Nunca lanza. */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: DocError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

fun <T> Outcome<T>.errorOrNull(): DocError? = (this as? Outcome.Failure)?.error

inline fun <T> Outcome<T>.onFailure(action: (DocError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}

/**
 * Ejecuta [block] y convierte las excepciones esperadas en [Outcome.Failure].
 * Relanza siempre [CancellationException]. `OutOfMemoryError` solo se captura cuando
 * [catchOutOfMemory] es true (únicamente alrededor de asignación de bitmaps).
 */
inline fun <T> outcomeOf(
    catchOutOfMemory: Boolean = false,
    ioError: DocError = DocError.Corrupt,
    block: () -> T,
): Outcome<T> = try {
    Outcome.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: SecurityException) {
    Outcome.Failure(DocError.AccessLost)
} catch (e: IOException) {
    Outcome.Failure(ioError)
} catch (e: IllegalStateException) {
    Outcome.Failure(DocError.Unknown)
} catch (e: IllegalArgumentException) {
    Outcome.Failure(DocError.Unknown)
} catch (e: OutOfMemoryError) {
    if (catchOutOfMemory) Outcome.Failure(DocError.OutOfMemory) else throw e
}
