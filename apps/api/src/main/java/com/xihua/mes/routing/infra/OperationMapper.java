package com.xihua.mes.routing.infra;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.routing.domain.Operation;

@Mapper
public interface OperationMapper extends BaseMapper<Operation> {

    /** Operation codes are globally unique (A5). */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT EXISTS (SELECT 1 FROM rtg_operation WHERE code = #{code} AND NOT deleted)")
    boolean codeTaken(String code);
}
