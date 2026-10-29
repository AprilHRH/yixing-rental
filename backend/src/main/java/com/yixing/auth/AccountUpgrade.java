package com.yixing.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(-100)
public class AccountUpgrade implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public AccountUpgrade(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public void run(ApplicationArguments arguments) {
        java.util.Set<String> columns = new java.util.HashSet<>();
        for (String column : jdbc.queryForRowSet("SELECT * FROM sys_user WHERE 1=0").getMetaData().getColumnNames()) columns.add(column.toLowerCase());
        if (!columns.contains("account_type")) jdbc.execute("ALTER TABLE sys_user ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'ADMIN'");
        if (!columns.contains("customer_id")) jdbc.execute("ALTER TABLE sys_user ADD COLUMN customer_id BIGINT UNIQUE");
        jdbc.update("UPDATE sys_user SET account_type='STAFF', role='ADMIN' WHERE role='STAFF'");
    }
}
