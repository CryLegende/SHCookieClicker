-- Tables de SHCookieClicker avec SHNetwork. Le plugin les crée seul au démarrage si l'utilisateur a le droit CREATE ;
-- ce fichier sert à les créer à la main et à ne donner à l'utilisateur que les droits de lecture et d'écriture.
--
-- Utilisation (base bo_net pour les essais multi-Opale) :
--   mysql -u root < sql/schema.sql   puis remplacer CHANGE_ME par le mot de passe de config.yml

CREATE DATABASE IF NOT EXISTS bo_net;

CREATE TABLE IF NOT EXISTS bo_net.cookieclicker_players (
    UUID            VARCHAR(64) NOT NULL,            -- uuid « hors ligne » du joueur, le même sur tous les serveurs
    NAME            VARCHAR(64) NOT NULL,
    COOKIES         BIGINT DEFAULT 0,
    PER_CLICK       BIGINT DEFAULT 0,
    CLICKER_CLICKS  BIGINT DEFAULT 0,
    BLOCK_DESIGN    BIGINT DEFAULT 0,
    PARTICLE_DESIGN BIGINT DEFAULT 0,
    MENU_DESIGN     BIGINT DEFAULT 0,
    PRIMARY KEY (UUID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS bo_net.cookieclicker_shops (
    UUID            VARCHAR(64) NOT NULL,
    SHOP_ID         VARCHAR(64) NOT NULL,
    ID              INTEGER NOT NULL,
    ITEMS_PURCHASED BIGINT NOT NULL,                 -- nombre d'achats de l'article (1 pour un design acheté)
    PRIMARY KEY (UUID, SHOP_ID, ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'shcookieclicker'@'127.0.0.1' IDENTIFIED BY 'CHANGE_ME';
GRANT SELECT, INSERT, UPDATE, DELETE ON bo_net.cookieclicker_players TO 'shcookieclicker'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE ON bo_net.cookieclicker_shops TO 'shcookieclicker'@'127.0.0.1';
