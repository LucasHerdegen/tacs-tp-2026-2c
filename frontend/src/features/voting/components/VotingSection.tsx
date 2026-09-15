import React, { useCallback, useEffect, useState } from 'react';
import { Card, CardBody } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { ApiError } from '../../../lib/api';
import { useAuth } from '../../auth/authContext';
import type { UserId } from '../../auth/types';
import { votingApi } from '../votingApi';
import type { AlternativaDto, VotacionDto } from '../types';
import { VotingCreateForm } from './VotingCreateForm';

interface VotingSectionProps {
  actividadId: number;
  organizadorId: UserId;
}

function badgeParaClima(cumple: boolean | null) {
  if (cumple === null) return <Badge variant="info">Sin reglas de clima</Badge>;
  return cumple
    ? <Badge variant="success">Clima OK</Badge>
    : <Badge variant="error">Clima desfavorable</Badge>;
}

export const VotingSection: React.FC<VotingSectionProps> = ({ actividadId, organizadorId }) => {
  const { user, token } = useAuth();
  const [votacion, setVotacion] = useState<VotacionDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [nuevaFechaAlternativa, setNuevaFechaAlternativa] = useState('');

  const esOrganizador = user?.id === organizadorId;

  const obtenerVotacion = useCallback(async () => {
    if (!token) return;
    return votingApi.buscarPorActividad(actividadId, token);
  }, [actividadId, token]);

  const cargarVotacion = useCallback(async () => {
    try {
      const encontrada = await obtenerVotacion();
      if (encontrada === undefined) return;
      setVotacion(encontrada);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos cargar la votación.');
    } finally {
      setLoading(false);
    }
  }, [obtenerVotacion]);

  useEffect(() => {
    let active = true;

    obtenerVotacion()
      .then((encontrada) => {
        if (!active || encontrada === undefined) return;
        setVotacion(encontrada);
      })
      .catch((requestError) => {
        if (!active) return;
        setError(requestError instanceof ApiError ? requestError.message : 'No pudimos cargar la votación.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [obtenerVotacion]);

  async function handleVotar(numeroAlternativa: number) {
    if (!token || !user || !votacion) return;
    setBusy(true);
    setError('');
    try {
      const actualizada = await votingApi.votar(
        votacion.id,
        { usuarioId: user.id, numeroAlternativa },
        token,
      );
      setVotacion(actualizada);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos registrar tu voto.');
    } finally {
      setBusy(false);
    }
  }

  async function handleAgregarAlternativa() {
    if (!token || !votacion || !nuevaFechaAlternativa) return;
    setBusy(true);
    setError('');
    try {
      const actualizada = await votingApi.agregarAlternativa(
        votacion.id,
        { fecha: nuevaFechaAlternativa },
        token,
      );
      setVotacion(actualizada);
      setNuevaFechaAlternativa('');
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos agregar la alternativa.');
    } finally {
      setBusy(false);
    }
  }

  async function handleEliminarAlternativa(numeroAlternativa: number) {
    if (!token || !votacion) return;
    setBusy(true);
    setError('');
    try {
      await votingApi.eliminarAlternativa(votacion.id, numeroAlternativa, token);
      await cargarVotacion();
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos eliminar la alternativa.');
    } finally {
      setBusy(false);
    }
  }

  async function handleCerrar() {
    if (!token || !votacion) return;
    setBusy(true);
    setError('');
    try {
      const cerrada = await votingApi.cerrar(votacion.id, token);
      setVotacion(cerrada);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos cerrar la votación.');
    } finally {
      setBusy(false);
    }
  }

  async function handleEliminarVotacion() {
    if (!token || !votacion) return;
    setBusy(true);
    setError('');
    try {
      await votingApi.eliminar(votacion.id, token);
      setVotacion(null);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos eliminar la votación.');
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return (
      <Card>
        <CardBody>
          <p className="text-sm text-gray-500">Cargando votación…</p>
        </CardBody>
      </Card>
    );
  }

  if (showCreateForm) {
    return (
      <VotingCreateForm
        actividadId={actividadId}
        onCreated={(creada) => {
          setVotacion(creada);
          setShowCreateForm(false);
        }}
        onCancel={() => setShowCreateForm(false)}
      />
    );
  }

  if (!votacion) {
    return (
      <Card>
        <CardBody>
          <h2 className="text-xl font-bold text-gray-900 mb-2">🗳️ Votación</h2>
          <p className="text-sm text-gray-600 mb-4">
            No hay ninguna votación para esta actividad. Si el pronóstico empeora, la
            aplicación puede abrir una automáticamente — o el organizador puede abrirla manualmente.
          </p>
          {esOrganizador && (
            <Button variant="primary" onClick={() => setShowCreateForm(true)}>
              Abrir votación manualmente
            </Button>
          )}
        </CardBody>
      </Card>
    );
  }

  return (
    <Card>
      <CardBody>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold text-gray-900">🗳️ Votación</h2>
          <Badge variant={votacion.abierta ? 'warning' : 'info'}>
            {votacion.abierta ? 'Abierta' : 'Cerrada'}
          </Badge>
        </div>

        {error && (
          <div className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
            {error}
          </div>
        )}

        <div className="text-sm text-gray-600 mb-4 space-y-1">
          <p>Quórum mínimo: <strong>{votacion.quorumMinimo}</strong> votos</p>
          <p>
            {votacion.abierta ? 'Vence' : 'Venció'}:{' '}
            {new Date(votacion.fechaLimite).toLocaleString('es-AR', { dateStyle: 'medium', timeStyle: 'short' })} hs
          </p>
        </div>

        {!votacion.abierta && (
          <div className={`rounded-lg p-4 mb-4 border ${votacion.alternativaGanadora ? 'bg-green-50 border-green-200' : 'bg-red-50 border-red-200'}`}>
            {votacion.alternativaGanadora ? (
              <p className="text-green-800 font-medium">
                ✅ Ganó la alternativa del{' '}
                {new Date(votacion.alternativaGanadora.fecha).toLocaleString('es-AR', { dateStyle: 'medium', timeStyle: 'short' })} hs
                — la actividad fue reprogramada.
              </p>
            ) : (
              <p className="text-red-800 font-medium">
                ❌ No se alcanzó el quórum mínimo — la actividad fue cancelada.
              </p>
            )}
          </div>
        )}

        <ul className="space-y-3">
          {votacion.alternativasDtos.map((alternativa: AlternativaDto) => (
            <li
              key={alternativa.numeroAlternativa}
              className="flex items-center justify-between gap-3 border border-gray-200 rounded-lg p-3"
            >
              <div>
                <p className="font-medium text-gray-900">
                  {new Date(alternativa.fecha).toLocaleString('es-AR', { dateStyle: 'medium', timeStyle: 'short' })} hs
                </p>
                <div className="flex items-center gap-2 mt-1">
                  {badgeParaClima(alternativa.cumpleReglasClima)}
                  <span className="text-xs text-gray-500">{alternativa.cantidadVotos} voto(s)</span>
                </div>
              </div>

              <div className="flex items-center gap-2">
                {votacion.abierta && (
                  <Button
                    variant="secondary"
                    className="text-xs px-3 py-1"
                    disabled={busy}
                    onClick={() => handleVotar(alternativa.numeroAlternativa)}
                  >
                    Votar
                  </Button>
                )}
                {votacion.abierta && esOrganizador && (
                  <Button
                    variant="danger"
                    className="text-xs px-3 py-1"
                    disabled={busy}
                    onClick={() => handleEliminarAlternativa(alternativa.numeroAlternativa)}
                  >
                    Quitar
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>

        {votacion.abierta && esOrganizador && (
          <div className="mt-5 border-t border-gray-100 pt-4">
            <label className="label-text">Proponer otra alternativa</label>
            <div className="flex gap-2">
              <input
                type="datetime-local"
                lang="es-AR"
                className="input-field"
                value={nuevaFechaAlternativa}
                onChange={(e) => setNuevaFechaAlternativa(e.target.value)}
              />
              <Button variant="secondary" disabled={busy || !nuevaFechaAlternativa} onClick={handleAgregarAlternativa}>
                Agregar
              </Button>
            </div>
          </div>
        )}

        {votacion.abierta && esOrganizador && (
          <div className="mt-5 flex justify-end">
            <Button variant="primary" disabled={busy} onClick={handleCerrar}>
              Cerrar votación ahora
            </Button>
          </div>
        )}

        {!votacion.abierta && esOrganizador && (
          <div className="mt-5 flex justify-end">
            <Button variant="danger" disabled={busy} onClick={handleEliminarVotacion}>
              Eliminar votación
            </Button>
          </div>
        )}
      </CardBody>
    </Card>
  );
};
