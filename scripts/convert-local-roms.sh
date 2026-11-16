#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_DIR="${ROOT_DIR}/msx2cas-core/resources/local"
WAV2CAS="${ROOT_DIR}/bin/wav2cas"
CASDIR="${ROOT_DIR}/bin/casdir"

BPS="2400"
RESET_FLAG=""
INVERT_FLAG=""

usage() {
    cat <<EOF
Usage: $(basename "$0") [-b <1200|2400|3000|3600>] [-r] [-i]

Converts all .rom files in msx2cas-core/resources/local/ into .wav and .cas files
alongside each source ROM.

Options:
  -b <bps>  Playback speed in bps: 1200, 2400, 3000, or 3600 (default: 2400)
  -r        Reset MSX after loading ROM
  -i        Invert waveform
  -h        Show this help
EOF
}

while getopts ":b:rih" opt; do
    case "${opt}" in
        b) BPS="${OPTARG}" ;;
        r) RESET_FLAG="-r" ;;
        i) INVERT_FLAG="-i" ;;
        h) usage; exit 0 ;;
        *) usage >&2; exit 1 ;;
    esac
done

case "${BPS}" in
    1200|2400|3000|3600) ;;
    *) echo "Error: invalid speed '${BPS}'. Expected 1200, 2400, 3000, or 3600." >&2; exit 1 ;;
esac

if [[ ! -x "${WAV2CAS}" ]]; then
    echo "Error: wav2cas binary not found or not executable at ${WAV2CAS}" >&2
    exit 1
fi

JAR_PATH="$(find "${ROOT_DIR}/msx2cas-cmd/target" -maxdepth 1 -name 'msx2cas-cmd-*-jar-with-dependencies.jar' | head -n 1 || true)"
if [[ -z "${JAR_PATH}" ]]; then
    echo "Error: packaged CLI JAR not found in ${ROOT_DIR}/msx2cas-cmd/target. Run 'mvn initialize package -DskipTests' first." >&2
    exit 1
fi

shopt -s nullglob
ROMS=("${LOCAL_DIR}"/*.rom)
shopt -u nullglob

if [[ ${#ROMS[@]} -eq 0 ]]; then
    echo "No .rom files found in ${LOCAL_DIR}"
    exit 0
fi

CLI_FLAGS=()
[[ -n "${RESET_FLAG}" ]] && CLI_FLAGS+=("${RESET_FLAG}")
[[ -n "${INVERT_FLAG}" ]] && CLI_FLAGS+=("${INVERT_FLAG}")

WAV2CAS_FLAGS=()
[[ -z "${INVERT_FLAG}" ]] && WAV2CAS_FLAGS+=("-p")

for rom in "${ROMS[@]}"; do
    base="${rom%.rom}"
    wav="${base}.wav"
    cas="${base}.cas"

    echo "==> $(basename "${rom}") -> $(basename "${wav}"), $(basename "${cas}") (${BPS} bps)"
    rm -f "${wav}" "${cas}"
    java -jar "${JAR_PATH}" "${CLI_FLAGS[@]}" -w "${wav}" "${BPS}" "${rom}"
    "${WAV2CAS}" "${WAV2CAS_FLAGS[@]}" "${wav}" "${cas}" >/dev/null
    if [[ ! -s "${cas}" ]]; then
        echo "Error: generated empty CAS file ${cas}" >&2
        exit 1
    fi
    if [[ -x "${CASDIR}" ]]; then
        "${CASDIR}" "${cas}"
    fi
done

echo "Done. Converted ${#ROMS[@]} ROM file(s) in ${LOCAL_DIR}."
