# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Build the React self-care-portal frontend
# ─────────────────────────────────────────────────────────────────────────────
FROM node:18-alpine AS builder

WORKDIR /app

# Copy package manifests and install dependencies first (layer cache)
COPY financial-services-accelerator/react-apps/self-care-portal/self-care-portal-frontend/package.json \
     financial-services-accelerator/react-apps/self-care-portal/self-care-portal-frontend/package-lock.json ./

RUN npm ci --legacy-peer-deps

# Copy the rest of the source
COPY financial-services-accelerator/react-apps/self-care-portal/self-care-portal-frontend/ .

# Build the production bundle
RUN npm run build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Serve with Nginx
# ─────────────────────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine

LABEL maintainer="WSO2 Financial Services Accelerator" \
      description="Self-Care Portal – React SPA for consent management" \
      version="4.0.0"

# Remove default nginx content
RUN rm -rf /usr/share/nginx/html/*

# Copy the built React app
COPY --from=builder /app/build /usr/share/nginx/html

# Copy custom nginx configuration
COPY integration/docker/self-care-portal/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
