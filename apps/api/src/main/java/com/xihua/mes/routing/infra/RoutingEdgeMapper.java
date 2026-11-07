package com.xihua.mes.routing.infra;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.routing.domain.RoutingEdge;

/** Child table: reached only through a visible {@code rtg_routing} root. */
@Mapper
public interface RoutingEdgeMapper extends BaseMapper<RoutingEdge> {
}
