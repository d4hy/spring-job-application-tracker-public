package com.example.jobtracker.core.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Applies small compatibility migrations that Hibernate ddl-auto=update does not handle.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseSchemaMigrator implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseSchemaMigrator.class);
    private static final int JOB_DETAIL_TEXT_LENGTH = 2000;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        widenVarcharColumnIfNeeded("job_applications", "job_url", JOB_DETAIL_TEXT_LENGTH);
        widenVarcharColumnIfNeeded("job_applications", "notes", JOB_DETAIL_TEXT_LENGTH);
    }

    private void widenVarcharColumnIfNeeded(String tableName, String columnName, int requiredLength) {
        Integer currentLength = findCharacterLength(tableName, columnName);
        if (currentLength == null || currentLength >= requiredLength) {
            return;
        }

        String sql = "ALTER TABLE " + tableName + " ALTER COLUMN " + columnName + " TYPE VARCHAR(" + requiredLength + ")";
        jdbcTemplate.execute(sql);
        LOGGER.info("Widened {}.{} from VARCHAR({}) to VARCHAR({})", tableName, columnName, currentLength, requiredLength);
    }

    private Integer findCharacterLength(String tableName, String columnName) {
        String sql = """
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE UPPER(table_name) = ? AND UPPER(column_name) = ?
                """;
        return jdbcTemplate.query(
                sql,
                ps -> {
                    ps.setString(1, tableName.toUpperCase(Locale.ROOT));
                    ps.setString(2, columnName.toUpperCase(Locale.ROOT));
                },
                rs -> rs.next() ? rs.getObject(1, Integer.class) : null
        );
    }
}