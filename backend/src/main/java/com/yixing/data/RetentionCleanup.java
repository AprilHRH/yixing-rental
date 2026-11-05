package com.yixing.data;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static com.yixing.common.Values.now;

@Component
public class RetentionCleanup {
    private final JdbcTemplate jdbc;
    public RetentionCleanup(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void scheduledCleanup() { cleanup(LocalDateTime.now().minusDays(7)); }

    @Transactional
    public void cleanup(LocalDateTime cutoff) {
        // 取消单及超过计划还车时间七天仍未取车的预约，可安全地连同线下流水清理。
        String expiredCancelled = "SELECT id FROM rental_order WHERE status='CANCELLED' AND cancelled_at <= ?";
        String expiredReservation = "SELECT id FROM rental_order WHERE status='RESERVED' AND planned_end <= ?";
        jdbc.update("DELETE FROM payment_record WHERE order_id IN (" + expiredCancelled + ")", cutoff.toString());
        jdbc.update("DELETE FROM payment_record WHERE order_id IN (" + expiredReservation + ")", cutoff.toString());
        jdbc.update("DELETE FROM rental_order WHERE status='CANCELLED' AND cancelled_at <= ?", cutoff.toString());
        jdbc.update("DELETE FROM rental_order WHERE status='RESERVED' AND planned_end <= ?", cutoff.toString());

        String customerCutoff = cutoff.toString();
        for (Long id : jdbc.queryForList(
                "SELECT id FROM customer WHERE closed_at IS NOT NULL AND closed_at <= ? AND purged_at IS NULL",
                Long.class, customerCutoff)) {
            for (String username : jdbc.queryForList(
                    "SELECT username FROM sys_user WHERE customer_id = ? AND role = 'CUSTOMER'",
                    String.class, id)) {
                jdbc.update("UPDATE operation_log SET operator='已注销客户', target='客户身份已清除' WHERE target=?",
                    username);
            }
            jdbc.update("DELETE FROM password_reset_ticket WHERE customer_id = ?", id);
            jdbc.update("DELETE FROM sys_user WHERE customer_id = ? AND role = 'CUSTOMER'", id);
            boolean hasHistory = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM rental_order WHERE customer_id = ?)", Boolean.class, id));
            if (hasHistory) {
                // 订单和收退款保留审计关系，但客户身份信息在七天后不可恢复。
                jdbc.update("UPDATE customer SET name='已注销客户', identity_no=?, phone='', license_no='', "
                        + "license_expiry='1970-01-01', notes='', restricted=TRUE, purged_at=? WHERE id=?",
                    "ERASED-" + id, now(), id);
            } else {
                jdbc.update("DELETE FROM customer WHERE id = ?", id);
            }
        }
    }
}
