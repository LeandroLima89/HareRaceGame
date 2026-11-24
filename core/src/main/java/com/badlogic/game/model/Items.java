package com.badlogic.game.model;

import com.badlogic.game.net.Protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Guarda os ITENS espalhados pelo labirinto (trevos e poças de lama).
 *
 * Funciona como uma "camada" por cima do labirinto: uma matriz do mesmo
 * tamanho, onde cada célula guarda o tipo do item que está nela:
 *   Protocol.CLOVER (trevo), Protocol.MUD (lama) ou Protocol.NO_EFFECT (vazio).
 *
 * Assim como o Maze, quem SORTEIA os itens é o servidor. Ele envia a lista
 * para os clientes, que só desenham.
 */
public class Items {

    private final int cols;
    private final int rows;

    /** grid[x][y] = tipo do item naquela célula. */
    private final char[][] grid;

    /** Cria uma camada de itens vazia. */
    public Items(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        this.grid = new char[cols][rows];
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                grid[x][y] = Protocol.NO_EFFECT;
            }
        }
    }

    /**
     * Sorteia os itens em células de CHÃO do labirinto.
     *
     * Regras do sorteio:
     * - nunca em parede, na largada, na chegada ou em cima de um portão;
     * - longe da largada (senão todo mundo pegaria o mesmo trevo no primeiro segundo);
     * - no máximo um item por célula.
     *
     * Técnica usada: listamos todas as células válidas, EMBARALHAMOS a lista
     * (Collections.shuffle) e pegamos as primeiras. Assim não há repetição.
     *
     * @param gates portões da corrida (pode ser null se não houver)
     */
    public static Items generate(Maze maze, Gates gates, Random random, int clovers, int muds,
                                 int minDistanceFromStart) {
        Items items = new Items(maze.getCols(), maze.getRows());

        List<int[]> candidates = new ArrayList<int[]>();
        for (int x = 0; x < maze.getCols(); x++) {
            for (int y = 0; y < maze.getRows(); y++) {
                if (maze.isWall(x, y)) {
                    continue;
                }
                if (x == maze.getFinishX() && y == maze.getFinishY()) {
                    continue;
                }
                if (gates != null && gates.find(x, y) != null) {
                    continue; // item em cima de portão ficaria preso quando ele fechasse
                }
                // Distância "Manhattan" (casas na horizontal + na vertical) até a
                // casa mais próxima da faixa de largada, para valer para TODAS as lebres.
                int distance = maze.distanceFromStartLane(x, y);
                if (distance < minDistanceFromStart) {
                    continue;
                }
                candidates.add(new int[]{x, y});
            }
        }

        Collections.shuffle(candidates, random);

        int index = 0;
        for (int i = 0; i < clovers && index < candidates.size(); i++, index++) {
            int[] cell = candidates.get(index);
            items.grid[cell[0]][cell[1]] = Protocol.CLOVER;
        }
        for (int i = 0; i < muds && index < candidates.size(); i++, index++) {
            int[] cell = candidates.get(index);
            items.grid[cell[0]][cell[1]] = Protocol.MUD;
        }
        return items;
    }

    /** Tipo do item na célula (Protocol.NO_EFFECT se estiver vazia ou fora do mapa). */
    public char get(int x, int y) {
        if (x < 0 || y < 0 || x >= cols || y >= rows) {
            return Protocol.NO_EFFECT;
        }
        return grid[x][y];
    }

    /** Remove o item da célula (usado quando alguém pega um trevo). */
    public void remove(int x, int y) {
        if (x >= 0 && y >= 0 && x < cols && y < rows) {
            grid[x][y] = Protocol.NO_EFFECT;
        }
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    /**
     * Converte os itens em texto para enviar pela rede: "x,y,T|x,y,T|...".
     * Enviamos só as células que TÊM item (são poucas), e não a matriz inteira.
     */
    public String toNetworkString() {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                if (grid[x][y] != Protocol.NO_EFFECT) {
                    if (sb.length() > 0) {
                        sb.append('|');
                    }
                    sb.append(x).append(',').append(y).append(',').append(grid[x][y]);
                }
            }
        }
        return sb.toString();
    }

    /** Caminho inverso de {@link #toNetworkString()}: monta os itens a partir do texto recebido. */
    public static Items fromNetworkString(int cols, int rows, String data) {
        Items items = new Items(cols, rows);
        if (data == null || data.isEmpty()) {
            return items;
        }
        for (String entry : data.split("\\|")) {
            String[] f = entry.split(",");
            int x = Integer.parseInt(f[0]);
            int y = Integer.parseInt(f[1]);
            char type = f[2].charAt(0);
            if (x >= 0 && y >= 0 && x < cols && y < rows
                && (type == Protocol.CLOVER || type == Protocol.MUD)) {
                items.grid[x][y] = type;
            }
        }
        return items;
    }
}
