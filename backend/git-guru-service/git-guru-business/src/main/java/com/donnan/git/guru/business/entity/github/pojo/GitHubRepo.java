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
    private int id;
    private String name;
    private String full_name;
    private String html_url;
    private String description;
    private Date createdAt;
    private Date updatedAt;
    private Date pushedAt;
    private int size;
    private int stargazers_count;
    private int watchers_count;
    private String language;
    private int forks_count;
    private int open_issues_count;
    private String[] topics;
    private int forks;
    private int open_issues;
    private int watchers;

    private  String owner_login;

}
