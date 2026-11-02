# Déploiement sur Raspberry Pi 3B

JARVIS tourne dans Docker, avec deux conteneurs :

| Conteneur | Rôle | Port |
|-----------|------|------|
| `jarvis`  | Hub Java : radio nRF24 (SPI) vers les modules + API REST | 8000 |
| `web`     | Caddy : sert l'appli web (construite depuis `./web`) et relaie l'API sous `/api` | 80 |

Au démarrage du Pi, le service systemd `jarvis.service` lance la stack. À l'arrêt, il l'arrête proprement : JARVIS sauvegarde alors ses modules et ses plannings.

## 1. Préparer la carte SD

Avec [Raspberry Pi Imager](https://www.raspberrypi.com/software/) :
- **OS** : *Raspberry Pi OS Lite (64-bit)*. Pas de bureau, et 64 bits est obligatoire.
- **Réglages avancés** (roue dentée) :
  - nom d'hôte `jarvis` ;
  - SSH activé ;
  - ton utilisateur ;
  - le Wi-Fi si besoin. L'Ethernet reste plus fiable pour un hub.

## 2. Installer

```bash
ssh <utilisateur>@jarvis.local
sudo apt-get install -y git
git clone https://github.com/desjonqc/jarvis.git ~/jarvis
cd ~/jarvis
sudo ./deploy/raspberrypi/setup.sh
sudo reboot
```

Le script peut être relancé sans risque. Voici ce qu'il fait :

- **Docker** : installation, logs des conteneurs plafonnés (10 Mo × 3).
- **Performance** :
  - le CPU reste à sa fréquence maximale (1,2 GHz) dès le démarrage, grâce au mode `performance` ;
  - aucun overclock ;
  - la mémoire GPU est réduite à 16 Mo, le pilote graphique est désactivé, ainsi que le Bluetooth et l'audio ;
  - le bus SPI est activé pour la radio (pris en compte au redémarrage).
- **Swap compressé en RAM** (zram) au lieu de la carte SD, plus rapide et sans usure de la carte.
- **Robustesse** :
  - le watchdog matériel redémarre le Pi s'il se bloque ;
  - le journal systemd est plafonné à 50 Mo.
- **JARVIS** :
  - création de `.env` avec une **nouvelle clé d'API** générée au hasard ;
  - **compte de l'appli web** : un mot de passe est tiré au hasard et affiché une fois (identifiant `admin` par défaut) ;
  - construction des images JARVIS et appli web (quelques minutes sur un Pi 3B) ;
  - activation de `jarvis.service`.

> **Matériel conseillé** : l'alimentation officielle 5 V / 2,5 A et un dissipateur. Pour vérifier que le Pi ne ralentit pas (sous-tension ou surchauffe), `vcgencmd get_throttled` doit répondre `throttled=0x0`.

## 3. Reprendre les données de l'ancienne installation

Copie `modules.json` et `schedules.json` (l'ancien dossier `JARVIS/Module/` sous Windows) dans `~/jarvis/data/Module/`, puis lance `sudo systemctl restart jarvis`. Le format n'a pas changé.

## 4. L'appli web

Elle est construite dans l'image `web` (source : [`web/`](../web/README.md)) et servie sur `http://jarvis.local/`. Elle appelle l'API sur la même origine, via `/api/...`.

- **Connexion** : avec le compte affiché par `setup.sh`. Change son mot de passe dans *Réglages*, puis retire `JARVIS_ADMIN_PASSWORD` de `.env` : le mot de passe est enregistré haché dans `data/Auth/users.json`.
- **Ajouter un compte ou réinitialiser un mot de passe** :
  ```bash
  docker compose run --rm jarvis user <nom>
  ```
- **Sessions** : « Rester connecté » garde la session 30 jours sans utilisation (`JARVIS_SESSION_DAYS`). Sinon, elle se ferme avec le navigateur. *Réglages → Appareils connectés* liste les sessions et permet de les fermer. Changer de mot de passe ferme toutes les autres sessions. Après 5 mots de passe faux, chaque nouvel essai sur le compte doit attendre (30 s, puis 1 min, 2 min…).
- **Scripts** : la clé d'API de `.env` (`JARVIS_API_KEYS`, en-tête `apikey`) reste acceptée sur le port 8000. Si aucun script ne s'en sert, ferme ce port avec `JARVIS_API_PUBLISH=127.0.0.1:8000`.
- Les **alertes** (module muet, pile faible ou à remplacer, signal faible, radio arrêtée) sont gardées dans `data/alerts.json`.

## Commandes utiles

```bash
cd ~/jarvis
docker compose logs -f jarvis        # logs en direct
docker compose ps                    # état et healthcheck
curl http://localhost:8000/health    # {"status":"ok","radio":true,...}
sudo systemctl stop jarvis && docker compose run --rm jarvis scan   # choisir un canal radio libre
sudo systemctl restart jarvis        # redémarrer la stack
git pull && docker compose up -d --build   # mettre à jour
```

## Configuration (`.env`)

| Variable | Défaut | Rôle |
|----------|--------|------|
| `JARVIS_ADMIN_USER` / `JARVIS_ADMIN_PASSWORD` | `admin` / *(vide)* | Compte de l'appli web créé au premier démarrage, s'il n'en existe aucun |
| `JARVIS_SESSION_DAYS` | `30` | Durée sans utilisation d'une session « Rester connecté » |
| `JARVIS_COOKIE_SECURE` | `false` | `true` si l'appli est servie en HTTPS : le cookie de session ne part alors jamais en HTTP |
| `JARVIS_API_KEYS` | *(vide)* | Clés d'API des scripts, séparées par des virgules |
| `JARVIS_RADIO_DRIVER` | `nrf24` | `nrf24` (radio réelle) ou `simulated` (sans matériel, pour développer) |
| `JARVIS_RADIO_CHANNEL` | `76` | Canal 0 à 83 (bande européenne). **Le même dans les firmwares** |
| `JARVIS_RADIO_DATA_RATE` | `250KBPS` | `250KBPS` (meilleure portée, nRF24L01+ partout), `1MBPS`, `2MBPS` |
| `JARVIS_RADIO_PA_LEVEL` | `MAX` | Puissance d'émission : `MIN`, `LOW`, `HIGH`, `MAX` |
| `JARVIS_RADIO_NETWORK_ID` | `4A52564E` | 4 octets communs à toutes les adresses radio (hexadécimal). **Le même dans les firmwares** |
| `JARVIS_RADIO_CE_GPIO` / `_IRQ_GPIO` | `22` / `24` | Broches CE et IRQ (numéros BCM, `-1` si IRQ non câblée) |
| `JARVIS_COMMAND_TIMEOUT_MS` | `12000` | Attente maximale de la réponse d'un module à une requête API |
| `JARVIS_AUTOSAVE_INTERVAL_MS` | `300000` | Sauvegarde périodique (en plus de celle faite à l'arrêt) |
| `JARVIS_CORS_ORIGIN` | `*` | Origine autorisée pour les appels directs au port 8000 |
| `TZ` | `Europe/Paris` | Fuseau horaire, utilisé par les plannings horaires |

## Câblage de la radio

nRF24L01+ PA+LNA sur le connecteur du Pi 3B, avec un condensateur de 10 à 100 µF soudé entre VCC et GND au plus près du module :

| nRF24L01+ | Raspberry Pi 3B (broche physique) |
|---|---|
| GND | 6 |
| VCC | 17 (3,3 V) |
| CE | 15 (GPIO22) |
| CSN | 24 (GPIO8 / CE0) |
| SCK | 23 (GPIO11) |
| MOSI | 19 (GPIO10) |
| MISO | 21 (GPIO9) |
| IRQ | 18 (GPIO24) |

Au démarrage, JARVIS vérifie que la radio répond. En cas de problème, les logs indiquent la cause : câblage SPI ou alimentation, puce qui n'est pas un L01+ (250 kbit/s impossible), ou SPI non activé.

Le protocole radio (pour les firmwares) est décrit dans [docs/protocol.md](../docs/protocol.md).

## Sécurité

L'appli et l'API ne sont pas chiffrées (HTTP) : le mot de passe circule en clair sur le réseau local. Laisse le Pi sur ton réseau local et **n'ouvre pas** les ports 80 et 8000 sur ta box. Pour piloter la maison depuis l'extérieur, passe par un VPN, par exemple Tailscale ou WireGuard.
