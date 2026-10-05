package com.cunoc.puentes.archivo;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.cunoc.puentes.PruebaIntegracion;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class AlmacenamientoMinioTest extends PruebaIntegracion {

  private static final String CLAVE = "2026/10/puente/inspeccion/documento.pdf";
  private static final byte[] PDF = "%PDF-1.7".getBytes(US_ASCII);

  private final URI url = URI.create(MINIO.getS3URL());
  private final AlmacenamientoArchivos almacenamiento =
      new AlmacenamientoMinio(url, url, MINIO.getUserName(), MINIO.getPassword());
  private final S3Client s3 =
      AlmacenamientoMinio.cliente(url, MINIO.getUserName(), MINIO.getPassword());

  @Test
  void guardaYEliminaObjetosEnElBucket() {
    almacenamiento.guardar(CLAVE, PDF, "application/pdf");
    assertThat(s3.headObject(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(CLAVE)).contentType())
        .isEqualTo("application/pdf");

    almacenamiento.eliminar(CLAVE);
    assertThatExceptionOfType(NoSuchKeyException.class)
        .isThrownBy(() -> s3.headObject(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(CLAVE)));
  }

  @Test
  void laUrlFirmadaDescargaElObjetoSinCredenciales() throws Exception {
    almacenamiento.guardar(CLAVE, PDF, "application/pdf");

    HttpResponse<byte[]> respuesta;
    try (HttpClient http = HttpClient.newHttpClient()) {
      respuesta =
          http.send(
              HttpRequest.newBuilder(URI.create(almacenamiento.obtenerUrlFirmada(CLAVE))).build(),
              HttpResponse.BodyHandlers.ofByteArray());
    }

    assertThat(respuesta.statusCode()).isEqualTo(200);
    assertThat(respuesta.body()).isEqualTo(PDF);
  }
}
