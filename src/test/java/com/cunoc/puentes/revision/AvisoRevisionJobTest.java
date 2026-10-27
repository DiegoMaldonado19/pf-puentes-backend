package com.cunoc.puentes.revision;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AvisoRevisionJobTest {
  @Test
  void usaRelojControlable() {
    var servicio = mock(AvisoRevisionService.class);
    var ahora = Instant.parse("2026-10-27T12:30:00Z");
    new AvisoRevisionJob(servicio, Clock.fixed(ahora, ZoneOffset.UTC)).ejecutar();
    verify(servicio).avisar(ahora);
  }
}
