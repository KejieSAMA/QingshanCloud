"""Create synthetic preview samples. Never reads a cloud storage or business file."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    parser.add_argument("--soffice", required=True)
    args = parser.parse_args()
    output = Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    from docx import Document
    from docx.shared import Inches
    from openpyxl import Workbook
    from pptx import Presentation
    from pptx.util import Inches as SlideInches

    document = Document()
    document.add_heading("云盘预览 · 合成验证", 0)
    document.add_paragraph("中文 Word 预览，仅用于隔离测试。")
    table = document.add_table(rows=1, cols=3)
    for cell, value in zip(table.rows[0].cells, ["编号", "名称", "数量"]):
        cell.text = value
    for i in range(35):
        for cell, value in zip(table.add_row().cells, [str(i + 1), "合成记录", str(i)]):
            cell.text = value
    document.add_page_break()
    document.add_heading("第二页", 1)
    document.add_paragraph("跨页内容完整保留。")
    document.save(output / "sample.docx")

    book = Workbook()
    for sheet in [book.active, book.create_sheet("横向工作表")]:
        sheet.append(["云盘预览", "名称", "数量"])
        for i in range(150):
            sheet.append([i + 1, "合成记录", i])
        sheet.print_title_rows = "1:1"
        sheet.print_area = "A1:C151"
        sheet.column_dimensions["A"].width = 20
        sheet.column_dimensions["B"].width = 25
    book.worksheets[1].page_setup.orientation = "landscape"
    book.save(output / "sample.xlsx")

    slides = Presentation()
    for index in range(2):
        slide = slides.slides.add_slide(slides.slide_layouts[6])
        box = slide.shapes.add_textbox(SlideInches(1), SlideInches(1), SlideInches(8), SlideInches(3))
        box.text_frame.text = "云盘预览 · 合成幻灯片 " + str(index + 1)
    slides.save(output / "sample.pptx")

    # Old binary formats are generated from these same synthetic sources in an isolated profile.
    with tempfile.TemporaryDirectory(prefix="cloudnext-fixture-profile-") as profile:
        for source, fmt in [("docx", "doc:MS Word 97"), ("xlsx", "xls:MS Excel 97"), ("pptx", "ppt:MS PowerPoint 97")]:
            subprocess.run([
                args.soffice, "-env:UserInstallation=" + Path(profile).as_uri(), "--headless", "--invisible",
                "--nologo", "--nodefault", "--nofirststartwizard", "--norestore", "--convert-to", fmt,
                "--outdir", str(output), str(output / ("sample." + source)),
            ], check=True, timeout=120, creationflags=0x08000000 if os.name == "nt" else 0)
    for ext in ["doc", "docx", "xls", "xlsx", "ppt", "pptx"]:
        file = output / ("sample." + ext)
        if not file.is_file() or file.stat().st_size == 0:
            raise RuntimeError("synthetic fixture missing: " + ext)
    print("Six synthetic Office fixtures generated")


if __name__ == "__main__":
    main()
