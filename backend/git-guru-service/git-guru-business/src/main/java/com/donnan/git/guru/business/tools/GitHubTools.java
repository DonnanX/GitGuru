package com.donnan.git.guru.business.tools;

import com.donnan.git.guru.business.entity.github.pojo.ESGitHubRepoContent;
import com.donnan.git.guru.business.entity.github.pojo.GitHubRepo;
import com.donnan.git.guru.business.entity.github.pojo.GitHubUser;
import com.donnan.git.guru.business.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Donnan
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class GitHubTools {

    private final GitHubService gitHubService;

    @Tool(description = "获取某个GitHub用户的个人信息(包括根据GitGuru的算法计算得到的技术score信息)。若返回为空，则是因为该用户不存在，请提醒用户输入正确的用户昵称。")
    public GitHubUser getGitHubUser(@ToolParam(description = "用户昵称") String login) {
        if (login == null || login.isEmpty()) {
            return null;
        }

        log.info("getGitHubUser被调用了, login: {}", login);
        return gitHubService.addGitHubUserByLogin(login);
    }

    @Tool(description = "获取某个用户的某个GitHub仓库的基础信息，若返回为空，则是因为该仓库不存在，请注意：必须是指定用户的指定仓库名称，因为GitHub上可能存在多个同名仓库，若用户没有这样做，请提醒他。")
    public GitHubRepo getGitHubRepo(@ToolParam(description = "用户昵称") String login, @ToolParam(description = "仓库名称") String repoName) {
        if (login == null || login.isEmpty() || repoName == null || repoName.isEmpty()) {
            return null;
        }

        log.info("getGitHubRepo被调用了, login: {}, repoName: {}", login, repoName);
        return gitHubService.getGitHubRepoByLoginAndRepoName(login, repoName);

    }

    @Tool(description = "请输入GitHub用户名和问题，可选指定仓库名称（留空则检索所有仓库）。我将从相关仓库的文档中提取与问题相似度高的内容，帮助你找到答案。从此处获取知识之后就可以根据内容回答用户问题了，不要再进行下一步操作。")
    public List<ESGitHubRepoContent> getGitHubRepoContent(@ToolParam(description = "用户昵称") String login, @ToolParam(description = "仓库名称", required = false) String repoName, @ToolParam(description = "用户提出的问题") String question) {
        if (login == null || login.isEmpty() || question == null || question.isEmpty()) {
            return null;
        }
        log.info("getGitHubRepoContents被调用了, login: {}, repoName: {}, question: {}", login, repoName, question);
        return gitHubService.getGitHubRepoContents(login, repoName, question);
    }
}
