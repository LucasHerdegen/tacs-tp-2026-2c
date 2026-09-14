import React, { useState } from 'react';
import { Card, CardBody } from '../../../components/ui/Card';
import { Button } from '../../../components/ui/Button';
import { ApiError } from '../../../lib/api';
import { useAuth } from '../../auth/authContext';
import { votingApi } from '../votingApi';
import type { VotacionDto } from '../types';

interface VotingCreateFormProps {
  actividadId: number;
  onCreated: (votacion: VotacionDto) => void;
  onCancel: () => void;
}

export const VotingCreateForm: React.FC<VotingCreateFormProps> = ({ actividadId, onCreated, onCancel }) => {
  const { token } = useAuth();
  const [quorumMinimo, setQuorumMinimo] = useState(2);
  const [fechaLimite, setFechaLimite] = useState('');
  const [fechasAlternativas, setFechasAlternativas] = useState<string[]>(['']);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function actualizarFecha(index: number, valor: string) {
    setFechasAlternativas((prev) => prev.map((f, i) => (i === index ? valor : f)));
  }

  function agregarFila() {
    setFechasAlternativas((prev) => [...prev, '']);
  }

  function quitarFila(index: number) {
    setFechasAlternativas((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!token) return;

    const alternativas = fechasAlternativas.filter(Boolean).map((fecha) => ({ fecha }));
    if (alternativas.length === 0) {
      setError('Proponé al menos una alternativa de fecha.');
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      const creada = await votingApi.crear(
        actividadId,
        { quorumMinimo, fechaLimite, alternativas },
        token,
      );
      onCreated(creada);
    } catch (requestError) {
      setError(requestError instanceof ApiError ? requestError.message : 'No pudimos abrir la votación.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card>
      <CardBody>
        <h2 className="text-xl font-bold text-gray-900 mb-4">🗳️ Abrir votación manualmente</h2>

        {error && (
          <div className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
            {error}
          </div>
        )}

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div>
            <label className="label-text">Quórum mínimo</label>
            <input
              type="number"
              min={1}
              className="input-field"
              value={quorumMinimo}
              onChange={(e) => setQuorumMinimo(Number(e.target.value))}
              required
            />
          </div>

          <div>
            <label className="label-text">Fecha límite para votar</label>
            <input
              type="datetime-local"
              lang="es-AR"
              className="input-field"
              value={fechaLimite}
              onChange={(e) => setFechaLimite(e.target.value)}
              required
            />
          </div>

          <div>
            <label className="label-text">Alternativas propuestas</label>
            <div className="space-y-2">
              {fechasAlternativas.map((fecha, index) => (
                <div key={index} className="flex gap-2">
                  <input
                    type="datetime-local"
                    lang="es-AR"
                    className="input-field"
                    value={fecha}
                    onChange={(e) => actualizarFecha(index, e.target.value)}
                  />
                  {fechasAlternativas.length > 1 && (
                    <Button type="button" variant="danger" className="px-3" onClick={() => quitarFila(index)}>
                      ✕
                    </Button>
                  )}
                </div>
              ))}
            </div>
            <button
              type="button"
              className="mt-2 text-sm font-medium text-indigo-600 hover:text-indigo-700"
              onClick={agregarFila}
            >
              + Agregar otra alternativa
            </button>
          </div>

          <div className="flex gap-3 justify-end pt-2">
            <Button type="button" variant="secondary" onClick={onCancel}>
              Cancelar
            </Button>
            <Button type="submit" variant="primary" disabled={submitting}>
              {submitting ? 'Abriendo…' : 'Abrir votación'}
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  );
};