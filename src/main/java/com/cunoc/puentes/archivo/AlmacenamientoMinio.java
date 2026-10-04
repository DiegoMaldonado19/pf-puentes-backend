package com.cunoc.puentes.archivo;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/** DT-ALM-01: MinIO por su API S3, así el mismo código sirve contra S3 real. */
@Component
class AlmacenamientoMinio implements AlmacenamientoArchivos {

  static final String BUCKET = "puentes";

  private final S3Client s3;

  AlmacenamientoMinio(
      @Value("${sgp.minio.url}") URI url,
      @Value("${sgp.minio.usuario}") String usuario,
      @Value("${sgp.minio.clave}") String clave) {
    s3 = cliente(url, usuario, clave);
    crearBucketSiNoExiste();
  }

  static S3Client cliente(URI url, String usuario, String clave) {
    return S3Client.builder()
        .endpointOverride(url)
        .region(Region.US_EAST_1) // MinIO la ignora, pero el SDK la exige
        .forcePathStyle(true)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(usuario, clave)))
        .build();
  }

  @Override
  public void guardar(String clave, byte[] contenido, String tipoMime) {
    s3.putObject(
        objeto -> objeto.bucket(BUCKET).key(clave).contentType(tipoMime),
        RequestBody.fromBytes(contenido));
  }

  @Override
  public void eliminar(String clave) {
    s3.deleteObject(objeto -> objeto.bucket(BUCKET).key(clave));
  }

  private void crearBucketSiNoExiste() {
    try {
      s3.headBucket(bucket -> bucket.bucket(BUCKET));
    } catch (NoSuchBucketException noExiste) {
      s3.createBucket(bucket -> bucket.bucket(BUCKET));
    }
  }
}
