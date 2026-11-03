#!/usr/bin/env bash
# Prepare a Raspberry Pi 3B running Raspberry Pi OS Lite 64-bit as the JARVIS hub :
# Docker, headless performance tuning, robustness (watchdog, logs, swap), and JARVIS started at boot.
#
# Usage, from the cloned repository on the Pi :   sudo ./deploy/raspberrypi/setup.sh
# The script can be run again safely (idempotent).
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TIMEZONE="${TIMEZONE:-Europe/Paris}"
# 0 = install the stack without starting it (e.g. while an older installation still uses ports 80 / 8000)
JARVIS_AUTOSTART="${JARVIS_AUTOSTART:-1}"
TARGET_USER="${SUDO_USER:-$(id -un 1000 2>/dev/null || echo pi)}"

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[!] %s\033[0m\n' "$*"; }

[[ $EUID -eq 0 ]] || { echo "Run with sudo : sudo $0"; exit 1; }
if [[ "$(uname -m)" != "aarch64" ]]; then
    warn "64-bit OS required (found $(uname -m)). Install Raspberry Pi OS Lite (64-bit) with Raspberry Pi Imager."
    exit 1
fi

BOOT_DIR=/boot/firmware
[[ -f $BOOT_DIR/config.txt ]] || BOOT_DIR=/boot
CONFIG_TXT=$BOOT_DIR/config.txt

# ---------------------------------------------------------------- system
# Debian versions out of support are moved to archive.debian.org : their packages disappear from the usual mirrors
# (404). Point APT to the archive (bullseye only for now, the OS upgrade is planned).
CODENAME="$(. /etc/os-release && echo "$VERSION_CODENAME")"
if [[ "$CODENAME" == "bullseye" ]] && grep -qE "^deb http://(deb|security)\.debian\.org" /etc/apt/sources.list; then
    log "Debian $CODENAME is archived : APT sources moved to archive.debian.org"
    cp -n /etc/apt/sources.list /etc/apt/sources.list.before-jarvis || true
    sed -i -E 's#^deb http://deb\.debian\.org/debian #deb http://archive.debian.org/debian #; s#^deb http://security\.debian\.org/debian-security #deb http://archive.debian.org/debian-security #' /etc/apt/sources.list
fi

log "System update"
export DEBIAN_FRONTEND=noninteractive
# Never ask about modified configuration files (keep the local version) : the script may run without terminal
APT_OPTIONS=(-y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold)
apt-get update
apt-get "${APT_OPTIONS[@]}" full-upgrade
apt-get "${APT_OPTIONS[@]}" install ca-certificates curl git openssl

log "Timezone : $TIMEZONE"
timedatectl set-timezone "$TIMEZONE"

# ---------------------------------------------------------------- docker
if ! command -v docker >/dev/null; then
    log "Docker installation"
    curl -fsSL https://get.docker.com | sh
fi
usermod -aG docker "$TARGET_USER"
mkdir -p /etc/docker
if [[ ! -f /etc/docker/daemon.json ]]; then
    # Container logs are capped : the SD card doesn't fill up and wears less.
    cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
EOF
fi
systemctl enable --now docker containerd

# ---------------------------------------------------------------- performance
log "Boot configuration (headless, minimal GPU memory)"
cp -n "$CONFIG_TXT" "$CONFIG_TXT.before-jarvis" || true
# No display on the hub : the KMS graphics driver and its reserved memory are useless.
sed -i 's/^dtoverlay=vc4-kms-v3d/#&/' "$CONFIG_TXT"
sed -i '/^# >>> JARVIS/,/^# <<< JARVIS/d' "$CONFIG_TXT"
cat >> "$CONFIG_TXT" <<'EOF'
# >>> JARVIS (headless hub, managed by deploy/raspberrypi/setup.sh)
[all]
# Minimal GPU memory : ~60 MB more RAM for Linux and the JVM
gpu_mem=16
# The nRF24 radio is on the SPI bus ; Bluetooth and audio are not used
dtparam=spi=on
dtoverlay=disable-bt
dtparam=audio=off
camera_auto_detect=0
display_auto_detect=0
# Hardware watchdog (used by systemd, see below)
dtparam=watchdog=on
# <<< JARVIS
EOF

log "CPU at full speed from boot (performance governor, no overclock)"
cat > /etc/systemd/system/cpu-performance.service <<'EOF'
[Unit]
Description=Set the CPU frequency governor to performance
After=multi-user.target raspi-config.service

[Service]
Type=oneshot
ExecStart=/bin/sh -c 'for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > "$g"; done'
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now cpu-performance.service

log "Disabling unused services"
for service in bluetooth hciuart triggerhappy ModemManager cups cups-browsed; do
    systemctl disable --now "$service.service" 2>/dev/null || true
done

# Wi-Fi configured with wpa_supplicant.conf and dhcpcd : Raspberry Pi's dhcpcd5 started wpa_supplicant on wlan0,
# Debian's dhcpcd (trixie and later) doesn't. The per-interface service does it instead.
if [[ -f /etc/wpa_supplicant/wpa_supplicant.conf ]] && systemctl is-enabled dhcpcd >/dev/null 2>&1 \
        && ! ls /lib/dhcpcd/dhcpcd-hooks/ 2>/dev/null | grep -q wpa_supplicant; then
    log "Wi-Fi : wpa_supplicant started on wlan0"
    [[ -e /etc/wpa_supplicant/wpa_supplicant-wlan0.conf ]] || ln -s wpa_supplicant.conf /etc/wpa_supplicant/wpa_supplicant-wlan0.conf
    systemctl enable --now wpa_supplicant@wlan0.service
fi

log "Swap in compressed RAM (zram) instead of the SD card"
if dpkg -s rpi-swap >/dev/null 2>&1; then
    echo "Swap already managed by rpi-swap (zram) : nothing to do."
else
    if command -v dphys-swapfile >/dev/null; then
        dphys-swapfile swapoff || true
        systemctl disable --now dphys-swapfile.service || true
    fi
    apt-get "${APT_OPTIONS[@]}" install zram-tools
fi
if [[ -f /etc/default/zramswap ]]; then
    sed -i -E 's/^#?ALGO=.*/ALGO=zstd/; s/^#?PERCENT=.*/PERCENT=50/' /etc/default/zramswap
    systemctl restart zramswap.service || true
fi

# ---------------------------------------------------------------- robustness
log "Systemd journal capped, hardware watchdog"
mkdir -p /etc/systemd/journald.conf.d /etc/systemd/system.conf.d
cat > /etc/systemd/journald.conf.d/jarvis.conf <<'EOF'
[Journal]
SystemMaxUse=50M
EOF
# If the kernel hangs, the Pi reboots by itself (the BCM2835 watchdog supports 15s at most).
cat > /etc/systemd/system.conf.d/watchdog.conf <<'EOF'
[Manager]
RuntimeWatchdogSec=15s
RebootWatchdogSec=2min
EOF

# ---------------------------------------------------------------- jarvis
log "JARVIS configuration ($REPO_DIR)"
cd "$REPO_DIR"
mkdir -p data/Module
chown -R 1000:1000 data
if [[ ! -f .env ]]; then
    cp .env.example .env
    API_KEY="$(openssl rand -hex 24)"
    sed -i "s/^JARVIS_API_KEYS=.*/JARVIS_API_KEYS=$API_KEY/" .env
    chown "$TARGET_USER" .env
    chmod 600 .env
    warn "New API key generated in $REPO_DIR/.env : $API_KEY"
fi

# First web app account : a random password, shown once (stored hashed by JARVIS at its first start)
if [[ ! -f data/Auth/users.json ]] && ! grep -q '^JARVIS_ADMIN_PASSWORD=.' .env; then
    ADMIN_PASSWORD="$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-16)"
    grep -q '^JARVIS_ADMIN_PASSWORD=' .env \
        && sed -i "s/^JARVIS_ADMIN_PASSWORD=.*/JARVIS_ADMIN_PASSWORD=$ADMIN_PASSWORD/" .env \
        || echo "JARVIS_ADMIN_PASSWORD=$ADMIN_PASSWORD" >> .env
    warn "Web app account : $(grep '^JARVIS_ADMIN_USER=' .env | cut -d= -f2 || echo admin) / $ADMIN_PASSWORD (change it in Réglages)"
fi

# Groups owning /dev/spidev* and /dev/gpiochip* : the container joins them (docker-compose.yml, group_add)
set_env() { grep -q "^$1=" .env && sed -i "s/^$1=.*/$1=$2/" .env || echo "$1=$2" >> .env; }
set_env SPI_GID "$(getent group spi | cut -d: -f3)"
set_env GPIO_GID "$(getent group gpio | cut -d: -f3)"

log "Building the JARVIS and web app images (a few minutes on a Pi 3B)"
docker compose build

log "JARVIS started at boot"
cat > /etc/systemd/system/jarvis.service <<EOF
[Unit]
Description=JARVIS home automation hub (docker compose)
Requires=docker.service
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$REPO_DIR
ExecStart=/usr/bin/docker compose up -d --remove-orphans
# Graceful stop : JARVIS saves the modules and schedules before exiting
ExecStop=/usr/bin/docker compose stop
TimeoutStartSec=0
TimeoutStopSec=60

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
REBOOT_NEEDED=0
[[ -e /dev/spidev0.0 ]] || REBOOT_NEEDED=1 # SPI is enabled at the next boot
if [[ "$JARVIS_AUTOSTART" == "1" ]]; then
    systemctl enable jarvis.service
    [[ $REBOOT_NEEDED -eq 0 ]] && systemctl restart jarvis.service # otherwise JARVIS starts at the next boot
else
    systemctl disable jarvis.service 2>/dev/null || true
    warn "Stack installed but not started (JARVIS_AUTOSTART=0). To switch to it : sudo systemctl enable --now jarvis"
fi

log "Automatic updates from git (branch ${JARVIS_BRANCH:-master}, every 15 min)"
# A launcher rather than a link : it works even if git didn't keep the executable bit of the script
rm -f /usr/local/sbin/jarvis-update
cat > /usr/local/sbin/jarvis-update <<EOF
#!/bin/sh
exec /bin/bash "$REPO_DIR/deploy/raspberrypi/jarvis-update.sh" "\$@"
EOF
chmod 755 /usr/local/sbin/jarvis-update
cat > /etc/systemd/system/jarvis-update.service <<EOF
[Unit]
Description=Update JARVIS from git (pull, rebuild, restart)
Requires=docker.service
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
Environment=JARVIS_BRANCH=${JARVIS_BRANCH:-master}
ExecStart=/usr/local/sbin/jarvis-update
# Low priority : the hub stays responsive while the images are rebuilt
Nice=10
IOSchedulingClass=idle
EOF
cat > /etc/systemd/system/jarvis-update.timer <<'EOF'
[Unit]
Description=Check for JARVIS updates every 15 minutes

[Timer]
OnBootSec=10min
OnUnitActiveSec=15min
RandomizedDelaySec=60

[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
if [[ -d "$REPO_DIR/.git" ]]; then
    systemctl enable --now jarvis-update.timer
else
    warn "$REPO_DIR is not a git clone : automatic updates disabled (git clone https://github.com/desjonqc/jarvis.git)"
fi

log "Done"
echo "JARVIS API : http://$(hostname).local:8000/health   Web app : http://$(hostname).local/"
echo "Logs : docker compose -f $REPO_DIR/docker-compose.yml logs -f jarvis"
if [[ $REBOOT_NEEDED -eq 1 ]]; then
    warn "Reboot to enable SPI and start JARVIS : sudo reboot"
else
    warn "Reboot to apply the boot configuration : sudo reboot"
fi
echo "Choose a free radio channel (stack stopped) : sudo systemctl stop jarvis && docker compose run --rm jarvis scan"
