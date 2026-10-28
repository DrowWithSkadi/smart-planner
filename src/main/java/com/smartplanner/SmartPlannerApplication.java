package com.smartplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling            // включает задачи по расписанию (@Scheduled) — нужно для напоминаний
@ConfigurationPropertiesScan // подхватывает классы настроек (@ConfigurationProperties)
public class SmartPlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartPlannerApplication.class, args);
    }
}
