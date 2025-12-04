# --- Этап 1: Сборка (Node.js) ---
FROM node:20-alpine as build

WORKDIR /app

# Копируем package.json и устанавливаем зависимости
COPY package*.json ./
# Устанавливаем зависимости (npm ci быстрее и надежнее для CI/CD)
RUN npm ci

# Копируем исходники и собираем проект
COPY . .

# ВАЖНО: Vite "запекает" переменные окружения ВО ВРЕМЯ БИЛДА.
# Нам нужно объявить их как ARG, чтобы Railway мог их передать.
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
ARG VITE_APP_ENV
ENV VITE_APP_ENV=$VITE_APP_ENV
ARG VITE_YMAPS_API_KEY
ENV VITE_YMAPS_API_KEY=$VITE_YMAPS_API_KEY

# Собираем проект (результат будет в папке dist)
RUN npm run build

# --- Этап 2: Раздача (Nginx) ---
FROM nginx:alpine

# Копируем собранный React-сайт из первого этапа
COPY --from=build /app/dist /usr/share/nginx/html

# Удаляем дефолтный конфиг nginx и копируем наш
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/


EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]