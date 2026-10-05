package com.cunoc.puentes.common;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DT-BE-14: cada {@code @Scheduled} lleva {@code @SchedulerLock}, así una sola réplica lo ejecuta.
 * La tabla {@code shedlock} sigue el esquema de la librería (ADR 0014).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
class ProcesosProgramadosConfig {

  @Bean
  LockProvider lockProvider(DataSource dataSource) {
    // Hora de la base: las réplicas no dependen de que sus relojes coincidan
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }
}
