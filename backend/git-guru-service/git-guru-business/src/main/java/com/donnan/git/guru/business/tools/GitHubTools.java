package com.donnan.git.guru.business.tools;

import com.donnan.git.guru.business.entity.github.pojo.GitHubRepo;
import com.donnan.git.guru.business.entity.github.pojo.GitHubUser;
import com.donnan.git.guru.business.service.GitHubService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * @author Donnan
 */
@RequiredArgsConstructor
@Component
public class GitHubTools {

    private final GitHubService gitHubService;

    @Tool(description = "获取某个GitHub用户的个人信息(包括根据GitGuru的算法计算得到的技术score信息)。若返回为空，则是因为该用户不存在，请提醒用户输入正确的用户昵称。")
    public GitHubUser getGitHubUser(@ToolParam(description = "用户昵称") String login) {
        if (login == null || login.isEmpty()) {
            return null;
        }
        GitHubUser user = gitHubService.addGitHubUserByLogin(login);
        return user;
    }

    @Tool(description = "获取某个用户的某个GitHub仓库的基础信息，若返回为空，则是因为该仓库不存在，请注意：必须是指定用户的指定仓库名称，因为GitHub上可能存在多个同名仓库，若用户没有这样做，请提醒他。")
    public GitHubRepo getGitHubRepo(@ToolParam(description = "用户昵称") String login, @ToolParam(description = "仓库名称") String repoName) {
        if (login == null || login.isEmpty() || repoName == null || repoName.isEmpty()) {
            return null;
        }
        GitHubRepo gitHubRepo = gitHubService.getGitHubRepoByLoginAndRepoName(login, repoName);
        return gitHubRepo;

    }

    @Tool(description = "获取某个用户的某个仓库的文档信息，可以根据用户的问题来返回相似度高的资料，帮助你回答用户的问题。")
    public String[] getGitHubRepoContent(@ToolParam(description = "用户昵称") String login, @ToolParam(description = "仓库名称") String repoName, @ToolParam(description = "用户提出的问题") String question) {
        if (login == null || login.isEmpty() || repoName == null || repoName.isEmpty() || question == null || question.isEmpty()) {
            return null;
        }
        String[] contents = gitHubService.getGitHubRepoContents(login, repoName, question);
        return contents;
    }
}
