package com.example.demo.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
/**
 * TripPlanSchemaInitializer类。
 * 该类型负责定义模块配置项和基础 Bean 装配，影响运行时行为。
 * 注释以中文详细描述职责边界，便于团队协作、排障与后续维护。
 */
public class TripPlanSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TripPlanSchemaInitializer.class);
    private static final String TABLE_NAME = "trip_plan_records";
    private static final String COLUMN_NAME = "answer_text";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造并初始化 TripPlanSchemaInitializer 对象。
     * 该构造方法用于注入运行所需依赖，保证对象创建后即可参与完整流程。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param jdbcTemplate 输入参数 jdbcTemplate，用于参与本次处理流程。
     */
    public TripPlanSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    /**
     * 执行 run 业务处理。
     * 该方法会结合输入参数完成当前步骤，并按约定输出处理结果。
     * 该方法用于配置管理与 Bean 装配，直接影响模块运行效果。
     * @param args 输入参数 args，用于参与本次处理流程。
     */
    public void run(String... args) {
        try {
            String dataType = jdbcTemplate.query(
                    "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() " +
                            "AND TABLE_NAME = ? " +
                            "AND COLUMN_NAME = ?",
                    preparedStatement -> {
                        preparedStatement.setString(1, TABLE_NAME);
                        preparedStatement.setString(2, COLUMN_NAME);
                    },
                    resultSet -> resultSet.next() ? resultSet.getString(1) : null
            );

            if (dataType == null) {
                return;
            }

            if (!"longtext".equals(dataType.toLowerCase(Locale.ROOT))) {
                jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " MODIFY COLUMN " + COLUMN_NAME + " LONGTEXT NOT NULL");
                log.info("Schema migrated: {}.{} -> LONGTEXT", TABLE_NAME, COLUMN_NAME);
            }
        } catch (Exception ex) {
            log.warn("Skip schema migration for {}.{}: {}", TABLE_NAME, COLUMN_NAME, ex.getMessage());
        }
    }
}
