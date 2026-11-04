package com.yixing.data;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static com.yixing.common.Values.*;

@Component
@Order(100)
@ConditionalOnProperty(name = "app.reset-accounts", havingValue = "RESET-CUSTOMERS-AND-ORDERS")
public class AccountReset implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final Store store;
    private final AccountSeed seed;
    public AccountReset(JdbcTemplate jdbc, Store store, AccountSeed seed) { this.jdbc = jdbc; this.store = store; this.seed = seed; }
    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        // 仅由停服后的显式维护命令触发；正常启动永远不会清空业务数据。
        if (!arguments.containsOption("spring.main.web-application-type")
            || !arguments.getOptionValues("spring.main.web-application-type").contains("none")) {
            throw new IllegalStateException("账号重置必须以非 Web 维护模式运行，且须事先备份数据库");
        }
        store.lock("settings", 1);
        jdbc.update("DELETE FROM payment_record");
        jdbc.update("DELETE FROM rental_order");
        jdbc.update("DELETE FROM password_reset_ticket");
        jdbc.update("DELETE FROM sys_user");
        jdbc.update("DELETE FROM customer");
        // 租赁订单已经删除，释放出租中车辆；调度和维修状态及其记录保持原样。
        jdbc.update("UPDATE vehicle SET status='AVAILABLE' WHERE status='RENTED'");
        seed.team(); seed.customers();
        store.insert("operation_log", map("operator", "系统维护", "action", "重建演示账号并清空客户订单", "target", "2 个团队账号 / 5 个客户账号 / 0 订单", "created_at", now()));
        com.yixing.auth.SessionFilter.invalidateSessions();
    }
}
