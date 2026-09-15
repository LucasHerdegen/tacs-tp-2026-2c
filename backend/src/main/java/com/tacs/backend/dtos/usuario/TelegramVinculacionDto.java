package com.tacs.backend.dtos.usuario;

import java.time.LocalDateTime;

public record TelegramVinculacionDto(String token, String deepLink, LocalDateTime expiraEn)
{
}
