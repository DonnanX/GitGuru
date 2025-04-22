package com.donnan.git.guru.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.donnan.git.guru.business.entity.github.pojo.GitHubUser;
import org.mapstruct.Mapper;

/**
 * @author Donnan
 */
@Mapper
public interface GitHubUserMapper extends BaseMapper<GitHubUser> {
}