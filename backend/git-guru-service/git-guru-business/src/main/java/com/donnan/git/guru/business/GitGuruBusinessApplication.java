package com.donnan.git.guru.business;

import org.mapstruct.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author Donnan
 */
@MapperScan("com.donnan.git.guru.business.mapper")
@SpringBootApplication(scanBasePackages = "com.donnan.git.guru.business")
@EnableScheduling
public class GitGuruBusinessApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitGuruBusinessApplication.class, args);
    }
}
