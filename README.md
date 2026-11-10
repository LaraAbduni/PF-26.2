# PF · Sistema de Avaliações

API da PF implementada a partir da estrutura do Crescit: Java 17, Spring Boot 3.3.5, Spring Security e Flyway. Para a execução local, usa H2 em arquivo e Auth0 real. O suporte a PostgreSQL e Docker continua disponível.

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

Da pasta `pf`, execute:

```powershell
.\iniciar.ps1
```

O script inicia front e back. API: `http://localhost:8084`. Front: `http://localhost:5173`. O banco é criado automaticamente em `PF-26.2/data/avaliacoes.mv.db` e persiste entre reinicializações. Para parar, execute `.\parar.ps1`. A documentação principal fica em `pf/LEIA-PRIMEIRO.md`, e os últimos ajustes de role/Action do Auth0 estão em `pf/AUTH0-LOCAL.md`.

O perfil Spring `local` mantém a autenticação JWT e altera apenas a configuração de execução/banco. Para executar pelo IntelliJ, selecione esse perfil e use a pasta `PF-26.2` como working directory. Os dados públicos do tenant já têm valores padrão nesse perfil.

### Alternativa com PostgreSQL e Docker

Para usar o Compose, copie `.env.example`, preencha o ambiente e execute nesta pasta:

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

Os testes usam H2 em modo PostgreSQL e executam a migração Flyway. Exercitam a API com o filtro de segurança real e um decoder simulado. Outros testes geram JWTs RSA reais e um servidor JWKS local para testar assinatura, issuer, audience e expiração. O teste do perfil local inicia a aplicação duas vezes no mesmo banco em arquivo, verifica a persistência e exige HTTP 401 sem token nas duas inicializações. A compatibilidade com o PostgreSQL real também deve ser verificada ao escolher o Compose/deploy.

## Deploy e entrega

Leia [GUIA-DEPLOY.md](docs/GUIA-DEPLOY.md): criação do Auth0, roles ADMIN/USER, Action, Vercel, secrets do GitHub, AWS, HTTPS e roteiro de demonstração.

O workflow roda testes em push/pull request e só publica se iniciado manualmente. Nesse caso, publica a imagem `larinhazzz/pf-avaliacoes-ci:<sha>` e usa os nomes `HOST_TEST`, `KEY_TEST` e `DOCKERHUB_TOKEN` do Crescit, além de `DATABASE_PASSWORD`. Essas configurações não são necessárias para a execução local.

Frontend: [LaraAbduni/PF-front-26.2](https://github.com/LaraAbduni/PF-front-26.2).
