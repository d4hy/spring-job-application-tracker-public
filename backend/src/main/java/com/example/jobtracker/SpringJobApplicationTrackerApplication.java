package com.example.jobtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @SpringBootApplication is a composed annotation:
// - @Configuration: this class can contribute configuration.
// - @EnableAutoConfiguration: Spring Boot auto-creates common beans based on classpath + properties.
// - @ComponentScan: scans com.example.jobtracker and subpackages for @Controller/@Service/@Repository, etc.
/**
 * Application entry point that boots the Spring context and enables scheduled tasks.
 */
@SpringBootApplication
// Enables Spring's scheduling subsystem at startup.
// Spring scans managed beans for @Scheduled methods and registers trigger tasks for them.
// Supports fixedDelay, fixedRate, and cron expressions (including optional zone).
// Without @EnableScheduling, @Scheduled annotations are ignored and never executed.
/**
 * Application bootstrap class for the Job Application Tracker backend.
 * Acts as the primary Spring Boot configuration anchor and starts component scanning,
 * auto-configuration, and scheduled tasks used across the application.
 */
@EnableScheduling
public class SpringJobApplicationTrackerApplication {
 
    
    public static void main(String[] args) {

        // Reflection = runtime inspection of class metadata (annotations/methods/fields).
        // Spring reflects on this class to read @SpringBootApplication and @EnableScheduling.
        // We pass the class literal (not an object) because Spring needs the type metadata as
        // the primary configuration source, then it creates/manages objects as beans in IoC.
        SpringApplication.run(SpringJobApplicationTrackerApplication.class, args);
    }
}
