#!/bin/sh
# T-NEG-01, T-NEG-02, T-LOC-06, T-RSVP-05
set -eu
root="$(cd "$(dirname "$0")/.." && pwd)/app/src"
fail=0

check_empty() {
  id="$1"
  shift
  if rg -n "$@" "$root"; then
    echo "FAIL $id"
    fail=1
  else
    echo "PASS $id"
  fi
}

check_empty T-NEG-02 -i "nominatim|openstreetmap|android.location.Geocoder"
check_empty T-NEG-01 -i "graph.microsoft|graph.microsoft.com"
check_empty T-NEG-01b -i "IPM.Task"
check_empty T-RSVP-05 "put\\(CalendarContract.Events.SELF_ATTENDEE_STATUS"
exit "$fail"
