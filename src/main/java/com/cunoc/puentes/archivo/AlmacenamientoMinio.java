package com.cunoc.puentes.archivo;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** DT-ALM-01: MinIO por su API S3, así el mismo código sirve contra S3 real. */
@Component
class AlmacenamientoMinio implements AlmacenamientoArchivos {

  // No "puentes": nginx reenvía /<bucket>/ a MinIO y /puentes/... es una ruta de la SPA (ADR 0013)
  static final String BUCKET = "archivos";
  // Igual que el token de acceso (DT-SEC-01): la URL no sobrevive a quien la pidió
  private static final Duration VIGENCIA_URL = Duration.ofMinutes(15);
  private static final Region REGION = Region.US_EAST_1; // MinIO la ignora, pero el SDK la exige

  private final S3Client s3;
  private final S3Presigner firmador;

  AlmacenamientoMinio(
      @Value("${sgp.minio.url}") URI url,
      @Value("${sgp.minio.url-publica}") URI urlPublica,
      @Value("${sgp.minio.usuario}") String usuario,
      @Value("${sgp.minio.clave}") String clave) {
    s3 = cliente(url, usuario, clave);
    // La firma incluye el host: se firma con el origen que usa el navegador, no con minio:9000
    firmador =
        S3Presigner.builder()
            .endpointOverride(urlPublica)
            .region(REGION)
            .credentialsProvider(credenciales(usuario, clave))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    crearBucketSiNoExiste();
  }

  static S3Client cliente(URI url, String usuario, String clave) {
    return S3Client.builder()
        .endpointOverride(url)
        .region(REGION)
        .forcePathStyle(true)
        .credentialsProvider(credenciales(usuario, clave))
        .build();
  }

  @Override
  public void guardar(String clave, byte[] contenido, String tipoMime) {
    s3.putObject(
        objeto -> objeto.bucket(BUCKET).key(clave).contentType(tipoMime),
        RequestBody.fromBytes(contenido));
  }

  @Override
  public String obtenerUrlFirmada(String clave) {
    return firmador
        .presignGetObject(
            firma ->
                firma
                    .signatureDuration(VIGENCIA_URL)
                    .getObjectRequest(objeto -> objeto.bucket(BUCKET).key(clave)))
        .url()
        .toString();
  }

  @Override
  public void eliminar(String clave) {
    s3.deleteObject(objeto -> objeto.bucket(BUCKET).key(clave));
  }

  private static AwsCredentialsProvider credenciales(String usuario, String clave) {
    return StaticCredentialsProvider.create(AwsBasicCredentials.create(usuario, clave));
  }

  private void crearBucketSiNoExiste() {
    try {
      s3.headBucket(bucket -> bucket.bucket(BUCKET));
    } catch (NoSuchBucketException noExiste) {
      s3.createBucket(bucket -> bucket.bucket(BUCKET));
    }
  }
}
