package com.cunoc.puentes.archivo;

import static org.assertj.core.api.Assertions.assertThat;

import com.cunoc.puentes.PruebaIntegracion;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.jdbc.Sql;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** RN-ARC-06 contra PostgreSQL y MinIO reales. */
@DataJpaTest
@Sql("/archivo/inspecciones.sql")
class PurgaDeArchivosTest extends PruebaIntegracion {

  @Autowired ArchivoRepository repositorio;

  @Autowired TestEntityManager em;

  @Test
  void borraLosObjetosDelBorradorAbandonadoYConservaTodosLosRegistros() {
    URI url = URI.create(MINIO.getS3URL());
    AlmacenamientoArchivos almacenamiento =
        new AlmacenamientoMinio(url, url, MINIO.getUserName(), MINIO.getPassword());
    S3Client s3 = AlmacenamientoMinio.cliente(url, MINIO.getUserName(), MINIO.getPassword());
    List<String> claves =
        repositorio.findAll().stream()
            .flatMap(archivo -> Stream.of(archivo.getClave(), archivo.getClaveMiniatura()))
            .filter(Objects::nonNull)
            .toList();
    claves.forEach(clave -> almacenamiento.guardar(clave, new byte[] {1}, "image/webp"));

    new ArchivoService(almacenamiento, repositorio, new ArchivoMapperImpl())
        .purgarBorradoresAbandonados();
    em.flush();
    em.clear();

    assertThat(repositorio.findAll())
        .hasSize(4)
        .allSatisfy(
            archivo ->
                assertThat(archivo.getPurgadoEn() != null)
                    .isEqualTo(ArchivoRepositoryTest.ABANDONADA.equals(archivo.getInspeccionId())));
    assertThat(claves)
        .allSatisfy(
            clave ->
                assertThat(existe(s3, clave)).isEqualTo(!clave.startsWith("prueba/abandonada")));
  }

  private static boolean existe(S3Client s3, String clave) {
    try {
      s3.headObject(objeto -> objeto.bucket(AlmacenamientoMinio.BUCKET).key(clave));
      return true;
    } catch (NoSuchKeyException noExiste) {
      return false;
    }
  }
}
