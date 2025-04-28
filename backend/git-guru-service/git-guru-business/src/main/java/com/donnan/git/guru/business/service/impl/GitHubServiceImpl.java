package com.donnan.git.guru.business.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.donnan.git.guru.business.constant.GitHubConstant;
import com.donnan.git.guru.business.constant.RedisConstant;
import com.donnan.git.guru.business.entity.github.dto.*;
import com.donnan.git.guru.business.entity.github.pojo.ESGitHubRepoContent;
import com.donnan.git.guru.business.entity.github.pojo.GitHubRepo;
import com.donnan.git.guru.business.entity.github.pojo.GitHubUser;
import com.donnan.git.guru.business.github.GitHubClient;
import com.donnan.git.guru.business.mapper.GitHubRepoMapper;
import com.donnan.git.guru.business.mapper.GitHubUserMapper;
import com.donnan.git.guru.business.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

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
    private final ElasticsearchClient elasticsearchClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final DashScopeEmbeddingModel embeddingModel;
    private final MultiQueryExpander multiQueryExpander;
    private final RewriteQueryTransformer rewriteQueryTransformer;

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
    public GitHubUser addGitHubUserByLogin(String login) {
        if (login == null || login.trim().isEmpty()) {
            log.error("GitHub用户名不能为空");
            return null;
        }

        try {
            log.info("开始添加GitHub用户: {}", login);

            // 获取用户详细信息
            GitHubUserInfoDto userInfo = gitHubClient.getUserInfo(login);
            if (userInfo == null) {
                log.warn("无法获取用户 {} 的详细信息", login);
                return null;
            }

            GitHubUser user = gitHubUserMapper.selectById(userInfo.getId());
            // 检查用户是否已存在
            if (user != null) {
                log.info("用户 {} 已存在", login);
                return user;
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
                    GitHubRepo gitHubRepo = gitHubRepoMapper.selectById(repo.getId());
                    if (gitHubRepo == null) gitHubRepoMapper.insert(repo);
                }
            }

            log.info("成功添加用户 {} 的数据", login);
            return gitHubUser;
        } catch (Exception e) {
            log.error("添加用户 {} 数据时发生错误: {}", login, e.getMessage(), e);
            throw new RuntimeException("添加GitHub用户失败: " + e.getMessage(), e);
        }
    }

    @Override
    public GitHubRepo getGitHubRepoByLoginAndRepoName(String login, String repoName) {
        // 1. 参数验证改进
        if (StringUtils.isAnyBlank(login, repoName)) {
            log.error("GitHub用户名和仓库名称不能为空");
            return null;
        }

        // 2. 先查询数据库
        LambdaQueryWrapper<GitHubRepo> queryWrapper = new QueryWrapper<GitHubRepo>().lambda()
                .eq(GitHubRepo::getOwnerLogin, login)
                .eq(GitHubRepo::getName, repoName);
        GitHubRepo gitHubRepo = gitHubRepoMapper.selectOne(queryWrapper);
        if (gitHubRepo != null) {
            log.info("仓库 {} 已存在于数据库中", login + "/" + repoName);
            return gitHubRepo;
        }

        // 3. 数据库不存在才调用API
        GitHubRepoDto repoDto = gitHubClient.getGitHubRepo(login, repoName);
        if (repoDto == null) {
            log.warn("无法从GitHub API获取用户 {} 的仓库 {}", login, repoName);
            return null;
        }

        GitHubRepo repo = convertToGitHubRepo(repoDto);

        gitHubRepoMapper.insert(repo);
        log.info("成功添加仓库 {}", login + "/" + repoName);

        return repo;
    }

    private GitHubRepo convertToGitHubRepo(GitHubRepoDto repoDto) {
        if (repoDto == null) {
            return null;
        }

        GitHubRepo repo = new GitHubRepo();

        // 只复制名称相同的属性
        BeanUtils.copyProperties(repoDto, repo);

        // 手动设置命名不一致的属性
        repo.setStargazersCount(repoDto.getStargazers_count());
        repo.setForksCount(repoDto.getForks_count());
        repo.setWatchersCount(repoDto.getWatchers_count());
        repo.setOpenIssuesCount(repoDto.getOpen_issues_count());
        repo.setFullName(repoDto.getFull_name());
        repo.setHtmlUrl(repoDto.getHtml_url());

        // 安全地设置owner_login
        if (repoDto.getOwner() != null) {
            repo.setOwnerLogin(repoDto.getOwner().getLogin());
        }

        // 处理topics，避免空指针异常
        if (repoDto.getTopics() != null && repoDto.getTopics().length > 0) {
            repo.setTopics(String.join(",", repoDto.getTopics()));
        }

        return repo;
    }


    /**
     * 批量插入数据到数据库，先检查记录是否已存在
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchInsertData(List<GitHubUser> users, List<GitHubRepo> repos) {
        try {
            int userInsertCount = 0;
            int repoInsertCount = 0;

            if (!users.isEmpty()) {
                for (GitHubUser user : users) {
                    gitHubUserMapper.insert(user);
                    userInsertCount++;
                }
            }

            if (!repos.isEmpty()) {
                for (GitHubRepo repo : repos) {
                    // 检查仓库是否已存在
                    GitHubRepo existingRepo = gitHubRepoMapper.selectById(repo.getId());
                    if (existingRepo == null) {
                        gitHubRepoMapper.insert(repo);
                        repoInsertCount++;
                    } else {
                        log.debug("仓库 {} 已存在，跳过插入", repo.getFullName());
                    }
                }
            }

            log.info("批量插入完成: {} 个用户(尝试 {})和 {} 个仓库(尝试 {})",
                    userInsertCount, users.size(),
                    repoInsertCount, repos.size());
        } catch (Exception e) {
            log.error("批量插入数据失败: {}", e.getMessage(), e);
            throw e;
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
            GitHubRepo gitHubRepo = convertToGitHubRepo(repoDto);
            repos.add(gitHubRepo);
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

    @Override
    public List<ESGitHubRepoContent> getGitHubRepoContents(String login, String repoName, String question) {
        // 参数验证
        if (StringUtils.isBlank(login)) {
            log.error("GitHub用户名不能为空");
            return null;
        }

        try {
            // 构建缓存键
            String cacheKey = StringUtils.isBlank(repoName) ?
                    login + "/all" : login + "/" + repoName;

            // 检查是否已加载到ES
            Boolean isLoaded = stringRedisTemplate.opsForSet()
                    .isMember(RedisConstant.GITHUB_REPO_CONTENT_PREFIX, cacheKey);

            if (Boolean.FALSE.equals(isLoaded)) {
                if (StringUtils.isBlank(repoName)) {
                    // 处理所有仓库
                    loadAllReposContent(login);
                } else {
                    // 处理单个仓库
                    loadSingleRepoContent(login, repoName);
                }
            }

            // 根据问题检索内容
            return searchRepoContentsByQuestion(login, repoName, question);
        } catch (Exception e) {
            log.error("获取仓库内容时出错: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 加载用户所有仓库内容到ES
     */
    private void loadAllReposContent(String login) {
        List<GitHubRepoContentDto> repoDocsByUser = gitHubClient.getRepoDocsByUser(login);
        if (repoDocsByUser == null || repoDocsByUser.isEmpty()) {
            log.info("用户 {} 没有可加载的仓库内容", login);
            return;
        }

        BulkRequest.Builder br = new BulkRequest.Builder();
        Set<String> indexedRepos = new HashSet<>();

        for (GitHubRepoContentDto repoDoc : repoDocsByUser) {
            if (repoDoc.getContent() == null || repoDoc.getContent().length == 0) {
                continue;
            }

            processAndIndexContent(br, repoDoc.getContent(), repoDoc.getRepoName(), repoDoc.getOwnerLogin());
            indexedRepos.add(repoDoc.getOwnerLogin() + "/" + repoDoc.getRepoName());
        }

        // 执行批量索引
        executeBulkIndex(br);

        // 更新Redis缓存
        String[] cacheKeys = indexedRepos.toArray(new String[0]);
        if (cacheKeys.length > 0) {
            indexedRepos.add(login + "/all");
            stringRedisTemplate.opsForSet().add(RedisConstant.GITHUB_REPO_CONTENT_PREFIX,
                    indexedRepos.toArray(new String[0]));
        }
    }

    /**
     * 加载单个仓库内容到ES
     */
    private void loadSingleRepoContent(String login, String repoName) {
        String[] repoDoc = gitHubClient.getRepoDocs(login, repoName);
        if (repoDoc == null || repoDoc.length == 0) {
            log.info("仓库 {}/{} 没有可加载的内容", login, repoName);
            return;
        }

        BulkRequest.Builder br = new BulkRequest.Builder();
        processAndIndexContent(br, repoDoc, repoName, login);

        // 执行批量索引
        executeBulkIndex(br);

        // 更新Redis缓存
        stringRedisTemplate.opsForSet().add(RedisConstant.GITHUB_REPO_CONTENT_PREFIX,
                login + "/" + repoName);
    }

    /**
     * 处理文档内容并创建ES索引请求
     */
    private void processAndIndexContent(BulkRequest.Builder br, String[] contents,
                                        String repoName, String ownerLogin) {
        TokenTextSplitter textSplitter = new TokenTextSplitter();
        List<Document> documents = new ArrayList<>();

        for (String content : contents) {
            if (StringUtils.isNotBlank(content)) {
                documents.add(new Document(content));
            }
        }

        // 分割文本
        List<Document> chunks = textSplitter.split(documents);

        for (Document chunk : chunks) {
            if (chunk.getText() == null || chunk.getText().isEmpty()) {
                continue;
            }
            ESGitHubRepoContent esContent = new ESGitHubRepoContent();
            esContent.setContent(chunk.getText());
            esContent.setRepo_name(repoName);
            esContent.setOwner_login(ownerLogin);
            float[] embed = embeddingModel.embed(chunk.getText());
            esContent.setContent_vector(embed);

            br.operations(op -> op.index(i -> i
                    .index("github_repo_content")
                    .document(esContent)
            ));
        }
    }

    /**
     * 执行批量ES索引操作
     */
    private void executeBulkIndex(BulkRequest.Builder br) {
        try {
            BulkResponse result = elasticsearchClient.bulk(br.build());

            if (result.errors()) {
                log.error("ES批量索引出现错误");
                for (BulkResponseItem item: result.items()) {
                    if (item.error() != null) {
                        log.error(item.error().reason());
                    }
                }
            }
        } catch (IOException e) {
            log.error("ES批量索引异常: {}", e.getMessage(), e);
            throw new RuntimeException("索引仓库内容失败", e);
        }
    }

    /**
     * 根据问题搜索仓库内容
     */
    private List<ESGitHubRepoContent> searchRepoContentsByQuestion(String login, String repoName, String question) {
        if (StringUtils.isBlank(question)) {
            log.warn("搜索问题不能为空");
            return Collections.emptyList();
        }

        try {
            // 1. 构建基础过滤查询
            co.elastic.clients.elasticsearch._types.query_dsl.Query filterQuery = buildOwnerRepoFilterQuery(login, repoName);

            // 2. 构建KNN语义搜索
            List<KnnSearch> knnQueries = buildKnnQueries(question);

            // 3. 执行搜索
            SearchResponse<ESGitHubRepoContent> response = elasticsearchClient.search(s -> s
                            .index("github_repo_content")
                            .query(filterQuery)
                            .knn(knnQueries)
                            .size(10),  // 限制返回条数，提高性能
                    ESGitHubRepoContent.class);  // 直接指定返回类型

            // 4. 处理结果
            return extractSearchResults(response);
        } catch (Exception e) {
            log.error("执行仓库内容向量搜索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 构建所有者和仓库名称的过滤查询
     */
    private co.elastic.clients.elasticsearch._types.query_dsl.Query buildOwnerRepoFilterQuery(String login, String repoName) {
        co.elastic.clients.elasticsearch._types.query_dsl.Query loginQuery = TermQuery.of(q -> q
                .field("owner_login")
                .value(login)
        )._toQuery();

        if (StringUtils.isNotBlank(repoName)) {
            co.elastic.clients.elasticsearch._types.query_dsl.Query repoNameQuery = TermQuery.of(q -> q
                    .field("repo_name")
                    .value(repoName)
            )._toQuery();

            return BoolQuery.of(b -> b
                    .must(loginQuery)
                    .must(repoNameQuery)
            )._toQuery();
        } else {
            return loginQuery;
        }
    }

    /**
     * 构建KNN查询
     */
    private List<KnnSearch> buildKnnQueries(String question) {
        List<KnnSearch> knnQueries = new ArrayList<>();
        List<Query> expandedQueries = multiQueryExpander.expand(new Query(question));

        // 限制查询数量，避免过多查询
        int queryLimit = Math.min(expandedQueries.size(), 3);

        for (int i = 0; i < queryLimit; i++) {
            Query query = expandedQueries.get(i);
            String text = rewriteQueryTransformer.transform(query).text();

            // 获取文本嵌入向量
            float[] embedVector = embeddingModel.embed(text);
            List<Float> embedList = convertFloatArrayToList(embedVector);

            KnnSearch knnSearch = KnnSearch.of(k -> k
                    .field("content_vector")
                    .k(3)
                    .queryVector(embedList)
                    .numCandidates(100)  // 减少候选数量，提高性能
            );

            knnQueries.add(knnSearch);
        }

        return knnQueries;
    }

    /**
     * 将float数组转换为List<Float>
     */
    private List<Float> convertFloatArrayToList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float value : array) {
            list.add(value);
        }
        return list;
    }

    /**
     * 从搜索响应中提取结果
     */
    private List<ESGitHubRepoContent> extractSearchResults(SearchResponse<ESGitHubRepoContent> response) {
        if (response == null || response.hits().hits().isEmpty()) {
            return Collections.emptyList();
        }

        List<ESGitHubRepoContent> results = new ArrayList<>();
        for (Hit<ESGitHubRepoContent> hit : response.hits().hits()) {
            ESGitHubRepoContent source = hit.source();
            if (source != null) {
                source.setContent_vector(null); // 清除向量数据
                results.add(hit.source());
            }
        }

        return results;
    }
}
