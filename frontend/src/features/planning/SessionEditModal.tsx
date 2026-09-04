import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { Modal } from '@/components/ui/Modal'
import { Select } from '@/components/ui/Select'
import { Button } from '@/components/ui/Button'
import { planningApi } from '@/api/planning.api'
import { organisationApi } from '@/api/organisation.api'
import type { FlatSession } from './TimetableGrid'

const DAYS_FR: Record<string, string> = {
  MONDAY: 'Lundi', TUESDAY: 'Mardi', WEDNESDAY: 'Mercredi',
  THURSDAY: 'Jeudi', FRIDAY: 'Vendredi', SATURDAY: 'Samedi',
}
const WEEK_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

interface Props {
  jobId: number
  session: FlatSession
  onClose: () => void
  onSaved: () => void
}

export function SessionEditModal({ jobId, session, onClose, onSaved }: Props) {
  const qc = useQueryClient()
  const [day, setDay] = useState(session.day)
  const [startTime, setStartTime] = useState(session.startTime)
  const [roomCode, setRoomCode] = useState(session.roomCode)

  const { data: config } = useQuery({ queryKey: ['school-config'], queryFn: organisationApi.config.get })
  const { data: rooms = [] } = useQuery({ queryKey: ['rooms'], queryFn: organisationApi.rooms.list })

  const daysWithSlots = useMemo(() => {
    const days = new Set((config?.timeSlots ?? []).map((s) => s.dayOfWeek))
    return WEEK_ORDER.filter((d) => days.has(d))
  }, [config])

  const timeOptions = useMemo(() => {
    const times = (config?.timeSlots ?? [])
      .filter((s) => s.dayOfWeek === day)
      .map((s) => s.startTime.slice(0, 5))
    return Array.from(new Set(times)).sort()
  }, [config, day])

  const moveMutation = useMutation({
    mutationFn: () => planningApi.timetable.sessions.move(jobId, session.id, { day, startTime, roomCode }),
    onSuccess: () => {
      toast.success('Séance mise à jour')
      qc.invalidateQueries({ queryKey: ['rooms'] })
      onSaved()
    },
    onError: (e: { response?: { data?: { message?: string } } }) =>
      toast.error(e.response?.data?.message ?? 'Impossible de modifier cette séance'),
  })

  return (
    <Modal open onClose={onClose} title={`Modifier — ${session.subjectName}`} size="sm">
      <div className="space-y-4">
        <p className="text-xs text-brand-textMuted dark:text-slate-400">
          {[session.classCode, session.teacherName, `${session.startTime}–${session.endTime}`].filter(Boolean).join(' · ')}
        </p>
        <div className="grid grid-cols-2 gap-4">
          <Select
            label="Jour"
            value={day}
            onChange={(e) => {
              const newDay = e.target.value
              setDay(newDay)
              const validTimes = (config?.timeSlots ?? [])
                .filter((s) => s.dayOfWeek === newDay)
                .map((s) => s.startTime.slice(0, 5))
              if (!validTimes.includes(startTime)) setStartTime(validTimes[0] ?? '')
            }}
            options={(daysWithSlots.length > 0 ? daysWithSlots : WEEK_ORDER).map((d) => ({ value: d, label: DAYS_FR[d] ?? d }))}
          />
          <Select
            label="Heure"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            options={timeOptions.map((t) => ({ value: t, label: t }))}
            placeholder={timeOptions.length === 0 ? 'Aucun créneau' : undefined}
          />
        </div>
        <Select
          label="Salle"
          value={roomCode}
          onChange={(e) => setRoomCode(e.target.value)}
          options={rooms.map((r) => ({ value: r.codeSalle, label: `${r.codeSalle} (${r.typeSalle})` }))}
        />
        <div className="flex justify-end gap-2 pt-2">
          <Button variant="outline" type="button" onClick={onClose}>Annuler</Button>
          <Button
            type="button"
            loading={moveMutation.isPending}
            disabled={!startTime}
            onClick={() => moveMutation.mutate()}
          >
            Enregistrer
          </Button>
        </div>
      </div>
    </Modal>
  )
}
