package com.donnan.git.guru.business.entity.github.dto;

import lombok.Data;

/**
 * @author Donnan
 */
@Data
public class GitHubFileDto {

    private String name;
    private String path;
    private String sha;
    private int size;
    private String url;
    private String type;
    private String content;
    private String encoding;

}
