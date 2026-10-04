package com.cunoc.puentes.indice;

import com.cunoc.puentes.common.EntidadBase;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VersionPesos extends EntidadBase {

  private UUID formularioVersionId;

  private String acta;

  private Instant vigenteDesde = Instant.now();
}
