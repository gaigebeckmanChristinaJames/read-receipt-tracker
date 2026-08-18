#!/system/bin/sh

# Persistent selection state intentionally lives outside the module directory.
# Module upgrades replace /data/adb/modules/wekit_zygisk, while this file must retain
# the user's per-Android-user choices across upgrades.
STATE_DIR=/data/adb/wekit_zygisk
TARGETS_FILE=$STATE_DIR/injection-targets.tsv
LOCK_DIR=$STATE_DIR/.injection-targets.lock
LOG_FILE=$STATE_DIR/webui.log
LOGCAT_FILE=$STATE_DIR/logcat.log
LOG_MAX_BYTES=131072
LOGCAT_MAX_BYTES=1048576
LOCK_RETRIES=10
# Keep this in lockstep with PackageNames.isWeChat(packageName):
# packageName.startsWith("com.tencent.mm").
TARGET_PACKAGE_PREFIX=com.tencent.mm

# KernelSU WebUI commands can run with a sparse environment. Use Android's
# canonical tool paths before invoking cmd/pm/app_process helpers.
export PATH=/system/bin:/system/xbin:/vendor/bin:/product/bin:/apex/com.android.runtime/bin:$PATH
export ANDROID_DATA=/data
export ANDROID_ROOT=/system
export BOOTCLASSPATH=${BOOTCLASSPATH:-}
export SYSTEMSERVERCLASSPATH=${SYSTEMSERVERCLASSPATH:-}

# Every temporary replacement file is published with rename(2). Set this before
# any redirection so it is private even during construction.
umask 077

log_event() {
  wekit_log_level=$1
  shift
  mkdir -p "$STATE_DIR" 2>/dev/null || return 0

  if [ -f "$LOG_FILE" ]; then
    wekit_log_size=$(wc -c < "$LOG_FILE" 2>/dev/null)
    case "$wekit_log_size" in
      ''|*[!0-9]*) wekit_log_size=0 ;;
    esac
    if [ "$wekit_log_size" -gt "$LOG_MAX_BYTES" ]; then
      mv -f "$LOG_FILE" "$LOG_FILE.1" 2>/dev/null || true
    fi
  fi

  wekit_log_timestamp=$(date '+%Y-%m-%d %H:%M:%S' 2>/dev/null)
  [ -n "$wekit_log_timestamp" ] || wekit_log_timestamp=unknown-time
  printf '%s [%s] %s\n' "$wekit_log_timestamp" "$wekit_log_level" "$*" >> "$LOG_FILE" 2>/dev/null || true
  chmod 600 "$LOG_FILE" 2>/dev/null || true
}

command_path() {
  command -v "$1" 2>/dev/null || printf '<not-found>'
}

summarize_file() {
  wekit_summary_file=$1
  if [ -s "$wekit_summary_file" ]; then
    head -c 2048 "$wekit_summary_file" 2>/dev/null | tr '\n' ' '
  else
    printf '<empty>'
  fi
}

report_error() {
  log_event ERROR "$*"
  printf '%s\nFull log: %s\n' "$*" "$LOG_FILE" >&2
}

report_warning() {
  log_event WARN "$*"
  printf '%s\nFull log: %s\n' "$*" "$LOG_FILE" >&2
}

ensure_state_dir() {
  umask 077
  mkdir -p "$STATE_DIR" || {
    report_error "cannot create state directory: $STATE_DIR"
    return 1
  }
  chmod 700 "$STATE_DIR" || {
    report_error "cannot set state directory mode: $STATE_DIR"
    return 1
  }
}

# Android module shells do not consistently ship flock(1). mkdir(2) is atomic,
# so use a private lock directory to serialize read-modify-publish operations.
# A killed WebUI command can leave a stale lock; the next command verifies the
# recorded PID and safely reclaims it.
release_targets_lock() {
  [ -r "$LOCK_DIR/pid" ] || return 0
  IFS= read -r lock_owner < "$LOCK_DIR/pid" || return 0
  [ "$lock_owner" = "$$" ] || return 0
  rm -f "$LOCK_DIR/pid"
  rmdir "$LOCK_DIR" 2>/dev/null || true
}

acquire_targets_lock() {
  ensure_state_dir || return 1
  lock_attempt=0
  while ! mkdir "$LOCK_DIR" 2>/dev/null; do
    if [ -r "$LOCK_DIR/pid" ]; then
      IFS= read -r lock_owner < "$LOCK_DIR/pid" || lock_owner=
      case "$lock_owner" in
        ''|*[!0-9]*)
          if [ "$lock_attempt" -ge 2 ]; then
            rm -f "$LOCK_DIR/pid"
            rmdir "$LOCK_DIR" 2>/dev/null && continue
          fi
          ;;
        *)
          if ! kill -0 "$lock_owner" 2>/dev/null; then
            rm -f "$LOCK_DIR/pid"
            rmdir "$LOCK_DIR" 2>/dev/null || true
            continue
          fi
          ;;
      esac
    elif [ "$lock_attempt" -ge 2 ]; then
      # Do not race the owner between mkdir and pid creation; only reclaim an
      # empty lock after it has remained incomplete for multiple seconds.
      rmdir "$LOCK_DIR" 2>/dev/null && continue
    fi

    lock_attempt=$((lock_attempt + 1))
    if [ "$lock_attempt" -ge "$LOCK_RETRIES" ]; then
      report_error "timed out waiting for target lock: $LOCK_DIR"
      return 1
    fi
    sleep 1
  done

  printf '%s\n' "$$" > "$LOCK_DIR/pid" || {
    rmdir "$LOCK_DIR" 2>/dev/null || true
    return 1
  }
}

run_with_targets_lock() {
  acquire_targets_lock || return 1
  "$@"
  status=$?
  release_targets_lock
  return "$status"
}

list_user_ids() {
  wekit_users_output=$STATE_DIR/.users.output.$$
  wekit_users_error=$STATE_DIR/.users.error.$$
  if cmd user list > "$wekit_users_output" 2> "$wekit_users_error"; then
    users=$(sed -n 's/.*UserInfo{\([0-9][0-9]*\):.*/\1/p' "$wekit_users_output")
  else
    wekit_users_status=$?
    wekit_users_stdout=$(summarize_file "$wekit_users_output")
    wekit_users_message=$(summarize_file "$wekit_users_error")
    log_event WARN "cmd user list failed status=$wekit_users_status cmd=$(command_path cmd) path=$PATH stdout=$wekit_users_stdout stderr=$wekit_users_message"
    users=
  fi
  rm -f "$wekit_users_output" "$wekit_users_error"
  if [ -z "$users" ] && [ -d /data/system/users ]; then
    users=$(find /data/system/users -maxdepth 1 -type d 2>/dev/null |
      sed -n 's#^.*/\([0-9][0-9]*\)$#\1#p')
    if [ -n "$users" ]; then
      log_event INFO "discovered Android users from /data/system/users: $(printf '%s' "$users" | tr '\n' ',')"
    fi
  fi
  if [ -n "$users" ]; then
    log_event INFO "discovered Android users: $(printf '%s' "$users" | tr '\n' ',')"
    printf '%s\n' "$users"
  else
    # AOSP devices always have user 0. Keep the UI usable on older builds whose
    # user-service command is unavailable to the root shell.
    log_event WARN "no Android users parsed; falling back to user 0"
    printf '%s\n' 0
  fi
}

is_valid_user_id() {
  case "$1" in
    ''|*[!0-9]*) return 1 ;;
    *) return 0 ;;
  esac
}

is_valid_package() {
  case "$1" in
    ''|.*|*.|*..*|*[!A-Za-z0-9._]*) return 1 ;;
    *.*) return 0 ;;
    *) return 1 ;;
  esac
}

is_wechat_package() {
  case "$1" in
    "$TARGET_PACKAGE_PREFIX"*) return 0 ;;
    *) return 1 ;;
  esac
}

list_installed_packages_for_user() {
  user_id=$1
  wekit_packages_output=$STATE_DIR/.packages.output.$$
  wekit_packages_error=$STATE_DIR/.packages.error.$$

  cmd package list packages --user "$user_id" > "$wekit_packages_output" 2> "$wekit_packages_error"
  wekit_cmd_status=$?
  if [ "$wekit_cmd_status" -eq 0 ]; then
    wekit_packages_count=$(sed -n 's/^package://p' "$wekit_packages_output" | wc -l)
    log_event INFO "package scan user=$user_id command=cmd count=$wekit_packages_count"
    cat "$wekit_packages_output"
    rm -f "$wekit_packages_output" "$wekit_packages_error"
    return 0
  fi
  wekit_cmd_stdout=$(summarize_file "$wekit_packages_output")
  wekit_cmd_error=$(summarize_file "$wekit_packages_error")
  log_event WARN "package scan user=$user_id command=cmd status=$wekit_cmd_status cmd=$(command_path cmd) stdout=$wekit_cmd_stdout stderr=$wekit_cmd_error"

  pm list packages --user "$user_id" > "$wekit_packages_output" 2> "$wekit_packages_error"
  wekit_pm_status=$?
  if [ "$wekit_pm_status" -eq 0 ]; then
    wekit_packages_count=$(sed -n 's/^package://p' "$wekit_packages_output" | wc -l)
    log_event INFO "package scan user=$user_id command=pm count=$wekit_packages_count"
    cat "$wekit_packages_output"
    rm -f "$wekit_packages_output" "$wekit_packages_error"
    return 0
  fi
  wekit_pm_stdout=$(summarize_file "$wekit_packages_output")
  wekit_pm_error=$(summarize_file "$wekit_packages_error")

  rm -f "$wekit_packages_output" "$wekit_packages_error"
  report_warning "package scan failed for Android user $user_id: cmd=$wekit_cmd_status stdout=[$wekit_cmd_stdout] stderr=[$wekit_cmd_error], pm=$wekit_pm_status stdout=[$wekit_pm_stdout] stderr=[$wekit_pm_error]"
  return 1
}

write_default_header() {
  printf '%s\n' '# WeKit Zygisk injection targets v1'
  printf '%s\n' '# userId<TAB>packageName<TAB>enabled'
}

collect_wechat_targets_locked() {
  scan_file=$1
  packages_file=$STATE_DIR/.injection-targets.packages.$$
  unsorted_file=$STATE_DIR/.injection-targets.unsorted.$$
  : > "$unsorted_file" || {
    report_error "cannot create scan staging file: $unsorted_file"
    return 1
  }

  wekit_scanned_users=0
  wekit_failed_users=0
  for user_id in $(list_user_ids); do
    is_valid_user_id "$user_id" || continue
    if ! list_installed_packages_for_user "$user_id" > "$packages_file"; then
      wekit_failed_users=$((wekit_failed_users + 1))
      continue
    fi
    wekit_scanned_users=$((wekit_scanned_users + 1))
    sed -n 's/^package://p' "$packages_file" |
      while IFS= read -r package_name; do
        is_valid_package "$package_name" || continue
        is_wechat_package "$package_name" || continue
        printf '%s\t%s\n' "$user_id" "$package_name" >> "$unsorted_file"
      done || {
        report_error "failed to filter package list for Android user $user_id"
        rm -f "$packages_file" "$unsorted_file"
        return 1
      }
  done
  rm -f "$packages_file"
  if [ "$wekit_scanned_users" -eq 0 ]; then
    report_error "package scan failed for every discovered Android user"
    rm -f "$unsorted_file"
    return 1
  fi
  if ! LC_ALL=C sort -u -k1,1n -k2,2 "$unsorted_file" > "$scan_file"; then
    report_error "cannot sort package scan results"
    rm -f "$unsorted_file" "$scan_file"
    return 1
  fi
  wekit_target_count=$(wc -l < "$scan_file" 2>/dev/null)
  log_event INFO "package scan complete users=$wekit_scanned_users failed_users=$wekit_failed_users targets=$wekit_target_count"
  rm -f "$unsorted_file"
  return 0
}

# Publish a package scan while preserving each surviving target's explicit
# toggle. Every newly discovered package starts disabled. WebUI supplies the
# scan after using KernelSU's root-shell API to run Android's pm CLI.
publish_scan_locked() {
  scan_file=$1
  ensure_state_dir || return 1
  temp_file=$TARGETS_FILE.tmp.$$
  previous_file=$TARGETS_FILE
  [ -f "$previous_file" ] || previous_file=/dev/null
  (
    umask 077
    write_default_header
    awk -F '\t' -v OFS='\t' -v prefix="$TARGET_PACKAGE_PREFIX" '
      FILENAME == ARGV[1] {
        if (NF == 3 && $1 ~ /^[0-9]+$/ && index($2, prefix) == 1 &&
            ($3 == "0" || $3 == "1")) {
          enabled[$1 SUBSEP $2] = $3
        }
        next
      }
      NF == 2 && $1 ~ /^[0-9]+$/ && index($2, prefix) == 1 {
        key = $1 SUBSEP $2
        print $1, $2, (key in enabled ? enabled[key] : "0")
      }
    ' "$previous_file" "$scan_file"
  ) > "$temp_file"
  status=$?
  if [ "$status" -ne 0 ]; then
    rm -f "$temp_file"
    return "$status"
  fi
  chmod 600 "$temp_file" || {
    report_error "cannot set target file mode: $temp_file"
    rm -f "$temp_file"
    return 1
  }
  mv -f "$temp_file" "$TARGETS_FILE" || {
    report_error "cannot publish target file: $TARGETS_FILE"
    rm -f "$temp_file"
    return 1
  }
  wekit_target_count=$(awk -F '\t' 'NF == 3 { count++ } END { print count + 0 }' "$TARGETS_FILE")
  log_event INFO "target refresh published targets=$wekit_target_count file=$TARGETS_FILE"
}

refresh_targets_locked() {
  ensure_state_dir || return 1
  scan_file=$STATE_DIR/.injection-targets.scan.$$
  collect_wechat_targets_locked "$scan_file" || {
    rm -f "$scan_file"
    return 1
  }
  publish_scan_locked "$scan_file"
  status=$?
  rm -f "$scan_file"
  return "$status"
}

# KernelSU WebUI passes userId<TAB>packageName rows on stdin. Validate and
# normalize them here before the same atomic publish path used by refresh.
replace_targets_from_stdin_locked() {
  ensure_state_dir || return 1
  scan_file=$STATE_DIR/.injection-targets.stdin.$$
  awk -F '\t' -v OFS='\t' -v prefix="$TARGET_PACKAGE_PREFIX" '
    NF == 2 && $1 ~ /^[0-9]+$/ &&
      $2 ~ /^[A-Za-z0-9._]+$/ && index($2, prefix) == 1 {
      print $1, $2
    }
  ' > "$scan_file" || {
    rm -f "$scan_file"
    report_error "cannot parse WebUI package scan"
    return 1
  }
  publish_scan_locked "$scan_file"
  status=$?
  rm -f "$scan_file"
  return "$status"
}

ensure_initialized_locked() {
  if [ ! -f "$TARGETS_FILE" ]; then
    ensure_state_dir || return 1
    temp_file=$TARGETS_FILE.tmp.$$
    write_default_header > "$temp_file" || {
      rm -f "$temp_file"
      return 1
    }
    chmod 600 "$temp_file" && mv -f "$temp_file" "$TARGETS_FILE"
  fi
}

list_targets_locked() {
  ensure_initialized_locked || return 1
  awk -F '\t' -v prefix="$TARGET_PACKAGE_PREFIX" '
    NF == 3 && $1 ~ /^[0-9]+$/ && index($2, prefix) == 1 &&
      ($3 == "0" || $3 == "1") {
      print $1 "\t" $2 "\t" $3
    }
  ' "$TARGETS_FILE"
}

list_targets() {
  run_with_targets_lock list_targets_locked
}

refresh_targets() {
  run_with_targets_lock refresh_targets_locked
}

replace_targets_from_stdin() {
  run_with_targets_lock replace_targets_from_stdin_locked
}

set_enabled_locked() {
  user_id=$1
  package_name=$2
  enabled=$3
  ensure_initialized_locked || return 1

  temp_file=$TARGETS_FILE.tmp.$$
  awk -F '\t' -v OFS='\t' -v user="$user_id" -v package="$package_name" -v enabled="$enabled" '
    $1 == user && $2 == package { print $1, $2, enabled; found = 1; next }
    { print }
    END { exit found ? 0 : 4 }
  ' "$TARGETS_FILE" > "$temp_file"
  status=$?
  if [ "$status" -ne 0 ]; then
    rm -f "$temp_file"
    return "$status"
  fi
  chmod 600 "$temp_file" || {
    report_error "cannot set target file mode: $temp_file"
    rm -f "$temp_file"
    return 1
  }
  mv -f "$temp_file" "$TARGETS_FILE" || {
    report_error "cannot publish target file: $TARGETS_FILE"
    rm -f "$temp_file"
    return 1
  }
  log_event INFO "target toggle user=$user_id package=$package_name enabled=$enabled"
}

set_enabled() {
  user_id=$1
  package_name=$2
  enabled=$3
  is_valid_user_id "$user_id" && is_valid_package "$package_name" || return 2
  if ! is_wechat_package "$package_name"; then
    echo "WeKit Zygisk supports only packages matching $TARGET_PACKAGE_PREFIX*" >&2
    return 4
  fi
  [ "$enabled" = 0 ] || [ "$enabled" = 1 ] || return 2
  run_with_targets_lock set_enabled_locked "$user_id" "$package_name" "$enabled"
}

run_logged_command() {
  wekit_command_name=$1
  shift
  log_event INFO "command=$wekit_command_name start"
  "$@"
  wekit_command_status=$?
  if [ "$wekit_command_status" -eq 0 ]; then
    log_event INFO "command=$wekit_command_name finish status=0"
  else
    log_event ERROR "command=$wekit_command_name finish status=$wekit_command_status"
    printf 'WeKit command failed: %s (exit %s)\nFull log: %s\n' "$wekit_command_name" "$wekit_command_status" "$LOG_FILE" >&2
  fi
  return "$wekit_command_status"
}

read_log() {
  if [ -r "$LOG_FILE" ]; then
    tail -n 200 "$LOG_FILE"
  else
    printf 'No WebUI log exists yet: %s\n' "$LOG_FILE"
  fi
}

clear_logcat_if_oversized_locked() {
  ensure_state_dir || return 1
  [ -f "$LOGCAT_FILE" ] || return 0
  logcat_size=$(wc -c < "$LOGCAT_FILE" 2>/dev/null)
  case "$logcat_size" in
    ''|*[!0-9]*)
      report_error "cannot determine logcat file size: $LOGCAT_FILE"
      return 1
      ;;
  esac
  [ "$logcat_size" -le "$LOGCAT_MAX_BYTES" ] && return 0

  temp_file=$LOGCAT_FILE.tmp.$$
  rm -f "$temp_file"
  : > "$temp_file" || {
    report_error "cannot create empty logcat file: $temp_file"
    return 1
  }
  chmod 600 "$temp_file" || {
    report_error "cannot set logcat file mode: $temp_file"
    rm -f "$temp_file"
    return 1
  }
  mv -f "$temp_file" "$LOGCAT_FILE" || {
    report_error "cannot clear oversized logcat file: $LOGCAT_FILE"
    rm -f "$temp_file"
    return 1
  }
  log_event INFO "logcat cleared size=$logcat_size limit=$LOGCAT_MAX_BYTES file=$LOGCAT_FILE"
}

clear_logcat_if_oversized() {
  run_with_targets_lock clear_logcat_if_oversized_locked
}

export_logcat_locked() {
  ensure_state_dir || return 1
  temp_file=$LOGCAT_FILE.tmp.$$
  rm -f "$temp_file"
  # KernelSU WebUI shells may inherit ANDROID_LOG_TAGS. Supply an explicit
  # verbose filter so every buffer includes WeKit's app-process tags.
  ANDROID_LOG_TAGS='*:V' /system/bin/logcat -d -b all -v threadtime '*:V' > "$temp_file"
  status=$?
  if [ "$status" -ne 0 ]; then
    rm -f "$temp_file"
    report_error "cannot export logcat status=$status command=/system/bin/logcat -d -b all -v threadtime '*:V'"
    return "$status"
  fi
  chmod 600 "$temp_file" || {
    report_error "cannot set logcat file mode: $temp_file"
    rm -f "$temp_file"
    return 1
  }
  mv -f "$temp_file" "$LOGCAT_FILE" || {
    report_error "cannot publish logcat file: $LOGCAT_FILE"
    rm -f "$temp_file"
    return 1
  }
  logcat_size=$(wc -c < "$LOGCAT_FILE" 2>/dev/null)
  case "$logcat_size" in
    ''|*[!0-9]*) logcat_size=0 ;;
  esac
  log_event INFO "logcat export published bytes=$logcat_size file=$LOGCAT_FILE"
  printf 'Exported current-boot logcat to %s (%s bytes)\n' "$LOGCAT_FILE" "$logcat_size"
}

export_logcat() {
  run_with_targets_lock export_logcat_locked
}

case "$1" in
  list) run_logged_command list list_targets ;;
  refresh) run_logged_command refresh refresh_targets ;;
  replace-stdin) run_logged_command replace replace_targets_from_stdin ;;
  set) run_logged_command "set user=$2 package=$3 enabled=$4" set_enabled "$2" "$3" "$4" ;;
  export-log) run_logged_command export-log export_logcat ;;
  check-logcat) clear_logcat_if_oversized ;;
  log) read_log ;;
  *)
    echo "Usage: $0 {list|refresh|set|export-log|check-logcat|log}" >&2
    exit 64
    ;;
esac
