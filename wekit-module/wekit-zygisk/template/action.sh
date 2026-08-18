#!/system/bin/sh

MODDIR=${0%/*}
CONFIG_SCRIPT=$MODDIR/config.sh

# Module actions run from a root-manager environment that may have a sparse PATH.
export PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin:/apex/com.android.runtime/bin:$PATH

if [ ! -x "$CONFIG_SCRIPT" ]; then
  echo "Unable to read WeKit Zygisk targets: $CONFIG_SCRIPT is unavailable" >&2
  exit 1
fi

target_rows=$("$CONFIG_SCRIPT" list)
list_status=$?
if [ "$list_status" -ne 0 ]; then
  echo "Unable to read WeKit Zygisk targets (exit $list_status)" >&2
  exit "$list_status"
fi

selected_target=$(printf '%s\n' "$target_rows" | awk -F '\t' '
  $3 == "1" {
    user_priority = ($1 == "0" ? 0 : 1)
    package_priority = ($2 == "com.tencent.mm" ? 0 : 1)
    if (!found ||
        user_priority < selected_user_priority ||
        (user_priority == selected_user_priority &&
         package_priority < selected_package_priority)) {
      selected_user_id = $1
      selected_package_name = $2
      selected_user_priority = user_priority
      selected_package_priority = package_priority
      found = 1
    }
  }
  END {
    if (found) {
      print selected_user_id "\t" selected_package_name
    }
  }
')

if [ -z "$selected_target" ]; then
  echo "No enabled WeKit Zygisk targets. Enable one in the module WebUI first." >&2
  exit 1
fi

tab=$(printf '\t')
IFS="$tab" read -r user_id package_name <<EOF
$selected_target
EOF

echo "- Restarting $package_name for Android user $user_id"
failed=false
if ! am force-stop --user "$user_id" "$package_name"; then
  echo "  Failed to force-stop $package_name for Android user $user_id" >&2
  failed=true
fi
if ! am start --user "$user_id" \
  -n "$package_name/com.tencent.mm.ui.LauncherUI"; then
  echo "  Failed to start $package_name for Android user $user_id" >&2
  failed=true
fi

if [ "$failed" = true ]; then
  echo "WeChat restart failed." >&2
  exit 1
fi

echo "WeChat restarted."
