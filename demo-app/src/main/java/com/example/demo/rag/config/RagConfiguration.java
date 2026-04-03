package com.example.demo.rag.config;

import com.example.demo.assistant.BailianProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        BailianProperties.class,
        AppSecurityProperties.class,
        RateLimitProperties.class,
        VectorDbProperties.class,
        RagPipelineProperties.class
})
public class RagConfiguration {
}
