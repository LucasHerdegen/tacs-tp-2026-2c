package com.tacs.backend.exceptions;

/**
 * Caso particular de {@link ProveedorClimaIndisponibleException}: la llamada
 * al proveedor fue exitosa, pero la fecha pedida queda fuera de los dias que
 * el forecast realmente cubrio
 */
public class PronosticoFueraDeCoberturaException extends ProveedorClimaIndisponibleException
{
  public PronosticoFueraDeCoberturaException(String message)
  {
    super(message);
  }
}
