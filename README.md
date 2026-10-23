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

## Compilation

```
mvn package
```

Le jar est `target/SHCookieClicker-1.0.jar`.
