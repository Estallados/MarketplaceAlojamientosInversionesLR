#!/bin/sh
# Escribe la dirección del backend (variable API_URL) en config.js antes de iniciar nginx.
set -eu
url=$(printf '%s' "${API_URL:-http://localhost:8081}" | sed 's#/*$##')
printf "window.__config = { apiUrl: '%s' };\n" "$url" > /usr/share/nginx/html/config.js
echo "config.js: apiUrl=$url"
