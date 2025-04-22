package com.donnan.git.guru.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.donnan.git.guru.business.entity.github.pojo.GitHubRepo;
import org.mapstruct.Mapper;

/**
 * @author Donnan
 */
@Mapper
public interface GitHubRepoMapper extends BaseMapper<GitHubRepo> {
}
