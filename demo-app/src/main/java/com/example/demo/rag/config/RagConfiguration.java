package com.example.demo.rag.config;

import com.example.demo.assistant.BailianProperties;
import com.example.demo.assistant.PromptProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        BailianProperties.class,
        AppSecurityProperties.class,
        ApiAuditProperties.class,
        RateLimitProperties.class,
        VectorDbProperties.class,
        RagPipelineProperties.class,
        PromptProperties.class,
        FlightLookupProperties.class,
        TrainLookupProperties.class,
        HotelLookupProperties.class,
        WebSearchProperties.class
})
/**
 * RagConfiguration类。
 * 该类型负责定义模块配置项和基础 Bean 装配，影响运行时行为。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class RagConfiguration {
}
