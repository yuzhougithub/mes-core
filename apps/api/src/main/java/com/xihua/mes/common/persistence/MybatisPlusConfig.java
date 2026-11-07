package com.xihua.mes.common.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;

/**
 * MyBatis-Plus wiring. Order: plant row filter → optimistic lock.
 * List paging is done with explicit COUNT + LIMIT/OFFSET (both pass through the plant filter).
 */
@Configuration
@MapperScan(basePackages = "com.xihua.mes", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PlantLineInnerInterceptor());
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
