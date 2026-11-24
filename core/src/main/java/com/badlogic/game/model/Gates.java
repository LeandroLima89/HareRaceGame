package com.badlogic.game.model;

import com.badlogic.game.net.Protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Os PORTÕES do labirinto: atalhos que abrem e fecham em ciclo.
 *
 *   ABERTO --(6 s)--> AVISO (pisca) --(1,5 s)--> FECHADO --(6 s)--> ABERTO ...
 *
 * Enquanto está FECHADO, o portão funciona como uma parede: o servidor recusa
 * movimentos para dentro dele. O jogador pode esperar abrir ou dar a volta.
 *
 * Os portões só são colocados nos ATALHOS do labirinto (veja Maze.getShortcuts),
 * o que garante que a cenoura nunca fica sem caminho.
 */
public class Gates {

    /** Um portão. */
    public static class Gate {
        public final int x;
        public final int y;

        /** Protocol.GATE_OPEN, GATE_WARNING ou GATE_CLOSED. */
        public char state = Protocol.GATE_OPEN;

        /**
         * Quando este portão deve mudar de estado (relógio do SERVIDOR).
         * Só o servidor usa este campo; no cliente ele fica sempre 0.
         */
        public long nextChangeTime;

        public Gate(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private final List<Gate> list = new ArrayList<Gate>();

    /**
     * Sorteia os portões entre os atalhos do labirinto.
     * Se o labirinto tiver menos atalhos do que o mínimo, usa todos que existirem.
     */
    public static Gates choose(Maze maze, Random random, int minCount, int maxCount) {
        List<int[]> candidates = new ArrayList<int[]>(maze.getShortcuts());
        Collections.shuffle(candidates, random);

        // nextInt(n) sorteia de 0 a n-1, então min + nextInt(max - min + 1) vai de min a max.
        int count = minCount + random.nextInt(maxCount - minCount + 1);
        count = Math.min(count, candidates.size());

        Gates gates = new Gates();
        for (int i = 0; i < count; i++) {
            int[] cell = candidates.get(i);
            gates.list.add(new Gate(cell[0], cell[1]));
        }
        return gates;
    }

    /** Todos os portões (para percorrer com for). */
    public List<Gate> all() {
        return list;
    }

    /** O portão na célula (x, y), ou null se não houver. */
    public Gate find(int x, int y) {
        for (Gate gate : list) {
            if (gate.x == x && gate.y == y) {
                return gate;
            }
        }
        return null;
    }

    /** true se existe um portão FECHADO na célula. */
    public boolean isClosed(int x, int y) {
        Gate gate = find(x, y);
        return gate != null && gate.state == Protocol.GATE_CLOSED;
    }

    /** Texto para a rede: "x,y|x,y|...". Todos começam abertos, então só a posição basta. */
    public String toNetworkString() {
        StringBuilder sb = new StringBuilder();
        for (Gate gate : list) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(gate.x).append(',').append(gate.y);
        }
        return sb.toString();
    }

    /** Caminho inverso de {@link #toNetworkString()}. */
    public static Gates fromNetworkString(String data) {
        Gates gates = new Gates();
        if (data == null || data.isEmpty()) {
            return gates;
        }
        for (String entry : data.split("\\|")) {
            String[] f = entry.split(",");
            gates.list.add(new Gate(Integer.parseInt(f[0]), Integer.parseInt(f[1])));
        }
        return gates;
    }
}
