package com.example.demo.rag.service;

import com.example.demo.rag.RagException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Service
public class KnowledgeFileParserService {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "",
            "txt",
            "md",
            "markdown",
            "csv",
            "json",
            "xml",
            "yaml",
            "yml",
            "log"
    );
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "txt",
            "md",
            "markdown",
            "csv",
            "json",
            "xml",
            "yaml",
            "yml",
            "log",
            "pdf",
            "docx"
    );

    public String parseContent(String originalFilename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new RagException("上传的知识文件为空。");
        }

        String extension = extensionOf(originalFilename);
        String content;
        if ("pdf".equals(extension)) {
            content = parsePdf(bytes, originalFilename);
        } else if ("docx".equals(extension)) {
            content = parseDocx(bytes, originalFilename);
        } else if (TEXT_EXTENSIONS.contains(extension)) {
            content = new String(bytes, StandardCharsets.UTF_8);
        } else {
            throw new RagException("暂不支持的文件类型: " + extension + "，仅支持 " + String.join(", ", SUPPORTED_EXTENSIONS));
        }

        String normalized = normalize(content);
        if (normalized.isBlank()) {
            throw new RagException("上传文件解析后无有效文本内容: " + safeFilename(originalFilename));
        }
        return normalized;
    }

    private String parsePdf(byte[] bytes, String originalFilename) {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException ex) {
            throw new RagException("PDF 解析失败: " + safeFilename(originalFilename));
        }
    }

    private String parseDocx(byte[] bytes, String originalFilename) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException ex) {
            throw new RagException("DOCX 解析失败: " + safeFilename(originalFilename));
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String cleaned = filename.trim();
        int dotIndex = cleaned.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == cleaned.length() - 1) {
            return "";
        }
        return cleaned.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replace('\0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unknown";
        }
        return filename.trim();
    }
}
