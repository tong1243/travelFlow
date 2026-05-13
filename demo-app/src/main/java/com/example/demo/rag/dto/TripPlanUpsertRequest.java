package com.example.demo.rag.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * TripPlanUpsertRequest记录类型。
 * 该类型负责封装请求与响应数据，保证接口契约清晰稳定。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 * @param title 记录字段 title，用于传递该对象的业务数据。
 * @param keyword 记录字段 keyword，用于传递该对象的业务数据。
 * @param summary 记录字段 summary，用于传递该对象的业务数据。
 * @param answer 记录字段 answer，用于传递该对象的业务数据。
 * @param departureCity 记录字段 departureCity，用于传递该对象的业务数据。
 * @param travelers 记录字段 travelers，用于传递该对象的业务数据。
 * @param startDate 记录字段 startDate，用于传递该对象的业务数据。
 * @param endDate 记录字段 endDate，用于传递该对象的业务数据。
 * @param budget 记录字段 budget，用于传递该对象的业务数据。
 * @param companionType 记录字段 companionType，用于传递该对象的业务数据。
 * @param travelStyle 记录字段 travelStyle，用于传递该对象的业务数据。
 */
public record TripPlanUpsertRequest(
        @NotBlank String title,
        @NotBlank String keyword,
        String summary,
        @NotBlank String answer,
        @NotBlank String departureCity,
        @NotNull @Min(1) Integer travelers,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank String budget,
        @NotBlank String companionType,
        @NotBlank String travelStyle
) {
}
