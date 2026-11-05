package com.yixing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class Application {
    public static void main(String[] arguments) {
        var context = SpringApplication.run(Application.class, arguments);
        if (java.util.Arrays.stream(arguments).anyMatch(argument -> argument.startsWith("--app.reset-accounts="))) {
            context.close();
        }
    }
}
