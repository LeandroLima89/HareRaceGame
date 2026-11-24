package com.badlogic.game.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Representa o labirinto como uma grade (matriz) de células.
 *
 * Cada célula é PAREDE (true) ou CHÃO (false).
 * Coordenadas: x = coluna (0 à esquerda), y = linha (0 no TOPO).
 *
 * Quem gera o labirinto é sempre o SERVIDOR. Depois ele envia a grade
 * pronta para todos os clientes, garantindo que todos vejam o mesmo mapa.
 */
public class Maze {

    private final int cols;
    private final int rows;

    /** walls[x][y] == true significa que existe uma parede naquela célula. */
    private final boolean[][] walls;

    /**
     * Células que ERAM parede e foram derrubadas para criar atalhos (passo extra
     * da geração). Só o servidor conhece esta lista; ela não vai pela rede.
     *
     * Por que ela é importante? O labirinto gerado pelo Recursive Backtracker é
     * "perfeito": existe UM caminho entre quaisquer duas casas. Os atalhos são
     * apenas caminhos A MAIS. Então, se fecharmos qualquer combinação de atalhos,
     * no pior caso voltamos ao labirinto perfeito, e a cenoura continua
     * alcançável. É por isso que os PORTÕES só podem ficar nessas células.
     */
    private final List<int[]> shortcuts = new ArrayList<int[]>();

    /** Cria um labirinto "vazio" (sem paredes) com o tamanho informado. */
    public Maze(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        this.walls = new boolean[cols][rows];
    }

    /**
     * Gera um labirinto aleatório usando o algoritmo "Recursive Backtracker"
     * (busca em profundidade com pilha).
     *
     * Ideia do algoritmo:
     * 1. Começa com TUDO sendo parede.
     * 2. Parte da célula (1,1) e marca como chão.
     * 3. Escolhe um vizinho a 2 casas de distância que ainda é parede,
     *    "cava" a parede do meio e vai para ele (empilha).
     * 4. Se não houver vizinho disponível, volta (desempilha).
     * 5. Termina quando a pilha esvazia: todas as células foram visitadas.
     *
     * @param seed           semente do gerador aleatório (mesma semente = mesmo labirinto)
     * @param extraPassages  chance (0..1) de derrubar paredes extras para criar atalhos
     */
    public static Maze generate(int cols, int rows, long seed, float extraPassages) {
        Maze maze = new Maze(cols, rows);
        Random random = new Random(seed);

        // Passo 1: tudo vira parede.
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                maze.walls[x][y] = true;
            }
        }

        // Direções possíveis, andando de 2 em 2 (cima, direita, baixo, esquerda).
        int[][] directions = {{0, -2}, {2, 0}, {0, 2}, {-2, 0}};

        // Passo 2: começa na célula (1,1).
        Deque<int[]> stack = new ArrayDeque<int[]>();
        maze.walls[1][1] = false;
        stack.push(new int[]{1, 1});

        while (!stack.isEmpty()) {
            int[] current = stack.peek();

            // Procura vizinhos que ainda não foram visitados (continuam sendo parede).
            List<int[]> neighbors = new ArrayList<int[]>();
            for (int[] d : directions) {
                int nx = current[0] + d[0];
                int ny = current[1] + d[1];
                boolean inside = nx > 0 && ny > 0 && nx < cols - 1 && ny < rows - 1;
                if (inside && maze.walls[nx][ny]) {
                    neighbors.add(new int[]{nx, ny});
                }
            }

            if (neighbors.isEmpty()) {
                // Passo 4: beco sem saída -> volta para a célula anterior.
                stack.pop();
            } else {
                // Passo 3: sorteia um vizinho, derruba a parede do meio e avança.
                int[] next = neighbors.get(random.nextInt(neighbors.size()));
                int wallX = (current[0] + next[0]) / 2;
                int wallY = (current[1] + next[1]) / 2;
                maze.walls[wallX][wallY] = false;
                maze.walls[next[0]][next[1]] = false;
                stack.push(next);
            }
        }

        // Extra: derruba algumas paredes "finas" (entre dois corredores) para criar atalhos.
        for (int x = 1; x < cols - 1; x++) {
            for (int y = 1; y < rows - 1; y++) {
                if (maze.isThinWall(x, y) && random.nextFloat() < extraPassages) {
                    maze.openShortcut(x, y);
                }
            }
        }

        return maze;
    }

    /**
     * Garante que o labirinto tenha pelo menos "min" atalhos, derrubando mais
     * paredes finas se o sorteio do generate() tiver criado poucos.
     * O servidor usa isto para sempre ter atalhos suficientes para os portões.
     */
    public void ensureShortcuts(int min, Random random) {
        if (shortcuts.size() >= min) {
            return;
        }
        // Lista as paredes finas que ainda existem e embaralha.
        List<int[]> candidates = new ArrayList<int[]>();
        for (int x = 1; x < cols - 1; x++) {
            for (int y = 1; y < rows - 1; y++) {
                if (isThinWall(x, y)) {
                    candidates.add(new int[]{x, y});
                }
            }
        }
        Collections.shuffle(candidates, random);
        for (int[] cell : candidates) {
            if (shortcuts.size() >= min) {
                break;
            }
            // Confere de novo: derrubar uma parede vizinha pode ter mudado esta célula.
            if (isThinWall(cell[0], cell[1])) {
                openShortcut(cell[0], cell[1]);
            }
        }
    }

    /**
     * Parede "fina": tem chão dos DOIS lados opostos (esquerda/direita ou
     * cima/baixo) e parede nos outros dois. Derrubar uma parede assim liga
     * dois corredores que já existiam, ou seja, cria um atalho.
     */
    private boolean isThinWall(int x, int y) {
        if (!walls[x][y]) {
            return false;
        }
        boolean horizontalGap = !walls[x - 1][y] && !walls[x + 1][y] && walls[x][y - 1] && walls[x][y + 1];
        boolean verticalGap = !walls[x][y - 1] && !walls[x][y + 1] && walls[x - 1][y] && walls[x + 1][y];
        return horizontalGap || verticalGap;
    }

    /** Derruba a parede (x, y) e anota como atalho (candidata a portão). */
    private void openShortcut(int x, int y) {
        walls[x][y] = false;
        shortcuts.add(new int[]{x, y});
    }

    /** Diz se a posição (x, y) é parede. Fora do mapa também conta como parede. */
    public boolean isWall(int x, int y) {
        if (x < 0 || y < 0 || x >= cols || y >= rows) {
            return true;
        }
        return walls[x][y];
    }

    /**
     * Cria a FAIXA DE LARGADA: um corredor reto na coluna 1, começando no topo,
     * com uma casa para cada lebre (uma embaixo da outra). Assim nenhuma lebre
     * nasce em cima da outra.
     *
     * Por que precisamos "cavar"? No labirinto gerado, só as casas de linha ÍMPAR
     * da coluna 1 são chão garantido; as de linha PAR costumam ser parede. Aqui
     * transformamos todas as casas da faixa em chão.
     *
     * Isso nunca bloqueia o caminho até a cenoura, porque só TROCAMOS parede por
     * chão (criamos caminhos, nunca fechamos). Chamar ANTES de ensureShortcuts.
     *
     * @param players quantas lebres vão correr (o tamanho da faixa)
     */
    public void carveStartLane(int players) {
        // A faixa não pode passar da última linha jogável (rows - 2).
        startLaneLength = Math.max(1, Math.min(players, rows - 2));
        for (int i = 0; i < startLaneLength; i++) {
            walls[getStartX(i)][getStartY(i)] = false;
        }
        // Se o sorteio de atalhos tinha derrubado uma parede dentro da faixa,
        // tiramos ela da lista: não queremos um PORTÃO bem no meio da largada.
        // (Usamos Iterator porque não dá para remover itens de uma lista
        // enquanto a percorremos com um for-each.)
        Iterator<int[]> it = shortcuts.iterator();
        while (it.hasNext()) {
            if (isInStartLane(it.next())) {
                it.remove();
            }
        }
    }

    /** Quantas casas tem a faixa de largada (1 se carveStartLane não foi chamado). */
    private int startLaneLength = 1;

    /** true se a célula está dentro da faixa de largada. */
    private boolean isInStartLane(int[] cell) {
        return cell[0] == getStartX(0) && cell[1] >= getStartY(0) && cell[1] < getStartY(0) + startLaneLength;
    }

    /** Coluna onde a lebre da vaga "slot" começa (todas na coluna 1, lado esquerdo). */
    public int getStartX(int slot) {
        return 1;
    }

    /** Linha onde a lebre da vaga "slot" começa: vaga 0 na linha 1, vaga 1 na linha 2, e assim por diante. */
    public int getStartY(int slot) {
        return 1 + slot;
    }

    /**
     * Distância "Manhattan" da célula (x, y) até a casa MAIS PRÓXIMA da faixa de largada.
     * Usada pelos itens para não nascerem colados em nenhuma lebre.
     */
    public int distanceFromStartLane(int x, int y) {
        int firstY = getStartY(0);
        int lastY = getStartY(startLaneLength - 1);
        int dx = Math.abs(x - getStartX(0));
        int dy = 0;
        if (y < firstY) {
            dy = firstY - y;
        } else if (y > lastY) {
            dy = y - lastY;
        }
        return dx + dy;
    }

    /** Coluna da chegada (onde fica a cenoura): canto inferior direito. */
    public int getFinishX() {
        return cols - 2;
    }

    /** Linha da chegada. */
    public int getFinishY() {
        return rows - 2;
    }

    /** Atalhos criados na geração (vazio no cliente, que recebe o labirinto pronto). */
    public List<int[]> getShortcuts() {
        return shortcuts;
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    /**
     * Converte o labirinto em texto para enviar pela rede.
     * Cada célula vira '1' (parede) ou '0' (chão), linha por linha.
     * Ex.: um labirinto 25x17 vira uma String de 425 caracteres.
     */
    public String toNetworkString() {
        StringBuilder sb = new StringBuilder(cols * rows);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                sb.append(walls[x][y] ? '1' : '0');
            }
        }
        return sb.toString();
    }

    /** Faz o caminho inverso de {@link #toNetworkString()}: monta o labirinto a partir do texto recebido. */
    public static Maze fromNetworkString(int cols, int rows, String data) {
        Maze maze = new Maze(cols, rows);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int index = y * cols + x;
                maze.walls[x][y] = index < data.length() && data.charAt(index) == '1';
            }
        }
        return maze;
    }
}
