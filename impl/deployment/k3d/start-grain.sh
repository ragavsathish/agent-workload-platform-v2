#!/bin/sh
set -eu

mkdir -p "${AWP_TASKING_STORAGE_DIR:?}" "${AWP_TASK_WORKSPACE_DIR:?}"
exec clojure \
  -J--add-opens=java.base/java.nio=ALL-UNNAMED \
  -J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
  -M -m awp-v2.live.main
