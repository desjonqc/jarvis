# JARVIS
This project was my hobby during the pandemic. It is a home automation project that allow multiple IoT devices to communicate to each other and to be controlled by a web app. The main use case of it was a remote light switch.

JARVIS is the hub : it drives a nRF24L01+ radio wired on the Raspberry Pi's GPIO header to talk to the modules
(binary protocol, see [docs/protocol.md](docs/protocol.md)), runs the schedules, raises alerts (module silent, low
battery or signal) and exposes a REST API used by the web app.

The web app ([web/](web/README.md), React + TypeScript + Tailwind) controls the modules, shows the alerts and edits the
automations, behind a login (sessions stored by the hub).

## Run

Requirements : Java 25, Maven.

```bash
mvn package
# without radio hardware : a simulated network (node 2 = relay, node 3 = button, press it with /sim/press?id=3)
JARVIS_ADMIN_USER=me JARVIS_ADMIN_PASSWORD=<password> JARVIS_RADIO_DRIVER=simulated java -jar target/JARVIS.jar
# then the web app : cd web && npm install && npm run dev
```

Web app accounts : the first one comes from `JARVIS_ADMIN_USER` / `JARVIS_ADMIN_PASSWORD`, more can be added (or a
password reset) with `java -jar target/JARVIS.jar user <name>`.

The configuration is read from environment variables, see [.env.example](.env.example).

## Deploy on a Raspberry Pi

Docker + automatic start at boot : see [deploy/README.md](deploy/README.md) (in French).
