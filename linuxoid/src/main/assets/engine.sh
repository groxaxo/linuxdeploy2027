# Linuxoid deployment engine. New implementation; not sourced from Linux Deploy.
fail() { echo "$*" >&2; exit 1; }
owned() {
    [ "$(cat "$BASE/.linuxoid-owner" 2>/dev/null)" = "$ID" ] || fail 'Profile ownership marker is missing.'
    [ "$(readlink -f "$ROOT")" = "$ROOT" ] || fail 'Unexpected profile filesystem path.'
}
mounted() { awk -v path="$1" '$2 == path { found=1 } END { exit !found }' /proc/mounts; }
inside() { "$BB" chroot "$ROOT" /usr/bin/env -i HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin TERM=xterm-256color DEBIAN_FRONTEND=noninteractive "$@"; }
mount_container() {
    owned
    # A private bind mount permits setuid inside Ubuntu without changing Android /data.
    mounted "$ROOT" || "$OPS" bind "$ROOT"
    "$OPS" private "$ROOT"
    "$OPS" suid "$ROOT"
    mkdir -p "$ROOT/proc" "$ROOT/sys" "$ROOT/dev" "$ROOT/run"
    mounted "$ROOT/proc" || mount -t proc -o nosuid,nodev,noexec proc "$ROOT/proc"
    if ! mounted "$ROOT/sys"; then
        mount -t sysfs -o ro,nosuid,nodev,noexec sysfs "$ROOT/sys" || echo 'sysfs is unavailable on this kernel; continuing without it.'
    fi
    mounted "$ROOT/dev" || mount -t tmpfs -o mode=755,nosuid,size=16m tmpfs "$ROOT/dev"
    for node in null:1:3 zero:1:5 full:1:7 random:1:8 urandom:1:9 tty:5:0; do
        name=${node%%:*}; values=${node#*:}; major=${values%%:*}; minor=${values#*:}
        [ -e "$ROOT/dev/$name" ] || mknod -m 666 "$ROOT/dev/$name" c "$major" "$minor"
    done
    mkdir -p "$ROOT/dev/pts" "$ROOT/dev/shm"
    mounted "$ROOT/dev/pts" || mount -t devpts -o newinstance,ptmxmode=0666,mode=620,gid=5 devpts "$ROOT/dev/pts"
    mounted "$ROOT/dev/shm" || mount -t tmpfs -o mode=1777,nosuid,nodev,size=128m tmpfs "$ROOT/dev/shm"
    ln -snf pts/ptmx "$ROOT/dev/ptmx"
    ln -snf /proc/self/fd "$ROOT/dev/fd"
    ln -snf /proc/self/fd/0 "$ROOT/dev/stdin"
    ln -snf /proc/self/fd/1 "$ROOT/dev/stdout"
    ln -snf /proc/self/fd/2 "$ROOT/dev/stderr"
    mounted "$ROOT/run" || mount -t tmpfs -o mode=755,nosuid,nodev,size=32m tmpfs "$ROOT/run"
}
stop_container() {
    owned
    echo 'Stopping processes in this Ubuntu workspace'
    for proc in /proc/[0-9]*; do
        [ "$(readlink "$proc/root" 2>/dev/null)" = "$ROOT" ] || continue
        kill -TERM "${proc##*/}" 2>/dev/null || true
    done
    sleep 1
    for proc in /proc/[0-9]*; do
        [ "$(readlink "$proc/root" 2>/dev/null)" = "$ROOT" ] || continue
        kill -KILL "${proc##*/}" 2>/dev/null || true
    done
    # Old shared trees can expose another mount at the same path after unmount.
    # Rescan after each pass. Never detach lazily or erase a live rootfs.
    for attempt in 1 2 3 4 5 6 7 8; do
        points=$(awk -v root="$ROOT" '$2==root || index($2, root "/")==1 { print $2 }' /proc/mounts | sort -ru)
        [ -n "$points" ] || break
        for point in $points; do
            umount "$point" 2>/dev/null || true
        done
    done
    remaining=$(awk -v root="$ROOT" '$2==root || index($2, root "/")==1 { print $2 }' /proc/mounts)
    [ -z "$remaining" ] || fail "Could not release workspace mounts; data retained: $remaining"
    echo 'Stopped. All workspace mounts released.'
}
install_container() {
    [ ! -e "$BASE" ] || fail 'Installation path already exists. Create a new workspace; existing data is never overwritten.'
    mkdir -p /data/local/linuxoid
    chmod 700 /data/local/linuxoid
    mkdir -m 700 "$BASE"
    printf '%s' "$ID" > "$BASE/.linuxoid-owner"
    mkdir -m 755 "$ROOT"
    trap 'result=$?; if [ "$result" -ne 0 ]; then echo "Installation incomplete; retaining files for diagnosis."; stop_container || true; fi; exit "$result"' EXIT
    echo 'Extracting verified Ubuntu Base'
    "$BB" tar xzpf "$ARCHIVE" -C "$ROOT"
    "$BB" chroot "$ROOT" /bin/true || fail 'Ubuntu cannot execute on this phone. Check the CPU architecture and Android kernel.'
    mount_container
    suite=$(inside /bin/sh -c '. /etc/os-release; printf "%s" "$VERSION_CODENAME"')
    case "$suite" in ''|*[!a-z]*) fail 'Invalid Ubuntu suite metadata.';; esac
    mirror=http://archive.ubuntu.com/ubuntu
    security=http://security.ubuntu.com/ubuntu
    case "$ARCH" in arm64|armhf) mirror=http://ports.ubuntu.com/ubuntu-ports; security=$mirror;; esac
    # Only pristine files from Ubuntu Base are changed here.
    rm -f "$ROOT/etc/resolv.conf" "$ROOT/etc/apt/sources.list"
    for source in "$ROOT"/etc/apt/sources.list.d/*.sources "$ROOT"/etc/apt/sources.list.d/*.list; do
        [ ! -e "$source" ] || rm -f "$source"
    done
    printf 'nameserver %s\n' "$DNS" > "$ROOT/etc/resolv.conf"
    printf 'deb %s %s main universe\ndeb %s %s-updates main universe\ndeb %s %s-security main universe\n' "$mirror" "$suite" "$mirror" "$suite" "$security" "$suite" > "$ROOT/etc/apt/sources.list"
    cat > "$ROOT/etc/apt/apt.conf.d/99linuxoid" <<'APT'
APT::Sandbox::Seccomp "false";
APT::Sandbox::User "root";
Acquire::Retries "2";
Acquire::http::Timeout "30";
Acquire::https::Timeout "30";
APT
    printf '#!/bin/sh\nexit 101\n' > "$ROOT/usr/sbin/policy-rc.d"
    chmod 755 "$ROOT/usr/sbin/policy-rc.d"
    echo 'Refreshing Ubuntu repositories'
    inside apt-get update -o APT::Update::Error-Mode=any
    packages='ca-certificates sudo'
    [ "$MODE" = MINIMAL ] || packages="$packages openssh-server"
    [ "$MODE" != DESKTOP ] || packages="$packages lxde-core lxterminal tigervnc-standalone-server tigervnc-tools dbus-x11 fonts-dejavu-core adwaita-icon-theme"
    echo 'Installing selected workspace packages'
    inside apt-get install -y --no-install-recommends $packages
    inside apt-get clean
    inside groupadd -g 3003 -o android_inet
    inside groupadd -g 60000 linuxoid
    inside useradd -u 60000 -g 60000 -m -s /bin/bash -G android_inet linuxoid
    printf 'linuxoid ALL=(ALL:ALL) NOPASSWD:ALL\n' > "$ROOT/etc/sudoers.d/linuxoid"
    chmod 440 "$ROOT/etc/sudoers.d/linuxoid"
    printf 'linuxoid\n' > "$ROOT/etc/hostname"
    printf '127.0.0.1 localhost linuxoid\n::1 localhost\n' > "$ROOT/etc/hosts"
    if [ "$MODE" != MINIMAL ]; then
        printf 'linuxoid:%s\n' "$PASSWORD" | inside chpasswd
        inside ssh-keygen -A
        cat > "$ROOT/etc/ssh/sshd_config" <<SSH
Port $SSH_PORT
ListenAddress $LISTEN
PidFile /run/linuxoid-sshd.pid
PasswordAuthentication yes
PermitRootLogin no
UsePAM yes
Subsystem sftp /usr/lib/openssh/sftp-server
SSH
    fi
    if [ "$MODE" = DESKTOP ]; then
        mkdir -p "$ROOT/home/linuxoid/.config/tigervnc"
        printf '%s\n' "$PASSWORD" | inside /usr/bin/tigervncpasswd -f > "$ROOT/home/linuxoid/.config/tigervnc/passwd"
        chmod 600 "$ROOT/home/linuxoid/.config/tigervnc/passwd"
        printf '#!/bin/sh\nunset SESSION_MANAGER DBUS_SESSION_BUS_ADDRESS\nexec dbus-run-session -- startlxde\n' > "$ROOT/home/linuxoid/.config/tigervnc/xstartup"
        chmod 755 "$ROOT/home/linuxoid/.config/tigervnc/xstartup"
        mkdir -p "$ROOT/home/linuxoid/.config/autostart" "$ROOT/home/linuxoid/.config/pcmanfm/LXDE" "$ROOT/tmp/.X11-unix"
        chmod 1777 "$ROOT/tmp/.X11-unix"
        mkdir -p "$ROOT/home/linuxoid/.config/lxsession/LXDE"
        awk '
            /^\[Session\]$/ { print; print "polkit/command=none"; next }
            /^\[GTK\]$/ { print; print "sNet/IconThemeName=Adwaita"; next }
            /^polkit\/command=|^sNet\/IconThemeName=/ { next }
            { print }
        ' "$ROOT/etc/xdg/lxsession/LXDE/desktop.conf" > "$ROOT/home/linuxoid/.config/lxsession/LXDE/desktop.conf"
        inside install -d -m 755 -o linuxoid -g linuxoid /home/linuxoid/Desktop /home/linuxoid/Documents /home/linuxoid/Downloads
        # A chroot has no logind session. Suppress the agent that otherwise opens
        # a modal "No session for pid" error; administration uses sudo.
        printf '[Desktop Entry]\nType=Application\nName=LXPolKit\nHidden=true\n' > "$ROOT/home/linuxoid/.config/autostart/lxpolkit.desktop"
        cat > "$ROOT/home/linuxoid/.config/pcmanfm/LXDE/desktop-items-0.conf" <<'DESKTOP'
[*]
wallpaper_mode=color
wallpaper_common=1
wallpaper=
desktop_bg=#214d38
desktop_fg=#f6f7f2
desktop_shadow=#172a1f
desktop_font=Sans 12
show_wm_menu=1
show_documents=0
show_trash=1
show_mounts=0
DESKTOP
        chmod 700 "$ROOT/home/linuxoid/.config/tigervnc"
        inside chown -R linuxoid:linuxoid /home/linuxoid/.config
    fi
    printf '%s\n' ready > "$BASE/.ready"
    trap - EXIT
    echo 'Ubuntu workspace installed.'
}
start_container() {
    owned
    [ -f "$BASE/.ready" ] || fail 'This workspace has not completed installation.'
    mount_container
    if [ "$MODE" != MINIMAL ]; then
        mkdir -p "$ROOT/run/sshd"
        chmod 755 "$ROOT/run/sshd"
        inside chown root:root /run/sshd
        if [ ! -f "$ROOT/run/linuxoid-sshd.pid" ] || ! kill -0 "$(cat "$ROOT/run/linuxoid-sshd.pid")" 2>/dev/null; then
            if ! inside /usr/sbin/sshd -f /etc/ssh/sshd_config </dev/null > "$BASE/ssh.log" 2>&1; then
                cat "$BASE/ssh.log"
                fail "SSH could not start."
            fi
        fi
    fi
    if [ "$MODE" = DESKTOP ]; then
        if ! inside /bin/su - linuxoid -c "/usr/bin/tigervncserver -list" | awk -v display="$DISPLAY_NUM" '$1 == display || $1 == ":" display { found=1 } END { exit !found }'; then
            if ! inside /bin/su - linuxoid -c "/usr/bin/tigervncserver :$DISPLAY_NUM -localhost yes -geometry 1280x800 -depth 24 -rfbauth /home/linuxoid/.config/tigervnc/passwd -xstartup /home/linuxoid/.config/tigervnc/xstartup" </dev/null > "$BASE/desktop.log" 2>&1; then
                cat "$BASE/desktop.log"
                fail "The desktop could not start."
            fi
            tail -n 10 "$BASE/desktop.log"
        fi
    fi
    echo 'Workspace running.'
}
