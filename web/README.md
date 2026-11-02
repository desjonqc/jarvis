# JARVIS — appli web

Interface de pilotage de la maison, dans le style de l'app Maison d'Apple : modules par pièce, automatisations
(les liens entre modules), alertes et réglages du compte.

React 19 + TypeScript (Vite), Tailwind CSS v4, TanStack Query, React Router.

## Développer

Il faut un hub JARVIS qui tourne, par exemple avec la radio simulée (depuis la racine du dépôt) :

```bash
mvn package
JARVIS_RADIO_DRIVER=simulated JARVIS_ADMIN_USER=moi JARVIS_ADMIN_PASSWORD=motdepasse java -jar target/JARVIS.jar
```

Puis, dans `web/` :

```bash
npm install
npm run dev        # http://localhost:5173, l'API est relayée vers http://localhost:8000
```

Pour viser un autre hub : `JARVIS_HUB=http://jarvis.local:8000 npm run dev`.

| Commande | Rôle |
|----------|------|
| `npm run build` | Vérifie les types puis construit `dist/` |
| `npm run lint` | oxlint |
| `npm run preview` | Sert le build localement |

## Fonctionnement

- Toutes les requêtes partent vers `/api/...`, sur la même origine que la page. En production, c'est Caddy qui relaie
  vers le hub (`deploy/Caddyfile`). En développement, c'est le proxy de Vite.
- **Connexion** : `POST /api/auth/login` ouvre une session. Le cookie `jarvis_session` est `HttpOnly` et
  `SameSite=Strict`, donc illisible par le JavaScript et jamais envoyé par un autre site. Une réponse 401 renvoie vers
  l'écran de connexion.
- Les modules sont relus toutes les 5 s, les alertes toutes les 15 s.
- Les automatisations sont les plannings du hub (`/schedules`). L'appli y ajoute `id`, `name` et `enabled`, que le
  hub conserve tels quels (`enabled: false` met une automatisation en pause).

## Arborescence

```
src/
  api/          client HTTP, endpoints et types du hub
  auth/         contexte de session (connexion, déconnexion, expiration)
  hooks/        requêtes et mutations (TanStack Query)
  components/   tuiles, feuilles de détails, éditeur d'automatisation, UI de base (ui/)
  pages/        Maison, Automatisation, Alertes, Réglages, Connexion
  lib/          libellés et descriptions en français
```

En production, l'image `web/Dockerfile` construit l'appli et la sert avec Caddy (voir `docker-compose.yml`).
