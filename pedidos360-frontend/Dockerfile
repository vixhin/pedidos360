FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# URL pública del despliegue (ej: https://34-207-191-208.nip.io).
# Se inyecta en environment.prod.ts antes de compilar.
ARG PUBLIC_URL=https://localhost
RUN sed -i "s|__PUBLIC_URL__|${PUBLIC_URL}|g" src/environments/environment.prod.ts
RUN npx ng build --configuration production

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/pedidos360-frontend/browser /usr/share/nginx/html
EXPOSE 80
