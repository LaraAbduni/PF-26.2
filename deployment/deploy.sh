#!/bin/sh
set -eu

# Mesmo modelo do Crescit: Docker Hub -> SSH na EC2 -> Docker.
# Este script administra somente os recursos com prefixo pf-avaliacoes.
: "${IMAGE_TAG:?Informe IMAGE_TAG}"
: "${DATABASE_PASSWORD:?Informe DATABASE_PASSWORD}"
: "${AUTH0_ISSUER:?Informe AUTH0_ISSUER}"
: "${AUTH0_AUDIENCE:?Informe AUTH0_AUDIENCE}"
: "${FRONTEND_URL:?Informe FRONTEND_URL}"
AUTH0_CLAIM_NAMESPACE=${AUTH0_CLAIM_NAMESPACE:-https://pf-avaliacoes.example.com}

docker pull "$IMAGE_TAG"
docker network inspect portfolio-network >/dev/null 2>&1 || docker network create portfolio-network
docker volume inspect pf-avaliacoes-data >/dev/null 2>&1 || docker volume create pf-avaliacoes-data

if ! docker container inspect pf-avaliacoes-postgres >/dev/null 2>&1; then
    docker run -d --name pf-avaliacoes-postgres --restart unless-stopped \
        --network portfolio-network \
        --log-opt max-size=10m --log-opt max-file=3 \
        -e POSTGRES_DB=avaliacoes -e POSTGRES_USER=avaliacoes \
        -e POSTGRES_PASSWORD="$DATABASE_PASSWORD" \
        -v pf-avaliacoes-data:/var/lib/postgresql/data postgres:16-alpine
else
    docker start pf-avaliacoes-postgres >/dev/null
fi

for attempt in $(seq 1 45); do
    if docker exec pf-avaliacoes-postgres pg_isready -U avaliacoes -d avaliacoes >/dev/null 2>&1; then break; fi
    if [ "$attempt" -eq 45 ]; then echo 'PostgreSQL não ficou pronto.' >&2; exit 1; fi
    sleep 2
done

if docker container inspect pf-avaliacoes-api-previous >/dev/null 2>&1; then
    docker rm -f pf-avaliacoes-api-previous >/dev/null
fi
if docker container inspect pf-avaliacoes-api >/dev/null 2>&1; then
    docker stop pf-avaliacoes-api >/dev/null
    docker rename pf-avaliacoes-api pf-avaliacoes-api-previous
fi

rollback() {
    echo 'Deploy falhou. Restaurando o contêiner anterior, se existir.' >&2
    docker logs --tail 60 pf-avaliacoes-api 2>/dev/null || true
    docker rm -f pf-avaliacoes-api >/dev/null 2>&1 || true
    if docker container inspect pf-avaliacoes-api-previous >/dev/null 2>&1; then
        docker rename pf-avaliacoes-api-previous pf-avaliacoes-api
        docker start pf-avaliacoes-api >/dev/null
    fi
    exit 1
}

if ! docker run -d --name pf-avaliacoes-api --restart unless-stopped \
    --network portfolio-network --memory 512m \
    --log-opt max-size=10m --log-opt max-file=3 \
    -p 127.0.0.1:8084:8080 \
    -e DATABASE_URL=jdbc:postgresql://pf-avaliacoes-postgres:5432/avaliacoes \
    -e DATABASE_USERNAME=avaliacoes -e DATABASE_PASSWORD="$DATABASE_PASSWORD" \
    -e AUTH0_ISSUER="$AUTH0_ISSUER" -e AUTH0_AUDIENCE="$AUTH0_AUDIENCE" \
    -e AUTH0_CLAIM_NAMESPACE="$AUTH0_CLAIM_NAMESPACE" -e FRONTEND_URL="$FRONTEND_URL" \
    "$IMAGE_TAG"; then rollback; fi

# 401 é o resultado esperado: a rota está de pé e exige autenticação.
for attempt in $(seq 1 60); do
    code=$(curl --max-time 3 -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8084/avaliacoes || true)
    if [ "$code" = 401 ]; then
        echo "API pronta: $IMAGE_TAG. GET sem token retornou 401."
        exit 0
    fi
    sleep 3
done
rollback
