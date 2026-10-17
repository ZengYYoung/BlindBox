FROM node:22-alpine AS build
WORKDIR /source/BlindBox-frontend
COPY BlindBox-frontend/package*.json ./
RUN npm ci
COPY BlindBox-frontend/ ./
COPY images/ /source/images/
RUN npm run build
FROM nginx:alpine
COPY infra/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /source/BlindBox-frontend/dist/ /usr/share/nginx/html/
EXPOSE 80
