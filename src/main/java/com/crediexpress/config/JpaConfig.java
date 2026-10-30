package com.crediexpress.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.crediexpress.dto.repository")
@EntityScan(basePackages = "com.crediexpress.dto.entity")
public class JpaConfig {
}
