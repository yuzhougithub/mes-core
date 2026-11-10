package com.xihua.mes.common.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;

/**
 * MyBatis-Plus wiring. Order: data scope → plant row filter → optimistic lock.
 * Data scope is ahead of pagination (there is no pagination interceptor; lists use COUNT + LIMIT).
 * Both data scope and the plant filter apply to the same statement.
 */
@Configuration
@MapperScan(basePackages = "com.xihua.mes", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new DataScopeInnerInterceptor());
        interceptor.addInnerInterceptor(new PlantLineInnerInterceptor());
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
