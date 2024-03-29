package com.tianji.common.autoconfigure.mybatis;


import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({MybatisPlusInterceptor.class, BaseMapper.class})
public class MybatisConfig {

    /**
     * @see MyBatisAutoFillInterceptor 通过自定义拦截器来实现自动注入creater和updater
     * @deprecated 存在任务更新数据导致updater写入0或null的问题，暂时废弃
     */
    // @Bean
    // @ConditionalOnMissingBean
    public BaseMetaObjectHandler baseMetaObjectHandler() {
        return new BaseMetaObjectHandler();
    }
//    配置MybatisPlus的拦截器链
//    DynamicTableNameInnerInterceptor不是所有服务必须的，如果没有配置动态表名，可以不配置
//    @Autowired(required = false) 注入时声明非必须

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(@Autowired(required = false) DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        if (dynamicTableNameInnerInterceptor != null) {
//            若存在动态表名拦截器，则添加
            interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);
        }

        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(200L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);//分页拦截器插件
        interceptor.addInnerInterceptor(new MyBatisAutoFillInterceptor());//自动填充拦截器插件
        return interceptor;
    }
}
