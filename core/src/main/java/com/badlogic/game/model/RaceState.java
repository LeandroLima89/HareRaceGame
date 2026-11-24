package com.badlogic.game.model;

import com.badlogic.game.Constants;
import com.badlogic.game.net.GameClient;
import com.badlogic.game.net.Protocol;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Guarda tudo o que o CLIENTE sabe sobre a partida: quem eu sou, quem está
 * na sala, o labirinto atual, os itens, a contagem e o ranking.
 *
 * Este estado é atualizado SOMENTE pelas mensagens que chegam do servidor
 * (método handle). As telas apenas leem estes dados para desenhar.
 */
public class RaceState {

    /** Fases da partida do ponto de vista do cliente. */
    public enum Phase {
        LOBBY,    // na sala de espera
        RACE,     // corrida (inclui a contagem regressiva e a espera depois de chegar)
        FINISHED  // corrida encerrada: mostrar o pódio
    }

    /**
     * Acontecimentos que merecem um SOM. O estado não toca som (isso é
     * trabalho da tela); ele só anota o que aconteceu numa lista, e a
     * GameScreen esvazia essa lista a cada frame tocando os sons.
     * Assim o "modelo" não depende do áudio do libGDX.
     */
    public enum Event {
        MY_CLOVER,     // EU peguei um trevo
        MY_MUD,        // EU pisei na lama
        I_WON,         // EU cheguei em 1o lugar
        I_FINISHED,    // EU cheguei (2o, 3o...)
        OTHER_FINISHED, // outra lebre chegou
        RACE_OVER,     // a corrida acabou
        GATE_WARNING_NEAR, // um portão PERTO de mim vai fechar
        GATE_CLOSED_NEAR   // um portão PERTO de mim fechou
    }

    /** Uma linha do ranking: quem chegou, em que posição e com que tempo. */
    public static class FinishEntry {
        public final int id;
        public final String name;
        public final int position;
        public final long timeMillis;

        public FinishEntry(int id, String name, int position, long timeMillis) {
            this.id = id;
            this.name = name;
            this.position = position;
            this.timeMillis = timeMillis;
        }
    }

    public Phase phase = Phase.LOBBY;

    /** Meu id (recebido no WELCOME) e o id do host. */
    public int myId = -1;
    public int hostId = -1;

    /** Labirinto da corrida atual (null enquanto estiver no lobby). */
    public Maze maze;

    /** Trevos e lama da corrida atual (null enquanto estiver no lobby). */
    public Items items;

    /** Portões da corrida atual (null enquanto estiver no lobby). */
    public Gates gates;

    /** Lebres indexadas pelo id. TreeMap mantém a ordem 0, 1, 2... */
    public final Map<Integer, Hare> hares = new TreeMap<Integer, Hare>();

    /** Momento (relógio LOCAL) em que a contagem termina e a corrida começa. */
    public long countdownEndMillis;

    /**
     * Ranking em ordem de chegada. Guardamos o NOME aqui (e não só o id)
     * porque o jogador pode sair da sala depois de chegar e mesmo assim
     * precisa aparecer no pódio.
     */
    public final List<FinishEntry> ranking = new ArrayList<FinishEntry>();

    /** Momento (relógio LOCAL) em que a corrida fecha por tempo. 0 = ninguém chegou ainda. */
    public long closingEndMillis;

    /** Sons pendentes (ver {@link Event}). */
    public final List<Event> events = new ArrayList<Event>();

    /** Erro enviado pelo servidor (ex.: sala cheia). */
    public String errorMessage;

    /** true quando a conexão com o servidor caiu. */
    public boolean disconnected;

    /** Processa TODAS as mensagens que estão na fila do cliente. Chamado a cada frame. */
    public void processMessages(GameClient client) {
        String line;
        while ((line = client.poll()) != null) {
            handle(line);
        }
    }

    /** Interpreta uma mensagem do servidor e atualiza o estado. */
    public void handle(String line) {
        String[] p = line.split(Protocol.SEP, -1);
        String type = p[0];
        try {
            if (Protocol.WELCOME.equals(type)) {
                myId = Integer.parseInt(p[1]);
                hostId = Integer.parseInt(p[2]);

            } else if (Protocol.ERROR.equals(type)) {
                errorMessage = p.length > 1 ? p[1] : "Erro desconhecido";

            } else if (Protocol.PLAYERS.equals(type)) {
                handlePlayers(p.length > 1 ? p[1] : "");

            } else if (Protocol.MAZE.equals(type)) {
                int cols = Integer.parseInt(p[1]);
                int rows = Integer.parseInt(p[2]);
                maze = Maze.fromNetworkString(cols, rows, p[3]);
                items = new Items(cols, rows); // itens e portões chegam logo depois
                gates = new Gates();

            } else if (Protocol.GATES.equals(type)) {
                gates = Gates.fromNetworkString(p.length > 1 ? p[1] : "");

            } else if (Protocol.GATE.equals(type)) {
                handleGate(p);

            } else if (Protocol.ITEMS.equals(type)) {
                if (maze != null) {
                    items = Items.fromNetworkString(maze.getCols(), maze.getRows(), p.length > 1 ? p[1] : "");
                }

            } else if (Protocol.COUNTDOWN.equals(type)) {
                // Uma nova corrida vai começar: zera tudo da corrida anterior.
                long duration = Long.parseLong(p[1]);
                countdownEndMillis = TimeUtils.millis() + duration;
                ranking.clear();
                closingEndMillis = 0;
                events.clear();
                for (Hare hare : hares.values()) {
                    hare.resetForRace();
                }
                phase = Phase.RACE;

            } else if (Protocol.POS.equals(type)) {
                Hare hare = hares.get(Integer.parseInt(p[1]));
                if (hare != null) {
                    // snap = false: a lebre desliza até a nova posição
                    hare.setGridPosition(Integer.parseInt(p[2]), Integer.parseInt(p[3]), false);
                }

            } else if (Protocol.ITEM.equals(type)) {
                handleItem(p);

            } else if (Protocol.FINISH.equals(type)) {
                handleFinish(p);

            } else if (Protocol.CLOSING.equals(type)) {
                closingEndMillis = TimeUtils.millis() + Long.parseLong(p[1]);

            } else if (Protocol.END.equals(type)) {
                phase = Phase.FINISHED;
                closingEndMillis = 0;
                events.add(Event.RACE_OVER);

            } else if (Protocol.LEFT.equals(type)) {
                int id = Integer.parseInt(p[1]);
                hares.remove(id);
                if (id == hostId && !hares.isEmpty()) {
                    // mesma regra do servidor: o primeiro da lista vira host
                    hostId = hares.keySet().iterator().next();
                }

            } else if (Protocol.DISCONNECTED.equals(type)) {
                disconnected = true;
            }
        } catch (RuntimeException e) {
            // Mensagem mal formada (NumberFormatException, índice inválido...): ignoramos
            // para que um erro de rede não derrube o jogo inteiro.
            System.err.println("Mensagem invalida recebida: " + line);
        }
    }

    /** ITEM;id;x;y;T;ms -> uma lebre pisou num trevo ou na lama. */
    private void handleItem(String[] p) {
        int id = Integer.parseInt(p[1]);
        int x = Integer.parseInt(p[2]);
        int y = Integer.parseInt(p[3]);
        char itemType = p[4].charAt(0);
        long duration = Long.parseLong(p[5]);

        // O trevo some do mapa de todo mundo; a lama continua lá.
        if (itemType == Protocol.CLOVER && items != null) {
            items.remove(x, y);
        }
        Hare hare = hares.get(id);
        if (hare != null) {
            hare.setEffect(itemType, duration);
        }
        // Som só para os MEUS itens (10 lebres pegando itens viraria barulho).
        if (id == myId) {
            events.add(itemType == Protocol.CLOVER ? Event.MY_CLOVER : Event.MY_MUD);
        }
    }

    /** GATE;x;y;S -> um portão mudou de estado. */
    private void handleGate(String[] p) {
        if (gates == null) {
            return;
        }
        int x = Integer.parseInt(p[1]);
        int y = Integer.parseInt(p[2]);
        char newState = p[3].charAt(0);
        Gates.Gate gate = gates.find(x, y);
        if (gate == null) {
            return;
        }
        gate.state = newState;

        // Som só para portões PERTO da minha lebre: com 8 portões mudando o tempo
        // todo, tocar todos viraria barulho. Distância "Manhattan" em casas.
        Hare me = getMyHare();
        if (me != null && me.finishPosition == 0) {
            int distance = Math.abs(me.gridX - x) + Math.abs(me.gridY - y);
            if (distance <= Constants.GATE_SOUND_DISTANCE) {
                if (newState == Protocol.GATE_WARNING) {
                    events.add(Event.GATE_WARNING_NEAR);
                } else if (newState == Protocol.GATE_CLOSED) {
                    events.add(Event.GATE_CLOSED_NEAR);
                }
            }
        }
    }

    /** FINISH;id;nome;pos;tempoMs -> uma lebre chegou na cenoura. */
    private void handleFinish(String[] p) {
        int id = Integer.parseInt(p[1]);
        String name = p[2];
        int position = Integer.parseInt(p[3]);
        long time = Long.parseLong(p[4]);

        ranking.add(new FinishEntry(id, name, position, time));
        Hare hare = hares.get(id);
        if (hare != null) {
            hare.finishPosition = position;
        }

        if (id == myId) {
            events.add(position == 1 ? Event.I_WON : Event.I_FINISHED);
        } else {
            events.add(Event.OTHER_FINISHED);
        }
    }

    /** Lê a lista "id,nome,x,y|id,nome,x,y" e sincroniza o mapa de lebres. */
    private void handlePlayers(String list) {
        Set<Integer> present = new HashSet<Integer>();
        if (!list.isEmpty()) {
            for (String entry : list.split("\\|")) {
                String[] f = entry.split(",");
                int id = Integer.parseInt(f[0]);
                Hare hare = hares.get(id);
                if (hare == null) {
                    hare = new Hare(id);
                    hares.put(id, hare);
                }
                hare.name = f[1];
                hare.setGridPosition(Integer.parseInt(f[2]), Integer.parseInt(f[3]), true);
                present.add(id);
            }
        }
        // Remove quem não está mais na lista.
        hares.keySet().retainAll(present);
    }

    /** true se eu sou o host (posso iniciar a corrida). */
    public boolean isHost() {
        return myId >= 0 && myId == hostId;
    }

    /** true durante o "3, 2, 1". */
    public boolean isCountingDown() {
        return phase == Phase.RACE && TimeUtils.millis() < countdownEndMillis;
    }

    /** Minha própria lebre (pode ser null antes do WELCOME). */
    public Hare getMyHare() {
        return hares.get(myId);
    }

    /** true se a minha lebre já chegou na cenoura nesta corrida. */
    public boolean iHaveFinished() {
        Hare me = getMyHare();
        return me != null && me.finishPosition > 0;
    }
}
