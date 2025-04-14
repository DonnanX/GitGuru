package com.donnan.git.guru.business.github;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Donnan
 */
@Configuration
public class GitHubClientConfiguration {

    @Bean
    public GitHubClientConfiguration gitHubUserClient() {
        return new GitHubClientConfiguration();
    }
}
