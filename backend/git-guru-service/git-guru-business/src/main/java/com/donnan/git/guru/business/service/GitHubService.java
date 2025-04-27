package com.donnan.git.guru.business.service;

import com.donnan.git.guru.business.entity.github.pojo.ESGitHubRepoContent;
import com.donnan.git.guru.business.entity.github.pojo.GitHubRepo;
import com.donnan.git.guru.business.entity.github.pojo.GitHubUser;

import java.util.List;

/**
 * @author Donnan
 */
public interface GitHubService {


    void fetchGithubUserDataPeriodically();

    GitHubUser addGitHubUserByLogin(String login);

    GitHubRepo getGitHubRepoByLoginAndRepoName(String login, String repoName);

    List<ESGitHubRepoContent> getGitHubRepoContents(String login, String repoName, String question);
}
