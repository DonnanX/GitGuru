package com.donnan.git.guru.business;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Donnan
 */
@SpringBootApplication(scanBasePackages = "com.donnan.git.guru.business")
public class GitGuruBusinessApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitGuruBusinessApplication.class, args);
    }
}
