FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# URL pública del despliegue (ej: https://3-92-44-37.sslip.io).
# BFF_PUBLIC_URL opcional de API Gateway (ej: https://abc123.execute-api.us-east-1.amazonaws.com/api/bff)
ARG PUBLIC_URL=https://localhost
ARG BFF_PUBLIC_URL=__BFF_PUBLIC_URL__
RUN sed -i "s|__PUBLIC_URL__|${PUBLIC_URL}|g" src/environments/environment.prod.ts
RUN sed -i "s|__BFF_PUBLIC_URL__|${BFF_PUBLIC_URL}|g" src/environments/environment.prod.ts
RUN npx ng build --configuration production

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/pedidos360-frontend/browser /usr/share/nginx/html
EXPOSE 80
