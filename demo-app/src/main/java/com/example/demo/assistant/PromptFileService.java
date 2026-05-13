package com.example.demo.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PromptFileService {

    private static final Logger log = LoggerFactory.getLogger(PromptFileService.class);
    private static final String PROMPT_CLASSPATH_PREFIX = "classpath:prompts/";

    private final ResourceLoader resourceLoader;
    private final PromptProperties promptProperties;

    public PromptFileService(ResourceLoader resourceLoader, PromptProperties promptProperties) {
        this.resourceLoader = resourceLoader;
        this.promptProperties = promptProperties;
    }

    public String loadOrDefault(String filename, String defaultContent) {
        String fromExternal = loadFromExternalDir(filename);
        if (fromExternal != null) {
            return fromExternal;
        }

        Resource resource = resourceLoader.getResource(PROMPT_CLASSPATH_PREFIX + filename);
        if (!resource.exists()) {
            return defaultContent;
        }

        try (var stream = resource.getInputStream()) {
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            return content.isBlank() ? defaultContent : content;
        } catch (IOException ex) {
            log.warn("读取提示词文件失败: {}, 使用默认模板。原因: {}", filename, ex.getMessage());
            return defaultContent;
        }
    }

    private String loadFromExternalDir(String filename) {
        String externalDir = promptProperties.getExternalDir();
        if (externalDir == null || externalDir.isBlank()) {
            return null;
        }

        try {
            Path filePath = Path.of(externalDir.trim(), filename);
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
                return null;
            }

            String content = Files.readString(filePath, StandardCharsets.UTF_8).trim();
            return content.isBlank() ? null : content;
        } catch (Exception ex) {
            log.warn("读取外部提示词文件失败: {}/{}，将尝试回退到 classpath。原因: {}",
                    externalDir, filename, ex.getMessage());
            return null;
        }
    }
}
