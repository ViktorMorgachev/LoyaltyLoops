# --- Этап 1: Сборка (Node.js) ---
FROM node:20-alpine as build

WORKDIR /app

# [ИЗМЕНЕНИЕ 1] Указываем путь от корня репозитория к package.json
COPY web-admin/package*.json ./

# Устанавливаем зависимости
RUN npm ci

# [ИЗМЕНЕНИЕ 2] Копируем исходники ИЗ папки web-admin В текущую папку контейнера
COPY web-admin/ .

# Аргументы (остаются как были)
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
ARG VITE_APP_ENV
ENV VITE_APP_ENV=$VITE_APP_ENV
ARG VITE_YMAPS_API_KEY
ENV VITE_YMAPS_API_KEY=$VITE_YMAPS_API_KEY
ARG VITE_SHOW_PLAY_LINKS=false
ENV VITE_SHOW_PLAY_LINKS=$VITE_SHOW_PLAY_LINKS

RUN npm run build

# --- Этап 2: Раздача (Nginx) ---
FROM nginx:alpine

# Удаляем дефолтный конфиг
RUN rm /etc/nginx/conf.d/default.conf

# Копируем ваш конфиг и СРАЗУ переименовываем его в default.conf
COPY web-admin/nginx.conf /etc/nginx/conf.d/default.conf

# Копируем билд
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]