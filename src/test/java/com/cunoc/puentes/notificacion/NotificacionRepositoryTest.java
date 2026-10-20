package com.cunoc.puentes.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import com.cunoc.puentes.notificacion.dto.NotificacionDTO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
class NotificacionRepositoryTest extends PruebaIntegracion {

  private static final UUID USUARIO_1 = UUID.fromString("0192f5a0-0000-7000-8000-000000000091");
  private static final UUID USUARIO_2 = UUID.fromString("0192f5a0-0000-7000-8000-000000000092");

  @Autowired NotificacionRepository repositorio;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired JdbcTemplate jdbc;

  private NotificacionService servicio;
  private TransactionTemplate tx;

  @BeforeEach
  void inicializar() {
    servicio = new NotificacionService(repositorio, Mappers.getMapper(NotificacionMapper.class));
    tx = new TransactionTemplate(transactionManager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    crearUsuario(USUARIO_1);
    crearUsuario(USUARIO_2);
  }

  @AfterEach
  void limpiarDatos() {
    jdbc.update("DELETE FROM notificacion WHERE usuario_id IN (?, ?)", USUARIO_1, USUARIO_2);
    jdbc.update("DELETE FROM usuario WHERE id IN (?, ?)", USUARIO_1, USUARIO_2);
  }

  private void crearUsuario(UUID id) {
    jdbc.update(
        "INSERT INTO usuario (id, correo, nombre, contrasena_hash, rol, estado, correo_verificado_en) VALUES (?, ?, ?, ?, 'ESTUDIANTE', 'ACTIVO', now())",
        id,
        "notificacion-repository-" + id + "@example.invalid",
        "Prueba repositorio notificaciones",
        "hash-no-utilizado-en-pruebas");
  }

  @Test
  void existeUsuarioRetornaTrueParaExistenteYFalseParaInexistente() {
    assertThat(repositorio.existeUsuario(USUARIO_1)).isTrue();
    assertThat(repositorio.existeUsuario(USUARIO_2)).isTrue();
    assertThat(repositorio.existeUsuario(UUID.randomUUID())).isFalse();
  }

  @Test
  void guardaYRecuperaNotificacionPorIdYUsuarioIdAisladamente() {
    Notificacion notificacion = new Notificacion();
    notificacion.setUsuarioId(USUARIO_1);
    notificacion.setTipo("ALERTA");
    notificacion.setMensaje("Mensaje de prueba");
    notificacion.setEnlace("/puentes/1");

    Notificacion guardada = repositorio.save(notificacion);

    assertThat(repositorio.findByIdAndUsuarioId(guardada.getId(), USUARIO_1))
        .hasValueSatisfying(
            n -> {
              assertThat(n.getId()).isEqualTo(guardada.getId());
              assertThat(n.getTipo()).isEqualTo("ALERTA");
              assertThat(n.getMensaje()).isEqualTo("Mensaje de prueba");
              assertThat(n.getEnlace()).isEqualTo("/puentes/1");
              assertThat(n.getLeidaEn()).isNull();
            });

    assertThat(repositorio.findByIdAndUsuarioId(guardada.getId(), USUARIO_2)).isEmpty();
  }

  @Test
  void cuentaSoloNotificacionesNoLeidasDelUsuario() {
    Notificacion n1 = new Notificacion();
    n1.setUsuarioId(USUARIO_1);
    n1.setTipo("TIPO1");
    n1.setMensaje("Mensaje 1");
    n1.setLeidaEn(null);

    Notificacion n2 = new Notificacion();
    n2.setUsuarioId(USUARIO_1);
    n2.setTipo("TIPO2");
    n2.setMensaje("Mensaje 2");
    n2.setLeidaEn(Instant.now());

    Notificacion n3 = new Notificacion();
    n3.setUsuarioId(USUARIO_2);
    n3.setTipo("TIPO3");
    n3.setMensaje("Mensaje 3");
    n3.setLeidaEn(null);

    repositorio.saveAll(List.of(n1, n2, n3));

    assertThat(repositorio.countByUsuarioIdAndLeidaEnIsNull(USUARIO_1)).isEqualTo(1);
    assertThat(repositorio.countByUsuarioIdAndLeidaEnIsNull(USUARIO_2)).isEqualTo(1);
  }

  @Test
  void listaPaginadaOrdenadaDescendentementePorCreadoEnYId() {
    Notificacion n1 = new Notificacion();
    n1.setUsuarioId(USUARIO_1);
    n1.setTipo("PRIMERA");
    n1.setMensaje("Aviso 1");

    Notificacion n2 = new Notificacion();
    n2.setUsuarioId(USUARIO_1);
    n2.setTipo("SEGUNDA");
    n2.setMensaje("Aviso 2");

    repositorio.save(n1);
    repositorio.save(n2);

    PageRequest pageable =
        PageRequest.of(0, 10, Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));

    Page<Notificacion> pagina = repositorio.findByUsuarioId(USUARIO_1, pageable);

    assertThat(pagina.getTotalElements()).isEqualTo(2);
    assertThat(pagina.getContent()).hasSize(2);
    assertThat(pagina.getContent().get(0).getTipo()).isEqualTo("SEGUNDA");
    assertThat(pagina.getContent().get(1).getTipo()).isEqualTo("PRIMERA");
  }

  @Test
  void lecturaSecuencialConservaTimestampExactoEnDtoYBaseDeDatos() {
    Notificacion notificacion = new Notificacion();
    notificacion.setUsuarioId(USUARIO_1);
    notificacion.setTipo("LECTURA");
    notificacion.setMensaje("Prueba secuencial");
    notificacion.setLeidaEn(null);

    Notificacion guardada = repositorio.saveAndFlush(notificacion);

    NotificacionDTO primeraRespuesta = servicio.marcarComoLeida(guardada.getId(), USUARIO_1);
    assertThat(primeraRespuesta.leidaEn()).isNotNull();

    Notificacion persistida = repositorio.findById(guardada.getId()).orElseThrow();
    assertThat(persistida.getLeidaEn()).isEqualTo(primeraRespuesta.leidaEn());

    NotificacionDTO segundaRespuesta = servicio.marcarComoLeida(guardada.getId(), USUARIO_1);
    assertThat(segundaRespuesta.leidaEn()).isEqualTo(primeraRespuesta.leidaEn());

    Notificacion persistidaSegunda = repositorio.findById(guardada.getId()).orElseThrow();
    assertThat(persistidaSegunda.getLeidaEn()).isEqualTo(primeraRespuesta.leidaEn());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void lecturasConcurrentesEnPostgresRealPreservanLaPrimeraFechaSinSobrescribirla()
      throws Exception {
    UUID notificacionId =
        tx.execute(
            status -> {
              Notificacion n = new Notificacion();
              n.setUsuarioId(USUARIO_1);
              n.setTipo("CONCURRENCIA");
              n.setMensaje("Prueba de carrera en PostgreSQL real");
              n.setLeidaEn(null);
              return repositorio.save(n).getId();
            });

    try {
      int cantidadHilos = 4;
      ExecutorService executor = Executors.newFixedThreadPool(cantidadHilos);
      CountDownLatch startLatch = new CountDownLatch(1);
      List<Future<NotificacionDTO>> futures = new ArrayList<>();

      for (int i = 0; i < cantidadHilos; i++) {
        futures.add(
            executor.submit(
                () -> {
                  startLatch.await();
                  return tx.execute(status -> servicio.marcarComoLeida(notificacionId, USUARIO_1));
                }));
      }

      startLatch.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

      List<Instant> fechasLeidas = new ArrayList<>();
      for (Future<NotificacionDTO> future : futures) {
        NotificacionDTO dto = future.get();
        assertThat(dto.leidaEn()).isNotNull();
        fechasLeidas.add(dto.leidaEn());
      }

      Instant primeraFecha = fechasLeidas.getFirst();
      for (Instant fecha : fechasLeidas) {
        assertThat(fecha).isEqualTo(primeraFecha);
      }

      Notificacion persistida =
          tx.execute(status -> repositorio.findById(notificacionId).orElseThrow());
      assertThat(persistida.getLeidaEn()).isEqualTo(primeraFecha);
    } finally {
      tx.execute(
          status -> {
            repositorio.deleteById(notificacionId);
            return null;
          });
    }
  }
}
