package com.tacs.backend.services;

import com.tacs.backend.domain.actividad.Actividad;
import com.tacs.backend.domain.actividad.TipoActividad;
import com.tacs.backend.domain.actividad.Ubicacion;
import com.tacs.backend.domain.clima.Clima;
import com.tacs.backend.domain.usuario.Usuario;
import com.tacs.backend.dtos.actividades.ActividadDto;
import com.tacs.backend.dtos.clima.PronosticoRespuestaDto;
import com.tacs.backend.mappers.ActividadesMapper;
import com.tacs.backend.repositories.ActividadesRepository;
import com.tacs.backend.repositories.EstadoActividadRepository;
import com.tacs.backend.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActividadesServiceTest {

    @Mock
    private ActividadesRepository actividadesRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ActividadesMapper actividadesMapper;

    @Mock
    private ProveedorClima proveedorClima;

    @Mock
    private com.tacs.backend.mappers.ClimaMapper climaMapper;

    @InjectMocks
    private com.tacs.backend.services.implem.ActividadesServiceImplem actividadesService;

    private Actividad actividadMock;
    private Usuario usuarioMock;

    @BeforeEach
    void setUp() {
        usuarioMock = new Usuario();
        usuarioMock.setId(1L);
        usuarioMock.setUsername("test_piola");

        actividadMock = new Actividad();
        actividadMock.setId(100L);
        actividadMock.setTipo(TipoActividad.AIRE_LIBRE);
        actividadMock.setUbicacion(new Ubicacion("Palermo", -34.58, -58.42));
        actividadMock.setFecha(LocalDateTime.now().plusDays(2));
        actividadMock.setMaximoParticipantes(2);
        actividadMock.setParticipantes(new ArrayList<>());
    }

    @Test
    void buscarActividades_FiltraCorrectamentePorBarrio() {
        Actividad actividadOtra = new Actividad();
        actividadOtra.setUbicacion(new Ubicacion("Recoleta", 0, 0));
        
        when(actividadesRepository.findAll()).thenReturn(List.of(actividadMock, actividadOtra));
        

        ActividadDto dummyDto = new ActividadDto(1L, "Titulo", "Desc", null, null, null, 0, 0, null, null, 0, null, null, null, null);
        when(actividadesMapper.actividadToActividadDto(any(Actividad.class))).thenReturn(dummyDto);

        List<ActividadDto> resultado = actividadesService.buscarActividades(null, "Palermo", null);

        assertEquals(1, resultado.size());
        verify(actividadesRepository, times(1)).findAll();
    }

    @Test
    void unirseActividad_AgregaUsuarioCorrectamente() {
        when(usuarioRepository.existsById(1L)).thenReturn(true);
        when(actividadesRepository.findById(100L)).thenReturn(Optional.of(actividadMock));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioMock));

        actividadesService.unirseActividad(100L, 1L);

        assertTrue(actividadMock.getParticipantes().contains(usuarioMock));
        verify(actividadesRepository, times(1)).save(actividadMock);
    }

    @Test
    void unirseActividad_LanzaExcepcionCuandoEstaLlena() {
        when(usuarioRepository.existsById(1L)).thenReturn(true);
        
        Usuario otroUsuario1 = new Usuario(); otroUsuario1.setId(2L);
        Usuario otroUsuario2 = new Usuario(); otroUsuario2.setId(3L);
        actividadMock.getParticipantes().add(otroUsuario1);
        actividadMock.getParticipantes().add(otroUsuario2); 

        when(actividadesRepository.findById(100L)).thenReturn(Optional.of(actividadMock));

        Exception exception = assertThrows(com.tacs.backend.exceptions.CapacidadMaximaException.class, () -> {
            actividadesService.unirseActividad(100L, 1L);
        });
        
        assertEquals("La actividad ya esta al maximo de participantes permitidos", exception.getMessage());
        verify(actividadesRepository, never()).save(any());
    }

    @Test
    void bajarseActividad_RemueveUsuarioCorrectamente() {
        when(usuarioRepository.existsById(1L)).thenReturn(true);
        actividadMock.getParticipantes().add(usuarioMock);
        
        when(actividadesRepository.findById(100L)).thenReturn(Optional.of(actividadMock));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuarioMock));

        actividadesService.bajarseActividad(100L, 1L);

        assertFalse(actividadMock.getParticipantes().contains(usuarioMock));
        verify(actividadesRepository, times(1)).save(actividadMock);
    }

    @Test
    void obtenerClimaActividad_RetornaClimaSiEsParticipante() {
        when(usuarioRepository.existsById(1L)).thenReturn(true);
        actividadMock.getParticipantes().add(usuarioMock); 
        
        Clima climaActualMock = new Clima(10.0, 25.0, 15.0);
        Clima pronosticoMock = new Clima(0.0, 28.0, 10.0);

        when(actividadesRepository.findById(100L)).thenReturn(Optional.of(actividadMock));
        when(proveedorClima.obtenerClima(actividadMock.getUbicacion())).thenReturn(climaActualMock);
        when(proveedorClima.obtenerPronostico(actividadMock.getUbicacion(), actividadMock.getFecha())).thenReturn(pronosticoMock);

        com.tacs.backend.dtos.clima.ClimaDto climaActualDtoMock = new com.tacs.backend.dtos.clima.ClimaDto(10.0, 25.0, 15.0);
        com.tacs.backend.dtos.clima.ClimaDto pronosticoDtoMock = new com.tacs.backend.dtos.clima.ClimaDto(0.0, 28.0, 10.0);
        when(climaMapper.climaToClimaDto(climaActualMock)).thenReturn(climaActualDtoMock);
        when(climaMapper.climaToClimaDto(pronosticoMock)).thenReturn(pronosticoDtoMock);

        PronosticoRespuestaDto respuesta = actividadesService.obtenerClimaActividad(100L, 1L);

        assertNotNull(respuesta);
        assertEquals(25.0, respuesta.climaActual().temperatura());
        assertEquals(28.0, respuesta.pronosticoFuturo().temperatura());
    }

    @Test
    void obtenerClimaActividad_LanzaExcepcionSiNoEsParticipante() {
        when(usuarioRepository.existsById(1L)).thenReturn(true);
        when(actividadesRepository.findById(100L)).thenReturn(Optional.of(actividadMock));

        Exception exception = assertThrows(com.tacs.backend.exceptions.NoParticipanteException.class, () -> {
            actividadesService.obtenerClimaActividad(100L, 1L);
        });

        assertEquals("Debes ser participante de la actividad para ver su clima", exception.getMessage());
        verify(proveedorClima, never()).obtenerPronostico(any(), any());
    }
}