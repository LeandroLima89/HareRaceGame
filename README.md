# 🐇 Corrida das Lebres (HareRace)

Jogo multiplayer em rede local feito com [libGDX](https://libgdx.com/) para a disciplina de
**Desenvolvimento de Jogos Digitais** (Análise e Desenvolvimento de Sistemas).

Até **10 lebres** disputam uma corrida em um **labirinto** gerado aleatoriamente.
**Quem chegar primeiro na cenoura vence!** Há trevos que aceleram, lama que atrasa e pódio com 1º, 2º e 3º lugar. Os jogadores ficam em máquinas diferentes da mesma
rede e se conectam informando apenas o **IP** e a **porta** do host, usando **sockets TCP**.

---

## Sumário

1. [Requisitos](#1-requisitos)
2. [Como executar](#2-como-executar)
3. [Como jogar](#3-como-jogar)
4. [Controles](#4-controles)
5. [Onde estão as informações de IP no código](#5-onde-estão-as-informações-de-ip-no-código)
6. [Problemas comuns de conexão](#6-problemas-comuns-de-conexão)
7. [Estrutura do projeto](#7-estrutura-do-projeto)
8. [Como funciona a rede](#8-como-funciona-a-rede)
9. [Imagens (PNG)](#9-imagens-png)
10. [Ideias de melhorias](#10-ideias-de-melhorias)

---

## 1. Requisitos

- **Java (JDK) 8 ou superior** instalado (testado com o JDK 21).
- Todos os computadores conectados na **mesma rede** (mesmo Wi-Fi ou mesma rede cabeada do laboratório).
- Não é preciso instalar o Gradle: o projeto já traz o `gradlew.bat` (Windows) e o `gradlew` (Linux/Mac).

## 2. Como executar

### Opção A: rodar direto pelo projeto

Abra um terminal na pasta do projeto e rode:

```bash
# Windows
gradlew.bat lwjgl3:run

# Linux / Mac
./gradlew lwjgl3:run
```

### Opção B: gerar um `.jar` para passar aos colegas

```bash
gradlew.bat lwjgl3:jar
```

O arquivo é criado em `lwjgl3/build/libs/HareRace-1.0.0.jar`. Copie para os colegas
(pen drive, e-mail, Drive...) e cada um executa com:

```bash
java -jar HareRace-1.0.0.jar
```

> As imagens já vão dentro do `.jar`, não é preciso copiar a pasta `assets`.

### Opção C: gerar um `.exe` para Windows (não precisa instalar Java)

```bash
gradlew.bat lwjgl3:packageWinX64
```

O plugin **Construo** (já configurado em `lwjgl3/build.gradle`) junta o jogo com um Java reduzido.
Na primeira vez ele baixa o JDK Temurin 21 (~190 MB), depois usa o que já baixou.
O resultado é `lwjgl3/build/construo/dist/HareRace-winX64.zip` (~42 MB).

Os colegas **descompactam o .zip** (não rodar de dentro dele!) e dão dois cliques em `HareRace.exe`.

> ⚠️ Como o `.exe` não é assinado digitalmente, o Windows pode mostrar
> **"O Windows protegeu o computador"**. Clique em **Mais informações → Executar assim mesmo**.
> O firewall vai perguntar sobre o **HareRace.exe** (em vez do Java): clique em **Permitir**.
>
> O `.exe` só funciona no **Windows 64 bits**. Colegas com Mac ou Linux usam o `.jar` (Opção B).

## 3. Como jogar

### Passo 1: o host cria a sala

Um dos alunos é o **host**: o computador dele roda o servidor.

1. Abra o jogo.
2. Digite seu **Nome** e confira a **Porta** (padrão `5000`).
3. Selecione **`[ Criar sala (sou o host) ]`** e aperte **ENTER**.
4. Na **sala de espera** aparece uma mensagem em azul como esta:

   ```
   Peca para os colegas conectarem em:
   IP: 192.168.0.15    Porta: 5000
   ```

5. Passe esse **IP e a porta** para os colegas (fale em voz alta ou escreva no quadro).

> ⚠️ Na primeira vez o **Firewall do Windows** pergunta se pode liberar o Java.
> Marque **"Redes privadas"** e clique em **Permitir**. Sem isso ninguém consegue conectar.

### Passo 2: os colegas entram na sala

1. Abra o jogo.
2. Digite seu **Nome**.
3. No campo **IP do host**, apague o `127.0.0.1` e digite o IP informado pelo host (ex.: `192.168.0.15`).
4. Confira se a **Porta** é igual à do host.
5. Selecione **`[ Entrar na sala ]`** e aperte **ENTER**.
6. Você aparece na lista da sala de espera com uma lebre de cor diferente.

### Passo 3: a corrida

1. Quando todos tiverem entrado, o **host aperta ENTER**.
2. Aparece o labirinto e a contagem **3, 2, 1, VAI!** Movimentos feitos antes do "VAI!" são ignorados.
3. As lebres largam do **lado esquerdo**, numa faixa de largada: uma embaixo da outra, cada uma na sua casa (o host fica no topo).
4. A **cenoura** 🥕 fica no **canto inferior direito**.
5. Pelo caminho há **itens**:
   - 🍀 **Trevo**: a lebre fica **mais rápida por 3 s** (brilho verde). O trevo some quando alguém pega.
   - 🟤 **Lama**: a lebre fica **mais lenta por 2 s** (cor escurecida). A lama **não some** e atrasa todo mundo que passar.
   - Um efeito novo substitui o anterior, então um trevo tira a lebre da lama.
   - 🚧 **Portões**: de 6 a 8 atalhos do labirinto abrem e fecham em ciclo. Aberto, o portão aparece apagado; antes de fechar, **pisca em vermelho por 1,5 s**; fechado, vira uma **barreira de madeira** por 6 s e depois reabre. Espere abrir ou procure outro caminho!
6. **Ranking:** quem chega na cenoura ganha uma posição (**1º, 2º, 3º**) e fica esperando.
   Quando a **primeira** lebre chega, os outros têm **20 segundos** para terminar (o HUD mostra a contagem).
7. A corrida acaba quando **3 lebres chegam**, quando **todas** chegam ou quando o **tempo acaba**.
   Aparece o **pódio** com ouro, prata e bronze, nome e tempo de cada um.
8. O host aperta **ENTER** para uma nova corrida, com um **labirinto diferente** e itens em lugares novos.

### Regras

| Regra | Detalhe |
|---|---|
| Máximo de jogadores | 10 (o 11º recebe "Sala cheia") |
| Entrar no meio da corrida | Não é permitido. Aguarde terminar e conecte de novo |
| Quem inicia a corrida | Só o host |
| Colisão entre lebres | Não existe: várias lebres podem ocupar a mesma casa |
| Sua lebre | Tem um **destaque amarelo** e o nome em amarelo |
| Atalhos | O labirinto tem alguns caminhos alternativos. Explore! |
| Portões | Nunca fecham em cima de uma lebre (esperam ela sair) e nunca deixam a cenoura sem caminho |

## 4. Controles

| Tela | Tecla | Ação |
|---|---|---|
| Menu | `↑` `↓` ou `TAB` | Trocar de campo |
| Menu | Digitar / `BACKSPACE` | Escrever / apagar |
| Menu | `ENTER` | Confirmar / ir para o próximo campo |
| Menu | `ESC` | Fechar o jogo |
| Sala de espera | `ENTER` (só o host) | Iniciar a corrida |
| Sala de espera | `ESC` | Sair da sala |
| Corrida | `↑ ↓ ← →` ou `W A S D` | Mover a lebre (segure para andar continuamente) |
| Corrida | `M` | Ligar / desligar o som |
| Fim da corrida | `ENTER` (só o host) | Nova corrida |
| Qualquer tela do jogo | `ESC` | Voltar ao menu |

> Se o **host** sair, o servidor é desligado e todos voltam para o menu.

## 5. Onde estão as informações de IP no código

O IP faz o caminho **digitar → conectar → servidor → mostrar na tela**:

### 5.1 Onde o IP é digitado: `MenuScreen.java`

| Local | O que faz |
|---|---|
| `MenuScreen.java:40` | Valor inicial do campo IP: `127.0.0.1` ("esta própria máquina") |
| `MenuScreen.java:41` | Porta inicial, que vem de `Constants.DEFAULT_PORT = 5000` (`Constants.java:47`) |
| `MenuScreen.java:141` | Filtra o que pode ser digitado no IP (letras, números, `.` e `-`) |
| `MenuScreen.java:253` | Desenha o campo "IP do host:" na tela |

### 5.2 Onde o IP é usado para conectar

- **Entrar na sala** (`MenuScreen.java:189`): usa o IP digitado.
  ```java
  startConnecting(false, ip.toString(), p, "Conectando em " + ip + ":" + p + "...");
  ```
- **Criar sala** (`MenuScreen.java:175`): o host conecta no próprio servidor, então usa sempre `127.0.0.1`.
- **Conexão de fato** (`GameClient.java:40`):
  ```java
  socket.connect(new InetSocketAddress(host, port), Constants.CONNECT_TIMEOUT_MS);
  ```

### 5.3 No servidor: `GameServer.java`

- `GameServer.java:97`: `serverSocket = new ServerSocket(port);`
  Não informamos IP, então o servidor escuta em **todas as placas de rede** (`0.0.0.0`).
  É isso que permite aos colegas conectarem pelo IP da rede local.
- `GameServer.java:118`: mostra no console o IP de quem conectou
  (ex.: `[SERVIDOR] Nova conexao de 192.168.0.22`).

### 5.4 Como o host descobre o próprio IP

- `NetUtils.java:22`, no método `getLocalIPv4Addresses()`: percorre as placas de rede do computador e
  devolve os IPs (ex.: `192.168.0.15`), ignorando o `127.0.0.1`.
- `LobbyScreen.java:29`: chama esse método quando a sala de espera abre.
- `LobbyScreen.java:96-98`: escreve na tela (em azul, só para o host):
  `IP: 192.168.0.15    Porta: 5000`.

> 💡 Se aparecer **mais de um IP** (Wi-Fi + cabo, ou VirtualBox/VPN instalado), o certo é o da
> rede da faculdade. Para conferir, rode `ipconfig` no Prompt de Comando e procure o
> **"Endereço IPv4"** do adaptador que está conectado.

## 6. Problemas comuns de conexão

| Mensagem / sintoma | Causa provável | Solução |
|---|---|---|
| `Nao foi possivel conectar: Connection timed out` | Firewall do host bloqueando, ou IP errado | Liberar o Java no firewall do **host** (Redes privadas) e conferir o IP com `ipconfig` |
| `Nao foi possivel conectar: Connection refused` | O host ainda não criou a sala, ou a porta está diferente | Criar a sala primeiro e usar a mesma porta |
| `Nao foi possivel criar a sala: Address already in use` | A porta já está sendo usada por outro programa (ou outro jogo aberto) | Fechar o outro jogo ou usar outra porta (ex.: `5001`) em todos |
| `Sala cheia (maximo 10)` | Já existem 10 jogadores | Aguardar alguém sair |
| `Corrida em andamento...` | Tentou entrar durante uma corrida | Conectar quando a corrida terminar |
| `Conexao com o servidor perdida` | O host fechou o jogo ou a rede caiu | O host cria a sala de novo |
| Funciona no mesmo PC, mas não em outro | Rede da faculdade isola os computadores ("isolamento de clientes" no Wi-Fi) | Testar na rede cabeada do laboratório ou usar o roteador do celular |

> **Testando sozinho:** abra o jogo **duas vezes** no mesmo computador. Em uma janela crie a sala e,
> na outra, entre com o IP `127.0.0.1`.

## 7. Estrutura do projeto

```
core/src/main/java/com/badlogic/game/
├── HareRaceGame.java      → classe principal, controla as telas
├── Constants.java         → tamanhos, porta 5000, máximo de 10 jogadores, cores
├── Assets.java            → carrega os PNGs (se faltar algum, gera um substituto)
├── Sounds.java            → carrega e toca os sons WAV (se faltar algum, fica mudo)
├── Session.java           → junta cliente, servidor e estado de uma partida
├── model/
│   ├── Maze.java          → gera o labirinto (algoritmo Recursive Backtracker)
│   ├── Items.java         → sorteia e guarda os trevos e a lama
│   ├── Gates.java         → os portões que abrem e fecham (só nos atalhos)
│   ├── Hare.java          → a lebre (posição, efeito ativo, posição de chegada)
│   └── RaceState.java     → o que o cliente sabe da partida (inclui o ranking)
├── net/
│   ├── Protocol.java      → as mensagens trocadas (protocolo de texto)
│   ├── GameServer.java    → servidor: valida movimentos, aplica itens, decide o ranking
│   ├── GameClient.java    → cliente: thread leitora + fila de mensagens
│   └── NetUtils.java      → descobre o IP da máquina
└── screens/
    ├── BaseScreen.java    → câmera, viewport e funções de desenho em comum
    ├── MenuScreen.java    → nome, IP, porta → "Criar sala" ou "Entrar na sala"
    ├── LobbyScreen.java   → sala de espera
    └── GameScreen.java    → a corrida, os sons e o pódio

lwjgl3/.../Lwjgl3Launcher.java → abre a janela 800x600 no desktop
assets/                        → imagens PNG do jogo
assets/sounds/                 → efeitos sonoros WAV
```

Todas as classes têm **comentários em português** explicando o que cada função faz.

## 8. Como funciona a rede

### Arquitetura cliente-servidor

```
             ┌───────────────────────────┐
             │  PC do HOST               │
             │  ┌─────────────┐          │
             │  │ GameServer  │◄──┐      │
             │  └─────────────┘   │      │
             │  ┌─────────────┐   │127.0.0.1
             │  │ GameClient  │───┘      │
             │  └─────────────┘          │
             └────────▲──────────────────┘
                      │ TCP (IP do host : 5000)
       ┌──────────────┼──────────────┐
  ┌────┴─────┐   ┌────┴─────┐   ┌────┴─────┐
  │ Colega 1 │   │ Colega 2 │   │ Colega N │  (até 10 jogadores no total)
  └──────────┘   └──────────┘   └──────────┘
```

- **O servidor é a autoridade.** O cliente só envia a *intenção* (`MOVE;U`). Quem decide se o
  movimento é válido (não atravessa parede, não queimou a largada, não está rápido demais) é o
  servidor. Assim ninguém consegue trapacear alterando o próprio jogo.
- **Threads no servidor:** uma thread aceita conexões e **uma thread por jogador** lê as
  mensagens dele. Os métodos que alteram o estado são `synchronized`.
- **Threads no cliente:** uma thread lê o socket e coloca as mensagens numa **fila**
  (`ConcurrentLinkedQueue`). A cada frame a tela esvazia essa fila, e por isso o jogo não trava esperando a rede.
- **Contagem regressiva:** o servidor envia a **duração** (3000 ms), e não o horário de largada,
  porque o relógio de cada computador pode estar diferente. O mesmo vale para a duração dos
  efeitos (`ITEM;...;3000`) e para o tempo limite (`CLOSING;20000`).
- **Itens:** quem aplica o efeito é o **servidor**. Ele muda o intervalo mínimo entre movimentos
  da lebre: 70 ms normal, 40 ms com trevo e 220 ms na lama (`GameServer.minMoveInterval`).
  O cliente só ajusta a repetição da tecla para acompanhar e desenha o efeito.
- **Tempo limite:** quando a 1ª lebre chega, o servidor agenda um alarme com `java.util.Timer`.
  O alarme guarda o **número da corrida**: se o host já começou outra corrida, o alarme antigo é ignorado.
- **Portões:** o mesmo `Timer` roda uma tarefa a cada 100 ms (`scheduleAtFixedRate`) que avança o
  ciclo de cada portão e avisa os clientes com `GATE;x;y;S` (`GameServer.onGateTick`).
  Cada portão começa o ciclo num momento sorteado, para não fecharem todos juntos.
  - **Por que a cenoura nunca fica sem caminho?** O *Recursive Backtracker* gera um labirinto
    **perfeito** (um único caminho entre quaisquer duas casas). Os atalhos são paredes derrubadas
    *depois*, ou seja, caminhos **a mais**. Os portões só ficam nesses atalhos, então, mesmo com
    todos fechados, sobra o labirinto perfeito, que liga todas as casas (`Maze.getShortcuts`).
  - **Lebre em cima do portão:** o servidor não fecha; o portão continua piscando até ela sair.
- **Sons:** o `RaceState` não toca som. Ele anota os acontecimentos numa lista (`events`) e a
  `GameScreen` toca os sons a cada frame. Assim o modelo não depende do áudio do libGDX.

### Protocolo (texto, uma mensagem por linha, campos separados por `;`)

| Direção | Mensagem | Significado |
|---|---|---|
| Cliente → Servidor | `JOIN;nome` | Pede para entrar na sala |
| Cliente → Servidor | `START` | (só host) inicia a corrida |
| Cliente → Servidor | `MOVE;U` / `D` / `L` / `R` | Tenta mover para cima/baixo/esquerda/direita |
| Servidor → Cliente | `WELCOME;meuId;hostId` | Entrada aceita |
| Servidor → Cliente | `ERROR;mensagem` | Entrada recusada |
| Servidor → Cliente | `PLAYERS;id,nome,x,y\|...` | Lista de jogadores e posições |
| Servidor → Cliente | `MAZE;colunas;linhas;0101...` | Labirinto (`1` = parede, `0` = chão) |
| Servidor → Cliente | `GATES;x,y|...` | Posição dos portões (todos começam abertos) |
| Servidor → Cliente | `GATE;x;y;S` | Um portão mudou: `O` aberto, `A` aviso, `F` fechado |
| Servidor → Cliente | `ITEMS;x,y,T|...` | Itens da corrida (`C` = trevo, `M` = lama) |\|...` | Itens da corrida (`C` = trevo, `M` = lama) |
| Servidor → Cliente | `COUNTDOWN;3000` | A corrida começa em 3000 ms |
| Servidor → Cliente | `POS;id;x;y` | Uma lebre se moveu |
| Servidor → Cliente | `ITEM;id;x;y;T;ms` | A lebre pisou no item `T`; o efeito dura `ms` |
| Servidor → Cliente | `FINISH;id;nome;posicao;tempoMs` | Uma lebre chegou (1º, 2º, 3º...) |
| Servidor → Cliente | `CLOSING;20000` | A 1ª chegou: a corrida fecha em 20 s |
| Servidor → Cliente | `END` | Fim da corrida: mostrar o pódio |
| Servidor → Cliente | `LEFT;id` | Um jogador saiu |

Exemplo de uma partida curta:

```
Cliente → JOIN;Ana
Servidor → WELCOME;0;0
Servidor → PLAYERS;0,Ana,0,0
Cliente → START
Servidor → MAZE;25;17;1111111111111111111111111100000...
Servidor → GATES;9,4|19,4|2,14|...
Servidor → ITEMS;3,7,C|9,5,M|...
Servidor → PLAYERS;0,Ana,1,1
Servidor → COUNTDOWN;3000
Cliente → MOVE;R
Servidor → POS;0;2;1
...
Servidor → POS;0;3;7
Servidor → ITEM;0;3;7;C;3000        (Ana pegou um trevo)
...
Servidor → FINISH;0;Ana;1;18342     (Ana em 1º lugar)
Servidor → END                      (só ela na sala: acabou)
```

O console do host mostra mensagens `[SERVIDOR] ...` com cada entrada, saída, chegada e fim de corrida. Isso ajuda muito a depurar.

## 9. Imagens (PNG)

Ficam na pasta `assets/`. Todas têm **32×32 pixels**. Para trocar a arte, basta substituir o
arquivo **mantendo o nome e o tamanho**.

| Arquivo | Conteúdo | Regras importantes |
|---|---|---|
| `hare.png` | Lebre de lado, **virada para a direita** | **Branca/cinza clara** com **fundo transparente**. O jogo pinta a lebre com a cor de cada jogador (10 cores) e espelha a imagem quando ela anda para a esquerda |
| `wall.png` | Parede do labirinto (cerca viva) | Fundo cheio, com bom contraste com o chão |
| `floor.png` | Chão (terra batida) | Cor clara e neutra. Evite verde, que se confunde com a lebre verde |
| `carrot.png` | Cenoura da chegada | Fundo transparente e cor forte |
| `clover.png` | Trevo de quatro folhas (item) | Fundo transparente. Verde forte para destacar no chão |
| `gate.png` | Portão (barreira de madeira com X) | Fundo transparente. Precisa ser bem diferente da parede verde |
| `mud.png` | Poça de lama (item) | Fundo transparente. Marrom escuro, diferente do chão |

Se algum PNG estiver faltando, o jogo **não quebra**: a classe `Assets` cria um quadrado ou círculo colorido no lugar.

Programas gratuitos para desenhar: **Piskel** (online), **Aseprite** ou **LibreSprite**.

### Sons (WAV)

Ficam em `assets/sounds/`. São carregados com `Gdx.audio.newSound` (efeitos curtos).
Se algum arquivo faltar, aquele som apenas não toca.

| Arquivo | Quando toca |
|---|---|
| `countdown.wav` | Em cada número do "3, 2, 1" |
| `go.wav` | No "VAI!" |
| `clover.wav` | Quando **você** pega um trevo |
| `mud.wav` | Quando **você** pisa na lama |
| `finish.wav` | Quando uma lebre chega (mais alto se for você) |
| `win.wav` | Quando **você** chega em 1º lugar |
| `end.wav` | Quando a corrida acaba por tempo esgotado |
| `gate_warning.wav` | Um portão **perto de você** (até 6 casas) começou a piscar |
| `gate_close.wav` | Um portão **perto de você** fechou |

Para trocar um som, substitua o arquivo **mantendo o nome** (WAV ou OGG curto; se mudar a extensão,
ajuste o nome em `Sounds.java`). Sites com sons gratuitos: **freesound.org**, **opengameart.org**, **kenney.nl**.

### Música de fundo

As músicas também ficam em `assets/sounds/`, mas são carregadas com `Gdx.audio.newMusic`
(lidas aos poucos do disco e repetidas em loop). Foram **compostas por código**: o programa
`tools/GerarMusica.java` calcula cada amostra do som (ondas quadrada, triângulo e seno, mais ruído
para a bateria) e salva os WAV. Para gerar de novo: `java tools/GerarMusica.java`.

| Arquivo | Quando toca |
|---|---|
| `music_menu.wav` | Menu, sala de espera e pódio (calma, 90 BPM) |
| `music_race.wav` | Durante a corrida (animada, 140 BPM) |
| `music_race_fast.wav` | Nos 20 s finais, depois que a 1ª lebre chega (a mesma música, a 175 BPM) |

A tecla **M** desliga efeitos **e** música. Cada tela chama `Sounds.playMusic(...)` a cada quadro
dizendo qual música quer; o método só troca, pausa ou retoma quando algo muda.

## 10. Ideias de melhorias

Já feitas: **itens** (trevo e lama), **ranking** com pódio, **sons**, **música de fundo** e **portões** que abrem e fecham.

- **Animação da lebre:** sprite sheet `128×32` com 4 quadros, usando a classe `Animation` do libGDX.
- **Neblina:** cada jogador só enxerga algumas casas ao redor da própria lebre.
- **Placar** acumulado de pontos entre as corridas (ex.: 1º = 3 pontos, 2º = 2, 3º = 1).
- **Música de fundo** com a classe `Music` do libGDX (para arquivos longos).

---

## Comandos úteis do Gradle

| Comando | O que faz |
|---|---|
| `gradlew.bat lwjgl3:run` | Executa o jogo |
| `gradlew.bat lwjgl3:jar` | Gera o `.jar` em `lwjgl3/build/libs/` |
| `gradlew.bat clean` | Apaga as pastas `build` |
| `gradlew.bat build` | Compila tudo |

Projeto gerado com [gdx-liftoff](https://github.com/libgdx/gdx-liftoff). Módulos: `core` (lógica do jogo) e `lwjgl3` (versão desktop).
