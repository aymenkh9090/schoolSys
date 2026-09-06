/**
 * Le catalogue de contraintes, dit en français et en arabe.
 *
 * Le backend publie les définitions avec un code technique et un intitulé
 * anglais issus du seed (« Maximum student hours per day »). C'est suffisant
 * pour un développeur et illisible pour un directeur d'établissement : la
 * traduction vit donc ici, côté interface, indexée par le code — la seule
 * partie du contrat qui ne bouge pas.
 *
 * Deux langues, et non une : l'établissement travaille en arabe, la circulaire
 * est en arabe, mais l'application et ses écrans sont en français. Afficher les
 * deux côte à côte évite de demander à qui rédige la règle de la traduire de
 * tête avant de décider.
 *
 * Un code absent d'ici retombe sur le libellé du serveur : la liste reste
 * complète même si le catalogue gagne une entrée avant que sa traduction
 * n'arrive.
 *
 * Les codes retirés du catalogue à l'étape I du plan de conformité n'ont plus
 * d'entrée ici : les heures creuses et la salle spécialisée restent imposées,
 * mais elles ne sont pas des options et n'ont donc plus de case à cocher.
 */

import type { ConstraintCategory, ConstraintType, ImportanceLevel } from '@/api/planning.api'

export interface LibelleBilingue {
  nom: string
  nomAr: string
  description: string
  descriptionAr: string
}

export const CATALOGUE_LABELS: Record<string, LibelleBilingue> = {
  MAX_STUDENT_HOURS_PER_DAY: {
    nom: 'Heures de cours maximum par jour pour une classe',
    nomAr: 'الحد الأقصى لساعات الدراسة اليومية للتلميذ',
    description:
      'Une classe ne dépasse pas le nombre d’heures de cours fixé sur une même journée.',
    descriptionAr: 'لا يتجاوز القسم عدد ساعات الدراسة المحدد في اليوم الواحد.',
  },
  MAX_TEACHER_HOURS_PER_DAY: {
    nom: 'Heures de cours maximum par jour pour un enseignant',
    nomAr: 'الحد الأقصى للساعات اليومية للأستاذ',
    description: 'Un enseignant ne dépasse pas le volume horaire fixé sur une même journée.',
    descriptionAr: 'لا يتجاوز الأستاذ الحجم الساعي المحدد في اليوم الواحد.',
  },
  MAX_TEACHER_HOURS_FRIDAY_SATURDAY: {
    nom: 'Journées allégées le vendredi et le samedi',
    nomAr: 'ساعات مخففة يومي الجمعة والسبت',
    description:
      'Le vendredi et le samedi sont des journées courtes : le volume d’un enseignant y est plafonné plus bas.',
    descriptionAr: 'الجمعة والسبت يومان قصيران: يُحدَّد الحجم الساعي للأستاذ فيهما بسقف أدنى.',
  },
  RESPECT_OFFICIAL_SUBJECT_HOURS: {
    nom: 'Respect des horaires officiels de chaque matière',
    nomAr: 'احترام التوقيت الرسمي لكل مادة',
    description:
      'Chaque matière reçoit exactement le volume hebdomadaire fixé par le programme national.',
    descriptionAr: 'تحصل كل مادة على الحجم الأسبوعي المحدد في البرنامج الوطني.',
  },
  PHYSICAL_EDUCATION_THREE_SESSIONS: {
    nom: 'Éducation physique répartie sur trois séances',
    nomAr: 'التربية البدنية موزعة على ثلاث حصص',
    description: 'Le sport est réparti sur plusieurs jours de la semaine plutôt que groupé.',
    descriptionAr: 'تُوزَّع حصص التربية البدنية على أيام مختلفة بدل تجميعها.',
  },
  MAX_TWO_CONSECUTIVE_SESSIONS_SAME_SUBJECT: {
    nom: 'Pas plus de deux séances consécutives dans la même matière',
    nomAr: 'لا أكثر من حصتين متتاليتين في نفس المادة',
    description: 'Une même matière n’occupe pas plus de deux créneaux qui se suivent.',
    descriptionAr: 'لا تشغل المادة الواحدة أكثر من حصتين متتاليتين.',
  },
  BALANCED_MORNING_AFTERNOON: {
    nom: 'Équilibre entre le matin et l’après-midi',
    nomAr: 'التوازن بين الفترة الصباحية والمسائية',
    description: 'Les cours d’un enseignant sont répartis entre les deux demi-journées.',
    descriptionAr: 'تُوزَّع دروس الأستاذ بين الفترتين الصباحية والمسائية.',
  },
  TEACHER_MIN_TWO_LEVELS: {
    nom: 'Un enseignant intervient sur au moins deux niveaux',
    nomAr: 'تدريس الأستاذ لمستويين على الأقل',
    description: 'Évite qu’un enseignant ne travaille que sur un seul niveau de classe.',
    descriptionAr: 'يتفادى إسناد مستوى واحد فقط للأستاذ.',
  },
  BALANCED_TEACHER_WORKLOAD: {
    nom: 'Service réparti sur la semaine',
    nomAr: 'توزيع التوقيت على أيام العمل',
    description:
      'Le service d’un enseignant s’étale sur ses jours de travail, au lieu d’être massé sur deux ou trois journées.',
    descriptionAr: 'يُوزَّع توقيت الأستاذ على أيام عمله بدل تجميعه في يومين أو ثلاثة.',
  },
  TEACHER_WEEKLY_REST_DAY: {
    nom: 'Un jour libre par semaine pour l’enseignant',
    nomAr: 'يوم راحة أسبوعي للأستاذ',
    description: 'Chaque enseignant garde, si possible, une journée sans cours dans la semaine.',
    descriptionAr: 'يحتفظ كل أستاذ، إن أمكن، بيوم دون دروس في الأسبوع.',
  },
  AVOID_SUBJECT_CONCENTRATION_SAME_DAY: {
    nom: 'Éviter de concentrer une matière sur une seule journée',
    nomAr: 'تفادي تركيز المادة في يوم واحد',
    description: 'Les heures d’une matière sont étalées sur la semaine.',
    descriptionAr: 'تُوزَّع ساعات المادة على أيام الأسبوع.',
  },
  MAIN_SUBJECT_BALANCED_DISTRIBUTION: {
    nom: 'Matière répartie entre le matin et l’après-midi',
    nomAr: 'توزيع المادة بين الفترة الصباحية والمسائية',
    description:
      'Les heures d’une même matière ne sont pas toutes placées le matin, ni toutes l’après-midi.',
    descriptionAr: 'لا تُبرمَج ساعات المادة الواحدة كلها صباحًا ولا كلها مساءً.',
  },
  THEORY_PRACTICE_SEPARATION: {
    nom: 'Séparation entre cours et travaux pratiques',
    nomAr: 'الفصل بين الدروس النظرية والأعمال التطبيقية',
    description: 'Le cours théorique et les travaux pratiques ne sont pas placés au même moment.',
    descriptionAr: 'لا يُبرمَج الدرس النظري والعمل التطبيقي في نفس الحصة.',
  },
}

/** Le libellé bilingue d'un code, ou le libellé du serveur quand il manque. */
export function libelleCatalogue(
  code: string,
  fallbackNom: string,
  fallbackDescription?: string
): LibelleBilingue {
  return (
    CATALOGUE_LABELS[code] ?? {
      nom: fallbackNom,
      nomAr: '',
      description: fallbackDescription ?? '',
      descriptionAr: '',
    }
  )
}

// ── Vocabulaire des énumérations, dans les deux langues ─────────────────────

export const TYPE_LABELS: Record<ConstraintType, string> = {
  HARD: 'Obligatoire',
  MEDIUM: 'Fortement recommandée',
  SOFT: 'Souhaitable',
}

export const TYPE_LABELS_AR: Record<ConstraintType, string> = {
  HARD: 'إجبارية',
  MEDIUM: 'موصى بها بشدة',
  SOFT: 'مستحبة',
}

export const TYPE_EXPLANATIONS: Record<ConstraintType, string> = {
  HARD: 'Jamais enfreinte : le planning généré la respecte toujours.',
  MEDIUM: 'Le générateur fait tout pour la respecter, et s’en écarte seulement s’il n’existe aucune autre solution.',
  SOFT: 'Simple préférence : elle est prise en compte quand c’est possible, sans jamais bloquer la génération.',
}

export const IMPORTANCE_LABELS: Record<ImportanceLevel, string> = {
  CRITICAL: 'Critique',
  HIGH: 'Élevée',
  MEDIUM: 'Moyenne',
  LOW: 'Faible',
}

export const CATEGORY_LABELS: Record<ConstraintCategory, string> = {
  STUDENT: 'Élèves',
  TEACHER: 'Enseignants',
  SUBJECT: 'Matières',
  ROOM: 'Salles',
  PEDAGOGICAL: 'Pédagogie',
}

export const CATEGORY_LABELS_AR: Record<ConstraintCategory, string> = {
  STUDENT: 'التلاميذ',
  TEACHER: 'الأساتذة',
  SUBJECT: 'المواد',
  ROOM: 'القاعات',
  PEDAGOGICAL: 'بيداغوجيا',
}
