package com.cunoc.puentes.curso;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Aplica los roles desde ahora; la autenticación JWT sigue siendo responsabilidad de P2. */
@Configuration
@EnableMethodSecurity
class SeguridadCursoConfig {}
