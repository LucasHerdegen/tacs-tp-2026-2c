package com.tacs.backend.dtos.admin;

public record EstadisticasDto(long actividadesCreadas,
                              long actividadesReprogramadas,
                              long actividadesCanceladas,
                              long actividadesConfirmadas,
                              long actividadesFinalizadas)
{
}
