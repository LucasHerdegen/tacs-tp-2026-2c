package com.tacs.backend.dtos.clima;

public record ReglasClimaDto(
    Double maxProbabilidadLluvia,
    Double minTemperatura,
    Double maxTemperatura,
    Double maxViento
) {}