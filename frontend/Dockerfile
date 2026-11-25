# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json .npmrc ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
ENV BACKEND_URL=http://backend:8080
EXPOSE 80
