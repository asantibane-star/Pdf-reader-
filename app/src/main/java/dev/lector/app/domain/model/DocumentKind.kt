package dev.lector.app.domain.model

/** Familia de visor que atiende a un tipo de documento. */
enum class DocumentFamily { Pdf, Presentation, Word, Sheet, Text }

/** Tipo detectado por bytes mágicos (nunca por extensión ni MIME). `monogram` se muestra en `KindTile`. */
enum class DocumentKind(val family: DocumentFamily, val monogram: String, val extension: String, val mimeType: String) {
    Pdf(DocumentFamily.Pdf, "PDF", "pdf", "application/pdf"),
    Pptx(DocumentFamily.Presentation, "PPT", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    Ppt(DocumentFamily.Presentation, "PPT", "ppt", "application/vnd.ms-powerpoint"),
    Odp(DocumentFamily.Presentation, "PPT", "odp", "application/vnd.oasis.opendocument.presentation"),
    Docx(DocumentFamily.Word, "DOC", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    Doc(DocumentFamily.Word, "DOC", "doc", "application/msword"),
    Odt(DocumentFamily.Word, "DOC", "odt", "application/vnd.oasis.opendocument.text"),
    Rtf(DocumentFamily.Word, "DOC", "rtf", "application/rtf"),
    Xlsx(DocumentFamily.Sheet, "XLS", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    Xls(DocumentFamily.Sheet, "XLS", "xls", "application/vnd.ms-excel"),
    Ods(DocumentFamily.Sheet, "XLS", "ods", "application/vnd.oasis.opendocument.spreadsheet"),
    Csv(DocumentFamily.Sheet, "XLS", "csv", "text/csv"),
    Text(DocumentFamily.Text, "TXT", "txt", "text/plain"),
    ;

    /** Esquema de ofimática que el motor B (WebView) puede mostrar sin LibreOffice. */
    val isOfficeFamily: Boolean get() = family == DocumentFamily.Presentation || family == DocumentFamily.Word

    companion object {
        fun fromName(name: String): DocumentKind = entries.firstOrNull { it.name == name } ?: Text
    }
}
