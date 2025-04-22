package com.donnan.git.guru.business.service.impl;

import com.donnan.git.guru.business.constant.GitHubConstant;
import com.donnan.git.guru.business.entity.github.dto.GitHubEventDto;
import com.donnan.git.guru.business.entity.github.dto.GitHubRepoDto;
import com.donnan.git.guru.business.entity.github.dto.GitHubUserDto;
import com.donnan.git.guru.business.entity.github.dto.GitHubUserInfoDto;
import com.donnan.git.guru.business.entity.github.pojo.GitHubRepo;
import com.donnan.git.guru.business.entity.github.pojo.GitHubUser;
import com.donnan.git.guru.business.github.GitHubClient;
import com.donnan.git.guru.business.mapper.GitHubRepoMapper;
import com.donnan.git.guru.business.mapper.GitHubUserMapper;
import com.donnan.git.guru.business.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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
    @Scheduled(cron = "${github.fetch.cron:0 0 22 * * ?}")
    @Override
    public void fetchGithubUserDataPeriodically() {
        log.info("开始添加随机数量的用户...");
        int fetchSize = GitHubConstant.GITHUB_USER_RANDOM_NUMBER;
        try {
            List<GitHubUserDto> users = gitHubClient.getRandomUserByPage(fetchSize);
            if (users == null || users.isEmpty()) {
                log.warn("未获取到GitHub用户数据");
                return;
            }

            int successCount = 0;
            List<GitHubUser> usersToInsert = new ArrayList<>();
            List<GitHubRepo> reposToInsert = new ArrayList<>();

            for (GitHubUserDto user : users) {
                try {
                    // 检查用户是否已存在
                    if (gitHubUserMapper.selectById(user.getId()) != null) {
                        log.info("用户 {} 已存在，跳过", user.getLogin());
                        continue;
                    }

                    // 获取用户详细信息
                    GitHubUserInfoDto userInfo = gitHubClient.getUserInfo(user.getLogin());
                    if (userInfo == null) {
                        log.warn("无法获取用户 {} 的详细信息，跳过", user.getLogin());
                        continue;
                    }

                    // 获取用户仓库
                    List<GitHubRepoDto> userRepos = gitHubClient.getUserRepos(user.getLogin());
                    if (userRepos == null || userRepos.isEmpty()) {
                        log.warn("用户 {} 没有可用仓库，跳过", user.getLogin());
                        continue;
                    }

                    // 转换并准备用户数据
                    GitHubUser gitHubUser = convertToGitHubUser(user, userInfo);

                    // 获取用户事件并计算相关指标
                    List<GitHubEventDto> events = gitHubClient.getUserEvents(user.getLogin());
                    processUserEvents(gitHubUser, events);

                    // 处理用户仓库
                    List<GitHubRepo> userRepoList = processUserRepos(userRepos, user.getLogin());

                    // 计算用户评分
                    calculateUserScores(gitHubUser, userRepoList);

                    // 添加到批量插入列表
                    usersToInsert.add(gitHubUser);
                    reposToInsert.addAll(userRepoList);

                    successCount++;

                    // 每处理10个用户执行一次批量插入
                    if (successCount % 10 == 0) {
                        batchInsertData(usersToInsert, reposToInsert);
                        usersToInsert.clear();
                        reposToInsert.clear();
                    }
                } catch (Exception e) {
                    log.error("处理用户 {} 数据时发生错误: {}", user.getLogin(), e.getMessage(), e);
                    // 单个用户失败不影响整体流程
                }
            }

            // 处理剩余数据
            if (!usersToInsert.isEmpty()) {
                batchInsertData(usersToInsert, reposToInsert);
            }

            log.info("本次定时任务完成，成功添加 {} 个用户", successCount);
        } catch (Exception e) {
            log.error("执行GitHub用户数据定时任务时发生异常: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addGitHubUserByLogin(String login) {
        if (login == null || login.trim().isEmpty()) {
            log.error("GitHub用户名不能为空");
            return;
        }

        try {
            log.info("开始添加GitHub用户: {}", login);

            // 获取用户详细信息
            GitHubUserInfoDto userInfo = gitHubClient.getUserInfo(login);
            if (userInfo == null) {
                log.warn("无法获取用户 {} 的详细信息", login);
                return;
            }

            // 检查用户是否已存在
            if (gitHubUserMapper.selectById(userInfo.getId()) != null) {
                log.info("用户 {} 已存在，跳过处理", login);
                return;
            }

            // 转换用户数据并处理
            GitHubUser gitHubUser = convertToGitHubUser(null, userInfo);

            // 获取用户事件并计算相关指标
            List<GitHubEventDto> events = gitHubClient.getUserEvents(login);
            processUserEvents(gitHubUser, events);

            // 获取用户仓库并处理
            List<GitHubRepo> userRepoList = new ArrayList<>();
            List<GitHubRepoDto> userRepos = gitHubClient.getUserRepos(login);
            if (userRepos != null && !userRepos.isEmpty()) {
                userRepoList = processUserRepos(userRepos, login);
            } else {
                log.warn("用户 {} 没有可用仓库", login);
            }

            // 计算用户评分
            calculateUserScores(gitHubUser, userRepoList);

            // 保存用户数据
            gitHubUserMapper.insert(gitHubUser);

            // 保存仓库数据(只有在有仓库时才执行)
            if (!userRepoList.isEmpty()) {
                for (GitHubRepo repo : userRepoList) {
                    gitHubRepoMapper.insert(repo);
                }
            }

            log.info("成功添加用户 {} 的数据", login);
        } catch (Exception e) {
            log.error("添加用户 {} 数据时发生错误: {}", login, e.getMessage(), e);
            throw new RuntimeException("添加GitHub用户失败: " + e.getMessage(), e);
        }
    }


    /**
     * 批量插入数据到数据库
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchInsertData(List<GitHubUser> users, List<GitHubRepo> repos) {
        try {
            if (!users.isEmpty()) {
                for (GitHubUser user : users) {
                    gitHubUserMapper.insert(user);
                }
            }

            if (!repos.isEmpty()) {
                for (GitHubRepo repo : repos) {
                    gitHubRepoMapper.insert(repo);
                }
            }
            log.info("批量插入 {} 个用户和 {} 个仓库成功", users.size(), repos.size());
        } catch (Exception e) {
            log.error("批量插入数据失败: {}", e.getMessage(), e);
            throw e; // 触发事务回滚
        }
    }

    /**
     * 计算用户所有评分
     */
    private void calculateUserScores(GitHubUser user, List<GitHubRepo> repos) {
        // 计算用户基础评分
        double userScore = calculateUserScore(user);

        // 计算仓库评分
        repos.sort((r1, r2) -> Integer.compare(r2.getStargazersCount(), r1.getStargazersCount()));
        double repoScore;

        // 如果仓库数量大于5，只取前5个计算
        if (repos.size() > 5) {
            repoScore = calculateRepoScore(repos.subList(0, 5));
        } else {
            repoScore = calculateRepoScore(repos);
        }

        // 四舍五入保留两位小数
        BigDecimal userScoreDecimal = BigDecimal.valueOf(userScore).setScale(2, RoundingMode.HALF_UP);
        BigDecimal repoScoreDecimal = BigDecimal.valueOf(repoScore).setScale(2, RoundingMode.HALF_UP);

        user.setUserScore(userScoreDecimal.doubleValue());
        user.setRepoScore(repoScoreDecimal.doubleValue());
        user.setTotalScore(userScoreDecimal.add(repoScoreDecimal).doubleValue());
    }

    /**
     * 处理用户仓库数据
     */
    private List<GitHubRepo> processUserRepos(List<GitHubRepoDto> reposDtos, String userLogin) {
        List<GitHubRepo> repos = new ArrayList<>();
        for (GitHubRepoDto repoDto : reposDtos) {
            if (!StringUtils.equals(userLogin, repoDto.getOwner().getLogin())) {
                continue;
            }
            GitHubRepo repo = new GitHubRepo();
            BeanUtils.copyProperties(repoDto, repo);
            repo.setStargazersCount(repoDto.getStargazers_count());
            repo.setForksCount(repoDto.getForks_count());
            repo.setWatchersCount(repoDto.getWatchers_count());
            repo.setOpenIssuesCount(repoDto.getOpen_issues_count());
            repo.setFullName(repoDto.getFull_name());
            repo.setHtmlUrl(repoDto.getHtml_url());
            StringBuilder sb = new StringBuilder();
            for (String topic : repoDto.getTopics()) {
                sb.append(topic).append(",");
            }
            repo.setTopics(sb.toString());
            repo.setOwnerLogin(repoDto.getOwner().getLogin()); // 确保设置所有者信息
            repos.add(repo);
        }
        return repos;
    }

    /**
     * 处理用户事件数据
     */
    private void processUserEvents(GitHubUser user, List<GitHubEventDto> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        int pushEventCount = calculateEventCount(events, GitHubConstant.GITHUB_EVENT_PUSH);
        int pullRequestEventCount = calculateEventCount(events, GitHubConstant.GITHUB_EVENT_PULL_REQUEST);
        int issueEventCount = calculateEventCount(events, GitHubConstant.GITHUB_EVENT_ISSUE);
        int prReviewEventCount = calculateEventCount(events, GitHubConstant.GITHUB_EVENT_PULL_REQUEST_REVIEW);

        user.setCommits(pushEventCount);
        user.setPrs(pullRequestEventCount);
        user.setIssues(issueEventCount);
        user.setPrReviews(prReviewEventCount);
    }

    /**
     * 将 GitHub 用户 DTO 转换为实体类
     */
    private GitHubUser convertToGitHubUser(GitHubUserDto userDto, GitHubUserInfoDto userInfo) {
        GitHubUser gitHubUser = new GitHubUser();
        if (userDto != null) {
            BeanUtils.copyProperties(userDto, gitHubUser);
        }
        if (userInfo != null) {
            BeanUtils.copyProperties(userInfo, gitHubUser);
        }



        // 初始化计数字段，避免空指针异常
        gitHubUser.setCommits(0);
        gitHubUser.setPrs(0);
        gitHubUser.setIssues(0);
        gitHubUser.setPrReviews(0);

        return gitHubUser;
    }

    /**
     * 计算 GitHub 用户的仓库得分（占比55%）
     * @param repos GitHub 仓库列表
     * @return 仓库得分
     */
    private double calculateRepoScore(List<GitHubRepo> repos) {
        if (repos == null || repos.isEmpty()) {
            return 0;
        }

        double score = 0;
        double MaxStarsScore = 25.0 / repos.toArray().length;
        double MaxForksScoreScore = 15.0 / repos.toArray().length;
        double MaxWatchesScore = 5.0 / repos.toArray().length;
        double MaxOpenIssuesScore = 10.0 / repos.toArray().length;

        for (GitHubRepo repo : repos) {
            double starsScore = calculateFunction(MaxStarsScore, 1000, repo.getStargazersCount());
            double forksScore = calculateFunction(MaxForksScoreScore, 50, repo.getForksCount());
            double watchesScore = calculateFunction(MaxWatchesScore, 20, repo.getWatchersCount());
            double openIssuesScore = calculateFunction(MaxOpenIssuesScore, 100, repo.getOpenIssuesCount());

            score += starsScore + forksScore + watchesScore + openIssuesScore;
        }

        return score;
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
     * 计算 GitHub 用户的基础数据得分（占比45%）
     * @param user GitHub 用户
     * @return 用户基础数据得分
     */
    private double calculateUserScore(GitHubUser user) {
        double followersScore = calculateFunction(8, 500, user.getFollowers());
        double reposScore = calculateFunction(2, 10, user.getPublicRepos());
        double commitAmountScore = calculateFunction(5, 1000, user.getCommits());
        double prAmountScore = calculateFunction(10, 100, user.getPrs());
        double issueAmountScore = calculateFunction(10, 50, user.getIssues());
        double prReviewAmountScore = calculateFunction(10, 50, user.getPrReviews());

        return followersScore + reposScore + commitAmountScore + prAmountScore + issueAmountScore + prReviewAmountScore;
    }

}
