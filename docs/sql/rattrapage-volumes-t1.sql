-- ─────────────────────────────────────────────────────────────────────────────
-- Rattrapage des volumes horaires d'un établissement déjà en service — § T.1
-- Plan de conformité, étape H. À lire en entier avant de l'exécuter.
-- ─────────────────────────────────────────────────────────────────────────────
--
-- CE QUE CE SCRIPT RÉPARE
--
-- Le programme national semé donnait 6 h de mathématiques et 5 h d'anglais en
-- classe entière. Le § T.1 dit 4 h de mathématiques et « (2)+1+1 » 4 h
-- d'anglais, la séance de 2 h étant en système de groupes ; il découpe le
-- français en « 2+1+1+① » en 7ᵉ et 8ᵉ, la quatrième séance étant de quinzaine.
-- Le seed est corrigé et se rattrape tout seul au démarrage (comparaison de
-- version). Les patterns DÉJÀ COPIÉS chez un établissement, eux, ne bougent
-- pas : « Appliquer le programme national » saute tout pattern dont le nom
-- existe déjà, précisément pour ne pas écraser ce qu'un établissement a réglé
-- à la main.
--
-- COMMENT IL PROCÈDE
--
-- Il ne réécrit pas les séances ligne à ligne : il SUPPRIME les trois patterns
-- divergents, puis laisse « Appliquer le programme national » les recréer
-- depuis le seed corrigé. Le chemin de recréation est celui que les tests
-- couvrent ; du SQL écrit à la main pour la même chose ne le serait pas.
--
-- ORDRE DES OPÉRATIONS
--
--   1. Démarrer l'API une fois : le seed passe les programmes en version 2.
--   2. Exécuter le diagnostic ci-dessous et LIRE ce qu'il renvoie.
--   3. Exécuter la réparation, vérifier, puis COMMIT.
--   4. Dans l'application : « Appliquer le programme national » sur les niveaux
--      concernés.
--   5. Relancer une génération d'emploi du temps.
--
-- CE QU'IL FAUT SAVOIR AVANT
--
-- Ramener les mathématiques de 6 h à 4 h retire deux heures à chaque classe.
-- Les affectations d'enseignants construites sur six heures deviennent
-- excédentaires : elles ne sont PAS touchées ici et doivent être revues.
--
-- Remplacer :tenant par l'identifiant de l'établissement.
-- ─────────────────────────────────────────────────────────────────────────────


-- ── 1. Diagnostic — ce qui diverge, avant de toucher à quoi que ce soit ──────

SELECT n.code                AS niveau,
       m.code_matiere        AS matiere,
       p.name                AS pattern,
       p.total_hours         AS heures_pattern,
       nm.heures_semaine     AS heures_niveau_matiere,
       p.repartition,
       count(pd.id_pattern_detail) AS nb_seances
  FROM patterns p
  JOIN niveaux_matieres nm ON nm.id_niveau_matiere = p.subject_level_id
  JOIN niveaux  n ON n.id_niveau  = nm.level_id
  JOIN matieres m ON m.id_matiere = nm.subject_id
  LEFT JOIN pattern_details pd ON pd.pattern_id = p.id_pattern
 WHERE p.tenant_id = :tenant
   AND m.code_matiere IN ('MATH', 'EN', 'FR')
 GROUP BY n.code, m.code_matiere, p.name, p.total_hours, nm.heures_semaine, p.repartition
 ORDER BY n.code, m.code_matiere;

-- Attendu APRÈS rattrapage complet, pour chaque niveau :
--   MATH : 4 h, 4 séances                     (§ T.1, 1+1+1+1)
--   EN   : 4 h, 3 séances dont une is_split   (§ T.1, (2)+1+1)
--   FR   : 5 h, 4 séances dont une BIWEEKLY en 7ᵉ et 8ᵉ ; 4 pleines en 9ᵉ


-- ── 2. Réparation ───────────────────────────────────────────────────────────

BEGIN;

-- 2.a — Les séances des trois patterns divergents.
DELETE FROM pattern_details pd
 USING patterns p, niveaux_matieres nm, matieres m
 WHERE pd.pattern_id = p.id_pattern
   AND p.subject_level_id = nm.id_niveau_matiere
   AND nm.subject_id = m.id_matiere
   AND p.tenant_id = :tenant
   AND p.name LIKE 'COLLEGE\_%\_OFFICIEL\_%'
   AND m.code_matiere IN ('MATH', 'EN', 'FR');

-- 2.b — Les patterns eux-mêmes.
DELETE FROM patterns p
 USING niveaux_matieres nm, matieres m
 WHERE p.subject_level_id = nm.id_niveau_matiere
   AND nm.subject_id = m.id_matiere
   AND p.tenant_id = :tenant
   AND p.name LIKE 'COLLEGE\_%\_OFFICIEL\_%'
   AND m.code_matiere IN ('MATH', 'EN', 'FR');

-- 2.c — Le volume déclaré au niveau, que le solveur lit comme volume officiel.
--       Le français ne change pas de total : sa quatrième séance devient de
--       quinzaine, elle occupe toujours son créneau.
UPDATE niveaux_matieres nm
   SET heures_semaine = 4
  FROM matieres m, niveaux n
 WHERE nm.subject_id = m.id_matiere
   AND nm.level_id   = n.id_niveau
   AND nm.tenant_id  = :tenant
   AND n.code IN ('7EME', '8EME', '9EME')
   AND m.code_matiere IN ('MATH', 'EN');

-- 2.d — Vérification : plus aucun pattern pour ces trois matières, et les
--       volumes de niveau sont à 4 h.
SELECT n.code AS niveau, m.code_matiere AS matiere, nm.heures_semaine,
       (SELECT count(*) FROM patterns p
         WHERE p.subject_level_id = nm.id_niveau_matiere) AS patterns_restants
  FROM niveaux_matieres nm
  JOIN niveaux  n ON n.id_niveau  = nm.level_id
  JOIN matieres m ON m.id_matiere = nm.subject_id
 WHERE nm.tenant_id = :tenant
   AND m.code_matiere IN ('MATH', 'EN', 'FR')
 ORDER BY n.code, m.code_matiere;

-- Relire le résultat ci-dessus, puis :
-- COMMIT;
-- (ou ROLLBACK; si quoi que ce soit surprend)
