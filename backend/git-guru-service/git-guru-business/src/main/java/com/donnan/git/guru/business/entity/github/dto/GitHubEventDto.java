package com.donnan.git.guru.business.entity.github.dto;

import lombok.Data;

import java.util.Date;

/**
 * @author Donnan
 */
@Data
public class GitHubEventDto {
    private String id;
    private String type;
    private Date createdAt;
}
