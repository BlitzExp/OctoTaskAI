package com.octotask.bot.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;


@Component
@ConditionalOnBean(name = "vectorDataSource")
public class VectorJdbcClient {

    private static final Logger log = LoggerFactory.getLogger(VectorJdbcClient.class);

    private final JdbcTemplate jdbc;

    public VectorJdbcClient(@Qualifier("vectorDataSource") DataSource ds) {
        this.jdbc = new JdbcTemplate(ds);
        log.info("VectorJdbcClient initialized with vectorDataSource");
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }
}
