package com.yixing.auth;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RecoveryThrottle {
    private record Bucket(long expires, int count) {}
    private final Map<String, Bucket> buckets = new HashMap<>();
    // 每个来源 15 分钟最多 60 次找回请求；不信任浏览器伪造的 X-Forwarded-For。
    // 反向代理还应按真实来源限速。容量有上限，避免无限积累外部输入。
    public synchronized boolean allow(String address) {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> entry.getValue().expires() <= now);
        Bucket bucket = buckets.get(address);
        if (bucket == null && buckets.size() >= 10000) return false;
        if (bucket != null && bucket.count() >= 60) return false;
        buckets.put(address, new Bucket(bucket == null ? now + 900000 : bucket.expires(), bucket == null ? 1 : bucket.count() + 1));
        return true;
    }
    public synchronized boolean answer(String username) {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> entry.getValue().expires() <= now);
        String key = "account:" + username.trim().toLowerCase(Locale.ROOT);
        Bucket bucket = buckets.get(key);
        if (bucket == null && buckets.size() >= 10000) return false;
        if (bucket != null && bucket.count() >= 5) return false;
        buckets.put(key, new Bucket(bucket == null ? now + 900000 : bucket.expires(), bucket == null ? 1 : bucket.count() + 1));
        return true;
    }
}
