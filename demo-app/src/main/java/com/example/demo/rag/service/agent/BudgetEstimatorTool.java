package com.example.demo.rag.service.agent;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(20)
public class BudgetEstimatorTool implements AgentTool {

    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern DAY_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*(?:天|日|晚|day|days|night|nights)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAVELER_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*(?:人|位|person|people|pax)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    @Override
    public String toolName() {
        return "预算估算";
    }

    @Override
    public boolean shouldRun(AgentToolExecutionContext context) {
        return true;
    }

    @Override
    public void execute(AgentToolExecutionContext context) {
        AgentToolRuntime runtime = context.runtime();
        String summary = estimateBudgetFromQuestion(runtime.getQuestion());
        runtime.setBudgetSummary(summary);
        runtime.addTrace(toolName(), "mode=budget_estimation; parse=days,travelers,budget", summary);
    }

    private String estimateBudgetFromQuestion(String question) {
        int days = resolveTravelDays(question);
        int travelers = resolveTravelers(question);
        int userBudget = resolveBudget(question);

        int economy = days * travelers * 320;
        int comfort = days * travelers * 620;
        int premium = days * travelers * 980;

        StringBuilder builder = new StringBuilder();
        builder.append("估算条件：")
                .append(days).append("天，")
                .append(travelers).append("人\n");
        builder.append("预算区间（人民币）：经济=")
                .append(economy)
                .append("，舒适=")
                .append(comfort)
                .append("，品质=")
                .append(premium)
                .append("。");

        if (userBudget > 0) {
            builder.append('\n');
            if (userBudget < economy) {
                builder.append("当前预算偏紧，建议减少高成本项目或缩短行程。");
            } else if (userBudget > premium) {
                builder.append("当前预算较充足，可提升酒店和体验项目标准。");
            } else {
                builder.append("当前预算可行，建议按舒适档执行。");
            }
        }

        return builder.toString();
    }

    private int resolveTravelDays(String question) {
        LocalDate start = resolveDateByKeys(question, "travel_start_date", "travelstartdate", "start_date", "departure_date");
        LocalDate end = resolveDateByKeys(question, "travel_end_date", "travelenddate", "end_date", "return_date");

        if (start != null && end != null) {
            LocalDate from = start.isAfter(end) ? end : start;
            LocalDate to = start.isAfter(end) ? start : end;
            long between = ChronoUnit.DAYS.between(from, to) + 1;
            return clamp((int) between, 1, 30);
        }

        String structured = firstNonBlank(
                extractStructuredValue(question, "trip_days", "days"),
                extractLabeledValue(question, "出行天数", "旅行天数")
        );
        int fromStructured = extractFirstNumber(structured, -1);
        if (fromStructured > 0) {
            return clamp(fromStructured, 1, 30);
        }

        Matcher matcher = DAY_PATTERN.matcher(nullToEmpty(question));
        if (matcher.find()) {
            try {
                return clamp(Integer.parseInt(matcher.group(1)), 1, 30);
            } catch (Exception ignored) {
                // ignore
            }
        }
        return 3;
    }

    private int resolveTravelers(String question) {
        int fromStructured = extractFirstNumber(extractStructuredValue(question, "travelers", "travellers", "people", "persons"), -1);
        if (fromStructured > 0) {
            return clamp(fromStructured, 1, 20);
        }

        Matcher matcher = TRAVELER_PATTERN.matcher(nullToEmpty(question));
        if (matcher.find()) {
            try {
                return clamp(Integer.parseInt(matcher.group(1)), 1, 20);
            } catch (Exception ignored) {
                // ignore
            }
        }
        return 1;
    }

    private int resolveBudget(String question) {
        String structured = firstNonBlank(
                extractStructuredValue(question, "budget", "total_budget"),
                extractLabeledValue(question, "预算", "总预算")
        );
        return extractFirstNumber(structured, -1);
    }

    private LocalDate resolveDateByKeys(String text, String... keys) {
        String direct = extractStructuredValue(text, keys);
        LocalDate parsed = parseDate(direct);
        if (parsed != null) {
            return parsed;
        }

        String fallbackLine = extractLabeledValue(text, keys);
        return parseDate(fallbackLine);
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            return LocalDate.of(year, month, day);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int extractFirstNumber(String text, int defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String extractStructuredValue(String text, String... keys) {
        if (text == null || text.isBlank() || keys == null) {
            return "";
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            int sep = trimmed.indexOf(':');
            if (sep < 0) {
                sep = trimmed.indexOf('=');
            }
            if (sep <= 0 || sep >= trimmed.length() - 1) {
                continue;
            }
            String key = trimmed.substring(0, sep).trim();
            String value = trimmed.substring(sep + 1).trim();
            for (String candidate : keys) {
                if (candidate != null && key.equalsIgnoreCase(candidate)) {
                    return value;
                }
            }
        }
        return "";
    }

    private String extractLabeledValue(String text, String... labels) {
        if (text == null || text.isBlank() || labels == null) {
            return "";
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            for (String label : labels) {
                if (label == null || label.isBlank()) {
                    continue;
                }
                int idx = trimmed.indexOf(label + "：");
                if (idx < 0) {
                    idx = trimmed.indexOf(label + ":");
                }
                if (idx < 0) {
                    continue;
                }
                String value = trimmed.substring(idx + label.length() + 1).trim();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
