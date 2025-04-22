package com.donnan.git.guru.business.entity.github.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @author Donnan
 */
@Getter
@Setter
@ToString
@Data
@TableName("github_repo")
public class GitHubRepo {
    @TableId(value = "id")
    private Integer id;
    private String name;
    private String fullName;
    private String htmlUrl;
    private String description;
    private Date createdAt;
    private Date updatedAt;
    private Date pushedAt;
    private int size;
    private int stargazersCount;
    private int watchersCount;
    private String language;
    private int forksCount;
    private int openIssuesCount;
    private String topics;

    private String ownerLogin;
}
