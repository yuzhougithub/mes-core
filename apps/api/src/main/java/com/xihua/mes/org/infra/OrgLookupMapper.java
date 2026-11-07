package com.xihua.mes.org.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** Read-only lookups; {@code org_work_center} is plant-owned (row filter = current plant). */
@Mapper
public interface OrgLookupMapper {

    @Select("SELECT EXISTS (SELECT 1 FROM org_work_center w WHERE w.id = #{id} AND NOT w.deleted)")
    boolean workCenterVisible(UUID id);
}
