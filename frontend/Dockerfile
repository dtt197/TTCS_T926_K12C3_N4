# S4-10: build giao diện rồi phục vụ bằng nginx; nginx chuyển /api và /uploads sang backend
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# S4-09: bật/tắt tài khoản mẫu và menu dữ liệu mẫu theo DEMO_MODE của docker-compose
ARG VITE_DEMO_MODE=false
ENV VITE_DEMO_MODE=$VITE_DEMO_MODE
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80