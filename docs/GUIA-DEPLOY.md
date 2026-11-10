# PF: do projeto local à entrega

Este guia acompanha os dois projetos: `PF-26.2` (API) e `PF-front-26.2` (site). A estrutura vem do Crescit, mas você vai criar seu próprio Auth0 e seu próprio projeto Vercel.

## 1. O que foi reaproveitado do Crescit

| Configuração | Valor preparado |
| --- | --- |
| Máquina AWS | `100.53.222.83` — confirmado por você |
| Usuário / porta SSH | `ubuntu` / `22` |
| Registro de imagens | Docker Hub; usuário padrão `larinhazzz` |
| Deploy | GitHub Actions → Docker Hub → SSH → Docker na EC2 |
| Secrets já usados no Crescit | `HOST_TEST`, `KEY_TEST`, `DOCKERHUB_TOKEN` |
| Rede Docker do proxy existente | `portfolio-network` |
| Proxy existente, segundo os arquivos | Caddy, contêiner `crescit-proxy`, HTTPS na porta `8081` |

Os arquivos locais indicam essa estrutura do proxy; confirme o estado da máquina nos comandos do passo 5. Os serviços existentes do Crescit não precisam ser recriados.

Recursos novos da PF: imagem `larinhazzz/pf-avaliacoes-ci`, contêiner `pf-avaliacoes-api`, banco `pf-avaliacoes-postgres`, volume `pf-avaliacoes-data` e porta local `8084`. O PostgreSQL de produção não publica porta para a internet.

O endereço planejado para a nova API é:

```text
https://avaliacoes.100-53-222-83.sslip.io:8081
```

Esse endereço só estará funcionando depois dos passos de deploy e HTTPS. A porta `8081` faz parte da configuração já usada pelo Crescit. A configuração da PF não usa o Auth0 nem a Vercel dos seus colegas.

## 2. Criar seu Auth0

### 2.1 Conta e aplicação

1. Entre em [auth0.com](https://auth0.com/) e crie/acesse uma conta sua. Crie um tenant seu, por exemplo relacionado à PF.
2. No Dashboard, abra **Applications → Applications → Create Application**.
3. Nome: `PF Avaliações Front`. Tipo: **Single Page Web Applications**. Não selecione Regular Web App.
4. Em **Settings**, copie **Domain** e **Client ID**. Não precisa de Client Secret para esta SPA.
5. Configure, inicialmente:

| Campo | Valor local |
| --- | --- |
| Allowed Callback URLs | `http://localhost:5173` |
| Allowed Logout URLs | `http://localhost:5173` |
| Allowed Web Origins | `http://localhost:5173` |

Salve. Se usar login com e-mail/senha, confira em **Connections** da aplicação se a conexão de banco de usuários do seu tenant está habilitada.

### 2.2 Registrar a API

1. Abra **Applications → APIs → Create API**.
2. Nome: `PF Avaliações API`.
3. **Identifier:** `https://api.pf-avaliacoes`.
4. Algoritmo: **RS256**.
5. Na API, abra **Settings → RBAC Settings** e ative **Enable RBAC**. Salve.

O Identifier é a **audience**. Ele identifica a API no token; não precisa ser uma URL que abre no navegador e não é o endereço da sua AWS. Use exatamente o mesmo valor no front e no back. A aplicação envia essa audience ao pedir o access token.

### 2.3 Roles e usuários para demonstração

1. Em **User Management → Roles**, crie as roles **ADMIN** e **USER**, com essas letras maiúsculas.
2. Em **User Management → Users**, crie dois usuários de teste seus, um para ADMIN e outro para USER. Use e-mails e senhas que você controla.
3. Abra o primeiro usuário → **Roles → Assign Roles** → atribua `ADMIN`.
4. Abra o segundo usuário → atribua `USER`.

O sistema lê a role no token. Um usuário recém-cadastrado sem role verá uma mensagem de acesso pendente. As roles são atribuídas pelo administrador do tenant. Não há promoção automática para ADMIN.

### 2.4 Action para inserir roles e e-mail no token

Criar roles no Dashboard não as coloca automaticamente nos claims usados por esta aplicação.

1. Abra **Actions** e crie uma Action personalizada do gatilho **Login / Post Login**. Conforme a versão do painel, a criação pode aparecer em **Library** ou dentro do fluxo de Login.
2. Nome: `PF - roles e email`.
3. Copie o conteúdo completo de [`auth0/post-login.cjs`](../auth0/post-login.cjs):

```javascript
exports.onExecutePostLogin = async (event, api) => {
  const namespace = 'https://pf-avaliacoes.example.com';
  const roles = (event.authorization?.roles ?? []).filter(role => ['ADMIN', 'USER'].includes(role));
  api.idToken.setCustomClaim(`${namespace}/roles`, roles);
  api.accessToken.setCustomClaim(`${namespace}/roles`, roles);
  if (event.user.email) {
    api.accessToken.setCustomClaim(`${namespace}/email`, event.user.email);
  }
};
```

4. Clique **Deploy** na Action.
5. Abra o fluxo **Login**, adicione essa Action entre Start e Complete e clique **Apply** para ativá-la no fluxo. Apenas salvar o código não basta.
6. Depois de mudar roles ou a Action, saia do site e entre novamente para obter tokens novos.

O namespace é um identificador de claim, não um domínio de hospedagem. Pode manter o valor do exemplo; ele é o mesmo nos dois projetos e na Action. Se trocar, atualize os três lugares. O ID token orienta a interface; a API recebe o access token, valida sua assinatura e aplica as roles. O e-mail do avaliador é extraído do access token no back, e não de um campo editável do formulário.

## 3. Testar localmente

Na pasta `PF-26.2`, copie `.env.example` para `.env` e preencha:

```dotenv
AUTH0_ISSUER=https://SEU-DOMINIO-DO-AUTH0/
AUTH0_AUDIENCE=https://api.pf-avaliacoes
AUTH0_CLAIM_NAMESPACE=https://pf-avaliacoes.example.com
FRONTEND_URL=http://localhost:5173
DATABASE_NAME=avaliacoes
DATABASE_USERNAME=avaliacoes
DATABASE_PASSWORD=UMA-SENHA-LOCAL-SUA
SERVER_PORT=8084
```

Em `AUTH0_ISSUER`, use o Domain do seu Auth0 com `https://` e `/` final. Suba com Docker Desktop aberto:

```powershell
docker compose up --build -d
docker compose logs -f api
```

Na pasta `PF-front-26.2`, copie `.env.example` para `.env` e preencha:

```dotenv
VITE_AUTH0_DOMAIN=SEU-DOMINIO-DO-AUTH0
VITE_AUTH0_CLIENT_ID=SEU-CLIENT-ID-DA-SPA
VITE_AUTH0_AUDIENCE=https://api.pf-avaliacoes
VITE_AUTH0_CLAIM_NAMESPACE=https://pf-avaliacoes.example.com
VITE_API_URL=http://localhost:8084
```

O Domain do front fica **sem** `https://` e sem barra. Execute:

```powershell
npm ci
npm run dev
```

Abra `http://localhost:5173`. Entre com ADMIN, cadastre uma avaliação, saia e entre como USER. A avaliação deve aparecer para ambos; o USER não tem botão de cadastro. Para conferir o bloqueio direto no back, os testes automatizados fazem POST autenticado como USER e exigem resposta 403.

Se preferir IntelliJ em vez de Docker para a API, o README do back explica como subir só o banco e passar as variáveis ao Java. O Docker Compose lê `.env`, mas o Spring executado pelo Maven não lê esse arquivo sozinho.

## 4. Subir o código para seus dois repositórios

Os diretórios locais já apontam para:

- Back: [LaraAbduni/PF-26.2](https://github.com/LaraAbduni/PF-26.2).
- Front: [LaraAbduni/PF-front-26.2](https://github.com/LaraAbduni/PF-front-26.2).

Faça os commits separadamente em cada pasta. Pode usar o painel Source Control do VS Code, selecionando o repositório correto, ou executar:

```powershell
git status
git add .
git commit -m "Implementa sistema de avaliacoes da PF"
git push origin main
```

Execute uma vez na pasta do back e outra na pasta do front. `.env`, chaves e senhas estão ignorados. O push na main do back dispara testes e deploy; se os secrets ainda não estiverem configurados, a etapa de deploy vai informar o nome que falta. Configure o passo 6 antes do push se quiser que ele já publique na primeira tentativa.

## 5. Conhecer a sua AWS antes de publicar

No seu computador, use a mesma chave `.pem` que já acessa a máquina do Crescit:

```powershell
ssh -i "C:\caminho\da\sua-chave.pem" ubuntu@100.53.222.83
```

Na EC2:

```sh
docker ps --format 'table {{.Names}}\t{{.Ports}}\t{{.Status}}'
docker network inspect portfolio-network
docker inspect crescit-proxy --format '{{json .Mounts}}'
free -h
df -h
```

Confira se Docker funciona sem sudo para `ubuntu`, se o proxy está em execução e se há espaço e memória para mais uma API e um PostgreSQL. Se `docker` não estiver instalado, use o roteiro oficial de [Docker Engine no Ubuntu](https://docs.docker.com/engine/install/ubuntu/); na máquina do Crescit ele já era usado. Se a rede não existir, o script da PF a cria, mas é preciso que o proxy também participe dela para resolver `pf-avaliacoes-api`.

No Security Group da EC2, preserve as regras atuais do Crescit. O proxy descrito nos arquivos usa **TCP 80** para emissão/renovação do certificado e **TCP 8081** para HTTPS, acessíveis aos visitantes. O SSH **22** deve permitir o acesso do runner do GitHub Actions, como já acontece no deploy do Crescit; uma regra restrita só ao IP do seu computador não permite o runner. A porta da API **8084** fica apenas em `127.0.0.1`; não precisa abrir 8084 nem uma porta de banco no Security Group.

## 6. Configurar o GitHub Actions do back

No GitHub, abra o repositório **PF-26.2 → Settings → Secrets and variables → Actions**.

### Secrets

Crie os **Repository secrets**:

| Nome | Conteúdo |
| --- | --- |
| `HOST_TEST` | `100.53.222.83` |
| `KEY_TEST` | Conteúdo completo da sua chave SSH privada `.pem`, incluindo BEGIN e END |
| `DOCKERHUB_TOKEN` | Token do Docker Hub com permissão de escrita para sua conta |
| `DATABASE_PASSWORD` | Uma senha forte e nova para o PostgreSQL da PF |

Os nomes são iguais aos do Crescit, mas secrets de outro repositório não são herdados automaticamente. GitHub não deixa revelar um secret antigo: para cadastrar aqui, use sua chave original e o token original, ou crie um novo token na sua conta Docker Hub.

No Docker Hub, crie o repositório **pf-avaliacoes-ci** como **público**. A EC2 vai puxar essa imagem pública sem precisar armazenar um token Docker Hub. O usuário padrão é `larinhazzz`, como no Crescit. Se o seu usuário for outro, preencha a variável `DOCKERHUB_USERNAME`.

### Variables

Na aba **Variables**, crie **Repository variables**:

| Nome | Valor |
| --- | --- |
| `AUTH0_ISSUER` | `https://SEU-DOMINIO-DO-AUTH0/` |
| `AUTH0_AUDIENCE` | `https://api.pf-avaliacoes` |
| `AUTH0_CLAIM_NAMESPACE` | `https://pf-avaliacoes.example.com` |
| `FRONTEND_URL` | Inicialmente `http://localhost:5173`; depois substitua pela sua URL de produção Vercel |
| `DOCKERHUB_USERNAME` | `larinhazzz` (opcional, é o padrão do workflow) |

O Docker usa `DATABASE_PASSWORD` só para inicializar o banco no primeiro deploy. Alterar o secret depois não troca automaticamente a senha de um PostgreSQL já persistido. Para rotacionar a senha, altere também a senha do usuário no banco; preserve o volume.

### Executar e entender o deploy

Faça push na main ou abra **Actions → Testar e publicar API de avaliações → Run workflow → main**.

1. **test:** executa `mvn verify`; compila e roda os testes.
2. **deploy:** confere a configuração e entra no Docker Hub.
3. Constrói a imagem e publica uma tag com o SHA do commit.
4. Copia `deployment/deploy.sh` para `/home/ubuntu/pf-avaliacoes` por SSH.
5. Cria/inicia o PostgreSQL da PF com volume persistente.
6. Guarda o contêiner anterior da API e inicia a nova imagem.
7. Consulta `http://127.0.0.1:8084/avaliacoes`: **401 é esperado**, porque a API exige login. Se a aplicação não ficar pronta, o script tenta restaurar o contêiner anterior. Esse rollback é do contêiner, não desfaz migrações de banco.

O Actions só faz deploy depois de os testes passarem. Pull requests rodam testes sem deploy. Não há comandos para remover os serviços/volumes do Crescit nem limpeza global de imagens.

## 7. Habilitar HTTPS para a PF no proxy já existente

O front na Vercel usa HTTPS. A API também precisa de HTTPS para as chamadas funcionarem no navegador.

Na sua EC2, confirme o caminho do Caddyfile pelo `docker inspect` do passo 5. Os scripts do Crescit indicam **`/home/ubuntu/crescit-runtime/caddy/Caddyfile`**. Preserve o conteúdo atual e faça uma cópia:

```sh
cp /home/ubuntu/crescit-runtime/caddy/Caddyfile /home/ubuntu/crescit-runtime/caddy/Caddyfile.backup-pf
nano /home/ubuntu/crescit-runtime/caddy/Caddyfile
```

No final, **acrescente** o bloco abaixo, que também está em [`deployment/Caddyfile.fragment`](../deployment/Caddyfile.fragment):

```caddyfile
avaliacoes.100-53-222-83.sslip.io:8081 {
    reverse_proxy pf-avaliacoes-api:8080
}
```

Mantenha o bloco global existente com `https_port 8081` e todas as rotas atuais. Depois valide e recarregue:

```sh
docker exec crescit-proxy caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker exec crescit-proxy caddy reload --config /etc/caddy/Caddyfile --adapter caddyfile
docker logs --tail 40 crescit-proxy
curl -i https://avaliacoes.100-53-222-83.sslip.io:8081/avaliacoes
```

Só execute o reload se o validate terminar com sucesso. O último comando deve retornar **HTTP 401**, com certificado válido, sem `-k`. O sslip.io resolve esse nome para o IP embutido; o Caddy solicita o certificado. A emissão depende de DNS, porta 80 acessível e limites da autoridade certificadora. Se a máquina mudou de IP ou já usa outra estrutura de proxy, ajuste o hostname e o encaminhamento para essa estrutura antes de continuar.

Se precisar restaurar o arquivo, copie `Caddyfile.backup-pf` sobre o Caddyfile e execute validate/reload. Não execute os scripts antigos de recriação do proxy só para adicionar esta rota.

## 8. Criar e publicar o SEU front na Vercel

1. Entre em [vercel.com](https://vercel.com/) com a sua conta e conecte seu GitHub.
2. **Add New → Project → Import** o repositório **LaraAbduni/PF-front-26.2**.
3. Configuração:

| Campo | Valor |
| --- | --- |
| Framework Preset | Vite |
| Root Directory | `./` — o repositório importado já é só o front |
| Install Command | `npm ci` |
| Build Command | `npm run build` |
| Output Directory | `dist` |
| Node.js | 22.x |

4. Cadastre as variáveis em **Environment Variables**, no ambiente **Production**:

```dotenv
VITE_AUTH0_DOMAIN=SEU-DOMINIO-DO-AUTH0
VITE_AUTH0_CLIENT_ID=SEU-CLIENT-ID-DA-SPA
VITE_AUTH0_AUDIENCE=https://api.pf-avaliacoes
VITE_AUTH0_CLAIM_NAMESPACE=https://pf-avaliacoes.example.com
VITE_API_URL=https://avaliacoes.100-53-222-83.sslip.io:8081
```

5. Clique **Deploy**. Copie a URL estável de produção realmente gerada pela Vercel; o nome pode receber um sufixo se já estiver em uso. Use essa URL nos próximos passos, e não um domínio do Crescit de terceiros nem uma URL temporária de preview.
6. No seu **Auth0 → Applications → sua SPA → Settings**, atualize os três campos com os dois endereços separados por vírgula:

```text
http://localhost:5173, https://SUA-URL-DE-PRODUCAO.vercel.app
```

Faça isso em **Allowed Callback URLs**, **Allowed Logout URLs** e **Allowed Web Origins**. Não acrescente caminhos como `/avaliacoes`, porque o código volta para a origem do site. Salve.

7. No **GitHub do back → Variables**, defina `FRONTEND_URL=https://SUA-URL-DE-PRODUCAO.vercel.app`, sem barra final. Para permitir também testes locais contra a API publicada, use as duas origens separadas por vírgula.
8. Rode novamente o workflow do back. Isso aplica o CORS com a sua URL.
9. Abra a URL de produção do front e entre com seu ADMIN.

Mudou `VITE_...`? **Faça um novo deploy na Vercel.** Essas variáveis são incorporadas na compilação. Valores `VITE_` são visíveis no navegador: somente Domain, Client ID, audience, namespace e URL da API vão ali. Senha de banco, chave SSH e Client Secret ficam fora do frontend.

## 9. Demonstrar a PF

1. Mostre no GitHub o workflow verde, a imagem no Docker Hub e a API na EC2.
2. Abra a URL Vercel e entre como ADMIN pelo Auth0.
3. Cadastre uma avaliação com e-mail do avaliado, comentário e nota entre 0 e 10. Mostre o e-mail do avaliador e a data preenchidos automaticamente.
4. Recarregue a página: a avaliação continua salva no PostgreSQL.
5. Use busca e ordenação, e mostre o resumo de notas.
6. Saia e entre como USER: pode listar, mas não cadastrar.
7. Mostre que GET sem token retorna 401 e o teste de POST como USER retorna 403. Mostre a validação de nota fora do intervalo nos testes.

Para chamar manualmente a API, no DevTools do navegador copie **o access token da requisição autenticada**, na aba Network → `/avaliacoes` → Request Headers → Authorization. Use temporariamente no seu terminal, sem compartilhar o token ou salvá-lo no repositório:

```powershell
$pfToken = Read-Host 'Cole o access token, sem a palavra Bearer'
$pfApi = 'https://avaliacoes.100-53-222-83.sslip.io:8081'
Invoke-RestMethod "$pfApi/avaliacoes" -Headers @{ Authorization = "Bearer $pfToken" }
$pfBody = @{ emailAvaliado = 'colega@empresa.com'; comentarios = 'Bom trabalho em equipe.'; nota = 8.5 } | ConvertTo-Json
Invoke-RestMethod "$pfApi/avaliacoes" -Method POST -ContentType 'application/json' -Headers @{ Authorization = "Bearer $pfToken" } -Body $pfBody
```

Use token de ADMIN para criar. Com USER, o POST deve retornar 403. Não use o ID token como bearer token da API: a audience da API é diferente da audience do ID token.

## 10. Se algo falhar

| Sintoma | Verificação |
| --- | --- |
| Callback URL mismatch | Auth0 precisa da origem exata que você abriu, incluindo esquema e porta; use a produção estável |
| Login funciona, mas sem acesso | Atribua ADMIN ou USER, ative a Action no fluxo e saia/entre novamente |
| GET retorna 401 com token | Confira issuer com barra final, audience igual nos projetos, assinatura e expiração; envie access token |
| GET retorna 403 | Confira o claim `https://pf-avaliacoes.example.com/roles` no access token e a role atribuída |
| POST retorna 403 como ADMIN | Verifique também o claim `/email`; a Action deve adicioná-lo ao access token |
| Erro CORS | `FRONTEND_URL` deve ter a origem exata da Vercel; execute novo deploy do back após mudar |
| Mixed Content | `VITE_API_URL` precisa ser HTTPS na produção; depois faça redeploy do front |
| HTTPS não abre / erro 502 | Confira DNS, regras 80/8081, logs do Caddy, nome da API e rede Docker |
| Actions não conecta por SSH | Confira HOST_TEST, KEY_TEST, usuário ubuntu e regra de rede para o runner |
| Pull da imagem não autorizado | Repositório Docker Hub da PF deve ser público; confira o usuário configurado |
| Banco rejeita senha após mudar secret | Banco persistido manteve a senha anterior; rotacione no PostgreSQL também |
| Front continua usando configuração antiga | Redeploy na Vercel; editar variável não altera o bundle já publicado |

Logs úteis na EC2:

```sh
docker logs --tail 100 pf-avaliacoes-api
docker logs --tail 50 pf-avaliacoes-postgres
docker logs --tail 50 crescit-proxy
```

## Referências oficiais consultadas

- [Auth0 React SDK e configuração de login](https://auth0.com/docs/quickstart/spa/react).
- [Auth0: roles em tokens usando Actions](https://support.auth0.com/center/s/article/add-roles-and-permissions-to-the-id-token-using-actions).
- [Auth0: atribuir roles a usuários](https://auth0.com/docs/manage-users/access-control/configure-core-rbac/rbac-users/assign-roles-to-users).
- [Spring Security: resource server JWT e validação de audience](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
- [Vite na Vercel](https://vercel.com/docs/frameworks/frontend/vite).
- [Vercel: gerenciamento de variáveis e redeploy](https://vercel.com/docs/environment-variables/managing-environment-variables).
- [Vite: variáveis públicas no bundle](https://vite.dev/guide/env-and-mode).
