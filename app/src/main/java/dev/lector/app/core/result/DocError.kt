package dev.lector.app.core.result

/** Errores tipados de toda la app. Cada uno tiene su mensaje en `ui/common/ErrorText.kt`. */
enum class DocError {
    UnsupportedSource,
    UnsupportedFormat,
    LegacyOfficeFormat,
    TooLarge,
    NoSpace,
    AccessLost,
    Corrupt,
    PasswordRequired,
    WrongPassword,
    ZipBomb,
    RenderTimeout,
    RendererCrashed,
    WebViewUnavailable,
    OutOfMemory,
    ConverterUnavailable,
    ConverterCrashed,
    ConversionTimeout,
    ScannerUnavailable,
    SaveFailed,
    Closed,
    Unknown,
}
