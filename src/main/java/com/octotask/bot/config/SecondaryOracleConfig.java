package com.octotask.bot.config;

import oracle.jdbc.pool.OracleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.SQLException;

@Configuration
public class SecondaryOracleConfig {

    private static final Logger log = LoggerFactory.getLogger(SecondaryOracleConfig.class);

    @Autowired
    private Environment env;

    @Bean(name = "vectorDataSource")
    @ConditionalOnProperty(name = "db2_url")
    public DataSource vectorDataSource() throws SQLException {
        String url = env.getProperty("db2_url");
        String user = env.getProperty("db2_user");
        String pass = env.getProperty("db2_password");
        OracleDataSource ds = new OracleDataSource();
        ds.setDriverType(env.getProperty("db2_driver_class_name", env.getProperty("driver_class_name")));
        ds.setURL(url);
        ds.setUser(user);
        ds.setPassword(pass);
        log.info("Secondary Oracle DataSource configured for url={} user={}", url, user);
        return ds;
    }
}
