# Protocole radio JARVIS-RF v1

Ce protocole relie le hub (Raspberry Pi) aux modules Arduino par nRF24L01+. Il est implémenté côté hub dans `com.cegesoft.jarvis.radio`. Les firmwares doivent le respecter **exactement**. Les vecteurs de test de [`protocol/test-vectors.json`](../protocol/test-vectors.json) sont communs aux tests Java et aux tests du firmware.

## Configuration radio (identique sur tous les nœuds)

| Paramètre | Valeur | RF24 (Arduino) |
|---|---|---|
| Canal | `JARVIS_RADIO_CHANNEL`, 0 à 83 (défaut 76) | `setChannel(76)` |
| Débit | 250 kbit/s | `setDataRate(RF24_250KBPS)` |
| Puissance | maximale | `setPALevel(RF24_PA_MAX)` |
| CRC | 16 bits | `setCRCLength(RF24_CRC_16)` |
| Taille des messages | dynamique | `enableDynamicPayloads()` |
| Diffusion sans accusé | activée | `enableDynamicAck()` |
| Accusé de réception matériel | activé | `setAutoAck(true)` |
| Renvois matériels | 15 × 1 ms (5 pour un nœud sur pile) | `setRetries(3, 15)` / `setRetries(3, 5)` |

### Adresses

Une adresse fait 5 octets : l'identifiant de nœud est l'octet de poids faible, et les 4 octets de poids fort sont l'identifiant de réseau (`JARVIS_RADIO_NETWORK_ID`, défaut `0x4A52564E`).

```cpp
uint64_t address(uint8_t node) { return ((uint64_t) 0x4A52564E << 8) | node; }
radio.openReadingPipe(1, address(MY_NODE_ID));    // ses propres messages
radio.openReadingPipe(2, address(0xFF));          // diffusion (seul l'octet de poids faible diffère : OK pour le canal 2)
radio.openWritingPipe(address(destination));
```

Les identifiants de nœud :
- `0x00` est le hub ;
- `0xFF` est la diffusion ;
- `0x01` à `0xFE` sont les modules. L'identifiant est aussi l'identifiant du module dans l'API (texte décimal).

## Trame

En-tête de 5 octets, suivi de 0 à 27 octets de données utiles. La longueur totale est donnée par `getDynamicPayloadSize()`.

```
Octet 0  dst    destinataire final
Octet 1  src    émetteur d'origine
Octet 2  ctrl   b7-6 version (01) | b5 ACK_REQ | b4 IS_REPLY | b3-0 TTL (2 par défaut)
Octet 3  seq    compteur de l'émetteur, recopié dans la réponse
Octet 4  op     code d'opération
Octets 5-31     données utiles, entiers petit-boutistes
```

## Codes d'opération

| op | Nom | Sens | Données utiles |
|---|---|---|---|
| 0x01 | HELLO | nœud → hub | type u8, version firmware u8, capacités u8 (b0 secteur, b1 veille, b2 pile, b3-4 type de pile : 1 = 2 × CR2032 en série, 2 = 2 × CR2032 en parallèle) [, relais u8 ajouté par un relais] |
| 0x02 | WELCOME | hub → nœud (réponse à HELLO) | parent u8 (0 = direct), heartbeat en secondes u16, nœud de secours u8 (0 = aucun) |
| 0x03 | STATUS | nœud → hub | drapeaux u8 (b0 activé, b1 en veille), échecs d'émission u8 depuis le dernier STATUS [, mesure pile 4 octets] |
| 0x04 | STATUS_REQ | hub → nœud | (rien), réponse STATUS |
| 0x05 / 0x06 | PING / PONG | les deux | octets renvoyés tels quels |
| 0x07 | DISCOVER | hub → nœud ou diffusion | (rien) : le nœud renvoie HELLO |
| 0x10 | SET_ENABLED | hub → nœud | u8 0/1, réponse STATUS |
| 0x11 | SET_STATE | hub → nœud | i8 (1 ON, 0 OFF, −1 ON_BACK), réponse STATE |
| 0x12 | PULSE | hub → nœud | (rien), réponse STATE |
| 0x13 | STATE | nœud → hub | i8 (réponse à SET_STATE ou PULSE, ou requête spontanée quand l'état change localement, par exemple via un bouton mural) |
| 0x20 | SWITCH | nœud → hub | u8 0/1 |
| 0x21 | ANALOG | nœud → hub | u16 (0 à 1023) |
| 0x22 | PULSE_EVT | nœud → hub | compteur u16 [, mesure pile 4 octets] |
| 0x7E | NACK | les deux | code u8 : 1 opération inconnue, 2 données invalides, 3 occupé, 4 injoignable |
| 0x7F | ACK | les deux | (rien) |

Types de module (HELLO) :

| Code | Type |
|---|---|
| 1 | relais statique |
| 2 | relais à impulsion (télérupteur) |
| 3 | interrupteur classique |
| 4 | bouton à impulsion |
| 5 | capteur de lumière |

**Mesure pile** (4 octets) :
- `V_repos` u8 et `V_charge` u8, par pas de 25 mV ;
- `V_charge` est mesurée à la fin de la fenêtre d'écoute de 50 ms du réveil **précédent** (environ 15 mA en continu). Avec un pont diviseur, le condensateur doit faire 10 nF, pas 100 nF : la constante de temps doit rester courte devant 50 ms ;
- température interne de l'ATmega i8 en °C ;
- drapeaux u8 : b0 indique un redémarrage après coupure d'alimentation, et doit être mis dans le premier message après le démarrage.

## Fiabilité : règles pour le firmware

1. **Requête :** toute requête (`ACK_REQ`) reçoit une réponse (`IS_REPLY`) avec le **même `seq`**. Ce sont soit des données (STATE, STATUS, PONG), soit ACK, soit NACK. Un module désactivé (SET_ENABLED 0) répond NACK 3 à ses opérations propres.
2. **Doublons :** le nœud garde ses dernières requêtes reçues **par émetteur** (`src`, `seq`, réponse envoyée, heure ; 8 sur le hub, 4 sur un module). Une requête déjà vue depuis moins de 30 s reçoit **la même réponse sans être réexécutée** : un PULSE répété ne fait claquer le relais qu'une fois. Les requêtes du hub sont oubliées à la réception de WELCOME.
3. **Renvois :** sans réponse, le nœud renvoie sa requête **avec le même `seq`** (3 fois au maximum). Le hub fait de même : 4 tentatives au total, avec des attentes de réponse de 200, 500, 1000 puis 1000 ms.
4. **Démarrage :** le nœud envoie HELLO au démarrage, et à chaque DISCOVER. Le hub répond WELCOME.
5. **Nœud sur secteur :**
   - toujours en écoute ;
   - envoie un STATUS toutes les `heartbeat` secondes (60 s) ;
   - le hub le déclare déconnecté après 2,5 × heartbeat sans nouvelles.
6. **Nœud en veille** (capacité b1) :
   - il n'écoute que **50 ms après chacune de ses émissions**, et le hub n'envoie rien en dehors de cette fenêtre ;
   - il se réveille au moins toutes les 12 h (watchdog) pour envoyer un STATUS avec sa mesure de pile.
7. **Relais** (si besoin un jour) : il retransmet toute trame dont `dst` n'est pas lui-même, en décrémentant le TTL ; la trame est abandonnée quand le TTL atteint 0.
8. **Secours :**
   - si le hub ne répond pas à un PULSE_EVT, un bouton qui connaît un nœud de secours (WELCOME) lui envoie PULSE directement ;
   - le hub choisit ce nœud automatiquement : c'est l'actionneur du premier planning déclenché par ce bouton ;
   - si les plannings changent, le hub envoie DISCOVER au bouton pendant sa fenêtre d'écoute, et le nouveau WELCOME lui transmet le nouveau nœud de secours.

## Exemple

Le hub demande au nœud 0x12 de passer à ON, puis le nœud répond :

```
12 00 62 2A 11 01     dst=0x12 src=hub ctrl=v1+ACK_REQ+TTL2 seq=42 SET_STATE ON
00 12 52 2A 13 01     dst=hub src=0x12 ctrl=v1+IS_REPLY+TTL2 seq=42 STATE ON
```
