package com.endel.demobox.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ddl-auto=update only adds columns; it never drops or relaxes them, and never refreshes the
 * CHECK constraint Hibernate generates for enum columns. Batteries moved from storing kWh and a
 * per-row depth of discharge to storing Ah (kWh and DoD are now derived), and gained the LIFEPO4
 * chemistry - so databases created before that need a one-time fix-up. Idempotent: does nothing
 * once the old capacity_kwh column is gone (or on a fresh schema that never had it).
 */
@Component
@Order(0)
@Log4j2
public class LegacySchemaMigration implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public LegacySchemaMigration(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws SQLException {
        if (!columnExists("batteries", "capacity_kwh")) {
            return;
        }

        int backfilled = jdbcTemplate.update(
                "UPDATE batteries SET capacity_ah = ROUND(CAST(capacity_kwh * 1000 / voltage AS NUMERIC), 2)"
                        + " WHERE capacity_ah IS NULL AND voltage > 0");
        jdbcTemplate.execute("ALTER TABLE batteries DROP COLUMN capacity_kwh");
        if (columnExists("batteries", "depth_of_discharge_percent")) {
            jdbcTemplate.execute("ALTER TABLE batteries DROP COLUMN depth_of_discharge_percent");
        }
        jdbcTemplate.execute("ALTER TABLE batteries DROP CONSTRAINT IF EXISTS batteries_chemistry_check");

        log.info("Migrated batteries to Ah-based capacity ({} rows backfilled).", backfilled);
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return hasColumn(metaData, table, column) || hasColumn(metaData, table.toUpperCase(), column.toUpperCase());
        }
    }

    private boolean hasColumn(DatabaseMetaData metaData, String table, String column) throws SQLException {
        try (ResultSet columns = metaData.getColumns(null, null, table, column)) {
            return columns.next();
        }
    }
}
