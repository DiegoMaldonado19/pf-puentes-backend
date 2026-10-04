package com.cunoc.puentes.archivo;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.cunoc.puentes.archivo.ArchivoService.FotoGuardada;
import com.cunoc.puentes.common.NegocioException;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ArchivoServiceTest {

  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
  private static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(US_ASCII);
  private static final byte[] PDF = "%PDF-1.7".getBytes(US_ASCII);

  private final AlmacenamientoArchivos almacenamiento = mock(AlmacenamientoArchivos.class);
  private final ArchivoService servicio = new ArchivoService(almacenamiento);
  private final UUID puente = UUID.randomUUID();
  private final UUID inspeccion = UUID.randomUUID();

  @Test
  void guardaLaFotoYSuMiniaturaEnLaCarpetaDeLaInspeccion() {
    FotoGuardada guardada = servicio.guardarFoto(puente, inspeccion, WEBP, JPEG);

    assertThat(guardada.clave())
        .matches("\\d{4}/\\d{2}/" + puente + "/" + inspeccion + "/[0-9a-f-]{36}\\.webp");
    assertThat(guardada.claveMiniatura())
        .isEqualTo(guardada.clave().replace(".webp", "-miniatura.jpg"));
    verify(almacenamiento).guardar(guardada.clave(), WEBP, "image/webp");
    verify(almacenamiento).guardar(guardada.claveMiniatura(), JPEG, "image/jpeg");
  }

  @Test
  void rechazaUnaFotoQueNoEsImagenSinGuardarNada() {
    assertThatExceptionOfType(NegocioException.class)
        .isThrownBy(() -> servicio.guardarFoto(puente, inspeccion, WEBP, PDF))
        .extracting(NegocioException::getStatusCode)
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    verifyNoInteractions(almacenamiento);
  }

  @Test
  void guardaUnPdf() {
    String clave = servicio.guardarDocumento(puente, inspeccion, PDF);

    assertThat(clave).endsWith(".pdf");
    verify(almacenamiento).guardar(clave, PDF, "application/pdf");
  }

  @Test
  void rechazaDocumentosQueNoSonPdfOPasanDe20Mb() {
    byte[] pdfGrande = Arrays.copyOf(PDF, (int) ArchivoService.MAX_BYTES_PDF + 1);

    for (byte[] documento : new byte[][] {JPEG, "texto".getBytes(US_ASCII), pdfGrande}) {
      assertThatExceptionOfType(NegocioException.class)
          .isThrownBy(() -> servicio.guardarDocumento(puente, inspeccion, documento));
    }
    verifyNoInteractions(almacenamiento);
  }
}
