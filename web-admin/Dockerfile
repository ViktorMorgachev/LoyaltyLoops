# --- Stage 1: Build React App ---
FROM node:20-alpine as build

WORKDIR /app

# Копируем package.json и устанавливаем зависимости
COPY package*.json ./
RUN npm ci

# Копируем исходники и собираем проект
COPY . .
# Важно: Передаем аргументы сборки (чтобы Vite "запек" их в код)
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
ARG VITE_YMAPS_API_KEY
ENV VITE_YMAPS_API_KEY=$VITE_YMAPS_API_KEY

RUN npm run build

# --- Stage 2: Serve with Nginx ---
FROM nginx:alpine

# Копируем собранные файлы из папки dist в папку Nginx
COPY --from=build /app/dist /usr/share/nginx/html

# Копируем кастомный конфиг Nginx (нужен, чтобы работал React Router)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]