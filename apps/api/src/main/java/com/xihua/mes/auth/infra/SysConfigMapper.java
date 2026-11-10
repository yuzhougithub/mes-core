package com.xihua.mes.auth.infra;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysConfigMapper {

    @Select("SELECT value FROM sys_config WHERE config_key = #{key} AND NOT deleted")
    String findValue(String key);
}
