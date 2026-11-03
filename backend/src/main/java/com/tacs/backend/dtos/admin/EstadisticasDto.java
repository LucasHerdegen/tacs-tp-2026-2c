package com.tacs.backend.dtos.admin;

import java.util.List;

public record EstadisticasDto(
    long actividadesCreadas,
    long actividadesReprogramadas,
    long actividadesCanceladas,
    long actividadesConfirmadas,
    long actividadesFinalizadas,
    List<EstadisticasProveedorClimaDto> proveedoresClima)
{
  public EstadisticasDto(
      long actividadesCreadas,
      long actividadesReprogramadas,
      long actividadesCanceladas,
      long actividadesConfirmadas,
      long actividadesFinalizadas)
  {
    this(
        actividadesCreadas,
        actividadesReprogramadas,
        actividadesCanceladas,
        actividadesConfirmadas,
        actividadesFinalizadas,
        List.of());
  }
}
