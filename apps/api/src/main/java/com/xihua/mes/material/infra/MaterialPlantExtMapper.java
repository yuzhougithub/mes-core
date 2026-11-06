package com.xihua.mes.material.infra;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.material.domain.MaterialPlantExt;

/** Plant-scoped by the row filter: only the current plant's extension row is visible. */
@Mapper
public interface MaterialPlantExtMapper extends BaseMapper<MaterialPlantExt> {
}
