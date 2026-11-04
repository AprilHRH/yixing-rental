package com.yixing.auth;

import java.util.Map;
import static com.yixing.common.BusinessException.require;
import static com.yixing.common.Values.*;

public final class SecurityQuestions {
    private SecurityQuestions() {}
    public static final Map<String, String> OPTIONS = Map.of(
        "birth_city", "你出生的城市是哪里？",
        "first_school", "你的小学名称是什么？",
        "favorite_food", "你最喜欢的食物是什么？");

    public static String question(Map<String, Object> input) {
        String question = required(input, "security_question", 100);
        require(OPTIONS.containsKey(question), "请选择有效的密保问题");
        return question;
    }
    public static String answer(Map<String, Object> input, String field) {
        // 只忽略答案两端空格，其他字符须与设置时一致。
        String answer = required(input, field, 60).trim();
        require(answer.length() >= 2 && answer.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72, "密保答案至少 2 个字符，且不超过 72 字节");
        return answer;
    }
    public static boolean configured(Map<String, Object> user) {
        return OPTIONS.containsKey(text(user, "security_question")) && !text(user, "security_answer_hash").isBlank();
    }
}
