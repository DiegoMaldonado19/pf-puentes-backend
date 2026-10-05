package com.cunoc.puentes.archivo;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cunoc.puentes.archivo.ArchivoRepository.InspeccionDelArchivo;
import com.cunoc.puentes.archivo.dto.ArchivoDTO;
import com.cunoc.puentes.archivo.dto.SubirDocumentoDTO;
import com.cunoc.puentes.archivo.dto.SubirFotoDTO;
import com.cunoc.puentes.common.NegocioException;
import com.cunoc.puentes.inspeccion.EstadoInspeccion;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class ArchivoServiceTest {

  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
  private static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(US_ASCII);
  private static final byte[] PDF = "%PDF-1.7".getBytes(US_ASCII);
  private static final UUID PUENTE = UUID.randomUUID();
  private static final UUID INSPECCION = UUID.randomUUID();
  private static final UUID AUTOR = UUID.randomUUID();
  private static final Instant CAPTURADA = Instant.parse("2026-10-04T15:30:00Z");

  private final AlmacenamientoArchivos almacenamiento = mock(AlmacenamientoArchivos.class);
  private final ArchivoRepository repositorio = mock(ArchivoRepository.class);
  private final ArchivoService servicio =
      new ArchivoService(almacenamiento, repositorio, new ArchivoMapperImpl());

  @BeforeEach
  void inspeccionPropiaEnBorrador() {
    enEstado(EstadoInspeccion.BORRADOR);
    when(repositorio.save(any())).thenAnswer(guardar -> guardar.getArgument(0));
    when(almacenamiento.obtenerUrlFirmada(anyString()))
        .thenAnswer(firmar -> "https://firmada/" + firmar.getArgument(0));
  }

  @Test
  void guardaLaFotoYSuMiniaturaEnLaCarpetaDelPuenteConSusMetadatos() {
    UUID id = UUID.randomUUID();

    ArchivoDTO dto = servicio.guardarFoto(INSPECCION, AUTOR, foto(id, WEBP, JPEG));

    Archivo archivo = guardado();
    assertThat(archivo.getClave())
        .matches("\\d{4}/\\d{2}/" + PUENTE + "/" + INSPECCION + "/[0-9a-f-]{36}\\.webp");
    assertThat(archivo.getClaveMiniatura())
        .isEqualTo(archivo.getClave().replace(".webp", "-miniatura.jpg"));
    verify(almacenamiento).guardar(archivo.getClave(), WEBP, "image/webp");
    verify(almacenamiento).guardar(archivo.getClaveMiniatura(), JPEG, "image/jpeg");
    assertThat(dto)
        .isEqualTo(
            new ArchivoDTO(
                id,
                TipoArchivo.WEBP,
                "subestructura.estribo_entrada",
                14.83,
                -91.52,
                CAPTURADA,
                WEBP.length,
                "https://firmada/" + archivo.getClave(),
                "https://firmada/" + archivo.getClaveMiniatura()));
  }

  @Test
  void unReintentoDevuelveElArchivoGuardadoSinSubirloOtraVez() {
    UUID id = UUID.randomUUID();
    when(repositorio.findById(id)).thenReturn(Optional.of(archivo(id, INSPECCION)));

    ArchivoDTO reintento = servicio.guardarFoto(INSPECCION, AUTOR, foto(id, WEBP, JPEG));

    assertThat(reintento.id()).isEqualTo(id);
    verify(almacenamiento, never()).guardar(anyString(), any(), anyString());
    verify(repositorio, never()).save(any());
  }

  @Test
  void rechazaUnIdQueYaEsDeOtraInspeccion() {
    UUID id = UUID.randomUUID();
    when(repositorio.findById(id)).thenReturn(Optional.of(archivo(id, UUID.randomUUID())));

    assertThatExceptionOfType(NegocioException.class)
        .isThrownBy(() -> servicio.guardarFoto(INSPECCION, AUTOR, foto(id, WEBP, JPEG)))
        .extracting(NegocioException::getStatusCode)
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void laInspeccionDeOtroAutorNoExisteParaElUsuario() {
    assertThatExceptionOfType(NegocioException.class)
        .isThrownBy(() -> servicio.guardarFoto(INSPECCION, UUID.randomUUID(), foto()))
        .extracting(NegocioException::getStatusCode)
        .isEqualTo(HttpStatus.NOT_FOUND);
    verifyNoInteractions(almacenamiento);
  }

  @Test
  void soloAgregaArchivosAUnBorrador() {
    enEstado(EstadoInspeccion.ENVIADA);

    assertThatExceptionOfType(NegocioException.class)
        .isThrownBy(() -> servicio.guardarFoto(INSPECCION, AUTOR, foto()))
        .extracting(NegocioException::getStatusCode)
        .isEqualTo(HttpStatus.CONFLICT);
    verifyNoInteractions(almacenamiento);
  }

  @Test
  void rechazaUnaFotoQueNoEsImagenSinGuardarNada() {
    assertThatExceptionOfType(NegocioException.class)
        .isThrownBy(
            () -> servicio.guardarFoto(INSPECCION, AUTOR, foto(UUID.randomUUID(), WEBP, PDF)))
        .extracting(NegocioException::getStatusCode)
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    verifyNoInteractions(almacenamiento);
  }

  @Test
  void respetaElLimiteDe60FotosY10Documentos() {
    when(repositorio.countByInspeccionIdAndTipoIn(eq(INSPECCION), any())).thenReturn(60L, 10L);

    assertThatExceptionOfType(NegocioException.class)
        .isThrownBy(() -> servicio.guardarFoto(INSPECCION, AUTOR, foto()))
        .withMessageContaining("60 fotos");
    assertThatExceptionOfType(NegocioException.class)
        .isThrownBy(() -> servicio.guardarDocumento(INSPECCION, AUTOR, documento(PDF)))
        .withMessageContaining("10 documentos");
    verifyNoInteractions(almacenamiento);
  }

  @Test
  void guardaUnPdfSinMiniatura() {
    ArchivoDTO dto = servicio.guardarDocumento(INSPECCION, AUTOR, documento(PDF));

    Archivo archivo = guardado();
    assertThat(archivo.getClave()).endsWith(".pdf");
    assertThat(dto.urlMiniatura()).isNull();
    verify(almacenamiento).guardar(archivo.getClave(), PDF, "application/pdf");
  }

  @Test
  void rechazaDocumentosQueNoSonPdfOPasanDe20Mb() {
    byte[] pdfGrande = Arrays.copyOf(PDF, (int) ArchivoService.MAX_BYTES_PDF + 1);

    for (byte[] contenido : new byte[][] {JPEG, "texto".getBytes(US_ASCII), pdfGrande}) {
      assertThatExceptionOfType(NegocioException.class)
          .isThrownBy(() -> servicio.guardarDocumento(INSPECCION, AUTOR, documento(contenido)));
    }
    verifyNoInteractions(almacenamiento);
  }

  @Test
  void eliminaElRegistroAntesQueSusObjetos() {
    Archivo archivo = archivo(UUID.randomUUID(), INSPECCION);
    when(repositorio.findById(archivo.getId())).thenReturn(Optional.of(archivo));

    servicio.eliminar(archivo.getId(), AUTOR);

    InOrder orden = inOrder(repositorio, almacenamiento);
    orden.verify(repositorio).delete(archivo);
    orden.verify(almacenamiento).eliminar("clave.webp");
    orden.verify(almacenamiento).eliminar("clave-miniatura.webp");
  }

  @Test
  void laEvidenciaDeUnaInspeccionPublicadaNoSeBorra() {
    enEstado(EstadoInspeccion.PUBLICADA);
    Archivo archivo = archivo(UUID.randomUUID(), INSPECCION);
    when(repositorio.findById(archivo.getId())).thenReturn(Optional.of(archivo));

    assertThatExceptionOfType(NegocioException.class)
        .isThrownBy(() -> servicio.eliminar(archivo.getId(), AUTOR))
        .extracting(NegocioException::getStatusCode)
        .isEqualTo(HttpStatus.CONFLICT);
    verify(repositorio, never()).delete(any());
    verifyNoInteractions(almacenamiento);
  }

  @Test
  void laPurgaBorraLosObjetosConservaElRegistroYSigueSiUnoFalla() {
    Archivo falla = archivo(UUID.randomUUID(), INSPECCION);
    falla.setClave("falla.webp");
    Archivo vencido = archivo(UUID.randomUUID(), INSPECCION);
    when(repositorio.buscarParaPurgar(any())).thenReturn(List.of(falla, vencido));
    doThrow(new IllegalStateException("MinIO caído")).when(almacenamiento).eliminar("falla.webp");

    servicio.purgarBorradoresAbandonados();

    assertThat(falla.getPurgadoEn()).isNull();
    assertThat(vencido.getPurgadoEn()).isNotNull();
    verify(almacenamiento).eliminar("clave-miniatura.webp");
    verify(repositorio).save(vencido);
    verify(repositorio, never()).delete(any());
  }

  @Test
  void listarNoFirmaLoQueYaSePurgo() {
    Archivo purgado = archivo(UUID.randomUUID(), INSPECCION);
    purgado.setPurgadoEn(Instant.now());
    when(repositorio.findByInspeccionIdOrderByCreadoEn(INSPECCION)).thenReturn(List.of(purgado));

    assertThat(servicio.listar(INSPECCION))
        .singleElement()
        .satisfies(dto -> assertThat(dto.url()).isNull());
    verify(almacenamiento, never()).obtenerUrlFirmada(anyString());
  }

  private void enEstado(EstadoInspeccion estado) {
    when(repositorio.buscarInspeccion(INSPECCION))
        .thenReturn(
            Optional.of(
                new InspeccionDelArchivo() {
                  @Override
                  public UUID getPuenteId() {
                    return PUENTE;
                  }

                  @Override
                  public UUID getAutorId() {
                    return AUTOR;
                  }

                  @Override
                  public EstadoInspeccion getEstado() {
                    return estado;
                  }
                }));
  }

  private Archivo guardado() {
    ArgumentCaptor<Archivo> captor = ArgumentCaptor.forClass(Archivo.class);
    verify(repositorio).save(captor.capture());
    return captor.getValue();
  }

  private static SubirFotoDTO foto() {
    return foto(UUID.randomUUID(), WEBP, JPEG);
  }

  private static SubirFotoDTO foto(UUID id, byte[] foto, byte[] miniatura) {
    return new SubirFotoDTO(
        id,
        new MockMultipartFile("foto", foto),
        new MockMultipartFile("miniatura", miniatura),
        "subestructura.estribo_entrada",
        14.83,
        -91.52,
        CAPTURADA);
  }

  private static SubirDocumentoDTO documento(byte[] pdf) {
    return new SubirDocumentoDTO(UUID.randomUUID(), new MockMultipartFile("pdf", pdf));
  }

  private static Archivo archivo(UUID id, UUID inspeccionId) {
    Archivo archivo = new Archivo();
    archivo.setId(id);
    archivo.setInspeccionId(inspeccionId);
    archivo.setTipo(TipoArchivo.WEBP);
    archivo.setClave("clave.webp");
    archivo.setClaveMiniatura("clave-miniatura.webp");
    archivo.setTamano(10);
    return archivo;
  }
}
