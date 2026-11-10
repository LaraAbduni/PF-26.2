# PF · Sistema de Avaliações

API da PF implementada a partir da estrutura do Crescit: Java 17, Spring Boot 3.3.5, Spring Security, PostgreSQL, Flyway e Docker. Deploy por GitHub Actions, Docker Hub e SSH na mesma EC2.

| Rota | Acesso | Resposta |
| --- | --- | --- |
| `POST /avaliacoes` | ADMIN | 201 com a avaliação criada |
| `GET /avaliacoes` | ADMIN ou USER | 200 com uma lista, das mais recentes para as mais antigas |

Todas as rotas de negócio exigem access token do Auth0. Sem token ou com token inválido: 401. Sem a role necessária: 403. A API verifica assinatura RS256, expiração, issuer e audience.

Corpo de cadastro:

```json
{
  "emailAvaliado": "colega@empresa.com",
  "comentarios": "Colaborou com o time e apresentou boas soluções.",
  "nota": 9.5
}
```

A resposta contém `id`, `emailAvaliador`, `emailAvaliado`, `comentarios`, `nota` e `dataAvaliacao`. O e-mail do avaliador vem do token assinado e a data é gerada pelo servidor em UTC. A nota aceita de 0 a 10, com até uma casa decimal. Comentários: até 2000 caracteres. Os dados ficam no PostgreSQL, com migração versionada.

## Como executar

Primeiro configure seu próprio Auth0 seguindo o [guia completo](docs/GUIA-DEPLOY.md). Depois, nesta pasta:

```powershell
Copy-Item .env.example .env
# Edite .env com SEU Auth0 e uma senha local para o banco.
docker compose up --build -d
```

API: `http://localhost:8084`. Banco local: `localhost:5434`. `docker compose logs -f api` mostra os logs. `docker compose down` interrompe os serviços e preserva os dados. Não use `down -v` se quiser preservar avaliações.

Também é possível executar pelo IntelliJ/Maven: suba só o banco com `docker compose up -d postgres`, configure no ambiente do processo as mesmas variáveis de `.env` e execute `AvaliacoesApplication`. Use `SERVER_PORT=8084` e `DATABASE_URL=jdbc:postgresql://localhost:5434/avaliacoes`. O Maven/Spring não carrega `.env` automaticamente; o Docker Compose carrega.

Testes (JDK 17 ou superior):

```powershell
.\mvnw.cmd verify
```

Os testes usam H2 em modo PostgreSQL e executam a migração Flyway. Exercitam a API com o filtro de segurança real e um decoder simulado. Outros testes geram JWTs RSA reais e um servidor JWKS local para testar assinatura, issuer, audience e expiração. A compatibilidade com o PostgreSQL real também deve ser verificada ao subir o Compose/deploy.

## Deploy e entrega

Leia [GUIA-DEPLOY.md](docs/GUIA-DEPLOY.md): criação do Auth0, roles ADMIN/USER, Action, Vercel, secrets do GitHub, AWS, HTTPS e roteiro de demonstração.

O workflow publica a imagem `larinhazzz/pf-avaliacoes-ci:<sha>` e usa os mesmos nomes `HOST_TEST`, `KEY_TEST` e `DOCKERHUB_TOKEN` do Crescit. Esses secrets precisam ser cadastrados neste repositório. O banco usa um secret novo `DATABASE_PASSWORD`. Não há tenant, client ID ou domínio Vercel de terceiros nos arquivos desta PF.

Frontend: [LaraAbduni/PF-front-26.2](https://github.com/LaraAbduni/PF-front-26.2).
