package com.cunoc.puentes.mantenimiento;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RelojMantenimientoConfig {
  @Bean
  Clock relojMantenimiento() {
    return Clock.systemUTC();
  }
}
