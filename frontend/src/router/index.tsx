import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { ProtectedRoute } from '@/components/auth/ProtectedRoute'
import { RedirectByRole } from '@/components/auth/RedirectByRole'
import PageNonAutorise from '@/pages/PageNonAutorise'
import LoginSuperAdmin from '@/pages/LoginSuperAdmin'
import LoginEtablissement from '@/pages/LoginEtablissement'
import ChangePasswordFirstLogin from '@/pages/ChangePasswordFirstLogin'

// Super Admin
import SuperAdminDashboard from '@/features/superadmin/SuperAdminDashboard'
import ListeEtablissements from '@/features/superadmin/ListeEtablissements'
import ListeAbonnements from '@/features/superadmin/subscriptions/ListeAbonnements'
import MonitoringPage from '@/features/superadmin/monitoring/MonitoringPage'

// School Dashboard (établissement ou enseignant selon le rôle)
import DashboardHome from '@/features/dashboard/DashboardHome'

// Organisation
import AcademiqueDashboard from '@/features/organisation/AcademiqueDashboard'
import GestionAnneesScolaires from '@/features/organisation/GestionAnneesScolaires'
import GestionNiveaux from '@/features/organisation/GestionNiveaux'
import GestionMatieres from '@/features/organisation/GestionMatieres'
import GestionSalles from '@/features/organisation/GestionSalles'
import GestionClasses from '@/features/organisation/GestionClasses'
import GestionEnseignants from '@/features/organisation/GestionEnseignants'
import GestionEleves from '@/features/organisation/GestionEleves'
import GestionAffectations from '@/features/organisation/GestionAffectations'
import GestionUsers from '@/features/organisation/GestionUsers'
import ConfigurationDashboard from '@/features/organisation/ConfigurationDashboard'
import ConfigurationHoraires from '@/features/organisation/ConfigurationHoraires'
import ProgrammeNational from '@/features/organisation/ProgrammeNational'
import ProgrammeEcole from '@/features/organisation/ProgrammeEcole'

// Planning
import EmploiDuTempsDashboard from '@/features/planning/EmploiDuTempsDashboard'
import GenerationPlanning from '@/features/planning/GenerationPlanning'
import ConsultationPlanning from '@/features/planning/ConsultationPlanning'
import ConfigurationContraintes from '@/features/planning/ConfigurationContraintes'
import AssistantPlanning from '@/features/planning/AssistantPlanning'

// Absences
import SessionsAppel from '@/features/absence/SessionsAppel'
import AbsencesDuJour from '@/features/absence/AbsencesDuJour'
import JustificatifsAbsences from '@/features/absence/JustificatifsAbsences'
import StatistiquesAbsences from '@/features/absence/StatistiquesAbsences'
import CahiersSeance from '@/features/absence/CahiersSeance'
import PilotagePedagogique from '@/features/absence/PilotagePedagogique'


// Statistiques
import DashboardStatistiques from '@/features/statistiques/DashboardStatistiques'

// Analytics & Rapports
import RapportsAnalyses from '@/features/rapports/RapportsAnalyses'

// Paramètres
import Parametres from '@/features/parametres/Parametres'

// Espace enseignant
import MonEspace from '@/features/enseignant/MonEspace'
import MonPlanning from '@/features/enseignant/MonPlanning'
import MesClasses from '@/features/enseignant/MesClasses'
import MonCahier from '@/features/enseignant/MonCahier'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RedirectByRole />,
  },
  {
    path: '/login',
    element: <LoginSuperAdmin />,
  },
  {
    path: '/etablissement/login',
    element: <LoginEtablissement />,
  },
  {
    path: '/non-autorise',
    element: <PageNonAutorise />,
  },
  {
    path: '/premiere-connexion/mot-de-passe',
    element: (
      <ProtectedRoute allowPasswordPending>
        <ChangePasswordFirstLogin />
      </ProtectedRoute>
    ),
  },
  // ── Super Admin ─────────────────────────────────────────────────────────────
  {
    path: '/super-admin',
    element: (
      <ProtectedRoute roles={['PLATFORM_SUPER_ADMIN']}>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <SuperAdminDashboard /> },
      { path: 'etablissements', element: <ListeEtablissements /> },
      { path: 'abonnements', element: <ListeAbonnements /> },
      { path: 'monitoring', element: <MonitoringPage /> },
    ],
  },
  // ── Ecole (all school roles) ─────────────────────────────────────────────────
  {
    path: '/ecole',
    element: (
      <ProtectedRoute roles={['SCHOOL_ADMIN', 'TEACHER', 'SURVEILLANT']}>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <DashboardHome /> },

      // Organisation
      { path: 'academique', element: <AcademiqueDashboard /> },
      { path: 'annees', element: <GestionAnneesScolaires /> },
      { path: 'niveaux', element: <GestionNiveaux /> },
      { path: 'matieres', element: <GestionMatieres /> },
      { path: 'salles', element: <GestionSalles /> },
      { path: 'classes', element: <GestionClasses /> },
      { path: 'enseignants', element: <GestionEnseignants /> },
      { path: 'eleves', element: <GestionEleves /> },
      { path: 'affectations', element: <GestionAffectations /> },
      { path: 'utilisateurs', element: <GestionUsers /> },
      { path: 'configuration', element: <ConfigurationDashboard /> },
      { path: 'configuration/horaires', element: <ConfigurationHoraires /> },
      { path: 'programme-national', element: <ProgrammeNational /> },
      { path: 'programme-ecole', element: <ProgrammeEcole /> },

      // Planning
      { path: 'planning', element: <EmploiDuTempsDashboard /> },
      { path: 'planning/consultation', element: <ConsultationPlanning /> },
      { path: 'planning/generer', element: <GenerationPlanning /> },
      { path: 'planning/contraintes', element: <ConfigurationContraintes /> },
      { path: 'planning/assistant', element: <AssistantPlanning /> },

      // Absences
      { path: 'absences/appel', element: <SessionsAppel /> },
      { path: 'absences/journee', element: <AbsencesDuJour /> },
      { path: 'absences/cahier', element: <CahiersSeance /> },
      // Restreint à la direction : un enseignant qui atteindrait cette page n'y
      // verrait que ses propres séances — le backend ne lui en donne pas d'autres.
      // Ce n'est donc pas une barrière de sécurité, mais un garde-fou contre une
      // page qui promet le périmètre de l'établissement et n'en montre qu'un bout.
      {
        path: 'absences/pilotage',
        element: (
          <ProtectedRoute roles={['SCHOOL_ADMIN']}>
            <PilotagePedagogique />
          </ProtectedRoute>
        ),
      },
      { path: 'absences/justificatifs', element: <JustificatifsAbsences /> },
      // Ancien chemin du suivi : conservé pour les liens et favoris existants.
      { path: 'absences/suivi', element: <Navigate to="/ecole/absences/justificatifs" replace /> },
      { path: 'absences/stats', element: <StatistiquesAbsences /> },
      { path: 'absences', element: <Navigate to="absences/appel" replace /> },


      // Statistiques & rapports
      { path: 'statistiques', element: <DashboardStatistiques /> },
      { path: 'rapports', element: <RapportsAnalyses /> },

      // Paramètres
      { path: 'parametres', element: <Parametres /> },

      // Espace enseignant
      { path: 'mon-espace', element: <MonEspace /> },
      { path: 'mon-planning', element: <MonPlanning /> },
      { path: 'mes-classes', element: <MesClasses /> },
      { path: 'mon-cahier', element: <MonCahier /> },
    ],
  },
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
])
