package com.example.demo.assistant;

import com.example.demo.assistant.dto.SystemStatusResponse;
import com.example.demo.assistant.dto.SystemTimeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private static final DateTimeFormatter RESPONSE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

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

    @GetMapping("/time")
    public SystemTimeResponse time() {
        ZoneId zone = ZoneId.systemDefault();
        String now = OffsetDateTime.now(zone).format(RESPONSE_TIME_FORMATTER);
        return new SystemTimeResponse(now, zone.getId());
    }
}
