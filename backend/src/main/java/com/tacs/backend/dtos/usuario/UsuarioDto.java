package com.tacs.backend.dtos.usuario;

import com.tacs.backend.domain.usuario.TipoRol;

public record UsuarioDto(Long id, String username, TipoRol rol)
{
}
