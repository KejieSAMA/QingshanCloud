package com.x.cloudnext.server.preview;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Opt-in, synthetic documents and a private UNO slot only. No server or database bootstrap. */
@EnabledIfSystemProperty(named="preview.integration",matches="true")
class LibreOfficeFormatsTest {
    @TempDir Path root;
    @Test void sixFormatsKeepChinesePagesAndResidentSlotCanRestart()throws Exception {
        PreviewProperties config=new PreviewProperties();config.setSofficePath(System.getProperty("preview.soffice"));config.setPythonPath(System.getProperty("preview.python"));
        config.setIsolatePresentations(Boolean.getBoolean("preview.isolatePresentations"));
        Path fixtures=Paths.get(System.getProperty("preview.fixtures"));LibreOfficeConverter converter=new LibreOfficeConverter(config);
        try {
            for(String ext:List.of("doc","docx","xls","xlsx","ppt","pptx")) {
                Path input=fixtures.resolve("sample."+ext),pdf=root.resolve(ext+".pdf");long start=System.nanoTime();
                converter.convert(input,pdf);
                try(PDDocument document=PDDocument.load(pdf.toFile())) {
                    String text=new PDFTextStripper().getText(document);
                    // PDF text extraction inserts spacing between positioned CJK glyphs in large headings.
                    assertTrue(document.getNumberOfPages()>=2,ext+" page count");assertTrue(text.replaceAll("\\s+", "").contains("云盘预览"),ext+" Chinese glyph sequence");
                    System.out.println("SYNTHETIC_PREVIEW "+ext+" pages="+document.getNumberOfPages()+" bytes="+Files.size(pdf)+" ms="+(System.nanoTime()-start)/1000000);
                }
            }
            converter.close();converter.convert(fixtures.resolve("sample.pptx"),root.resolve("restart.pdf"));assertTrue(Files.size(root.resolve("restart.pdf"))>0);
        } finally {converter.close();}
    }
}
