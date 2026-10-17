package com.cunoc.puentes.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Habilita {@code @Async}: el envío de correos no debe retrasar ni delatar la respuesta HTTP. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsincroniaConfig {}
