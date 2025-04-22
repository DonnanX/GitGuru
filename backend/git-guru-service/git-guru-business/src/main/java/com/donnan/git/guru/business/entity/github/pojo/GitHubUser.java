package com.donnan.git.guru.business.entity.github.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Getter
@Setter
@ToString
@Data
@TableName("github_user")
public class GitHubUser {
    private String login;
    @TableId(value = "id")
    private Integer id;
    private String avatarUrl;
    private String htmlUrl;
    private String name;
    private String company;
    private String blog;
    private String location;
    private String email;
    private String bio;
    private String twitterUsername;
    private Integer publicRepos;
    private Integer publicGists;
    private Integer followers;
    private Integer following;
    private Date createdAt;
    private Date updatedAt;

    // 通过计算得到
    private String topic;
    private Double totalScore;
    private Double userScore;
    private Double repoScore;
    private Integer issues;
    private Integer commits;
    private Integer prs;
    private Integer prReviews;
}
