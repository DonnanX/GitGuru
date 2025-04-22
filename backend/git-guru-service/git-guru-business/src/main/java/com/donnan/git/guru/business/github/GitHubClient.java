package com.donnan.git.guru.business.github;

import com.alibaba.fastjson.JSON;
import com.donnan.git.guru.business.entity.github.dto.GitHubEventDto;
import com.donnan.git.guru.business.entity.github.dto.GitHubRepoDto;
import com.donnan.git.guru.business.entity.github.dto.GitHubUserDto;
import com.donnan.git.guru.business.entity.github.dto.GitHubUserInfoDto;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Donnan
 */
@Component
@Slf4j
@Data
public class GitHubClient {

    // 最大的用户数
    @Value("${github.client.user.max.num}")
    private int userMaxNum;

    // 访问github，加速token
    @Value("${github.client.token}")
    private List<String> authGitHub;

    // 最大线程数
    @Value("${github.client.thread.num}")
    private int threadNum;

    private CloseableHttpClient httpClient;

    private ExecutorService executor;

    private Map<String, Integer> gitHubToken;

    @PostConstruct
    public void init() {
        // 创建连接池管理器
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        // 设置最大连接数
        connectionManager.setMaxTotal(100);
        // 设置每个路由的最大连接数
        connectionManager.setDefaultMaxPerRoute(20);

        // 请求配置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setSocketTimeout(15000)
                .setConnectionRequestTimeout(5000)
                .build();

        // 创建HttpClient
        this.httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        this.executor = Executors.newFixedThreadPool(threadNum);

        // 初始化GitHubToken
        this.gitHubToken = new HashMap<>();
        for (int i = 0; i < authGitHub.size(); i++) {
            gitHubToken.put(authGitHub.get(i), 0);
        }
    }

    /**
     * 分页获取随机GitHub用户
     * @param nums 请求的用户数量
     * @return GitHub用户列表
     */
    public List<GitHubUserDto> getRandomUserByPage(int nums) {
        int pageSize = Math.min(nums, 100);  // 每页最多100条
        int pageCount = (int) Math.ceil((double) nums / pageSize);

        log.info("开始获取GitHub用户，请求数量：{}，分{}页获取", nums, pageCount);
        List<GitHubUserDto> userList = new ArrayList<>();
        Random random = new Random();

        try {
            List<Future<List<GitHubUserDto>>> futures = new ArrayList<>();

            // 提交所有任务
            for (int i = 0; i < pageCount; i++) {
                // 获取随机起点
                int randomStart = random.nextInt(this.userMaxNum);
                String url = "https://api.github.com" + "/users?repos%3E0&since=" + randomStart + "&per_page=" + pageSize;

                final String pageUrl = url;  // 用于lambda表达式的final变量
                Future<List<GitHubUserDto>> future = executor.submit(() -> {
                    try {
                        String response = getGitHubResource(pageUrl);
                        if (response != null) {
                            return JSON.parseArray(response, GitHubUserDto.class);
                        }
                        return null;
                    } catch (Exception e) {
                        log.error("获取GitHub用户失败，URL：{}，错误：{}", pageUrl, e.getMessage());
                        return null;
                    }
                });

                futures.add(future);
            }

            // 收集结果
            for (Future<List<GitHubUserDto>> future : futures) {
                try {
                    List<GitHubUserDto> pageUsers = future.get();
                    if (pageUsers != null && !pageUsers.isEmpty()) {
                        userList.addAll(pageUsers);
                        log.debug("成功获取{}个GitHub用户", pageUsers.size());
                    }
                } catch (Exception e) {
                    log.error("处理GitHub用户结果时出错：{}", e.getMessage());
                }
            }

            log.info("GitHub用户获取完成，共获取到{}个用户", userList.size());
        } catch (Exception e) {
            log.error("获取GitHub用户过程中发生异常：{}", e.getMessage(), e);
            throw new RuntimeException("获取GitHub用户失败", e);
        }

        return userList;
    }

    /**
     * 根据用户名获取GitHub用户信息
     * @param userName 用户名
     * @return 用户信息JSON字符串
     */
    public GitHubUserInfoDto getUserInfo(String userName) {
        try {
            String json = getGitHubResource("https://api.github.com" + "/users/" + userName);

            if (json == null || StringUtils.isBlank(json)) {
                return null;
            }

            return JSON.parseObject(json, GitHubUserInfoDto.class);
        } catch (IOException e) {
            log.error("请求GitHub API异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据用户名获取GitHub用户的所有仓库
     * @param userName 用户名
     * @return 用户的所有仓库列表
     */
    public List<GitHubRepoDto> getUserRepos(String userName) {
        try {
            String json = getGitHubResource("https://api.github.com" + "/users/" + userName + "/repos?type=all&sort=updated");

            if (json == null || StringUtils.isBlank(json)) {
                return null;
            }

            return JSON.parseArray(json, GitHubRepoDto.class);
        } catch (IOException e) {
            log.error("请求GitHub API异常: {}", e.getMessage());
            return null;
        }
    }

    public List<GitHubEventDto> getUserEvents(String userName) {
        try {
            String json = getGitHubResource("https://api.github.com" + "/users/" + userName + "/events");

            if (json == null || StringUtils.isBlank(json)) {
                return null;
            }

            return JSON.parseArray(json, GitHubEventDto.class);
        } catch (IOException e) {
            log.error("请求GitHub API异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 通用的GitHub API资源获取方法
     * @param resourcePath API资源路径
     * @return API响应内容
     */
    private String getGitHubResource(String resourcePath) throws IOException {
        if (gitHubToken == null || gitHubToken.isEmpty()) {
            throw new IllegalStateException("GitHub认证token未配置");
        }

        // 选择使用次数最少的token
        String selectedToken = null;
        int minUsage = Integer.MAX_VALUE;

        for (Map.Entry<String, Integer> entry : gitHubToken.entrySet()) {
            if (entry.getValue() < 3000 && entry.getValue() < minUsage) {
                minUsage = entry.getValue();
                selectedToken = entry.getKey();
            }
        }

        if (selectedToken == null) {
            log.warn("所有GitHub token已达到使用上限");
            return null;
        }

        // 增加已选token的计数
        gitHubToken.put(selectedToken, minUsage + 1);

        HttpGet request = new HttpGet(resourcePath);
        request.setHeader("User-Agent", "Mozilla/5.0");
        request.setHeader("Authorization", "token " + selectedToken);
        request.setHeader("Accept", "application/vnd.github.v3+json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 200) {
                HttpEntity entity = response.getEntity();
                return EntityUtils.toString(entity);
            } else {
                log.warn("获取GitHub资源失败, 状态码: {}, 资源路径: {}", statusCode, resourcePath);
                return null;
            }
        } catch (Exception e) {
            log.error("请求GitHub API异常: {}", e.getMessage());
            throw e;
        }
    }

    @PreDestroy
    public void close() {
        if (httpClient != null) {
            try {
                httpClient.close();
                log.info("HttpClient资源已释放");
            } catch (IOException e) {
                log.error("关闭HttpClient时出错", e);
            }
        }

        if (executor != null) {
            executor.shutdown();
            log.info("ExecutorService资源已释放");
        }
    }
}