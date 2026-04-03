package com.example.demo.assistant;

import com.example.demo.assistant.dto.FileUploadResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final BailianClient bailianClient;

    public FileController(BailianClient bailianClient) {
        this.bailianClient = bailianClient;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Deprecated(since = "2026-04", forRemoval = false)
    public FileUploadResult upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "purpose", required = false) String purpose
    ) {
        return bailianClient.uploadFile(file, purpose);
    }
}
