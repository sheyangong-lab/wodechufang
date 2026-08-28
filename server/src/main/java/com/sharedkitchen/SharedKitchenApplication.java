package com.sharedkitchen;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SharedKitchenApplication {

    public static void main(String[] args) throws Exception {
        // SQLite 开发库所在目录：jdbc:sqlite 不会自动创建父目录
        Files.createDirectories(Path.of("./data"));
        SpringApplication.run(SharedKitchenApplication.class, args);
    }
}
