package com.example.wpfsboot;

import io.github.asleepyfish.annotation.EnableChatGPT;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;




@SpringBootApplication
@EnableChatGPT
public class WpfsBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(WpfsBootApplication.class, args);

    }


}
