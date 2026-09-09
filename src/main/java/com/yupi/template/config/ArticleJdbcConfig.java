package com.yupi.template.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC review/media writes must use the same Flex transactional connection as article mapper writes. */
@Configuration
public class ArticleJdbcConfig {
    @Bean
    public JdbcTemplate jdbcTemplate(SqlSessionFactory sessions) {
        return new JdbcTemplate(sessions.getConfiguration().getEnvironment().getDataSource());
    }
}
