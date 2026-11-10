# SHCookieClicker

Version SkyHand de **CookieClicker par ZilloLP** (https://github.com/ZilloLP/CookieClicker, commit dans
`UPSTREAM-COMMIT`), distribuée sous la licence d'origine (`LICENSE` : usage non commercial, crédit de l'auteur,
dérivés sous la même licence).

## Différences avec l'original

- **Paper 26.3, Java 25, sans NMS.** L'original embarquait un module par version (spigot_1_20_R1 à
  spigot_1_21_R8) compilé contre les noms internes de Spigot, que Paper 26.x ne traduit plus. Tout passe par l'API
  Paper :
  - hologrammes : `PaperHologram`, un TextDisplay non sauvegardé, visible du seul joueur concerné ;
  - clics sur le clicker : `PaperClickReader` (événements Bukkit) au lieu de l'injection netty de `PacketReader` ;
  - crânes du clicker : `Player#sendBlockChange` puis `Player#sendBlockUpdate` ;
  - cookies de l'explosion : invisibles pour les autres joueurs dès leur apparition, ramassage par
    `playPickupItemAnimation` ; leur contour lumineux est blanc (doré dans l'original, par un paquet d'équipe).
- **Données reprises** : au premier démarrage, `plugins/CookieClicker` est copié dans `plugins/SHCookieClicker`
  (l'ancien dossier reste en place). Même base (`cookieclicker_players`, `cookieclicker_shops`), même commande
  `/cookieclicker`, mêmes permissions, mêmes placeholders `%cookieclicker_...%`.
- **Positions de CookieClicker 4.x** (`==: org.bukkit.Location` dans `locations.yml`) relues correctement ;
  l'original les lisait en 0, 0, 0.
- Pas de statistiques bStats (l'identifiant est celui du plugin original).

## Mode réseau

Avec **SHNetwork** installé (et `reseau.actif: true`, valeur par défaut), plusieurs serveurs de jeu partagent la progression des joueurs :

- elle est dans la base MySQL du réseau (tables `cookieclicker_players` et `cookieclicker_shops`, mêmes colonnes qu'avant) au lieu du fichier SQLite du serveur ;
- à la connexion, une fois que l'autre serveur a fini de sauvegarder le joueur, son profil est relu en base ;
- à son départ, son profil est sauvegardé avant que l'autre serveur ne le lise (la barrière de SHNetwork attend cette sauvegarde) ;
- tant que le joueur est sur un serveur, la mémoire de ce serveur fait foi : `/cookieclicker modify` et `/cookieclicker reset` visant un joueur d'un autre serveur lui sont transmis et exécutés là où se trouve son profil ; pour un joueur connecté nulle part, le profil est relu en base avant la modification ;
- la progression du fichier SQLite se copie une fois avec `/cookieclicker import`, sans rien écraser ;
- les clickers sont dans le monde `spawn` : on ne clique que sur le serveur qui l'héberge, les autres serveurs ne font que relire la progression pour les placeholders `%cookieclicker_...%`.

Sans SHNetwork, ou sans la section `reseau` dans la configuration, le plugin se comporte comme avant : SQLite, ou MySQL avec `mysql.yml`. Si la base du réseau est injoignable au démarrage, il reste en mode local. Les tables se créent toutes seules, ou à la main avec `sql/schema.sql`.

Une commande exécutée par la console (par exemple transmise par un autre serveur) fonctionne désormais : l'original faisait un cast en joueur après avoir accepté la console pour `reset` et `remove`.

## Compilation

```
mvn package
```

Le jar est `target/SHCookieClicker-1.0.jar`.
