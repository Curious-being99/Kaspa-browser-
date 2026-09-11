#!/bin/bash
while true; do
  if grep -q "BUILD SUCCESSFUL" <<< "$(manage_task status 0b31b2dd-881d-45de-9775-822b53836821/task-246)"; then
    echo "Done"
    break
  fi
  sleep 2
done
