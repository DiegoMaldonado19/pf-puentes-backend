package com.cunoc.puentes.archivo;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.cunoc.puentes.PruebaIntegracion;
import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class AlmacenamientoMinioTest extends PruebaIntegracion {

  private static final String CLAVE = "2026/10/puente/inspeccion/documento.pdf";

  @Test
  void guardaYEliminaObjetosEnElBucket() {
    URI url = URI.create(MINIO.getS3URL());
    AlmacenamientoArchivos almacenamiento =
        new AlmacenamientoMinio(url, MINIO.getUserName(), MINIO.getPassword());
    S3Client s3 = AlmacenamientoMinio.cliente(url, MINIO.getUserName(), MINIO.getPassword());

    almacenamiento.guardar(CLAVE, "%PDF-1.7".getBytes(US_ASCII), "application/pdf");
    assertThat(s3.headObject(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(CLAVE)).contentType())
        .isEqualTo("application/pdf");

    almacenamiento.eliminar(CLAVE);
    assertThatExceptionOfType(NoSuchKeyException.class)
        .isThrownBy(() -> s3.headObject(o -> o.bucket(AlmacenamientoMinio.BUCKET).key(CLAVE)));
  }
}
