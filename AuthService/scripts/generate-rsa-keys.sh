#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEY_DIR="${SCRIPT_DIR}/../src/main/resources/keys"

mkdir -p "${KEY_DIR}"

if [[ -f "${KEY_DIR}/private.pem" && -f "${KEY_DIR}/public.pem" ]]; then
  echo "RSA keys already exist in ${KEY_DIR}"
  exit 0
fi

openssl genpkey -algorithm RSA -out "${KEY_DIR}/private.pem" -pkeyopt rsa_keygen_bits:2048
openssl pkey -in "${KEY_DIR}/private.pem" -pubout -out "${KEY_DIR}/public.pem"

echo "Generated RSA key pair:"
echo "  ${KEY_DIR}/private.pem"
echo "  ${KEY_DIR}/public.pem"
