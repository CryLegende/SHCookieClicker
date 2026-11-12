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
- **Menus au format SkyHand** (`menus.yml`) : titre « Menu | Nom », cadre de vitres noires, accents gris, « Retour » en
  papier au centre de la dernière ligne, flèches de page. Chaque case porte son action : le plugin ne compare plus le
  nom ou le matériau d'un objet pour savoir ce qui a été cliqué (l'original s'en servait, ce qui cassait avec un
  autre texte ou une autre langue) et un achat se fait sur le fil principal, sans possibilité de double dépense.
  Le fond des menus est le design de menu acheté par le joueur, noir par défaut.
- **Sons par clé de son** (`entity.item.pickup`, `minecraft:block.anvil.break`, ou la clé d'un son de pack de
  ressources) : en 26.x les sons sont un registre, plus une énumération. Les anciens noms (`ENTITY_ITEM_PICKUP`) sont
  reconnus ; un nom inconnu est signalé dans la console et le son reste muet au lieu d'être remplacé par un autre.
- **Fichiers** : `config.yml` (réglages, boutiques, sons), `messages.yml` (textes, en français), `menus.yml`, `locations.yml`,
  `mysql.yml`. Une clé ou une section absente d'un fichier du serveur prend la valeur du jar : un `config.yml` de
  CookieClicker 4.8 fonctionne tel quel.
- **Base de données** : tous les joueurs sont lus en deux requêtes au démarrage, la mémoire fait foi ensuite et la
  base n'est touchée que pour écrire, hors du fil principal (sauvegarde périodique, au départ du joueur, à l'arrêt).
  Un fichier SQLite n'a qu'une connexion. Les clics, les menus et les achats ne touchent jamais la base.
- **Données reprises** : au premier démarrage, `cookieclicker.db`, `locations.yml` et `mysql.yml` de
  `plugins/CookieClicker` sont copiés dans `plugins/SHCookieClicker` (l'ancien dossier reste en place). Même base
  (`cookieclicker_players`, `cookieclicker_shops`), mêmes placeholders `%cookieclicker_...%`. La commande
  s'appelle désormais `/shcookieclicker` (l'ancien nom `/cookieclicker` reste un alias) et les permissions portent le préfixe `shcookieclicker.`.
- **Boutique de la 4.8 reprise** : CookieClicker 4.8 enregistrait le prix courant de chacune des 14 améliorations, la 5.9
  le nombre d'achats. Le prix de la 4.8 montait de 115 × cookies par clic à chaque achat, le nombre d'achats se retrouve donc exactement
  (`cookieclicker_players_v4` → `cookieclicker_shops`, une seule fois, repère dans `cookieclicker_migrations`). Les
  prix du jar (`price_step`) reprennent cette règle. Le design de menu choisi dans la 4.8 (gratuit) est repris comme
  design acheté et sélectionné. `ITEMS_PURCHASED` est le nombre d'achats d'un article (1 pour un design acheté).
- **Positions de CookieClicker 4.x** (`==: org.bukkit.Location` dans `locations.yml`) relues correctement ;
  l'original les lisait en 0, 0, 0.
- Pas de statistiques bStats (l'identifiant est celui du plugin original).
- **Têtes des joueurs par SHSkins.** Le mur de statistiques (un classement montre souvent des joueurs hors ligne) et le
  design de bloc « tête du joueur » prennent le skin de chaque joueur dans **SHSkins** (facultatif, `softdepend`). Sur
  le réseau en mode hors ligne, l'UUID d'un joueur est inconnu de Mojang : une tête construite avec son seul pseudo
  afficherait Steve ou Alex. Sans SHSkins, la tête n'a que l'UUID et le pseudo (skin par défaut), sauf celle du joueur
  connecté qui garde le profil reçu à sa connexion. Le plugin ne fait plus aucune requête à Mojang : l'original
  cherchait chaque pseudo auprès de leur API.

## Mode réseau

Avec **SHNetwork** installé (et `reseau.actif: true`, valeur par défaut), plusieurs serveurs de jeu partagent la progression des joueurs :

- elle est dans la base MySQL du réseau (tables `cookieclicker_players` et `cookieclicker_shops`, mêmes colonnes qu'avant) au lieu du fichier SQLite du serveur ;
- à la connexion, une fois que l'autre serveur a fini de sauvegarder le joueur, son profil est relu en base ;
- à son départ, son profil est sauvegardé avant que l'autre serveur ne le lise (la barrière de SHNetwork attend cette sauvegarde) ;
- tant que le joueur est sur un serveur, la mémoire de ce serveur fait foi : `/shcookieclicker modify` et `/shcookieclicker reset` visant un joueur d'un autre serveur lui sont transmis et exécutés là où se trouve son profil ; pour un joueur connecté nulle part, le profil est relu en base avant la modification ;
- la progression du fichier SQLite se copie une fois avec `/shcookieclicker import`, sans rien écraser ;
- les clickers sont dans le monde `spawn` : on ne clique que sur le serveur qui l'héberge, les autres serveurs ne font que relire la progression pour les placeholders `%cookieclicker_...%`.

Sans SHNetwork, ou sans la section `reseau` dans la configuration, le plugin se comporte comme avant : SQLite, ou MySQL avec `mysql.yml`. Si la base du réseau est injoignable au démarrage, il reste en mode local. Les tables se créent toutes seules, ou à la main avec `sql/schema.sql`.

Une commande exécutée par la console (par exemple transmise par un autre serveur) fonctionne désormais : l'original faisait un cast en joueur après avoir accepté la console pour `reset` et `remove`.

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shcookieclicker stats [joueur]` | `/cookieclicker` | Statistiques d'un joueur (un clic sur une tête du mur de statistiques la lance). | aucune |
| `/shcookieclicker list` | idem | Liste les clickers. | `shcookieclicker.admin.list` |
| `/shcookieclicker set clicker <n>` / `resettimer` / `statswall <alltime\|time> <n>` | idem | Pose un clicker, le chronomètre de réinitialisation ou un mur de statistiques (joueur, clic droit sur un bloc). | `shcookieclicker.admin.set` |
| `/shcookieclicker remove clicker <n>` / `resettimer` | idem | Retire un clicker ou le chronomètre. | `shcookieclicker.admin.remove` |
| `/shcookieclicker modify <joueur> <add\|set\|remove> <COOKIES\|PER_CLICK\|CLICKER_CLICKS> <n>` | idem | Modifie la progression d'un joueur. | `shcookieclicker.admin.modify` |
| `/shcookieclicker reset <joueur>` | idem | Réinitialise la progression d'un joueur. | `shcookieclicker.admin.reset` |
| `/shcookieclicker import` | idem | Mode réseau : copie la progression SQLite dans la base du réseau. | `shcookieclicker.admin.import` |
| `/shcookieclicker reload` | idem | Relit `config.yml`, `messages.yml`, `menus.yml`, `locations.yml` et `mysql.yml`. Un fichier illisible ne change rien ; `MySQL`, `mysql.yml` et `reseau` demandent un redémarrage. | `shcookieclicker.admin.reload` |

Sans argument, ou avec une sous-commande inconnue, une ligne d'usage ne liste que les sous-commandes permises à l'expéditeur. Toutes les sous-commandes marchent depuis la console sauf `set`, qui a besoin de la position et du clic du joueur.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shcookieclicker.admin` | Toute l'administration (parent des sept nœuds `shcookieclicker.admin.*`). | `op` |
| `shcookieclicker.admin.reload` `.list` `.set` `.remove` `.modify` `.reset` `.import` | Une sous-commande chacun ; `.remove` permet aussi de casser un mur de statistiques. | `op` |
| `shcookieclicker.premium` | Boutique VipElite. | `op` |

Les noms de permission ne se lisent plus dans `permissions.yml` : ils sont fixes.

## Crédit

CookieClicker est l'œuvre de ZilloLP (https://github.com/ZilloLP/CookieClicker), sous la licence CookieClicker 1.0 (voir `LICENSE`).

## Compilation

```
mvn package
```

SHSkins doit être installé dans le dépôt Maven local avant la compilation : `mvn install` dans `SHSkins-Velocity`.

Le jar est `target/SHCookieClicker-1.3.jar`.
