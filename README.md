# SHCookieClicker

Le Cookie Clicker du spawn de SkyHand (Paper 26.3, Java 25).

- **Clic gauche** sur un clicker : des cookies, autant que les cookies par clic du joueur.
- **Clic droit** : le menu, avec la boutique d'améliorations (des cookies par clic en plus, prix qui monte à chaque
  achat ; shift-clic pour dix achats d'un coup), la boutique **VipElite** et les **designs** : bloc vu à la place du
  clicker, effet de coup, fond des menus. Un design s'achète une fois, puis se sélectionne ou se retire.
- Au hasard d'un clic : le **cookie doré** (les clics rapportent le double pendant une minute) ou l'**explosion de
  cookies** (vingt cookies à ramasser, vus du seul joueur, dix fois les cookies par clic chacun).
- **Anti-autoclic** : au-delà de 12 clics par seconde, les clics ne rapportent plus rien jusqu'à ce que le joueur
  ralentisse. **Anti-AFK** : un joueur qui n'a pas changé de bloc depuis 15 secondes ne gagne plus rien.
- Les réglages, les boutiques et les sons sont dans `config.yml` (son en-tête décrit tout), les textes dans
  `messages.yml`, les menus dans `menus.yml`, les clickers dans `locations.yml`.

## Commandes et permissions

| Commande | Permission | |
|---|---|---|
| `/cookies [joueur]` (alias `/cookie`) | `shcookieclicker.use` (tout le monde) | Statistiques. |
| `/shcookieclicker reload` | `shcookieclicker.admin.reload` | Relit les fichiers ; un fichier illisible ne change rien. `base-de-donnees` et `reseau` demandent un redémarrage. |
| `/shcookieclicker list` | `shcookieclicker.admin.list` | Liste des clickers (clic : téléportation). |
| `/shcookieclicker set clicker <n>` | `shcookieclicker.admin.set` | Puis clic droit sur un bloc. |
| `/shcookieclicker remove clicker <n>` | `shcookieclicker.admin.remove` | |
| `/shcookieclicker modify <joueur> <add\|set\|remove> <COOKIES\|PER_CLICK\|CLICKER_CLICKS> <n>` | `shcookieclicker.admin.modify` | |
| `/shcookieclicker reset <joueur>` | `shcookieclicker.admin.reset` | |
| `/shcookieclicker import` | `shcookieclicker.admin.import` | Mode réseau : copie le fichier SQLite dans la base du réseau, sans rien écraser. |

`shcookieclicker.admin` (op) donne toutes les sous-commandes, `shcookieclicker.premium` (op) la boutique VipElite.
L'ancien nom `/cookieclicker` reste un alias de `/shcookieclicker`, et `/shcookieclicker stats [joueur]` marche encore.
Toutes les sous-commandes marchent depuis la console, sauf `set`.

## Placeholders

`%cookieclicker_cookies%`, `%cookieclicker_perclick%`, `%cookieclicker_clickerclicks%` et `%cookieclicker_place%`
(place au classement des cookies, `N/A` sans cookie). La casse du nom ne compte pas. Les nombres sont écrits
`1,011,876,175,853` : ajLeaderboards, QuestCreator (`%shraw_...%`) et les scripts de PlaceholderAPI les relisent tels
quels. Dans les textes du plugin, ils sont écrits `1 011 876 175 853`.

## Données

La base est celle de CookieClicker : `cookieclicker.db` (SQLite) ou MySQL, tables `cookieclicker_players`,
`cookieclicker_shops` et `cookieclicker_migrations`, colonnes inchangées. `cookieclicker_shops.ITEMS_PURCHASED` est le
nombre d'achats d'une amélioration (1 pour un design) et `SHOP_ID` vaut `DEFAULT`, `PREMIUM`, `BLOCK_DESIGN`,
`HIT_PARTICLE_DESIGN` ou `MENU_DESIGN`.

- Toute la base est lue au démarrage (6 280 joueurs en une cinquantaine de millisecondes) ; ensuite la mémoire fait
  foi. Un joueur n'a de profil qu'à partir de sa première action au clicker.
- Les profils modifiés partent par lots, en une transaction, sur un fil à part : toutes les 60 secondes, au départ
  d'un joueur et à l'arrêt. Un clic ne touche jamais la base.
- Les clics arrivent sur le fil du monde du clicker (mondes en parallèle de DivineMC) : chaque profil a son verrou,
  les commandes et ce qui touche à d'autres mondes passent par le fil principal.
- Boutique de CookieClicker 4.8 (table `cookieclicker_players_v4`, SQLite) : reprise une fois, repère `shop-v4`. La 4.8
  gardait le prix courant des 14 améliorations ; il montait de 115 × gain à chaque achat, d'où le nombre d'achats exact.
  Le fond de menu choisi dans la 4.8 devient un fond acheté et sélectionné.

## Mode réseau

Avec **SHNetwork** installé (et `reseau.actif: true`), plusieurs serveurs partagent la progression : elle est dans la
base de la section `reseau`, relue à l'arrivée d'un joueur une fois que l'autre serveur a fini de l'écrire (barrière de
SHNetwork), écrite à son départ. `modify` et `reset` visant un joueur connecté ailleurs lui sont transmis. Le fichier
SQLite se copie une fois avec `/shcookieclicker import`. Sans SHNetwork, rien de tout cela n'est chargé. Tables à créer à
la main (droits limités) : `sql/schema.sql`.

## Têtes

Le design « Votre tête » prend le skin du joueur dans **SHSkins** (facultatif) ; sans lui, le profil reçu à la connexion.
Aucune requête à Mojang.

## Passage de la 1.x à la 2.0

La 2.0 est une réécriture complète. Au premier démarrage, elle passe les fichiers au nouveau format et le dit dans la
console : les réglages de `config.yml` (boutiques, prix, sons, événements, protections, MySQL, mode réseau) sont repris,
`mysql.yml` rejoint `config.yml`, `messages.yml` et `menus.yml` sont remplacés. La base est lue telle quelle.

Retirés car jamais utilisés sur Opale : les murs de statistiques et le chronomètre de réinitialisation (le classement
passe par ajLeaderboards et `/cookietop`), l'événement `CookieClickerInteractEvent`.

## Historique

Jusqu'à la 1.3, ce plugin était une adaptation de CookieClicker de ZilloLP (https://github.com/ZilloLP/CookieClicker),
sous la licence CookieClicker 1.0 (voir l'historique du dépôt). La 2.0 n'en reprend aucun code : seulement les données
(base, positions) et les noms connus des joueurs et des autres plugins (commande, placeholders, permissions).

## Compilation

```
mvn package
```

SHSkins et SHNetwork doivent être dans le dépôt Maven local (`mvn install` dans `SHSkins-Velocity` et `SHNetwork`).
Le test `ServerDatabaseTest` rejoue la reprise et une écriture complète sur la copie d'une vraie base :
`mvn test -Dcookieclicker.db=chemin/vers/cookieclicker.db` (jamais versionnée).

Le jar est `target/SHCookieClicker-2.0.jar`.
