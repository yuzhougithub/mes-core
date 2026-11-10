package com.xihua.mes.org.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xihua.mes.org.domain.Person;

@Mapper
public interface PersonMapper extends BaseMapper<Person> {

    @Select("SELECT * FROM org_person WHERE code = #{code} AND NOT deleted")
    Person findByCode(String code);

    @Select("SELECT enabled FROM org_person WHERE id = #{id} AND NOT deleted")
    Boolean enabledFlag(UUID id);
}
