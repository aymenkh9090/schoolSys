/**
 * Styles partagés des pages de connexion (maquette « Portail SchoolSys ») :
 * colonne formulaire à gauche sur fond clair, illustration à droite.
 *
 * Les bases ne portent aucune couleur d'accent — chaque page ajoute la sienne
 * en classes littérales (bleu portail, violet super admin) pour que Tailwind
 * puisse les détecter à la compilation.
 */

export const PORTAL_PAGE = 'min-h-screen bg-[#f4f8f9] dark:bg-slate-950 lg:grid lg:grid-cols-2'

export const PORTAL_COLUMN =
  'flex min-h-screen flex-col px-6 py-8 sm:px-10 lg:min-h-0 lg:px-14 xl:px-20'

export const PORTAL_CARD =
  'mt-7 rounded-3xl bg-white p-6 shadow-[0_20px_50px_-30px_rgba(13,38,68,0.45)] dark:bg-slate-900 sm:p-8'

export const PORTAL_TITLE =
  'font-serif text-3xl font-bold tracking-tight dark:text-slate-100 sm:text-4xl'

/** Champ en pilule — ajouter `focus:ring-[…]` côté page. */
export const PILL_FIELD =
  'rounded-full border-slate-200 py-3 pl-11 pr-5 text-[15px] rtl:pl-5 rtl:pr-11'

/** Champ mot de passe en pilule (place pour l'œil à droite). */
export const PILL_PASSWORD =
  'rounded-full border-slate-200 py-3 pl-11 pr-11 text-[15px] rtl:pl-11 rtl:pr-11'

/** Bouton principal en pilule — ajouter `bg-[…] hover:bg-[…] focus-visible:ring-[…]`. */
export const PILL_BUTTON = 'w-full rounded-full py-3 text-[15px]'

export const PORTAL_LINK_ROW = 'mt-5 flex flex-wrap items-center justify-between gap-3'

/** Lien secondaire de la carte — ajouter `hover:text-[…]` et `dark:hover:text-…`. */
export const PORTAL_LINK =
  'text-sm font-medium text-slate-500 transition-colors dark:text-slate-400'
