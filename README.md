# BoJogar

Plataforma completa para organizar peladas via WhatsApp. Um bot inteligente que cuida de tudo: criacao de partidas, convites, controle de participantes, lista de espera, cobranca via PIX, pagamentos automaticos e painel de administracao — tudo direto no WhatsApp.

---

## Indice

- [Visao Geral](#visao-geral)
- [Arquitetura](#arquitetura)
- [Tech Stack](#tech-stack)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Modelo de Dados](#modelo-de-dados)
  - [Entidades](#entidades)
  - [Enums e Maquinas de Estado](#enums-e-maquinas-de-estado)
- [Comandos do Bot (WhatsApp)](#comandos-do-bot-whatsapp)
  - [/start](#start)
  - [/criar](#criar)
  - [/entrar](#entrar)
  - [/pagar](#pagar)
  - [/minhas](#minhas)
  - [/gerenciar](#gerenciar)
  - [/conta](#conta)
  - [/ajuda](#ajuda)
  - [/adminsuper](#adminsuper)
- [API REST](#api-rest)
- [Fluxos Principais](#fluxos-principais)
  - [Criacao de Pelada](#criacao-de-pelada)
  - [Entrada na Pelada](#entrada-na-pelada)
  - [Pagamento via PIX](#pagamento-via-pix)
  - [Lista de Espera](#lista-de-espera)
  - [Saque do Organizador](#saque-do-organizador)
  - [Cancelamento](#cancelamento)
- [Integracoes Externas](#integracoes-externas)
  - [WhatsApp Business API](#whatsapp-business-api)
  - [AbacatePay](#abacatepay)
  - [Pushover](#pushover)
- [Gerenciamento de Sessao](#gerenciamento-de-sessao)
- [Roteamento de Mensagens](#roteamento-de-mensagens)
- [Tarefas Agendadas](#tarefas-agendadas)
- [Seguranca e Autorizacao](#seguranca-e-autorizacao)
- [Configuracao e Variaveis de Ambiente](#configuracao-e-variaveis-de-ambiente)
- [Como Rodar](#como-rodar)

---

## Visao Geral

O BoJogar resolve o problema de organizar peladas: o organizador cria a partida, compartilha o codigo de convite com os amigos, e o bot cuida de tudo — confirmacoes, lista de espera, cobranca, pagamentos e notificacoes. Tudo acontece dentro do WhatsApp, sem precisar baixar nenhum app.

**Principais funcionalidades:**
- Criacao de peladas com wizard conversacional (esporte, local, data, vagas, preco)
- Convite por codigo de 6 caracteres ou deep link do WhatsApp
- Controle automatico de vagas com lista de espera
- Cobranca via PIX com geracao automatica de QR code (AbacatePay)
- Confirmacao automatica de pagamento via webhook
- Painel de gerenciamento para organizadores (participantes, financeiro, edicao)
- Painel de super admin (metricas da plataforma, configuracoes, saques)
- Transicao automatica de status das peladas (OPEN -> IN_PROGRESS -> FINISHED)
- Notificacoes assincronas para todos os participantes

---

## Arquitetura

```
                                 +------------------+
                                 |   WhatsApp User  |
                                 +--------+---------+
                                          |
                                          v
                              +-----------+-----------+
                              | WhatsApp Business API |
                              |   (Meta Graph API)    |
                              +-----------+-----------+
                                          |
                              Webhook POST /v1/api/webhook
                                          |
                                          v
+-------------------------------------------------------------------------+
|                          BoJogar (Spring Boot)                          |
|                                                                         |
|  +---------------+    +------------------+    +--------------------+    |
|  | MessageHandler +--->| CommandProcessor +--->|    Bot Commands    |    |
|  | (dedup, route) |    | (parse, dispatch)|    | (criar, entrar,   |    |
|  +---------------+    +------------------+    |  pagar, gerenciar) |    |
|         |                                     +--------+-----------+    |
|         v                                              |               |
|  +---------------+                                     v               |
|  |SessionManager |    +------------------+    +------------------+     |
|  | (state, TTL)  |    |  REST Controllers|    |    Services      |     |
|  +---------------+    |  /v1/api/*       +--->| (Pelada, Pagamento|     |
|                       +------------------+    |  Participant,User)|     |
|                                               +--------+---------+     |
|                                                        |               |
|                              +-------------------------+               |
|                              |                         |               |
|                              v                         v               |
|                    +---------+-------+     +-----------+----------+    |
|                    | PostgreSQL (JPA)|     |    AbacatePay API    |    |
|                    | (Neon / local)  |     |  (PIX, webhooks)     |    |
|                    +-----------------+     +----------------------+    |
+-------------------------------------------------------------------------+
```

**Camadas:**
1. **WhatsApp Layer** — Recebe mensagens, envia respostas (texto, botoes, listas)
2. **Command Layer** — Comandos do bot, wizard conversacional, gerenciamento de sessao
3. **Service Layer** — Logica de negocio, validacoes, orquestracao
4. **Repository Layer** — Acesso ao banco via Spring Data JPA
5. **Integration Layer** — WhatsApp API, AbacatePay, Pushover

---

## Tech Stack

| Componente      | Tecnologia                          |
|-----------------|-------------------------------------|
| Linguagem       | Kotlin 2.2                          |
| Framework       | Spring Boot 4.0                     |
| Build           | Gradle (Kotlin DSL)                 |
| Banco de Dados  | PostgreSQL (Neon em producao)       |
| ORM             | Spring Data JPA / Hibernate         |
| Mensageria      | WhatsApp Business API (Meta Graph)  |
| Pagamentos      | AbacatePay (PIX transparente)       |
| Notificacoes    | Pushover (alertas de venda)         |
| Teste           | JUnit 5, H2 (in-memory)            |
| Deploy          | Render                              |
| Java            | 17                                  |

---

## Estrutura do Projeto

```
src/main/kotlin/com/bojogar/bot/
├── BoJogarApplication.kt              # Entry point
├── config/
│   ├── WhatsAppProperties.kt          # Props da API do WhatsApp
│   ├── AbacatePayProperties.kt        # Props do AbacatePay
│   ├── PushoverProperties.kt          # Props do Pushover
│   ├── AdminProperties.kt             # Phone do super admin
│   ├── DataSourceConfig.kt            # Conexao PostgreSQL via DATABASE_URL
│   ├── RestClientConfig.kt            # RestClients pre-configurados
│   └── AuditingConfig.kt              # JPA auditing
├── controller/
│   ├── HealthController.kt            # GET /health
│   ├── WebhookController.kt           # Webhooks WhatsApp + AbacatePay
│   ├── PeladaController.kt            # CRUD de peladas
│   ├── ParticipantController.kt       # Entrada/saida de participantes
│   ├── PaymentController.kt           # Pagamentos
│   └── UserController.kt              # Perfil do usuario
├── dto/
│   ├── request/
│   │   ├── CreatePeladaRequest.kt
│   │   └── UpdatePeladaRequest.kt
│   ├── response/
│   │   ├── PeladaResponse.kt
│   │   ├── ParticipantResponse.kt
│   │   ├── PaymentResponse.kt
│   │   ├── UserResponse.kt
│   │   └── ErrorResponse.kt
│   └── abacatepay/                     # DTOs da integracao AbacatePay
│       ├── AbacatePayTransparentRequest.kt
│       ├── AbacatePayTransparentResponse.kt
│       ├── AbacatePayWebhookPayload.kt
│       ├── AbacatePayStoreResponse.kt
│       └── AbacatePayPayoutRequest/Response.kt
├── entity/
│   ├── User.kt                        # Usuario (phone, name, cpf, email)
│   ├── Pelada.kt                      # Pelada (codigo, esporte, local, etc)
│   ├── PeladaParticipant.kt           # Relacao usuario-pelada
│   ├── Pagamento.kt                   # Registro de pagamento
│   ├── Location.kt                    # Local com coordenadas
│   └── PlatformConfig.kt              # Config da plataforma (singleton)
├── enums/
│   ├── Esporte.kt                     # FUTEBOL, FUTEVOLEI, VOLEI
│   ├── StatusPelada.kt                # DRAFT -> OPEN -> FULL -> FINISHED
│   ├── ParticipantStatus.kt           # CONFIRMED, PENDING_PAYMENT, WAITLIST...
│   ├── ParticipantRole.kt             # OWNER, ADMIN, PLAYER
│   └── StatusPagamento.kt             # PENDENTE, CONFIRMADO, ESTORNADO
├── exception/
│   ├── ApiException.kt                # Base exception com HTTP status
│   ├── BusinessException.kt           # Erro de regra de negocio (422)
│   ├── ResourceNotFoundException.kt   # Nao encontrado (404)
│   └── GlobalExceptionHandler.kt      # Handler global (@ControllerAdvice)
├── mapper/
│   ├── PeladaMapper.kt
│   ├── ParticipantMapper.kt
│   ├── PaymentMapper.kt
│   └── UserMapper.kt
├── repository/
│   ├── UserRepository.kt
│   ├── PeladaRepository.kt
│   ├── PeladaParticipantRepository.kt
│   ├── PagamentoRepository.kt
│   ├── PlatformConfigRepository.kt
│   └── LocationRepository.kt
├── service/
│   ├── UserService.kt                 # Cadastro e perfil
│   ├── PeladaService.kt              # CRUD de peladas
│   ├── ParticipantService.kt         # Entrada, saida, lista de espera
│   ├── PagamentoService.kt           # PIX, confirmacao, estorno
│   ├── AbacatePayClient.kt           # Client HTTP do AbacatePay
│   ├── NotificationService.kt        # Notificacoes async
│   ├── PushoverClient.kt             # Alertas de venda
│   ├── AuthorizationService.kt       # Checagem de permissoes
│   ├── PlatformConfigService.kt      # Config da plataforma
│   └── PeladaScheduler.kt            # Jobs agendados
├── util/
│   └── CodeGenerator.kt              # Gerador de codigos de 6 chars
└── whatsapp/
    ├── UxCopy.kt                      # Formatacao de mensagens
    ├── command/
    │   ├── BotCommand.kt              # Interface base
    │   ├── CommandContext.kt          # Contexto da mensagem
    │   ├── CommandProcessor.kt        # Roteador de comandos
    │   ├── CommandRegistry.kt         # Auto-discovery de comandos
    │   └── impl/
    │       ├── StartCommand.kt        # Menu principal
    │       ├── CriarCommand.kt        # Criar pelada (wizard)
    │       ├── EntrarCommand.kt       # Entrar na pelada
    │       ├── PagarCommand.kt        # Pagamento PIX
    │       ├── MinhasCommand.kt       # Minhas peladas
    │       ├── GerenciarCommand.kt    # Painel do organizador
    │       ├── ContaCommand.kt        # Perfil do usuario
    │       ├── AjudaCommand.kt        # Ajuda e tutoriais
    │       └── AdminSuperCommand.kt   # Super admin
    ├── handler/
    │   └── MessageHandler.kt          # Entry point do webhook
    ├── model/
    │   ├── Button.kt                  # Botao interativo
    │   ├── ListRow.kt                 # Linha de lista
    │   └── ListSection.kt            # Secao de lista
    ├── service/
    │   └── WhatsAppService.kt         # Envio de mensagens
    └── session/
        ├── ConversationSession.kt     # Estado da conversa
        ├── ConversationState.kt       # Enum de estados
        └── SessionManager.kt         # Gerenciamento de sessoes
```

---

## Modelo de Dados

### Entidades

#### User
Representa um usuario do sistema. Criado automaticamente na primeira interacao com o bot.

| Campo      | Tipo     | Descricao                                    |
|------------|----------|----------------------------------------------|
| id         | UUID     | Chave primaria                               |
| phone      | String   | Telefone normalizado (55 + DDD + 9 + 8 dig.) |
| name       | String   | Nome do usuario                              |
| cpf        | String?  | CPF (necessario para gerar PIX)              |
| email      | String?  | Email (fallback para AbacatePay)             |
| criadoEm   | Instant  | Data de criacao (auditado)                   |
| atualizadoEm | Instant | Data de atualizacao (auditado)              |

#### Pelada
A entidade principal. Representa uma partida esportiva organizada.

| Campo            | Tipo           | Descricao                                   |
|------------------|----------------|---------------------------------------------|
| id               | UUID           | Chave primaria                              |
| codigo           | String         | Codigo unico de 6 caracteres (ex: `A3KM7P`) |
| createdBy        | User (FK)      | Organizador                                 |
| esporte          | Esporte        | Tipo de esporte                             |
| descricao        | String?        | Descricao opcional                          |
| dataHora         | LocalDateTime  | Data e hora da partida                      |
| local            | String         | Local da partida                            |
| limiteJogadores  | Int            | Max de jogadores (0 = ilimitado)            |
| valorPorJogador  | BigDecimal     | Preco por pessoa (0 = gratuita)             |
| chavePix         | String?        | Chave PIX do organizador                    |
| status           | StatusPelada   | Estado atual no ciclo de vida               |
| version          | Long           | Controle de concorrencia otimista           |

#### PeladaParticipant
Relacao entre usuario e pelada, com role e status.

| Campo           | Tipo              | Descricao                         |
|-----------------|-------------------|-----------------------------------|
| id              | UUID              | Chave primaria                    |
| user            | User (FK)         | Usuario                           |
| pelada          | Pelada (FK)       | Pelada                            |
| role            | ParticipantRole   | OWNER, ADMIN ou PLAYER            |
| displayName     | String?           | Nome customizado na pelada        |
| shirtNumber     | Int?              | Numero da camisa                  |
| status          | ParticipantStatus | Estado da participacao            |
| waitlistPosition| Int?              | Posicao na fila de espera         |
| joinedAt        | Instant           | Quando entrou                     |

Constraint unica: `(user_id, pelada_id)` — um usuario so participa uma vez por pelada.

#### Pagamento
Registro de pagamento PIX vinculado a um participante.

| Campo             | Tipo              | Descricao                              |
|-------------------|--------------------|----------------------------------------|
| id                | UUID               | Chave primaria                         |
| participant       | Participant (FK)   | Participante que deve pagar            |
| valor             | BigDecimal         | Valor cobrado                          |
| status            | StatusPagamento    | PENDENTE, CONFIRMADO ou ESTORNADO      |
| transactionId     | String?            | ID end-to-end do AbacatePay            |
| paidAt            | Instant?           | Quando foi pago                        |
| syncpayIdentifier | String             | ID transparente do AbacatePay (unico)  |
| pixCode           | String?            | Codigo PIX copia-e-cola                |
| pixGeneratedAt    | Instant?           | Quando o PIX foi gerado                |

#### PlatformConfig
Configuracao global da plataforma (singleton — chave fixa `"SINGLETON"`).

| Campo              | Tipo       | Descricao                    |
|--------------------|------------|------------------------------|
| key                | String     | Sempre "SINGLETON"           |
| minPrice           | BigDecimal | Preco minimo permitido       |
| maxPrice           | BigDecimal | Preco maximo permitido       |
| platformFeePercent | Int        | Percentual da taxa (padrao 10%) |
| version            | Long       | Concorrencia otimista        |

#### Location
Local com coordenadas geograficas (opcional).

| Campo     | Tipo    | Descricao       |
|-----------|---------|-----------------|
| id        | UUID    | Chave primaria  |
| name      | String  | Nome do local   |
| address   | String? | Endereco        |
| city      | String? | Cidade          |
| state     | String? | Estado          |
| latitude  | Double? | Latitude        |
| longitude | Double? | Longitude       |

### Enums e Maquinas de Estado

#### Esporte

| Valor     | Label     | Emoji |
|-----------|-----------|-------|
| FUTEBOL   | Futebol   | ⚽    |
| FUTEVOLEI | Futevolei | 🏖️    |
| VOLEI     | Volei     | 🏐    |

#### StatusPelada (ciclo de vida da pelada)

```
DRAFT ──> OPEN ──> FULL ──> IN_PROGRESS ──> FINISHED
             │       │           │
             │       │           └──> (auto: 3h apos horario)
             │       │
             │       └──> IN_PROGRESS (auto: apos horario)
             │
             └──> IN_PROGRESS (auto: apos horario)

Qualquer estado (exceto FINISHED) ──> CANCELLED
```

| Transicao permitida          | Descricao                              |
|------------------------------|----------------------------------------|
| DRAFT -> OPEN                | Pelada publicada                       |
| OPEN -> FULL                 | Todas as vagas preenchidas             |
| FULL -> OPEN                 | Alguem saiu, vagas abertas             |
| OPEN/FULL -> IN_PROGRESS     | Horario da pelada chegou (automatico)  |
| IN_PROGRESS -> FINISHED      | 3 horas apos o horario (automatico)    |
| Qualquer -> CANCELLED        | Cancelada pelo organizador             |

#### ParticipantStatus (ciclo de vida do participante)

```
                +--> CONFIRMED ──> CANCELLED / REMOVED
                |
ENTRADA ──> PENDING_PAYMENT ──> CONFIRMED ──> CANCELLED / REMOVED
                |
                +--> WAITLIST ──> CONFIRMED / PENDING_PAYMENT ──> ...
```

| Transicao permitida               | Descricao                                |
|------------------------------------|------------------------------------------|
| PENDING_PAYMENT -> CONFIRMED       | Pagamento confirmado                     |
| WAITLIST -> CONFIRMED              | Promovido (pelada gratuita)              |
| WAITLIST -> PENDING_PAYMENT        | Promovido (pelada paga)                  |
| CONFIRMED -> CANCELLED             | Saiu voluntariamente                     |
| CONFIRMED -> REMOVED               | Removido pelo admin                      |
| PENDING_PAYMENT -> CANCELLED       | Cancelou antes de pagar                  |

#### ParticipantRole (hierarquia de permissoes)

```
OWNER > ADMIN > PLAYER
```

| Role   | Permissoes                                                      |
|--------|-----------------------------------------------------------------|
| OWNER  | Tudo: editar, cancelar, remover, promover, financeiro, saque    |
| ADMIN  | Gerenciar participantes, confirmar pagamento, ver financeiro     |
| PLAYER | Ver detalhes, sair da pelada                                    |

A verificacao usa `hasAuthority(required)` baseada no ordinal do enum.

---

## Comandos do Bot (WhatsApp)

### /start
**Aliases:** `/inicio`, `/menu`

Menu principal com lista interativa. Exibe dinamicamente:
- Quantidade de pagamentos pendentes
- Quantidade de peladas gerenciadas

**Secoes do menu:**
- **Jogar:** Criar pelada, Entrar com codigo, Minhas peladas
- **Mais:** Pagamentos pendentes, Gerenciar peladas, Minha conta, Ajuda

---

### /criar
**Aliases:** `/nova`, `/new`

Wizard conversacional de 6-7 etapas para criar uma pelada:

| Etapa | Campo          | Validacao                                          |
|-------|----------------|----------------------------------------------------|
| 1     | Esporte        | Selecao via lista (Futebol, Futevolei, Volei)      |
| 2     | Nome           | Opcional (pode pular com "pular")                  |
| 3     | Local          | Minimo 5 caracteres                                |
| 4     | Data e hora    | Formato `DD/MM HH:MM`, deve ser no futuro          |
| 5     | Max jogadores  | 2 a 20, ou "sem limite"                            |
| 6     | Preco          | Min/max configuravel, ou "gratuita"                |
| 7     | Chave PIX      | Obrigatoria se pelada for paga                     |

Apos confirmacao:
1. Gera codigo unico de 6 caracteres
2. Organizador vira OWNER + CONFIRMED automaticamente
3. Envia mensagem de convite pronta para encaminhar (com deep link)
4. Exibe botoes: Convidar, Gerenciar, Menu

---

### /entrar
**Aliases:** `/join`, `/codigo`

Entrada em pelada existente por codigo.

**Fluxo:**
1. Recebe codigo (argumento ou sessao interativa)
2. Exibe detalhes da pelada (esporte, local, data, vagas, preco, organizador)
3. Botoes: "Entrar" ou "Cancelar"
4. Ao entrar, tres cenarios:
   - **Gratuita + vaga:** CONFIRMED imediatamente
   - **Paga:** PENDING_PAYMENT + registro de pagamento criado
   - **Cheia (gratuita):** WAITLIST com posicao na fila

---

### /pagar
**Aliases:** `/pay`, `/pix`

Gerenciamento de pagamentos pendentes.

**Subcomandos:**
- `/pagar` — Lista todos os pagamentos pendentes
- `/pagar gerar [codigo]` — Gera PIX para uma pelada especifica
- `/pagar cpf [codigo] [cpf]` — Informa CPF e gera PIX

**Fluxo de geracao de PIX:**
1. Verifica se o usuario tem CPF cadastrado (obrigatorio para AbacatePay)
2. Se nao tem, solicita CPF
3. Chama AbacatePay para gerar PIX transparente
4. Retorna codigo copia-e-cola (valido por 30 minutos)
5. Se PIX ja existe e nao expirou, reutiliza o existente
6. Se expirou, gera um novo automaticamente

---

### /minhas

Visualizacao das peladas do usuario.

**Subcomandos:**

| Subcomando        | Descricao                                      |
|-------------------|-------------------------------------------------|
| `proximas`        | Peladas futuras (confirmadas, pendentes, fila)  |
| `ver [codigo]`    | Detalhes de uma pelada especifica               |
| `cancelar [codigo]` | Cancelar participacao                         |
| `confirmados [codigo]` | Lista de confirmados                       |
| `historico`       | Peladas passadas (finalizadas/canceladas)       |

---

### /gerenciar
**Aliases:** `/admin`, `/manage`

Painel completo do organizador. Requer role OWNER ou ADMIN.

**Subcomandos:**

| Subcomando                   | Descricao                                    |
|------------------------------|----------------------------------------------|
| `pelada [codigo]`            | Dashboard com resumo e acoes rapidas         |
| `participantes [codigo]`     | Lista de participantes por status            |
| `financeiro [codigo]`        | Resumo financeiro (pago, pendente, saldo)    |
| `editar [codigo]`            | Menu de campos editaveis                     |
| `editar_campo [codigo] [campo] [valor]` | Editar campo especifico          |
| `cancelar [codigo]`          | Cancelar pelada (com confirmacao)            |
| `convidar [codigo]`          | Gerar link de convite para compartilhar      |
| `saque [codigo]`             | Solicitar saque do saldo                     |
| `remover [codigo] [phone]`   | Remover participante                         |
| `confirmar_pgto [codigo] [phone]` | Confirmar pagamento manualmente         |

**Dashboard da pelada** mostra:
- Esporte, local, data/hora
- Confirmados / limite / lista de espera
- Status do pagamento (pago / pendente / total)
- Botoes de acao rapida

**Convite** gera mensagem pronta para encaminhar:
```
Bora jogar! Entra na pelada comigo! 💪

🏆 *Futebol*
📍 Quadra do Parque
📅 20/08/2026 as 19:00
👥 3 vagas restantes
💰 R$ 25,00

Para participar, clique no link e envie a mensagem:
https://wa.me/5581983868651?text=A3KM7P
```

---

### /conta
**Aliases:** `/perfil`

Perfil do usuario com:
- Nome e telefone
- Peladas ativas (participando)
- Peladas jogadas (historico)
- Saldo em carteira (soma de todas as peladas organizadas)
- Botoes para ver peladas e historico

---

### /ajuda
**Aliases:** `/help`, `/comofunciona`

Central de ajuda com tutoriais.

| Topico        | Conteudo                                              |
|---------------|-------------------------------------------------------|
| `criar`       | Passo a passo para criar pelada                       |
| `entrar`      | Como entrar usando codigo                             |
| `pagar`       | Como funciona o pagamento PIX                         |
| `organizador` | Funcionalidades do painel de organizador              |
| `suporte`     | Link direto para suporte via WhatsApp                 |

---

### /adminsuper

Comando exclusivo do super admin (definido por `ADMIN_SUPER_PHONE`).

**Subcomandos:**

| Subcomando       | Descricao                                             |
|------------------|-------------------------------------------------------|
| `peladas`        | Lista todas as peladas ativas                         |
| `pelada [codigo]`| Detalhes de qualquer pelada                           |
| `organizadores`  | Lista organizadores com estatisticas                  |
| `financeiro`     | Receita total, taxas, lucro, saldo AbacatePay         |
| `usuarios`       | Contagem e lista de usuarios                          |
| `saque`          | Saldo disponivel para saque da plataforma             |
| `sacar [valor]`  | Executar saque via AbacatePay                         |
| `config`         | Visualizar configuracoes da plataforma                |
| `setmin [valor]` | Alterar preco minimo                                  |
| `setmax [valor]` | Alterar preco maximo                                  |
| `settaxa [valor]`| Alterar percentual de taxa da plataforma              |

---

## API REST

Todos os endpoints autenticados usam o header `X-User-Phone` para identificacao.

### Health

| Metodo | Endpoint   | Descricao                                |
|--------|------------|------------------------------------------|
| GET    | `/health`  | Status da aplicacao e conexao com o banco |

### Webhooks

| Metodo | Endpoint                        | Descricao                                |
|--------|---------------------------------|------------------------------------------|
| GET    | `/v1/api/webhook`               | Verificacao do WhatsApp (challenge)      |
| POST   | `/v1/api/webhook`               | Recebe mensagens do WhatsApp             |
| POST   | `/v1/api/abacatepay/webhook`    | Recebe eventos de pagamento              |

### Peladas

| Metodo | Endpoint                      | Auth     | Descricao                     |
|--------|-------------------------------|----------|-------------------------------|
| POST   | `/v1/api/peladas`             | Phone    | Criar pelada                  |
| GET    | `/v1/api/peladas/{code}`      | -        | Buscar pelada por codigo      |
| GET    | `/v1/api/peladas/user`        | Phone    | Peladas do usuario            |
| GET    | `/v1/api/peladas/created`     | Phone    | Peladas criadas pelo usuario  |
| PATCH  | `/v1/api/peladas/{code}`      | Phone    | Atualizar pelada              |
| POST   | `/v1/api/peladas/{code}/cancel` | Phone  | Cancelar pelada               |

### Participantes

| Metodo | Endpoint                                              | Auth       | Descricao              |
|--------|-------------------------------------------------------|------------|------------------------|
| GET    | `/v1/api/peladas/{code}/participants`                 | Phone/Admin| Listar participantes   |
| POST   | `/v1/api/peladas/{code}/participants/join`             | Phone      | Entrar na pelada       |
| POST   | `/v1/api/peladas/{code}/participants/leave`            | Phone      | Sair da pelada         |
| DELETE | `/v1/api/peladas/{code}/participants/{targetPhone}`    | Phone/Admin| Remover participante   |

### Pagamentos

| Metodo | Endpoint                                                     | Auth       | Descricao                  |
|--------|--------------------------------------------------------------|------------|----------------------------|
| GET    | `/v1/api/peladas/{code}/payments`                            | Phone/Admin| Listar pagamentos          |
| GET    | `/v1/api/peladas/{code}/payments/unpaid`                     | Phone/Admin| Listar pendentes           |
| POST   | `/v1/api/peladas/{code}/payments/{participantId}/confirm`    | Phone/Admin| Confirmar pagamento manual |

### Usuarios

| Metodo | Endpoint                    | Auth  | Descricao          |
|--------|-----------------------------|-------|--------------------|
| GET    | `/v1/api/users/me`          | Phone | Perfil do usuario  |
| PATCH  | `/v1/api/users/me/name`     | Phone | Atualizar nome     |

---

## Fluxos Principais

### Criacao de Pelada

```
Usuario                          Bot                           Sistema
   |                              |                               |
   |-- /criar ------------------>|                               |
   |                              |-- Qual esporte? ------------>|
   |-- Futebol ----------------->|                               |
   |                              |-- Nome da pelada? ---------->|
   |-- Pelada do Gabiru -------->|                               |
   |                              |-- Local? ------------------->|
   |-- Quadra do Parque -------->|                               |
   |                              |-- Data e hora? ------------->|
   |-- 20/08 19:00 ------------->|                               |
   |                              |-- Quantos jogadores? ------->|
   |-- 10 ---------------------->|                               |
   |                              |-- Valor por jogador? ------->|
   |-- 25 ---------------------->|                               |
   |                              |-- Chave PIX? --------------->|
   |-- email@email.com --------->|                               |
   |                              |                               |
   |                              |--- Confirma? [Sim] [Nao] --->|
   |-- Sim --------------------->|                               |
   |                              |           PeladaService.create()
   |                              |           CodeGenerator -> A3KM7P
   |                              |           User -> OWNER + CONFIRMED
   |                              |                               |
   |<-- Pelada criada! A3KM7P ---|                               |
   |<-- Msg de convite pronta ---|                               |
   |<-- [Convidar][Gerenciar] ---|                               |
```

### Entrada na Pelada

```
Usuario                          Bot                           Sistema
   |                              |                               |
   |-- A3KM7P ----------------->|                               |
   |                              |  (reconhece codigo de 6 chars)|
   |                              |  PeladaService.findByCode()   |
   |                              |                               |
   |<-- Detalhes da pelada ------|                               |
   |<-- [Entrar] [Cancelar] ----|                               |
   |                              |                               |
   |-- Entrar ------------------>|                               |
   |                              |  ParticipantService.join()    |
   |                              |                               |
   |                     [Se gratuita + vaga]                     |
   |<-- Confirmado! ------------|                               |
   |                                                              |
   |                     [Se paga]                                |
   |<-- Pendente pagamento ------|  Pagamento criado (PENDENTE)  |
   |<-- [Pagar agora] ----------|                               |
   |                                                              |
   |                     [Se cheia]                               |
   |<-- Lista de espera #3 -----|  waitlistPosition = 3         |
```

### Pagamento via PIX

```
Usuario                    Bot                  AbacatePay           Banco
   |                        |                       |                  |
   |-- /pagar gerar A3KM7P->|                       |                  |
   |                        |                       |                  |
   |              [Se nao tem CPF]                   |                  |
   |<-- Qual seu CPF? ------|                       |                  |
   |-- 12345678900 -------->|                       |                  |
   |                        |                       |                  |
   |                        |-- POST /transparent -->|                  |
   |                        |<-- brCode (PIX) ------|                  |
   |                        |                       |                  |
   |<-- Codigo PIX ---------|  (cache 30min)        |                  |
   |<-- Copie e pague ------|                       |                  |
   |                        |                       |                  |
   |                        |        ... usuario paga no app ...       |
   |                        |                       |                  |
   |                        |                       |<-- Pagamento ----|
   |                        |<-- webhook completed --|                  |
   |                        |                       |                  |
   |                        |  PagamentoService                       |
   |                        |    .processWebhookPayment()             |
   |                        |  PENDENTE -> CONFIRMADO                 |
   |                        |  PENDING_PAYMENT -> CONFIRMED           |
   |                        |  Verifica se pelada ficou FULL          |
   |                        |                       |                  |
   |<-- Pagamento confirmado!|                       |                  |
   |  [Organizador notificado]                       |                  |
```

### Lista de Espera

```
Jogador X sai da pelada (CONFIRMED -> CANCELLED)
              |
              v
ParticipantService.leave()
              |
              v
Tem alguem na WAITLIST? ──sim──> Pega primeiro (por waitlistPosition)
              |                          |
              no                         v
              |                  Pelada e paga?
              v                  /           \
           (nada)              sim           nao
                                |             |
                                v             v
                        WAITLIST ->      WAITLIST ->
                      PENDING_PAYMENT   CONFIRMED
                                |             |
                                v             v
                        Notifica:        Notifica:
                        "Voce foi        "Voce foi
                        promovido!       confirmado!"
                        Pague agora"
                                |
                                v
                     Recalcula posicoes
                     da fila restante
```

### Saque do Organizador

```
Organizador                    Bot                      AbacatePay
     |                          |                            |
     |-- /gerenciar saque CODE ->|                            |
     |                          |                            |
     |                  Calcula saldo:                       |
     |                  Receita bruta (pagtos confirmados)    |
     |                  - Taxa plataforma (10%)              |
     |                  = Saldo disponivel                   |
     |                          |                            |
     |<-- Saldo: R$ 225,00 ----|                            |
     |    Taxa: R$ 25,00       |                            |
     |    Bruto: R$ 250,00     |                            |
     |                          |                            |
     |-- [Confirmar saque] ---->|                            |
     |                          |-- POST /payout ----------->|
     |                          |<-- Payout criado ----------|
     |                          |                            |
     |<-- Saque solicitado! ----|                            |
```

### Cancelamento

```
Organizador                    Bot                       Sistema
     |                          |                            |
     |-- /gerenciar cancelar CODE->|                          |
     |                          |                            |
     |<-- Tem certeza? ---------|                            |
     |    [Sim, cancelar]       |                            |
     |    [Nao, voltar]         |                            |
     |                          |                            |
     |-- Sim, cancelar -------->|                            |
     |                          |  PeladaService.cancel()    |
     |                          |  Status -> CANCELLED       |
     |                          |  Pagamentos PENDENTE ->    |
     |                          |    ESTORNADO               |
     |                          |                            |
     |                          |  NotificationService       |
     |                          |    .notifyPeladaCancelled() |
     |                          |  (todos participantes      |
     |                          |   recebem notificacao)     |
     |                          |                            |
     |<-- Pelada cancelada! ----|                            |
```

---

## Integracoes Externas

### WhatsApp Business API

**Endpoint:** `https://graph.facebook.com/v22.0`

**Autenticacao:** Bearer token no header `Authorization`

**Capacidades utilizadas:**

| Recurso            | Descricao                                          |
|--------------------|----------------------------------------------------|
| Mensagem de texto  | Envio de mensagens formatadas (Markdown do WhatsApp)|
| Botoes interativos | Ate 3 botoes de resposta rapida por mensagem        |
| Listas             | Menu com secoes e opcoes (ate 10 itens)             |
| Mark as read       | Marca mensagens como lidas                         |

**Deduplicacao:** Mensagens sao deduplicadas por `message_id` com TTL de 5 minutos para evitar processamento duplicado de webhooks.

### AbacatePay

**Endpoint:** `https://api.abacatepay.com/v2`

**Autenticacao:** Bearer token

**Funcionalidades:**

| Recurso              | Endpoint                  | Descricao                        |
|----------------------|---------------------------|----------------------------------|
| Criar PIX            | POST /transparent         | Gera codigo PIX copia-e-cola     |
| Checar status        | GET /transparent/{id}     | Verifica status do pagamento     |
| Saldo da loja        | GET /store                | Saldo disponivel, pendente, bloqueado |
| Criar saque          | POST /payout              | Solicita transferencia           |
| Listar saques        | GET /payout               | Lista de saques realizados       |

**Webhook:**
- Verificacao de assinatura HMAC-SHA256
- Eventos tratados:
  - `transparent.completed` — Pagamento confirmado
  - `transparent.refunded` — Pagamento estornado
  - `transparent.disputed` — Disputa (logado)
  - `transparent.lost` — Perdido (logado)

**PIX Transparente:**
- Valor em centavos
- Expiracao configuravel (padrao 30 minutos)
- Requer CPF do pagador
- Retorna `brCode` (string do QR code PIX)

### Pushover

**Endpoint:** `https://api.pushover.net/1/messages.json`

Servico opcional de notificacao push para o admin. Envia alerta a cada venda confirmada com mensagem motivacional aleatoria (15 templates diferentes).

Pode ser desabilitado simplesmente nao configurando as variaveis `PUSHOVER_TOKEN` e `PUSHOVER_USER_KEY`.

---

## Gerenciamento de Sessao

O bot usa sessoes in-memory para gerenciar conversas multi-etapa (wizard).

**Estados de conversa (`ConversationState`):**

| Estado            | Descricao                              |
|-------------------|----------------------------------------|
| IDLE              | Sem conversa ativa                     |
| CREATING_PELADA   | Wizard de criacao (/criar)             |
| EDITING_PELADA    | Editando campo de pelada               |
| ENTERING_CODE     | Aguardando codigo de pelada            |
| ENTERING_CPF      | Aguardando CPF para PIX                |
| ADMIN_CONFIG      | Configuracao do admin                  |

**ConversationSession** armazena:
- `state` — Estado atual
- `currentPeladaCode` — Codigo da pelada em contexto
- `collectedFields` — Mapa de dados coletados (ex: local, dataHora)
- `nextField` — Proximo campo esperado

**Limpeza automatica:**
- TTL de 30 minutos por sessao
- Job de limpeza a cada 5 minutos
- Armazenamento em `ConcurrentHashMap` (thread-safe)

---

## Roteamento de Mensagens

O `MessageHandler` roteia mensagens seguindo esta prioridade:

```
Mensagem recebida
       |
       v
  Deduplicacao (message_id, 5min TTL)
       |
       v
  Auto-criar usuario se nao existe
       |
       v
  Tipo de mensagem?
  ├── Resposta interativa (botao/lista) -> Extrai comando do button_id
  ├── Texto com "/" -> Comando explicito
  ├── Sessao ativa -> Continua conversa (wizard)
  ├── Texto de 6 chars alfanumerico -> /entrar com codigo
  └── Qualquer outro -> /start (menu)
```

---

## Tarefas Agendadas

### PeladaScheduler (a cada 15 minutos)

Transiciona automaticamente o status das peladas:

1. **OPEN/FULL -> IN_PROGRESS:** Peladas cuja `dataHora` ja passou
2. **IN_PROGRESS -> FINISHED:** Peladas cuja `dataHora` foi ha mais de 3 horas

Timezone: `America/Sao_Paulo`

### Limpeza de mensagens processadas (a cada 5 minutos)

Remove entradas de deduplicacao com mais de 5 minutos do `ConcurrentHashMap`.

### Limpeza de sessoes expiradas (a cada 5 minutos)

Remove sessoes de conversa com mais de 30 minutos de inatividade.

---

## Seguranca e Autorizacao

### Autenticacao

| Canal     | Metodo                                                   |
|-----------|----------------------------------------------------------|
| WhatsApp  | Mensagem vem do webhook verificado (Meta Graph API)      |
| REST API  | Header `X-User-Phone` (identificacao por telefone)       |
| AbacatePay| Verificacao HMAC-SHA256 do webhook                       |

### Autorizacao por role

A hierarquia `OWNER > ADMIN > PLAYER` controla acesso a funcionalidades:

| Acao                       | Role minima |
|----------------------------|-------------|
| Ver detalhes da pelada     | PLAYER      |
| Sair da pelada             | PLAYER      |
| Listar participantes       | ADMIN       |
| Remover participante       | ADMIN       |
| Confirmar pagamento manual | ADMIN       |
| Ver financeiro             | ADMIN       |
| Editar pelada              | ADMIN       |
| Cancelar pelada            | OWNER       |
| Gerar convite              | ADMIN       |
| Solicitar saque            | OWNER       |

### Super Admin

Acesso restrito ao telefone configurado em `ADMIN_SUPER_PHONE`. Permite:
- Ver metricas globais da plataforma
- Configurar precos e taxas
- Executar saques da plataforma
- Listar todos os usuarios e peladas

---

## Configuracao e Variaveis de Ambiente

### Obrigatorias

| Variavel                 | Descricao                                          |
|--------------------------|----------------------------------------------------|
| `DATABASE_URL`           | URL do PostgreSQL (suporta postgres://, postgresql://, jdbc:) |
| `WHATSAPP_API_TOKEN`     | Token da API do WhatsApp (Meta Graph API)          |
| `WHATSAPP_PHONE_NUMBER_ID` | ID do numero de telefone no WhatsApp             |
| `WHATSAPP_VERIFY_TOKEN`  | Token de verificacao do webhook                    |

### Opcionais

| Variavel                 | Default             | Descricao                              |
|--------------------------|---------------------|----------------------------------------|
| `SERVER_PORT`            | `8080`              | Porta do servidor                      |
| `WHATSAPP_API_VERSION`   | `v22.0`             | Versao da API do WhatsApp              |
| `WHATSAPP_PHONE_NUMBER`  | `5581983868651`     | Numero do bot (para deep links)        |
| `ABACATEPAY_API_KEY`     | (vazio)             | API key do AbacatePay                  |
| `ABACATEPAY_WEBHOOK_SECRET` | (vazio)          | Secret para validar webhooks           |
| `PLATFORM_FEE_PERCENT`   | `10`                | Taxa da plataforma (%)                 |
| `PUSHOVER_TOKEN`         | (vazio)             | Token do Pushover (desabilitado se vazio) |
| `PUSHOVER_USER_KEY`      | (vazio)             | User key do Pushover                   |
| `ADMIN_SUPER_PHONE`      | (vazio)             | Telefone do super admin                |

### Configuracao do Banco

O `DataSourceConfig` parseia automaticamente o `DATABASE_URL` em qualquer formato:
- `postgres://user:pass@host:port/db`
- `postgresql://user:pass@host:port/db`
- `jdbc:postgresql://host:port/db`

Pool de conexoes (HikariCP):
- Maximo: 3 conexoes
- Minimo idle: 1 conexao

---

## Como Rodar

### Pre-requisitos

- Java 17+
- PostgreSQL (local ou Neon)
- Conta no WhatsApp Business API (Meta)
- (Opcional) Conta no AbacatePay para pagamentos
- (Opcional) Conta no Pushover para alertas

### Setup local

1. Clone o repositorio:
```bash
git clone https://github.com/GBLins14/BoJogar-BOT.git
cd BoJogar-BOT
```

2. Configure as variaveis de ambiente:
```bash
export DATABASE_URL="postgresql://user:pass@localhost:5432/bojogar"
export WHATSAPP_API_TOKEN="seu_token"
export WHATSAPP_PHONE_NUMBER_ID="seu_phone_number_id"
export WHATSAPP_VERIFY_TOKEN="seu_verify_token"
```

3. Rode a aplicacao:
```bash
./gradlew bootRun
```

4. Configure o webhook do WhatsApp apontando para:
```
https://seu-dominio/v1/api/webhook
```

5. Configure o webhook do AbacatePay (se usar pagamentos):
```
https://seu-dominio/v1/api/abacatepay/webhook
```

### Build

```bash
./gradlew build
```

O JAR sera gerado em `build/libs/`.

### Testes

```bash
./gradlew test
```

Usa H2 in-memory para testes.
