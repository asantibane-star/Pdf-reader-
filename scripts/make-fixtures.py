#!/usr/bin/env python3
"""Genera los archivos de prueba de PLAN §11.1 en app/src/test/resources/fixtures/.

Uso:
    python3 -m venv .venv && .venv/bin/pip install -r scripts/requirements-fixtures.txt
    .venv/bin/python scripts/make-fixtures.py [--no-libreoffice]

Los formatos binarios antiguos (.xls/.doc/.ppt) y los PNG de referencia se generan con LibreOffice de
escritorio (`soffice`) y `pdftoppm` si están instalados; con --no-libreoffice se omiten.
Los resultados están versionados: no hace falta regenerarlos para compilar o probar.
"""
import argparse
import io
import random
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "test" / "resources" / "fixtures"


# ---------------------------------------------------------------- PDF a mano (sin dependencias)
def make_pdf(pages):
    """pages: lista de (ancho, alto, texto). PDF 1.4 mínimo con texto buscable."""
    objects = []

    def add(body):
        objects.append(body)
        return len(objects)

    catalog = add(b"")  # se rellena después
    pages_obj = add(b"")
    font = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    page_ids = []
    for w, h, text in pages:
        stream = f"BT /F1 32 Tf 60 {h - 120} Td ({text}) Tj ET".encode("latin-1")
        content = add(b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream")
        page = add(
            f"<< /Type /Page /Parent {pages_obj} 0 R /MediaBox [0 0 {w} {h}] "
            f"/Resources << /Font << /F1 {font} 0 R >> >> /Contents {content} 0 R >>".encode()
        )
        page_ids.append(page)
    kids = " ".join(f"{i} 0 R" for i in page_ids)
    objects[catalog - 1] = f"<< /Type /Catalog /Pages {pages_obj} 0 R >>".encode()
    objects[pages_obj - 1] = f"<< /Type /Pages /Kids [{kids}] /Count {len(page_ids)} >>".encode()

    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for i, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n".encode() + b"0000000000 65535 f \n"
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objects) + 1} /Root {catalog} 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return bytes(out)


def write(name, data):
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / name
    path.write_bytes(data if isinstance(data, bytes) else data.encode("utf-8"))
    print(f"  {name} ({path.stat().st_size} bytes)")
    return path


def gen_pdfs():
    simple = make_pdf([(595, 842, f"Pagina {i} de 3 - Lector") for i in (1, 2, 3)])
    write("simple.pdf", simple)
    write("long.pdf", make_pdf([(595, 842, f"Pagina {i} de 500") for i in range(1, 501)]))
    write("mixed-sizes.pdf", make_pdf([(595, 842, "A4 vertical"), (842, 595, "A4 horizontal"),
                                       (300, 300, "Cuadrada"), (1000, 400, "Panoramica")]))
    write("truncated.pdf", simple[: int(len(simple) * 0.6)])
    write("empty.pdf", b"")
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("hola.txt", "esto es un zip, no un pdf")
    write("fake.pdf", buf.getvalue())
    from pypdf import PdfReader, PdfWriter

    writer = PdfWriter(clone_from=PdfReader(io.BytesIO(simple)))
    writer.encrypt(user_password="test", owner_password="owner-test", algorithm="AES-128")
    enc = io.BytesIO()
    writer.write(enc)
    write("encrypted.pdf", enc.getvalue())


# ---------------------------------------------------------------- Word / Excel
def gen_office_basic():
    from docx import Document
    from docx.shared import Inches
    from PIL import Image

    doc = Document()
    doc.add_heading("Informe de prueba", level=1)
    doc.add_paragraph("Este documento sirve para probar el visor. Incluye acentos: canción, año, ñandú.")
    table = doc.add_table(rows=3, cols=3)
    for r in range(3):
        for c in range(3):
            table.cell(r, c).text = f"R{r + 1}C{c + 1}"
    path = OUT / "simple.docx"
    OUT.mkdir(parents=True, exist_ok=True)
    doc.save(path)
    print(f"  simple.docx ({path.stat().st_size} bytes)")

    img = Image.new("RGB", (640, 360), (14, 107, 86))
    img_path = Path(tempfile.mkdtemp()) / "pic.png"
    img.save(img_path)
    doc2 = Document()
    doc2.add_heading("Documento con imagen", level=1)
    doc2.add_picture(str(img_path), width=Inches(5))
    doc2.add_paragraph("Texto bajo la imagen.")
    path = OUT / "images.docx"
    doc2.save(path)
    print(f"  images.docx ({path.stat().st_size} bytes)")

    from openpyxl import Workbook

    wb = Workbook()
    ws = wb.active
    ws.title = "Resumen"
    ws.append(["Concepto", "Valor"])
    ws.append(["Ingresos", 125000.5])
    ws.append(["Gastos", 87000.25])
    ws.append(["Neto", "=B2-B3"])
    ws2 = wb.create_sheet("Ventas")
    ws2.append(["Mes", "Unidades", "Región"])
    for i, m in enumerate(["Enero", "Febrero", "Marzo", "Abril"], start=1):
        ws2.append([m, i * 120, "Norte" if i % 2 else "Sur"])
    ws3 = wb.create_sheet("Notas")
    ws3.append(["<script>alert(1)</script>", "=HYPERLINK(\"http://example.invalid\")", "ñ ü é"])
    path = OUT / "simple.xlsx"
    wb.save(path)
    print(f"  simple.xlsx ({path.stat().st_size} bytes)")

    big = Workbook()
    ws = big.active
    ws.title = "Datos"
    ws.append(["Id", "Nombre", "Valor", "Fecha", "Notas"])
    for i in range(1, 10001):
        ws.append([i, f"Fila {i}", i * 1.5, "2026-01-01", "x" * (i % 20)])
    path = OUT / "big.xlsx"
    big.save(path)
    print(f"  big.xlsx ({path.stat().st_size} bytes)")


def gen_malicious_zips():
    base = {
        "[Content_Types].xml": '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>',
        "word/document.xml": '<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body/></w:document>',
    }
    # Bomba: una entrada que descomprime a ~310 MB (ceros) y ocupa unos 300 KB.
    path = OUT / "zipbomb.docx"
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in base.items():
            z.writestr(name, data)
        with z.open("word/bomb.bin", "w", force_zip64=True) as entry:
            chunk = bytes(1024 * 1024)
            for _ in range(310):
                entry.write(chunk)
    print(f"  zipbomb.docx ({path.stat().st_size} bytes)")
    path = OUT / "traversal.docx"
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in base.items():
            z.writestr(name, data)
        z.writestr("../../x", "traversal")
    print(f"  traversal.docx ({path.stat().st_size} bytes)")
    path = OUT / "manyentries.docx"
    with zipfile.ZipFile(path, "w", zipfile.ZIP_STORED) as z:
        for name, data in base.items():
            z.writestr(name, data)
        for i in range(5100):
            z.writestr(f"word/media/e{i}.txt", "")
    print(f"  manyentries.docx ({path.stat().st_size} bytes)")


# ---------------------------------------------------------------- Presentaciones
def gen_presentations():
    from pptx import Presentation
    from pptx.chart.data import CategoryChartData
    from pptx.dml.color import RGBColor
    from pptx.enum.chart import XL_CHART_TYPE
    from pptx.util import Inches, Pt

    def new_prs():
        prs = Presentation()
        prs.slide_width, prs.slide_height = Inches(13.333), Inches(7.5)
        return prs

    def title_slide(prs, title, subtitle):
        s = prs.slides.add_slide(prs.slide_layouts[0])
        s.shapes.title.text = title
        s.placeholders[1].text = subtitle
        return s

    prs = new_prs()
    title_slide(prs, "Presentación simple", "Prueba del visor Lector")
    s = prs.slides.add_slide(prs.slide_layouts[1])
    s.shapes.title.text = "Puntos clave"
    s.placeholders[1].text_frame.text = "Primer punto"
    for t in ("Segundo punto con acentos: canción", "Tercer punto"):
        s.placeholders[1].text_frame.add_paragraph().text = t
    s.notes_slide.notes_text_frame.text = "Notas del orador (no se muestran)."
    prs.save(OUT / "simple.pptx")

    prs = new_prs()
    s = prs.slides.add_slide(prs.slide_layouts[6])
    fill = s.background.fill
    fill.gradient()
    fill.gradient_angle = 45
    fill.gradient_stops[0].color.rgb = RGBColor(0x0E, 0x6B, 0x56)
    fill.gradient_stops[1].color.rgb = RGBColor(0x16, 0x33, 0x2C)
    box = s.shapes.add_textbox(Inches(1), Inches(2.5), Inches(11), Inches(2))
    box.text_frame.text = "Degradado de fondo"
    box.text_frame.paragraphs[0].runs[0].font.size = Pt(54)
    box.text_frame.paragraphs[0].runs[0].font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
    prs.save(OUT / "theme-gradients.pptx")

    prs = new_prs()
    s = prs.slides.add_slide(prs.slide_layouts[5])
    s.shapes.title.text = "Gráfico y tabla"
    data = CategoryChartData()
    data.categories = ["T1", "T2", "T3", "T4"]
    data.add_series("2025", (10, 14, 9, 17))
    data.add_series("2026", (12, 15, 13, 19))
    s.shapes.add_chart(XL_CHART_TYPE.COLUMN_CLUSTERED, Inches(0.7), Inches(1.8), Inches(6), Inches(4.5), data)
    table = s.shapes.add_table(4, 3, Inches(7.2), Inches(1.8), Inches(5.4), Inches(2.4)).table
    for r in range(4):
        for c in range(3):
            table.cell(r, c).text = f"F{r + 1}C{c + 1}"
    prs.save(OUT / "charts-tables.pptx")

    prs = new_prs()
    s = prs.slides.add_slide(prs.slide_layouts[6])
    y = 0.6
    for font in ("Calibri", "Cambria", "Arial", "Times New Roman", "Courier New"):
        tb = s.shapes.add_textbox(Inches(1), Inches(y), Inches(11), Inches(1))
        tb.text_frame.text = f"{font}: El veloz murciélago hindú comía feliz cardillo y kiwi"
        tb.text_frame.paragraphs[0].runs[0].font.name = font
        tb.text_frame.paragraphs[0].runs[0].font.size = Pt(28)
        y += 1.2
    prs.save(OUT / "fonts-calibri.pptx")

    prs = new_prs()
    s = title_slide(prs, "Enlace externo", "Debe mostrarse sin resolver nada")
    run = s.shapes.title.text_frame.paragraphs[0].runs[0]
    run.hyperlink.address = "https://example.invalid/tracking"
    prs.save(OUT / "external-link.pptx")

    # .pptm: pptx con tipo de contenido habilitado para macros y un vbaProject.bin ficticio.
    src = OUT / "simple.pptx"
    dst = OUT / "macro.pptm"
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == "[Content_Types].xml":
                data = data.decode().replace(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
                    "application/vnd.ms-powerpoint.presentation.macroEnabled.main+xml",
                ).replace("<Default ", '<Default Extension="bin" ContentType="application/vnd.ms-office.vbaProject"/><Default ', 1).encode()
            if item.filename == "ppt/_rels/presentation.xml.rels":
                data = data.decode().replace(
                    "</Relationships>",
                    '<Relationship Id="rIdVba" Type="http://schemas.microsoft.com/office/2006/relationships/vbaProject" Target="vbaProject.bin"/></Relationships>',
                ).encode()
            zout.writestr(item, data)
        zout.writestr("ppt/vbaProject.bin", b"\x00FAKE-VBA-PROJECT-FOR-TESTING-ONLY")
    for n in ("simple", "theme-gradients", "charts-tables", "fonts-calibri", "external-link"):
        print(f"  {n}.pptx ({(OUT / (n + '.pptx')).stat().st_size} bytes)")
    print(f"  macro.pptm ({dst.stat().st_size} bytes)")


# ---------------------------------------------------------------- Texto
def gen_text():
    write("utf8.txt", "Línea con acentos: canción, año, ñandú.\nSímbolos: € – “comillas” — 日本語 🙂\nÚltima línea.\n")
    write("latin1.txt", "Línea Latin-1: canción, año, ñandú.\nSegunda línea.\n".encode("latin-1"))
    rnd = random.Random(1234)
    write("binary.bin", bytes(rnd.randrange(256) for _ in range(4096)) + b"\x00\x00\x00")
    write("sample.csv", "nombre,edad,ciudad\nAna,31,Santiago\nLuis,45,Valparaíso\n")


# ---------------------------------------------------------------- LibreOffice (opcional)
def lo_convert(src: Path, fmt: str, outdir: Path):
    subprocess.run(["soffice", "--headless", "--convert-to", fmt, "--outdir", str(outdir), str(src)],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=180)


def gen_legacy_and_refs():
    if not shutil.which("soffice") or not shutil.which("pdftoppm"):
        print("  (sin soffice/pdftoppm: se omiten .xls/.doc/.ppt y PNG de referencia)")
        return
    tmp = Path(tempfile.mkdtemp())
    for src, fmt, name in (("simple.xlsx", "xls", "legacy.xls"), ("simple.docx", "doc", "legacy.doc"),
                           ("simple.pptx", "ppt", "legacy.ppt")):
        lo_convert(OUT / src, fmt, tmp)
        shutil.copy(tmp / (Path(src).stem + "." + fmt), OUT / name)
        print(f"  {name} ({(OUT / name).stat().st_size} bytes)")
    for n in ("simple", "theme-gradients", "charts-tables", "fonts-calibri", "external-link"):
        lo_convert(OUT / f"{n}.pptx", "pdf", tmp)
        subprocess.run(["pdftoppm", "-png", "-r", "60", "-f", "1", "-l", "1", "-singlefile",
                        str(tmp / f"{n}.pdf"), str(OUT / f"{n}.ref")], check=True)
        print(f"  {n}.ref.png")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-libreoffice", action="store_true")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    print("PDF"); gen_pdfs()
    print("Word/Excel"); gen_office_basic()
    print("ZIP maliciosos"); gen_malicious_zips()
    print("Presentaciones"); gen_presentations()
    print("Texto"); gen_text()
    if not args.no_libreoffice:
        print("Legados y referencias (LibreOffice)"); gen_legacy_and_refs()
    return 0


if __name__ == "__main__":
    sys.exit(main())
