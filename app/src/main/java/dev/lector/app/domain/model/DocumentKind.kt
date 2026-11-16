package dev.lector.app.domain.model

/** Familia de visor que atiende a un tipo de documento. */
enum class DocumentFamily { Pdf, Presentation, Word, Sheet, Text }

/** Tipo detectado por bytes mágicos (nunca por extensión ni MIME). `monogram` se muestra en `KindTile`. */
enum class DocumentKind(val family: DocumentFamily, val monogram: String) {
    Pdf(DocumentFamily.Pdf, "PDF"),
    Pptx(DocumentFamily.Presentation, "PPT"),
    Ppt(DocumentFamily.Presentation, "PPT"),
    Odp(DocumentFamily.Presentation, "PPT"),
    Docx(DocumentFamily.Word, "DOC"),
    Doc(DocumentFamily.Word, "DOC"),
    Odt(DocumentFamily.Word, "DOC"),
    Rtf(DocumentFamily.Word, "DOC"),
    Xlsx(DocumentFamily.Sheet, "XLS"),
    Xls(DocumentFamily.Sheet, "XLS"),
    Ods(DocumentFamily.Sheet, "XLS"),
    Csv(DocumentFamily.Sheet, "XLS"),
    Text(DocumentFamily.Text, "TXT"),
    ;

    /** Esquema de ofimática que el motor B (WebView) puede mostrar sin LibreOffice. */
    val isOfficeFamily: Boolean get() = family == DocumentFamily.Presentation || family == DocumentFamily.Word

    companion object {
        fun fromName(name: String): DocumentKind = entries.firstOrNull { it.name == name } ?: Text
    }
}
