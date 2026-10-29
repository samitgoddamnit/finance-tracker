package com.personal_finance.backend;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class PDFConverter {
    public String parse(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                throw new IOException("Encrypted PDFs are not supported");
            }

            String text = new PDFTextStripper().getText(document);

            System.out.println("Extracted characters: " + text.length());
            System.out.println(text.substring(0, text.length()));

            PDStructureTreeRoot structureTree = document.getDocumentCatalog().getStructureTreeRoot();
            System.out.println(structureTree);

            return text;
        }
    }
}
