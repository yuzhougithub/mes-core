package com.xihua.mes.config.infra;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.config.domain.CustomFieldSchema;

/** Plant-scoped by {@code PlantLineInnerInterceptor} ({@code plant_id = current}). */
@Mapper
public interface CustomFieldSchemaMapper extends BaseMapper<CustomFieldSchema> {
}
