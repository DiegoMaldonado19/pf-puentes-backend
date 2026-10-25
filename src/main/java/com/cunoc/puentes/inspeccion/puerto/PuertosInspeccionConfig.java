package com.cunoc.puentes.inspeccion.puerto;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Los provisionales solo existen mientras P4/P8 no registren el puerto real.
 * {@code @ConditionalOnMissingBean} sobre {@code @Component} del mismo tipo no se registra (Spring
 * lo ve como ya presente); el {@code @Bean} del config sí es el patrón correcto.
 */
@Configuration(proxyBeanMethods = false)
class PuertosInspeccionConfig {

  @Bean
  @ConditionalOnMissingBean(ConsultaFormulario.class)
  ConsultaFormularioProvisional consultaFormulario(
      EntityManager em,
      @Value("${sgp.formulario.rutas-permanentes:}") List<String> permanentes,
      @Value("${sgp.formulario.rutas-dano:}") List<String> danos) {
    return new ConsultaFormularioProvisional(em, permanentes, danos);
  }

  @Bean
  @ConditionalOnMissingBean(CalculoIndice.class)
  CalculoIndiceProvisional calculoIndice() {
    return new CalculoIndiceProvisional();
  }
}
