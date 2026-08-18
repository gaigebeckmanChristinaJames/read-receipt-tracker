#!/system/bin/sh

# Keep upgrades persistent, but a real module uninstall must not leave an
# allow-list that silently re-enables injection on a later fresh installation.
STATE_DIR=/data/adb/wekit_zygisk
rm -f "$STATE_DIR/injection-targets.tsv"
rmdir "$STATE_DIR" 2>/dev/null || true
