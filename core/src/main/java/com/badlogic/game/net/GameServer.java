package com.badlogic.game.net;

import com.badlogic.game.Constants;
import com.badlogic.game.model.Gates;
import com.badlogic.game.model.Items;
import com.badlogic.game.model.Maze;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * SERVIDOR do jogo. Roda na máquina de quem escolheu "Criar sala" (o host).
 *
 * Ele é a "autoridade" da partida: é o servidor quem
 * - gera o labirinto e sorteia os itens (trevos e lama);
 * - decide se um movimento é válido (não atravessa parede, respeita a velocidade);
 * - aplica os efeitos dos itens;
 * - decide a ordem de chegada (1o, 2o e 3o lugar) e quando a corrida acaba.
 * Os clientes só enviam INTENÇÕES ("quero ir para cima") e desenham o que o
 * servidor manda. Assim ninguém consegue trapacear alterando o próprio jogo.
 *
 * Estrutura de threads:
 * - 1 thread aceita novas conexões (acceptLoop).
 * - 1 thread por jogador lê as mensagens daquele jogador (ClientConnection).
 * Como várias threads mexem nos mesmos dados, os métodos que alteram o estado
 * são "synchronized" (só uma thread entra por vez), evitando bugs de concorrência.
 */
public class GameServer {

    /** Fases da partida do lado do servidor. */
    private enum State {
        LOBBY,    // sala de espera, ninguém correu ainda
        RACING,   // corrida em andamento (inclui a contagem regressiva)
        FINISHED  // o pódio fechou (ou o tempo acabou), aguardando o host iniciar outra
    }

    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running;

    /** Jogadores que já fizeram JOIN com sucesso. Acesso sempre dentro de synchronized. */
    private final List<ClientConnection> clients = new ArrayList<ClientConnection>();

    private State state = State.LOBBY;
    private Maze maze;

    /** Trevos e poças de lama da corrida atual. */
    private Items items;

    /** Portões (atalhos que abrem e fecham) da corrida atual. */
    private Gates gates;

    /** Momento (relógio do servidor) em que a largada acontece, após a contagem. */
    private long raceStartTime;

    /** Quantas lebres já chegaram na cenoura nesta corrida (define o 1o, 2o, 3o...). */
    private int finishedCount;

    /**
     * Número da corrida atual (1, 2, 3...). Serve para o alarme do tempo limite
     * saber se ainda é da MESMA corrida: se o host já começou outra, o alarme
     * antigo é ignorado.
     */
    private int raceNumber;

    /**
     * Relógio que dispara tarefas no futuro ("daqui a 20 s, encerre a corrida").
     * true = thread daemon, não impede o programa de fechar.
     */
    private final Timer timer = new Timer("HareRace-Relogio", true);

    /** Id do jogador que pode iniciar a corrida (o primeiro a entrar = quem criou a sala). */
    private int hostId = -1;

    public GameServer(int port) {
        this.port = port;
    }

    /**
     * Abre a porta e começa a aceitar jogadores em uma thread separada.
     * Lança IOException se a porta já estiver em uso, por exemplo.
     */
    public void start() throws IOException {
        // Sem informar IP, o ServerSocket escuta em TODAS as placas de rede (0.0.0.0),
        // então os colegas conseguem conectar pelo IP da rede local.
        serverSocket = new ServerSocket(port);
        running = true;

        Thread acceptThread = new Thread(this::acceptLoop, "HareRace-Servidor");
        acceptThread.setDaemon(true); // não impede o programa de fechar
        acceptThread.start();
        log("Servidor iniciado na porta " + port);
    }

    /** Porta em que o servidor está escutando. */
    public int getPort() {
        return port;
    }

    /** Fica em loop esperando novos jogadores se conectarem. */
    private void acceptLoop() {
        while (running) {
            try {
                // accept() BLOQUEIA até alguém conectar. Por isso roda em thread própria.
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true); // envia mensagens pequenas na hora (menos atraso)
                log("Nova conexao de " + socket.getInetAddress().getHostAddress());
                new ClientConnection(socket).start();
            } catch (IOException e) {
                if (running) {
                    log("Erro ao aceitar conexao: " + e.getMessage());
                }
            }
        }
    }

    /** Desliga o servidor e desconecta todo mundo. */
    public void stop() {
        running = false;
        timer.cancel(); // cancela um eventual alarme de "tempo esgotado"
        try {
            if (serverSocket != null) {
                serverSocket.close(); // faz o accept() lançar exceção e o loop terminar
            }
        } catch (IOException ignored) {
        }
        List<ClientConnection> copy;
        synchronized (this) {
            copy = new ArrayList<ClientConnection>(clients);
            clients.clear();
        }
        for (ClientConnection c : copy) {
            c.close();
        }
        log("Servidor encerrado");
    }

    // ==================================================================
    // Tratamento das mensagens recebidas (todas synchronized)
    // ==================================================================

    /** Um cliente pediu para entrar na sala (mensagem JOIN). */
    private synchronized void onJoin(ClientConnection c, String rawName) {
        if (c.id >= 0) {
            return; // já entrou antes, ignora JOIN repetido
        }
        if (state == State.RACING) {
            c.send(Protocol.build(Protocol.ERROR, "Corrida em andamento. Tente quando acabar."));
            c.close();
            return;
        }
        if (clients.size() >= Constants.MAX_PLAYERS) {
            c.send(Protocol.build(Protocol.ERROR, "Sala cheia (maximo " + Constants.MAX_PLAYERS + ")."));
            c.close();
            return;
        }

        c.id = findFreeId();
        c.name = Protocol.sanitizeName(rawName);
        clients.add(c);
        if (hostId < 0) {
            hostId = c.id; // o primeiro a entrar vira o host
        }

        log(c.name + " entrou (id " + c.id + ")");
        c.send(Protocol.build(Protocol.WELCOME, c.id, hostId));
        broadcastPlayers();
    }

    /** O host pediu para iniciar a corrida (mensagem START). */
    private synchronized void onStart(ClientConnection c) {
        if (c.id != hostId || state == State.RACING) {
            return; // só o host pode iniciar, e não durante uma corrida
        }
        startNewRace();
    }

    /** Um jogador tentou se mover (mensagem MOVE). Aqui ficam as regras do jogo! */
    private synchronized void onMove(ClientConnection c, String direction) {
        if (state != State.RACING || c.id < 0) {
            return;
        }
        if (c.finishPosition > 0) {
            return; // esta lebre já chegou na cenoura: fica parada esperando as outras
        }
        long now = System.currentTimeMillis();
        if (now < raceStartTime) {
            return; // ainda está na contagem regressiva: queimou a largada, ignora
        }
        if (now - c.lastMoveTime < minMoveInterval(c, now)) {
            return; // anti-trapaça: movimentos rápidos demais são ignorados
        }

        // Converte a letra da direção em deslocamento (lembrando: y = 0 é o topo).
        int dx = 0;
        int dy = 0;
        if (Protocol.UP.equals(direction)) {
            dy = -1;
        } else if (Protocol.DOWN.equals(direction)) {
            dy = 1;
        } else if (Protocol.LEFT_DIR.equals(direction)) {
            dx = -1;
        } else if (Protocol.RIGHT_DIR.equals(direction)) {
            dx = 1;
        } else {
            return; // direção desconhecida
        }

        int newX = c.x + dx;
        int newY = c.y + dy;
        if (maze.isWall(newX, newY)) {
            return; // bateu na parede: movimento recusado
        }
        if (gates.isClosed(newX, newY)) {
            return; // portão fechado funciona como parede
        }

        // Movimento válido: atualiza e avisa TODO MUNDO.
        c.lastMoveTime = now;
        c.x = newX;
        c.y = newY;
        broadcast(Protocol.build(Protocol.POS, c.id, newX, newY));

        // Pisou em algum item?
        char item = items.get(newX, newY);
        if (item == Protocol.CLOVER) {
            items.remove(newX, newY); // o trevo é "comido" e some do mapa
            applyEffect(c, Protocol.CLOVER, Constants.CLOVER_DURATION_MS, now, newX, newY);
        } else if (item == Protocol.MUD) {
            // a lama NÃO some: quem passar depois também atola
            applyEffect(c, Protocol.MUD, Constants.MUD_DURATION_MS, now, newX, newY);
        }

        // Chegou na cenoura?
        if (newX == maze.getFinishX() && newY == maze.getFinishY()) {
            onFinish(c, now);
        }
    }

    /**
     * Uma lebre chegou na cenoura: recebe a próxima posição do ranking.
     * A corrida só termina quando o pódio fecha, quando todos chegam
     * ou quando acaba o tempo limite.
     */
    private void onFinish(ClientConnection c, long now) {
        finishedCount++;
        c.finishPosition = finishedCount;
        long raceTime = now - raceStartTime;
        log(c.name + " chegou em " + c.finishPosition + "o lugar (" + raceTime + " ms)");
        broadcast(Protocol.build(Protocol.FINISH, c.id, c.name, c.finishPosition, raceTime));

        if (isRaceComplete()) {
            endRace("podio completo");
            return;
        }

        // Foi a PRIMEIRA a chegar: liga o alarme do tempo limite para os demais.
        if (finishedCount == 1) {
            broadcast(Protocol.build(Protocol.CLOSING, Constants.FINISH_TIMEOUT_MS));
            final int race = raceNumber; // "fotografa" o número desta corrida
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    onFinishTimeout(race);
                }
            }, Constants.FINISH_TIMEOUT_MS);
        }
    }

    /** Chamado pelo Timer quando o tempo limite acaba (roda na thread do Timer). */
    private synchronized void onFinishTimeout(int race) {
        // Só encerra se ainda for a mesma corrida e ela ainda não tiver acabado.
        if (race == raceNumber && state == State.RACING) {
            endRace("tempo esgotado");
        }
    }

    /** Um jogador desconectou (fechou o jogo, caiu a rede etc.). */
    private synchronized void onDisconnect(ClientConnection c) {
        if (clients.remove(c)) {
            log(c.name + " saiu (id " + c.id + ")");
            broadcast(Protocol.build(Protocol.LEFT, c.id));
            if (c.id == hostId) {
                // Se o host sair mas o servidor continuar, o próximo jogador vira host.
                hostId = clients.isEmpty() ? -1 : clients.get(0).id;
            }
            // Quem saiu pode ser o único que faltava chegar: talvez a corrida tenha acabado.
            if (state == State.RACING && isRaceComplete()) {
                endRace("todos os que restaram chegaram");
            }
        }
    }

    // ==================================================================
    // Métodos auxiliares (chamados sempre de dentro de métodos synchronized)
    // ==================================================================

    /**
     * Intervalo mínimo entre dois movimentos desta lebre, de acordo com o efeito ativo.
     * É AQUI que o trevo e a lama realmente funcionam: como o servidor é a
     * autoridade, não adianta o cliente "fingir" que pegou um trevo.
     */
    private long minMoveInterval(ClientConnection c, long now) {
        if (now < c.effectEndTime) {
            if (c.effect == Protocol.CLOVER) {
                return Constants.SERVER_MIN_MOVE_INTERVAL_CLOVER_MS;
            }
            if (c.effect == Protocol.MUD) {
                return Constants.SERVER_MIN_MOVE_INTERVAL_MUD_MS;
            }
        }
        return Constants.SERVER_MIN_MOVE_INTERVAL_MS;
    }

    /**
     * Aplica um efeito na lebre e avisa todo mundo (para tocar som e mostrar o ícone).
     * Um efeito novo SUBSTITUI o anterior: pegar um trevo tira a lebre da lama.
     */
    private void applyEffect(ClientConnection c, char effect, long duration, long now, int x, int y) {
        c.effect = effect;
        c.effectEndTime = now + duration;
        broadcast(Protocol.build(Protocol.ITEM, c.id, x, y, effect, duration));
    }

    /**
     * A corrida acabou? Sim se o pódio fechou (3 chegaram) ou se TODOS os
     * jogadores ainda conectados já chegaram (ex.: só 2 jogadores na sala).
     */
    private boolean isRaceComplete() {
        if (finishedCount >= Constants.PODIUM_PLACES) {
            return true;
        }
        for (ClientConnection c : clients) {
            if (c.finishPosition == 0) {
                return false; // ainda tem alguém correndo
            }
        }
        return true;
    }

    /** Encerra a corrida e avisa os clientes. O ranking eles já receberam nos FINISH. */
    private void endRace(String reason) {
        state = State.FINISHED;
        log("Fim da corrida (" + reason + ")");
        broadcast(Protocol.END);
    }

    /** Gera um novo labirinto, sorteia portões e itens, coloca todos na largada e começa a contagem. */
    private void startNewRace() {
        long seed = System.nanoTime();
        Random random = new Random(seed + 1);
        maze = Maze.generate(Constants.MAZE_COLS, Constants.MAZE_ROWS, seed, Constants.EXTRA_PASSAGES);
        maze.carveStartLane(clients.size()); // uma casa de largada para cada lebre
        maze.ensureShortcuts(Constants.GATE_MAX_COUNT, random); // atalhos suficientes para os portões
        gates = Gates.choose(maze, random, Constants.GATE_MIN_COUNT, Constants.GATE_MAX_COUNT);
        items = Items.generate(maze, gates, random, Constants.CLOVER_COUNT,
            Constants.MUD_COUNT, Constants.ITEM_MIN_DISTANCE_FROM_START);

        // Cada lebre recebe a sua vaga na faixa de largada, na ordem de entrada
        // na sala: o host (primeiro da lista) fica no topo e os outros logo abaixo.
        for (int slot = 0; slot < clients.size(); slot++) {
            ClientConnection c = clients.get(slot);
            c.x = maze.getStartX(slot);
            c.y = maze.getStartY(slot);
            c.lastMoveTime = 0;
            c.finishPosition = 0;
            c.effect = Protocol.NO_EFFECT;
            c.effectEndTime = 0;
        }
        finishedCount = 0;
        raceNumber++;
        state = State.RACING;
        raceStartTime = System.currentTimeMillis() + Constants.COUNTDOWN_MS;

        // Cada portão começa ABERTO e dá o primeiro aviso num momento sorteado
        // depois da largada. Assim eles não fecham todos ao mesmo tempo.
        for (Gates.Gate gate : gates.all()) {
            gate.state = Protocol.GATE_OPEN;
            gate.nextChangeTime = raceStartTime + 1000 + random.nextInt((int) Constants.GATE_FIRST_CHANGE_MAX_MS);
        }

        log("Nova corrida com " + clients.size() + " jogador(es) e " + gates.all().size() + " portoes");
        // A ordem importa: primeiro o mapa, depois portões e itens, as posições e por fim a contagem.
        broadcast(Protocol.build(Protocol.MAZE, maze.getCols(), maze.getRows(), maze.toNetworkString()));
        broadcast(Protocol.build(Protocol.GATES, gates.toNetworkString()));
        broadcast(Protocol.build(Protocol.ITEMS, items.toNetworkString()));
        broadcastPlayers();
        // Mandamos a DURAÇÃO da contagem (e não o horário), porque o relógio de cada
        // computador pode estar diferente. Cada cliente soma com o próprio relógio.
        broadcast(Protocol.build(Protocol.COUNTDOWN, Constants.COUNTDOWN_MS));

        startGateTimer();
    }

    /**
     * Liga um "relógio" que, a cada GATE_TICK_MS, chama onGateTick.
     * scheduleAtFixedRate = repete a tarefa em intervalos fixos.
     * A tarefa se cancela sozinha quando a corrida acaba ou outra começa.
     */
    private void startGateTimer() {
        final int race = raceNumber; // "fotografa" o número desta corrida
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!onGateTick(race)) {
                    cancel(); // esta corrida acabou: para de repetir
                }
            }
        }, Constants.GATE_TICK_MS, Constants.GATE_TICK_MS);
    }

    /**
     * Avança o ciclo dos portões cujo tempo chegou: ABERTO -> AVISO -> FECHADO -> ABERTO.
     *
     * @return false se a corrida "race" já terminou (o timer deve parar)
     */
    private synchronized boolean onGateTick(int race) {
        if (race != raceNumber || state != State.RACING) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Gates.Gate gate : gates.all()) {
            if (now < gate.nextChangeTime) {
                continue; // ainda não é hora de mudar
            }
            if (gate.state == Protocol.GATE_OPEN) {
                gate.state = Protocol.GATE_WARNING;
                gate.nextChangeTime = now + Constants.GATE_WARNING_MS;
            } else if (gate.state == Protocol.GATE_WARNING) {
                if (isOccupied(gate.x, gate.y)) {
                    // Tem lebre em cima do portão: fechar agora a prenderia DENTRO da
                    // parede. Continuamos no aviso e tentamos de novo no próximo tick.
                    continue;
                }
                gate.state = Protocol.GATE_CLOSED;
                gate.nextChangeTime = now + Constants.GATE_CLOSED_MS;
            } else {
                gate.state = Protocol.GATE_OPEN;
                gate.nextChangeTime = now + Constants.GATE_OPEN_MS;
            }
            broadcast(Protocol.build(Protocol.GATE, gate.x, gate.y, gate.state));
        }
        return true;
    }

    /** true se alguma lebre está na célula (x, y). */
    private boolean isOccupied(int x, int y) {
        for (ClientConnection c : clients) {
            if (c.x == x && c.y == y) {
                return true;
            }
        }
        return false;
    }

    /** Envia a lista completa de jogadores: "PLAYERS;0,Ana,1,1|1,Beto,3,5". */
    private void broadcastPlayers() {
        StringBuilder sb = new StringBuilder();
        for (ClientConnection c : clients) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(c.id).append(',').append(c.name).append(',').append(c.x).append(',').append(c.y);
        }
        broadcast(Protocol.build(Protocol.PLAYERS, sb.toString()));
    }

    /** Envia a mesma mensagem para todos os jogadores conectados. */
    private void broadcast(String message) {
        for (ClientConnection c : clients) {
            c.send(message);
        }
    }

    /** Procura o menor id livre (0..9). Reaproveitar ids mantém as cores entre 10 opções. */
    private int findFreeId() {
        for (int id = 0; id < Constants.MAX_PLAYERS; id++) {
            boolean used = false;
            for (ClientConnection c : clients) {
                if (c.id == id) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return id;
            }
        }
        return -1; // não deve acontecer, pois verificamos a lotação antes
    }

    private static void log(String message) {
        System.out.println("[SERVIDOR] " + message);
    }

    // ==================================================================
    // Classe interna: representa UM jogador conectado ao servidor
    // ==================================================================

    /**
     * Cada jogador conectado ganha um objeto desta classe, que é uma Thread.
     * Ela fica lendo linha por linha o que o jogador manda e repassa para os
     * métodos onJoin/onStart/onMove do servidor.
     */
    private class ClientConnection extends Thread {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;

        // Dados do jogador mantidos pelo servidor (a "verdade" do jogo).
        int id = -1;
        String name = "?";
        int x;
        int y;
        long lastMoveTime;
        int finishPosition;                 // 0 = ainda correndo; 1, 2, 3... = posição de chegada
        char effect = Protocol.NO_EFFECT;   // efeito ativo (trevo ou lama)
        long effectEndTime;                 // até quando o efeito vale (relógio do servidor)

        ClientConnection(Socket socket) throws IOException {
            super("HareRace-Jogador");
            setDaemon(true);
            this.socket = socket;
            // Usamos UTF-8 nos dois lados para nomes com acento funcionarem.
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public void run() {
            try {
                String line;
                // readLine() bloqueia até chegar uma linha; retorna null quando a conexão fecha.
                while ((line = in.readLine()) != null) {
                    handleLine(line);
                }
            } catch (IOException e) {
                // conexão caiu: tratamos no finally
            } finally {
                onDisconnect(this);
                close();
            }
        }

        /** Descobre o tipo da mensagem (primeiro campo) e chama o método certo. */
        private void handleLine(String line) {
            String[] parts = line.split(Protocol.SEP, -1);
            String type = parts[0];
            if (Protocol.JOIN.equals(type)) {
                onJoin(this, parts.length > 1 ? parts[1] : "");
            } else if (Protocol.START.equals(type)) {
                onStart(this);
            } else if (Protocol.MOVE.equals(type) && parts.length > 1) {
                onMove(this, parts[1]);
            }
            // Mensagens desconhecidas são ignoradas.
        }

        /** Envia uma linha para este jogador. synchronized para duas threads não misturarem textos. */
        void send(String message) {
            synchronized (out) {
                out.print(message);
                out.print('\n');
                out.flush();
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
