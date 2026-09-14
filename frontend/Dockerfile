# 1단계: 빌드
FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# PortOne storeId·channelKey 는 브라우저 노출값이라 빌드 시점에 주입한다
ARG VITE_PORTONE_STORE_ID
ARG VITE_PORTONE_CHANNEL_KEY
ENV VITE_PORTONE_STORE_ID=${VITE_PORTONE_STORE_ID} \
    VITE_PORTONE_CHANNEL_KEY=${VITE_PORTONE_CHANNEL_KEY}
RUN npm run build

# 2단계: 정적 파일 서빙 + API 게이트웨이 (설정은 infra/nginx/nginx.prod.conf 를 마운트)
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
