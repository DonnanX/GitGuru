package com.donnan.git.guru.business.service.impl;

import com.donnan.git.guru.business.constant.GitHubConstant;
import com.donnan.git.guru.business.entity.github.dto.GitHubEventDto;
import com.donnan.git.guru.business.entity.github.dto.GitHubRepoDto;
import com.donnan.git.guru.business.entity.github.dto.GitHubUserDto;
import com.donnan.git.guru.business.entity.github.dto.GitHubUserInfoDto;
import com.donnan.git.guru.business.entity.github.pojo.GitHubUser;
import com.donnan.git.guru.business.github.GitHubClient;
import com.donnan.git.guru.business.mapper.GitHubRepoMapper;
import com.donnan.git.guru.business.mapper.GitHubUserMapper;
import com.donnan.git.guru.business.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Donnan
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubServiceImpl implements GitHubService {

    private final GitHubClient gitHubClient;
    private final GitHubUserMapper gitHubUserMapper;
    private final GitHubRepoMapper gitHubRepoMapper;

    /**
     * 定时任务，每天22点执行
     */
    @Scheduled(cron = "0 0 22 * * ?")
    @Override
    public void fetchGithubUserDataPeriodically() {
        log.info("开始添加随机数量的用户...");
        List<GitHubUserDto> users = gitHubClient.getRandomUserByPage(GitHubConstant.GITHUB_USER_RANDOM_NUMBER);
        if (users == null || users.isEmpty()) return;

        for (GitHubUserDto user : users) {
            GitHubUserInfoDto userInfo = gitHubClient.getUserInfo(user.getLogin());
            if (userInfo == null) continue;
            List<GitHubRepoDto> userRepos = gitHubClient.getUserRepos(user.getLogin());
            if (userRepos == null || userRepos.isEmpty()) continue;

            GitHubUser gitHubUser = new GitHubUser();
            BeanUtils.copyProperties(userInfo, gitHubUser);
            BeanUtils.copyProperties(user, gitHubUser);

            List<GitHubEventDto> events = gitHubClient.getUserEvents(user.getLogin());

            if (events != null && !events.isEmpty()) {
                int pushEventCount = calculateEventCount(events, GitHubConstant.GITHUB_EVENT_PUSH);
                int pullRequestEventCount = calculateEventCount(events, GitHubConstant.GITHUB_EVENT_PULL_REQUEST);
                int issueEventCount = calculateEventCount(events, GitHubConstant.GITHUB_EVENT_ISSUE);
                int prReviewEventCount = calculateEventCount(events, GitHubConstant.GITHUB_EVENT_PULL_REQUEST_REVIEW);

                gitHubUser.setCommits(pushEventCount);
                gitHubUser.setPrs(pullRequestEventCount);
                gitHubUser.setIssues(issueEventCount);
                gitHubUser.setPrReviews(prReviewEventCount);
            }

            double userScore = calculateUserScore(gitHubUser);

        }
    }

    /**
     * 计算事件数量
     * @param events 事件列表
     * @param eventType 事件类型
     * @return 事件数量
     */
    private int calculateEventCount(List<GitHubEventDto> events, String eventType) {
        return (int) events.stream()
                .filter(event -> event.getType().equals(eventType))
                .count();
    }

    /**
     * 计算函数值
     * @param maxScore 最大得分
     * @param criticalPoint 临界点
     * @param point 当前点
     * @return 计算结果
     */
    private double calculateFunction(double maxScore, double criticalPoint, double point) {
        return maxScore * (1 - Math.exp(-2.5 * point / criticalPoint));
    }

    /**
     * 计算 GitHub 用户的基础数据得分
     * @param user GitHub 用户
     * @return 用户基础数据得分
     */
    private double calculateUserScore(GitHubUser user) {
        double followersScore = calculateFunction(8, 500, user.getFollowers());
        double reposScore = calculateFunction(2, 10, user.getPublicRepos());
        double commitAmountScore = calculateFunction(5, 1000, user.getCommits());
        double prAmountScore = calculateFunction(10, 100, user.getPrs());
        double issueAmountScore = calculateFunction(5, 50, user.getIssues());
        double prReviewAmountScore = calculateFunction(5, 50, user.getPrReviews());

        return followersScore + reposScore + commitAmountScore + prAmountScore + issueAmountScore + prReviewAmountScore;
    }
}
