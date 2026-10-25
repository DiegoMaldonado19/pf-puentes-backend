package com.cunoc.puentes.formulario;

import com.cunoc.puentes.formulario.dto.FormularioVersionDTO;
import com.cunoc.puentes.formulario.dto.PaginaFormularioVersionDTO;
import java.util.List;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;

@Mapper(injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface FormularioVersionMapper {

  FormularioVersionDTO aDTO(FormularioVersion entidad);

  List<FormularioVersionDTO> aListaDTO(List<FormularioVersion> lista);

  default PaginaFormularioVersionDTO aPaginaDTO(Page<FormularioVersion> pagina) {
    return new PaginaFormularioVersionDTO(
        aListaDTO(pagina.getContent()),
        pagina.getNumber(),
        pagina.getSize(),
        pagina.getTotalElements(),
        pagina.getTotalPages());
  }
}
