package com.example.demo.assistant;

import com.example.demo.assistant.dto.SystemStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final BailianClient bailianClient;
    private final BailianProperties bailianProperties;

    public SystemController(BailianClient bailianClient, BailianProperties bailianProperties) {
        this.bailianClient = bailianClient;
        this.bailianProperties = bailianProperties;
    }

    @GetMapping("/status")
    public SystemStatusResponse status() {
        return new SystemStatusResponse(
                bailianClient.hasApiKey(),
                bailianProperties.getBaseUrl(),
                bailianProperties.getDefaultModel(),
                bailianProperties.getFileModel(),
                bailianProperties.getFallbackModel(),
                bailianProperties.getMaxTokens()
        );
    }
}
