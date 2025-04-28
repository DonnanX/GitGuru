package com.donnan.git.guru.business.entity.github.pojo;

import lombok.Data;

/**
 * @author Donnan
 */
@Data
public class ESGitHubRepoContent {

    private String owner_login;
    private String repo_name;
    private String content;
    private float[] content_vector;
}
