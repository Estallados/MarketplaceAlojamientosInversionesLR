# ---------- Etapa 1: compilación ----------
FROM node:22-alpine AS build
WORKDIR /app

# Primero las dependencias, para que Docker las guarde en caché si solo cambia el código
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- Etapa 2: servidor ----------
FROM nginx:1.27-alpine

# La imagen de nginx procesa las plantillas con las variables de entorno al arrancar
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
# y ejecuta los scripts de /docker-entrypoint.d antes de iniciar
COPY --chmod=755 docker/40-config.sh /docker-entrypoint.d/40-config.sh
COPY --from=build /app/dist/marketplaceInversionesLRFront/browser /usr/share/nginx/html

# Render indica el puerto en PORT. API_URL es la dirección pública del backend; se puede cambiar
# en el panel de Render sin volver a construir la imagen
ENV PORT=80 \
    API_URL=https://marketplacealojamientosinversioneslr.onrender.com
EXPOSE 80
