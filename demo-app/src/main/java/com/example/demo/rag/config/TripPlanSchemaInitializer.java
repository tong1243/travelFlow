package com.example.demo.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TripPlanSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TripPlanSchemaInitializer.class);
    private static final String TABLE_NAME = "trip_plan_records";
    private static final String COLUMN_NAME = "answer_text";

    private final JdbcTemplate jdbcTemplate;

    public TripPlanSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
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
