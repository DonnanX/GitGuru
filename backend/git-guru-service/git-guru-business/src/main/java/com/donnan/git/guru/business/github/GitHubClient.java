package com.donnan.git.guru.business.github;

import com.alibaba.fastjson.JSON;
import com.donnan.git.guru.business.entity.github.dto.*;
import com.donnan.git.guru.business.entity.github.pojo.GitHubRepo;
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
import java.util.stream.Collectors;

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

    /**
     * 根据用户名获取GitHub用户的所有事件
     * @param userName 用户名
     * @return 用户的所有事件列表
     */
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
     * 根据用户名和仓库名称获取GitHub仓库信息
     * @param login 用户名
     * @param repoName 仓库名称
     * @return 仓库信息JSON字符串
     */
    public GitHubRepoDto getGitHubRepo(String login, String repoName) {
        try {
            String json = getGitHubResource("https://api.github.com" + "/repos/" + login + "/" + repoName);

            if (json == null || StringUtils.isBlank(json)) {
                return null;
            }

            return JSON.parseObject(json, GitHubRepoDto.class);
        } catch (IOException e) {
            log.error("请求GitHub API异常: {}", e.getMessage());
            return null;
        }
    }

    public List<GitHubRepoContentDto> getRepoDocsByUser(String login) {
        List<GitHubRepoDto> repos = getUserRepos(login);
        if (repos == null || repos.isEmpty()) {
            log.warn("用户 {} 没有仓库", login);
            return null;
        }
        repos = repos.stream().sorted((o1, o2) -> o1.getStargazers_count() - o2.getStargazers_count() > 0 ? -1 : 1).limit(5).toList();
        List<GitHubRepoContentDto> repoContents = new ArrayList<>();
        for (GitHubRepoDto repo : repos) {
            String repoName = repo.getName();
            String[] docs = getRepoDocs(login, repoName);
            if (docs != null && docs.length > 0) {
                GitHubRepoContentDto contentDto = new GitHubRepoContentDto();
                contentDto.setOwnerLogin(login);
                contentDto.setRepoName(repoName);
                contentDto.setContent(docs);
                repoContents.add(contentDto);
            } else {
                log.warn("用户 {} 的仓库 {} 没有文档信息", login, repoName);
            }
        }

        if (repoContents.isEmpty()) {
            log.warn("用户 {} 的仓库没有文档信息", login);
            return null;
        }

        return repoContents;
    }

    // 对外暴露的简单方法
    public String[] getRepoDocs(String login, String repoName) {
        return getRepoDocs(login, repoName, null, 0);
    }

    /**
     * 获取仓库中的文档内容
     * @param login 用户名
     * @param repoName 仓库名称
     * @param path 路径，初始调用时传入null或空字符串
     * @param depth 当前递归深度
     * @return 文档内容数组
     */
    public String[] getRepoDocs(String login, String repoName, String path, int depth) {
        // 参数校验
        if (StringUtils.isAnyBlank(login, repoName)) {
            log.error("获取仓库文档失败：用户名和仓库名不能为空");
            return new String[0];
        }

        // 递归深度控制
        final int MAX_DEPTH = 3;
        if (depth > MAX_DEPTH) {
            log.warn("达到最大递归深度({})，停止获取更深层级文档", MAX_DEPTH);
            return new String[0];
        }

        try {
            // 构建请求URL
            String url = "https://api.github.com/repos/" + login + "/" + repoName + "/contents";
            if (StringUtils.isNotBlank(path)) {
                url += "/" + path;
            }

            String json = getGitHubResource(url);
            if (StringUtils.isBlank(json)) {
                return new String[0];
            }

            List<GitHubFileDto> repoList = JSON.parseArray(json, GitHubFileDto.class);
            if (repoList == null || repoList.isEmpty()) {
                return new String[0];
            }

            List<String> docs = new ArrayList<>();
            List<Future<String[]>> dirFutures = new ArrayList<>();

            // 并行处理目录
            for (GitHubFileDto repo : repoList) {
                if ("file".equals(repo.getType()) && isDocFile(repo.getName())) {
                    // 处理文档文件
                    String fileContent = getFileContent(repo.getUrl());
                    if (fileContent != null) {
                        docs.add(fileContent);
                    }
                } else if ("dir".equals(repo.getType()) && (path == null || repo.getPath().contains("docs"))) {
                    // 并行处理子目录
                    final String subPath = repo.getPath();
                    Future<String[]> future = executor.submit(() ->
                            getRepoDocs(login, repoName, subPath, depth + 1)
                    );
                    dirFutures.add(future);
                }
            }

            // 收集子目录的结果
            for (Future<String[]> future : dirFutures) {
                try {
                    String[] subDocs = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                    if (subDocs != null && subDocs.length > 0) {
                        Collections.addAll(docs, subDocs);
                    }
                } catch (Exception e) {
                    log.error("获取子目录文档失败: {}", e.getMessage());
                }
            }

            return docs.toArray(new String[0]);
        } catch (IOException e) {
            log.error("请求GitHub API获取仓库文档异常: {}, 仓库: {}/{}", e.getMessage(), login, repoName);
            return new String[0];
        }
    }

    /**
     * 判断是否为文档文件
     */
    private boolean isDocFile(String fileName) {
        return fileName.endsWith(".md") || fileName.endsWith(".txt");
    }

    /**
     * 获取文件内容
     */
    private String getFileContent(String fileUrl) {
        try {
            String resource = getGitHubResource(fileUrl);
            if (resource != null) {
                GitHubFileDto content = JSON.parseObject(resource, GitHubFileDto.class);
                if (content != null && content.getContent() != null) {
                    return new String(Base64.getDecoder().decode(content.getContent()));
                }
            }
        } catch (Exception e) {
            log.warn("获取文件内容失败: {}", e.getMessage());
        }
        return null;
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