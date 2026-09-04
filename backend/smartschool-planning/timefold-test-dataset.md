# 🧠 Timefold Solver - Dataset de Test (École Secondaire)

Ce fichier contient un dataset réaliste pour tester un moteur de génération d'emploi du temps basé sur Timefold.

Il est inspiré du modèle de répartition visible dans le document fourni :
- Colonnes par niveau (7ème, 8ème, 9ème)
- Lignes = matières
- Valeurs = pondération / nombre de séances hebdomadaires (format combinatoire type 2+1+1, (2), etc.)
- Gestion des groupes pour TP / TD

---

# 🏫 Structure générale

## 📌 Niveaux et classes

### 7ème année (5 classes)
- 7A1
- 7A2
- 7A3
- 7A4
- 7A5

### 8ème année (4 classes)
- 8A1
- 8A2
- 8A3
- 8A4

### 9ème année (4 classes)
- 9A1
- 9A2
- 9A3
- 9A4

---

# 📚 Matières (communes à tous les niveaux)

- AR : Arabe
- FR : Français
- EN : Anglais
- MATH : Mathématiques
- SCI : Sciences naturelles
- PHY : Physique
- TECH : Technologie
- ISL : التربية الإسلامية
- CIV : التربية المدنية
- SPORT : Éducation physique

---

# 📊 Pattern de charge hebdomadaire (inspiré du modèle image)

## 🟦 Niveau 7ème (7ème année)

```json
{
  "AR": "2+1+1+1",
  "FR": "2+1+1",
  "EN": "1+1+1",
  "MATH": "2+1+1+1",
  "SCI": "1+1+1+1+1",
  "PHY": "1+1+1",
  "TECH": "1+1",
  "ISL": "2+1",
  "CIV": "1",
  "SPORT": "2"
}
{
  "AR": "2+1+1",
  "FR": "2+1+1+1",
  "EN": "1+1+1+1",
  "MATH": "2+1+1+1",
  "SCI": "1+1+1+1",
  "PHY": "1+1+1",
  "TECH": "1+1+1",
  "ISL": "2+1",
  "CIV": "1",
  "SPORT": "2"
}
{
  "AR": "2+1+1",
  "FR": "2+1+1",
  "EN": "1+1+1",
  "MATH": "2+1+1+1",
  "SCI": "1+1+1+1",
  "PHY": "1+1+1+1",
  "TECH": "1+1",
  "ISL": "2+1",
  "CIV": "1",
  "SPORT": "2"
}
pour les repartitions comme 2+1+1 sa represente les nombre d heures pour chaque seance de cette matiere
👥 Gestion des groupes (TRÈS IMPORTANT pour Timefold)

Certaines matières sont divisées en groupes :

🔬 Sciences / Physique / TP
groupIndex = 1, 2 (voire 3 selon effectif)
utilisé pour :
SCI (TP)
PHY (TP)
TECH (TP)
🏃 Sport
groupIndex = 1, 2
rotation obligatoire des groupes
📌 Règles
Un groupe ne peut pas avoir 2 sessions en même temps
Les TP doivent être en salle LAB uniquement
Les COURS peuvent être en salle normale
🏫 Ressources
Rooms
S01 → salle normale
S02 → salle normale
LAB1 → laboratoire sciences/physique
TECH-LAB → laboratoire technologie
SPORT1 → salle de sport
👨‍🏫 Teachers (pool de test)
AR: Arabic teachers (2-3)
FR: French teachers (2-3)
EN: English teachers (2)
MATH: Math teachers (2-3)
SCI: Science teachers (2)
PHY: Physics teachers (2)
TECH: Technology teachers (1-2)
SPORT: PE teachers (2)
⚙️ Timefold Constraints à tester
Hard Constraints
No teacher overlap
No room overlap
No class overlap
Group separation respected
Lab sessions only in LAB rooms
Sport only in SPORT1
Soft Constraints
Minimize gaps in day
Spread subjects evenly
Avoid late sessions for 7th grade
Balance teacher workload
🎯 Objectif de test

Ce dataset est conçu pour tester :

🔁 propagation des contraintes Timefold
🧠 résolution multi-classes
👥 gestion des groupes
🏫 allocation des rooms
👨‍🏫 conflits enseignants
📊 équilibre des charges