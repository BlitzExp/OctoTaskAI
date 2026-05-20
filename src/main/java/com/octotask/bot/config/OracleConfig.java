package com.octotask.bot.config;

import oracle.jdbc.pool.OracleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.SQLException;

@Configuration
public class OracleConfig {

    private static final Logger log = LoggerFactory.getLogger(OracleConfig.class);

    @Autowired
    private Environment env;

    @Bean
    public DataSource dataSource() throws SQLException {
        OracleDataSource ds = new OracleDataSource();
        ds.setDriverType(env.getProperty("driver_class_name"));
        ds.setURL(env.getProperty("db_url"));
        ds.setUser(env.getProperty("db_user"));
        ds.setPassword(env.getProperty("dbpassword"));
        log.info("Oracle DataSource configured for url={} user={}", env.getProperty("db_url"), env.getProperty("db_user"));
        return ds;
    }
}
