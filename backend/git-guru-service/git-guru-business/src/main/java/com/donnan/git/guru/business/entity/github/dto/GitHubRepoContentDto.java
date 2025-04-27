package com.donnan.git.guru.business.entity.github.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * @author Donnan
 */
@Data
public class GitHubRepoContentDto {
    private String ownerLogin;
    private String repoName;
    private String[] content;
}
