package com.tacs.backend.config;

import com.tacs.backend.domain.usuario.TipoMedioContacto;
import com.tacs.backend.persistence.entities.ActividadEntity;
import com.tacs.backend.persistence.entities.AlternativaEntity;
import com.tacs.backend.persistence.entities.CambioFechaEntity;
import com.tacs.backend.persistence.entities.MedioContactoEntity;
import com.tacs.backend.persistence.entities.RangoReprogramacionEntity;
import com.tacs.backend.persistence.entities.ReglasClimaEntity;
import com.tacs.backend.persistence.entities.UbicacionEntity;
import com.tacs.backend.persistence.entities.UsuarioEntity;
import com.tacs.backend.persistence.entities.VotacionEntity;
import com.tacs.backend.persistence.entities.VotoEntity;
import com.tacs.backend.persistence.repositories.ActividadesMongoRepository;
import com.tacs.backend.persistence.repositories.UsuarioMongoRepository;
import com.tacs.backend.persistence.repositories.VotacionesMongoRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.CollectionCallback;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration
public class TestMongoFallbackConfig
{
  private static final ThreadLocal<Boolean> MONGO_DISPONIBLE_INIT = new ThreadLocal<>();

  public static class InMemoryMongoStore
  {
    private final Map<String, UsuarioEntity> usuarios = new ConcurrentHashMap<>();
    private final Map<String, ActividadEntity> actividades = new ConcurrentHashMap<>();
    private final Map<String, VotacionEntity> votaciones = new ConcurrentHashMap<>();
    private final Object lockActividades = new Object();
    private final Object lockVotaciones = new Object();
    private volatile Boolean mongoRealDisponible;

    public boolean verificarDisponibilidad(MongoDatabaseFactory factory)
    {
      Boolean cacheado = mongoRealDisponible;
      if (cacheado != null)
        return cacheado;

      synchronized (this)
      {
        if (mongoRealDisponible != null)
          return mongoRealDisponible;

        try
        {
          Document indexSpec = new Document("key", new Document("probe", 1)).append("name", "probe_1");
          Document cmd = new Document("createIndexes", "_test_auth_probe")
              .append("indexes", List.of(indexSpec));
          factory.getMongoDatabase().runCommand(cmd);
          mongoRealDisponible = true;
        }
        catch (RuntimeException e)
        {
          mongoRealDisponible = false;
        }
        return mongoRealDisponible;
      }
    }

    public synchronized UsuarioEntity guardarUsuario(UsuarioEntity entrada)
    {
      UsuarioEntity copia = copiarUsuario(entrada);
      if (copia.getId() == null)
        copia.setId(new ObjectId().toHexString());
      usuarios.put(copia.getId(), copia);
      return copiarUsuario(copia);
    }

    public Optional<UsuarioEntity> buscarUsuarioPorId(String id)
    {
      if (id == null)
        return Optional.empty();
      return Optional.ofNullable(copiarUsuario(usuarios.get(id)));
    }

    public boolean existeUsuarioPorId(String id)
    {
      return id != null && usuarios.containsKey(id);
    }

    public Optional<UsuarioEntity> buscarUsuarioPorMedioContacto(String valor, TipoMedioContacto tipo)
    {
      for (UsuarioEntity u : usuarios.values())
      {
        MedioContactoEntity mc = u.getMedioContacto();
        if (mc != null && Objects.equals(mc.getValor(), valor) && mc.getTipo() == tipo)
          return Optional.of(copiarUsuario(u));
      }
      return Optional.empty();
    }

    public void borrarTodosLosUsuarios()
    {
      usuarios.clear();
    }

    public ActividadEntity guardarActividad(ActividadEntity entrada)
    {
      synchronized (lockActividades)
      {
        ActividadEntity copia = copiarActividad(entrada);
        if (copia.getId() == null)
          copia.setId(new ObjectId().toHexString());

        if (copia.getVersion() == null)
        {
          copia.setVersion(0L);
          actividades.put(copia.getId(), copia);
          return copiarActividad(copia);
        }

        ActividadEntity actual = actividades.get(copia.getId());
        if (actual == null || !Objects.equals(actual.getVersion(), copia.getVersion()))
          throw new OptimisticLockingFailureException("Conflicto optimista en ActividadEntity id=" + copia.getId());

        copia.setVersion(copia.getVersion() + 1L);
        actividades.put(copia.getId(), copia);
        return copiarActividad(copia);
      }
    }

    public Optional<ActividadEntity> buscarActividadPorId(String id)
    {
      synchronized (lockActividades)
      {
        if (id == null)
          return Optional.empty();
        return Optional.ofNullable(copiarActividad(actividades.get(id)));
      }
    }

    public void borrarTodasLasActividades()
    {
      synchronized (lockActividades)
      {
        actividades.clear();
      }
    }

    public VotacionEntity guardarVotacion(VotacionEntity entrada)
    {
      synchronized (lockVotaciones)
      {
        VotacionEntity copia = copiarVotacion(entrada);
        if (copia.getId() == null)
          copia.setId(new ObjectId().toHexString());

        if (copia.getVersion() == null)
        {
          copia.setVersion(0L);
          votaciones.put(copia.getId(), copia);
          return copiarVotacion(copia);
        }

        VotacionEntity actual = votaciones.get(copia.getId());
        if (actual == null || !Objects.equals(actual.getVersion(), copia.getVersion()))
          throw new OptimisticLockingFailureException("Conflicto optimista en VotacionEntity id=" + copia.getId());

        copia.setVersion(copia.getVersion() + 1L);
        votaciones.put(copia.getId(), copia);
        return copiarVotacion(copia);
      }
    }

    public Optional<VotacionEntity> buscarVotacionPorId(String id)
    {
      synchronized (lockVotaciones)
      {
        if (id == null)
          return Optional.empty();
        return Optional.ofNullable(copiarVotacion(votaciones.get(id)));
      }
    }

    public Optional<VotacionEntity> buscarVotacionAbiertaPorActividad(ObjectId actividadId)
    {
      synchronized (lockVotaciones)
      {
        if (actividadId == null)
          return Optional.empty();
        String hex = actividadId.toHexString();
        for (VotacionEntity v : votaciones.values())
        {
          if (v.isAbierta() && v.getActividad() != null && Objects.equals(v.getActividad().getId(), hex))
            return Optional.of(copiarVotacion(v));
        }
        return Optional.empty();
      }
    }

    public void borrarTodasLasVotaciones()
    {
      synchronized (lockVotaciones)
      {
        votaciones.clear();
      }
    }

    public VotacionEntity cerrarVotacionAtomicamente(Query query, UpdateDefinition update)
    {
      synchronized (lockVotaciones)
      {
        Document qDoc = query.getQueryObject();
        Object rawId = qDoc.containsKey("_id") ? qDoc.get("_id") : qDoc.get("id");
        String id = rawId instanceof ObjectId oid ? oid.toHexString() : (rawId != null ? rawId.toString() : null);
        if (id == null)
          return null;

        VotacionEntity actual = votaciones.get(id);
        if (actual == null || !actual.isAbierta())
          return null;

        Document uDoc = update.getUpdateObject();
        Document setDoc = uDoc.get("$set", Document.class);
        if (setDoc != null)
        {
          if (setDoc.containsKey("abierta"))
            actual.setAbierta(Boolean.TRUE.equals(setDoc.getBoolean("abierta")));
          if (setDoc.get("fechaCierre") instanceof LocalDateTime fc)
            actual.setFechaCierre(fc);
          if (setDoc.get("alternativaGanadora") instanceof AlternativaEntity ag)
            actual.setAlternativaGanadora(copiarAlternativa(ag));
        }
        long versionActual = actual.getVersion() == null ? 0L : actual.getVersion();
        actual.setVersion(versionActual + 1L);
        return copiarVotacion(actual);
      }
    }

    private UsuarioEntity copiarUsuario(UsuarioEntity u)
    {
      if (u == null)
        return null;
      UsuarioEntity c = new UsuarioEntity();
      c.setId(u.getId());
      c.setUsername(u.getUsername());
      c.setPassword(u.getPassword());
      c.setRol(u.getRol());
      c.setProveedorClima(u.getProveedorClima());
      if (u.getMedioContacto() != null)
        c.setMedioContacto(new MedioContactoEntity(u.getMedioContacto().getTipo(), u.getMedioContacto().getValor()));
      return c;
    }

    private ActividadEntity copiarActividad(ActividadEntity a)
    {
      if (a == null)
        return null;
      ActividadEntity c = new ActividadEntity();
      c.setId(a.getId());
      c.setVersion(a.getVersion());
      c.setTitulo(a.getTitulo());
      c.setDescripcion(a.getDescripcion());
      c.setTipo(a.getTipo());
      if (a.getUbicacion() != null)
      {
        c.setUbicacion(new UbicacionEntity(
            a.getUbicacion().getCiudad(),
            a.getUbicacion().getLatitud(),
            a.getUbicacion().getLongitud()));
      }
      c.setFechaCreacion(a.getFechaCreacion());
      c.setFechaRealizacion(a.getFechaRealizacion());
      c.setDuracionEstimada(a.getDuracionEstimada());
      c.setMinimoParticipantes(a.getMinimoParticipantes());
      c.setMaximoParticipantes(a.getMaximoParticipantes());
      c.setRecordatorioEnviado(a.isRecordatorioEnviado());
      c.setOrganizador(copiarUsuario(a.getOrganizador()));

      List<UsuarioEntity> parts = new ArrayList<>();
      if (a.getParticipantes() != null)
      {
        for (UsuarioEntity p : a.getParticipantes())
          parts.add(copiarUsuario(p));
      }
      c.setParticipantes(parts);
      c.setHorasAnticipacion(a.getHorasAnticipacion());

      if (a.getRangoReprogramacion() != null)
      {
        c.setRangoReprogramacion(new RangoReprogramacionEntity(
            a.getRangoReprogramacion().getDias(),
            a.getRangoReprogramacion().getHoraInicio(),
            a.getRangoReprogramacion().getHoraFinal()));
      }

      List<CambioFechaEntity> cambios = new ArrayList<>();
      if (a.getCambiosFecha() != null)
      {
        for (CambioFechaEntity cf : a.getCambiosFecha())
          cambios.add(new CambioFechaEntity(cf.getFecha(), cf.getFechaAntigua(), cf.getFechaNueva()));
      }
      c.setCambiosFecha(cambios);
      c.setEstado(a.getEstado());

      if (a.getReglasClima() != null)
      {
        c.setReglasClima(new ReglasClimaEntity(
            a.getReglasClima().getMaxProbabilidadLluvia(),
            a.getReglasClima().getMinTemperatura(),
            a.getReglasClima().getMaxTemperatura(),
            a.getReglasClima().getMaxViento()));
      }
      return c;
    }

    private VotacionEntity copiarVotacion(VotacionEntity v)
    {
      if (v == null)
        return null;
      VotacionEntity c = new VotacionEntity();
      c.setId(v.getId());
      c.setVersion(v.getVersion());
      c.setFechaApertura(v.getFechaApertura());
      c.setFechaCierre(v.getFechaCierre());
      c.setAbierta(v.isAbierta());
      c.setFechaLimite(v.getFechaLimite());
      if (v.getActividad() != null && v.getActividad().getId() != null)
      {
        ActividadEntity fresca = buscarActividadPorId(v.getActividad().getId()).orElse(v.getActividad());
        c.setActividad(copiarActividad(fresca));
      }
      else
        c.setActividad(copiarActividad(v.getActividad()));

      List<AlternativaEntity> alts = new ArrayList<>();
      if (v.getAlternativas() != null)
      {
        for (AlternativaEntity alt : v.getAlternativas())
          alts.add(copiarAlternativa(alt));
      }
      c.setAlternativas(alts);

      List<VotoEntity> votosCopia = new ArrayList<>();
      if (v.getVotos() != null)
      {
        for (VotoEntity vt : v.getVotos())
        {
          VotoEntity vc = new VotoEntity();
          vc.setId(vt.getId());
          vc.setUsuario(copiarUsuario(vt.getUsuario()));
          vc.setAlternativa(copiarAlternativa(vt.getAlternativa()));
          votosCopia.add(vc);
        }
      }
      c.setVotos(votosCopia);
      c.setAlternativaGanadora(copiarAlternativa(v.getAlternativaGanadora()));
      c.setQuorumMinimo(v.getQuorumMinimo());
      return c;
    }

    private AlternativaEntity copiarAlternativa(AlternativaEntity alt)
    {
      if (alt == null)
        return null;
      AlternativaEntity c = new AlternativaEntity();
      c.setId(alt.getId());
      c.setNumeroAlternativa(alt.getNumeroAlternativa());
      c.setFecha(alt.getFecha());
      c.setClima(alt.getClima());
      return c;
    }
  }

  public static class FallbackMongoTemplate extends MongoTemplate
  {
    private final InMemoryMongoStore store;
    private final MongoDatabaseFactory databaseFactory;

    public FallbackMongoTemplate(
        MongoDatabaseFactory mongoDbFactory,
        MongoConverter mongoConverter,
        InMemoryMongoStore store)
    {
      super(mongoDbFactory, mongoConverter);
      this.databaseFactory = mongoDbFactory;
      this.store = store;
    }

    @Override
    public <T> T execute(String collectionName, CollectionCallback<T> action)
    {
      if (Boolean.FALSE.equals(MONGO_DISPONIBLE_INIT.get()))
        return null;
      if (store != null && !store.verificarDisponibilidad(databaseFactory))
        return null;
      try
      {
        return super.execute(collectionName, action);
      }
      catch (RuntimeException e)
      {
        return null;
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T findAndModify(Query query, UpdateDefinition update, FindAndModifyOptions options, Class<T> entityClass)
    {
      if (!store.verificarDisponibilidad(databaseFactory) && entityClass == VotacionEntity.class)
        return (T) store.cerrarVotacionAtomicamente(query, update);
      return super.findAndModify(query, update, options, entityClass);
    }
  }

  @Bean
  public InMemoryMongoStore inMemoryMongoStore()
  {
    return new InMemoryMongoStore();
  }

  @Bean
  @Primary
  public MongoTemplate mongoTemplate(
      MongoDatabaseFactory factory,
      MongoConverter converter,
      InMemoryMongoStore store)
  {
    boolean disponible = store.verificarDisponibilidad(factory);
    MONGO_DISPONIBLE_INIT.set(disponible);
    try
    {
      return new FallbackMongoTemplate(factory, converter, store);
    }
    finally
    {
      MONGO_DISPONIBLE_INIT.remove();
    }
  }

  @Bean
  public BeanPostProcessor mongoRepositoryFallbackPostProcessor(
      MongoDatabaseFactory factory,
      InMemoryMongoStore store)
  {
    return new BeanPostProcessor()
    {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException
      {
        if (store.verificarDisponibilidad(factory))
          return bean;

        if (bean instanceof UsuarioMongoRepository delegado)
          return crearProxyUsuario(delegado, store);

        if (bean instanceof ActividadesMongoRepository delegado)
          return crearProxyActividades(delegado, store);

        if (bean instanceof VotacionesMongoRepository delegado)
          return crearProxyVotaciones(delegado, store);

        return bean;
      }
    };
  }

  private UsuarioMongoRepository crearProxyUsuario(UsuarioMongoRepository delegado, InMemoryMongoStore store)
  {
    return (UsuarioMongoRepository) Proxy.newProxyInstance(
        UsuarioMongoRepository.class.getClassLoader(),
        new Class<?>[] { UsuarioMongoRepository.class },
        (proxy, method, args) ->
        {
          String name = method.getName();
          if ("save".equals(name) && args != null && args.length == 1)
            return store.guardarUsuario((UsuarioEntity) args[0]);
          if ("findById".equals(name) && args != null && args.length == 1)
            return store.buscarUsuarioPorId((String) args[0]);
          if ("existsById".equals(name) && args != null && args.length == 1)
            return store.existeUsuarioPorId((String) args[0]);
          if ("findByMedioContacto_ValorAndMedioContacto_Tipo".equals(name) && args != null && args.length == 2)
            return store.buscarUsuarioPorMedioContacto((String) args[0], (TipoMedioContacto) args[1]);
          if ("deleteAll".equals(name) && (args == null || args.length == 0))
          {
            store.borrarTodosLosUsuarios();
            return null;
          }
          return method.invoke(delegado, args);
        });
  }

  private ActividadesMongoRepository crearProxyActividades(ActividadesMongoRepository delegado, InMemoryMongoStore store)
  {
    return (ActividadesMongoRepository) Proxy.newProxyInstance(
        ActividadesMongoRepository.class.getClassLoader(),
        new Class<?>[] { ActividadesMongoRepository.class },
        (proxy, method, args) ->
        {
          String name = method.getName();
          if ("save".equals(name) && args != null && args.length == 1)
            return store.guardarActividad((ActividadEntity) args[0]);
          if ("findById".equals(name) && args != null && args.length == 1)
            return store.buscarActividadPorId((String) args[0]);
          if ("deleteAll".equals(name) && (args == null || args.length == 0))
          {
            store.borrarTodasLasActividades();
            return null;
          }
          return method.invoke(delegado, args);
        });
  }

  private VotacionesMongoRepository crearProxyVotaciones(VotacionesMongoRepository delegado, InMemoryMongoStore store)
  {
    return (VotacionesMongoRepository) Proxy.newProxyInstance(
        VotacionesMongoRepository.class.getClassLoader(),
        new Class<?>[] { VotacionesMongoRepository.class },
        (proxy, method, args) ->
        {
          String name = method.getName();
          if ("save".equals(name) && args != null && args.length == 1)
            return store.guardarVotacion((VotacionEntity) args[0]);
          if ("findById".equals(name) && args != null && args.length == 1)
            return store.buscarVotacionPorId((String) args[0]);
          if ("findByAbiertaTrueAndActividadId".equals(name) && args != null && args.length == 1)
            return store.buscarVotacionAbiertaPorActividad((ObjectId) args[0]);
          if ("deleteAll".equals(name) && (args == null || args.length == 0))
          {
            store.borrarTodasLasVotaciones();
            return null;
          }
          return method.invoke(delegado, args);
        });
  }
}
