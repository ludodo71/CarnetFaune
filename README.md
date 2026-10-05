# Carnet Faune — Android — version 3 « terrain »

Carnet naturaliste personnel pour les oiseaux, mammifères, rongeurs et reptiles de France métropolitaine.

## Fonctions
- Tableau type tableur : espèces en lignes, lieux en colonnes.
- Plusieurs passages par cellule : aucune observation n'est écrasée.
- Catalogue synchronisable TAXREF/GBIF, avec recherche et miniatures.
- Fiche espèce avec nom français, scientifique, groupe et identifiant.
- Lieux personnalisés : habitat et GPS facultatif.
- Observation : date, heure, effectif, température, météo, habitat, comportement, sexe, stade/âge, notes et photo.
- Historique complet d'une espèce dans un lieu.
- Analyses : activité par heure, filtres par lieu et espèce.
- Calendrier chronologique des observations.
- Galerie des photos prises/associées aux observations.
- Carte : ouverture des lieux géolocalisés dans l'application cartographique Android disponible.
- Export CSV pour Excel/LibreOffice/Google Sheets.
- Base Room locale : les données restent accessibles hors connexion.
- Migration de base prévue pour conserver les données lors des mises à jour.
- Préparation à la sauvegarde/restauration du carnet et à l'export complet.
- Raccourcis Android : appui long sur l'icône pour « Nouvelle observation », « Carte » ou « Statistiques ».

## Raccourci Android
Après installation, l'application apparaît normalement dans le lanceur Android comme toute application. Un appui long sur son icône affiche les raccourcis déclarés par `res/xml/shortcuts.xml`. « Nouvelle observation » ouvre directement le carnet, « Carte » ouvre la carte et « Statistiques » ouvre les analyses.

## Installation
1. Ouvrir le dossier dans Android Studio récent.
2. Synchroniser Gradle.
3. Installer sur un appareil Android 8 (API 26) ou supérieur.
4. Ouvrir « Espèces » et synchroniser le catalogue une première fois avec Internet.
5. Pour le GPS, autoriser la localisation uniquement si elle est souhaitée.

## Sources et licences
TAXREF est le référentiel taxonomique national de l'INPN/MNHN. Les médias sont recherchés via GBIF et leurs crédits/licences doivent être respectés selon le fournisseur.

## Limite de production
La synchronisation actuelle utilise TAXREF distribué via GBIF et sélectionne les groupes demandés. Pour une version exhaustive et strictement métropolitaine, il faut embarquer une extraction TAXREF métropole versionnée et appliquer le filtre biogéographique métropolitain à la génération du catalogue.
