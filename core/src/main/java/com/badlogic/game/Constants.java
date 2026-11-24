package com.badlogic.game;

import com.badlogic.gdx.graphics.Color;

/**
 * Reúne todas as constantes (valores fixos) do jogo em um só lugar.
 *
 * Vantagem: se quisermos mudar o tamanho do labirinto, a porta padrão ou o
 * número máximo de jogadores, alteramos aqui e o resto do código se adapta.
 */
public final class Constants {

    /** Construtor privado: ninguém precisa criar objetos desta classe, só usar as constantes. */
    private Constants() {
    }

    // ------------------------------------------------------------------
    // Tamanhos da tela e do labirinto
    // ------------------------------------------------------------------

    /** Tamanho (em pixels) de cada quadradinho (tile) do labirinto. As imagens PNG têm esse tamanho. */
    public static final int TILE_SIZE = 32;

    /** Quantidade de colunas do labirinto. Precisa ser ÍMPAR por causa do algoritmo de geração. */
    public static final int MAZE_COLS = 25;

    /** Quantidade de linhas do labirinto. Também precisa ser ÍMPAR. */
    public static final int MAZE_ROWS = 17;

    /** Altura da barra de informações (HUD) que fica no topo da tela. */
    public static final int HUD_HEIGHT = 56;

    /** Largura do "mundo" do jogo: 25 colunas x 32 px = 800 px. */
    public static final int WORLD_WIDTH = MAZE_COLS * TILE_SIZE;

    /** Altura do "mundo" do jogo: 17 linhas x 32 px + 56 px de HUD = 600 px. */
    public static final int WORLD_HEIGHT = MAZE_ROWS * TILE_SIZE + HUD_HEIGHT;

    // ------------------------------------------------------------------
    // Rede
    // ------------------------------------------------------------------

    /** Número máximo de jogadores conectados ao mesmo tempo (pedido do professor). */
    public static final int MAX_PLAYERS = 10;

    /** Porta TCP sugerida. Qualquer porta livre entre 1024 e 65535 funciona. */
    public static final int DEFAULT_PORT = 5000;

    /** Tempo máximo (ms) para tentar conectar ao servidor antes de desistir. */
    public static final int CONNECT_TIMEOUT_MS = 5000;

    // ------------------------------------------------------------------
    // Regras da corrida
    // ------------------------------------------------------------------

    /**
     * Intervalo mínimo (ms) entre dois movimentos aceitos pelo SERVIDOR.
     * Isso impede que alguém "trapaceie" mandando mil movimentos por segundo.
     */
    public static final long SERVER_MIN_MOVE_INTERVAL_MS = 70;

    /** De quanto em quanto tempo (segundos) o CLIENTE envia um movimento quando a tecla fica pressionada. */
    public static final float CLIENT_MOVE_REPEAT_SECONDS = 0.12f;

    /** Duração da contagem regressiva "3, 2, 1, VAI!" antes da largada. */
    public static final long COUNTDOWN_MS = 3000;

    /**
     * Porcentagem de paredes extras derrubadas depois de gerar o labirinto.
     * Com 0 existe só UM caminho até a cenoura; com um valor maior surgem
     * atalhos e caminhos alternativos, deixando a corrida mais interessante.
     */
    public static final float EXTRA_PASSAGES = 0.15f;

    // ------------------------------------------------------------------
    // Ranking (1o, 2o e 3o lugar)
    // ------------------------------------------------------------------

    /** Quantas posições do pódio são disputadas. A corrida acaba quando elas forem preenchidas. */
    public static final int PODIUM_PLACES = 3;

    /**
     * Depois que a PRIMEIRA lebre chega, os outros têm este tempo (ms) para
     * terminar. Sem esse limite, um jogador parado travaria a corrida para sempre.
     */
    public static final long FINISH_TIMEOUT_MS = 20000;

    // ------------------------------------------------------------------
    // Itens do labirinto: trevo (acelera) e lama (atrasa)
    // ------------------------------------------------------------------

    /** Quantidade de trevos espalhados no labirinto. O trevo SOME quando alguém pega. */
    public static final int CLOVER_COUNT = 6;

    /** Quantidade de poças de lama. A lama FICA no mapa e atrasa todos que passarem. */
    public static final int MUD_COUNT = 8;

    /** Itens não aparecem a menos desta distância (em casas) da largada. */
    public static final int ITEM_MIN_DISTANCE_FROM_START = 4;

    /** Duração (ms) do efeito do trevo. */
    public static final long CLOVER_DURATION_MS = 3000;

    /** Duração (ms) do efeito da lama. */
    public static final long MUD_DURATION_MS = 2000;

    /**
     * Intervalo mínimo (ms) entre movimentos no SERVIDOR com cada efeito.
     * Ficam um pouco ABAIXO do intervalo do cliente (logo abaixo), para que o
     * atraso normal da rede não faça o servidor recusar movimentos honestos.
     */
    public static final long SERVER_MIN_MOVE_INTERVAL_CLOVER_MS = 40;
    public static final long SERVER_MIN_MOVE_INTERVAL_MUD_MS = 220;

    /** Intervalo (s) de repetição do movimento no CLIENTE com cada efeito. */
    public static final float CLIENT_MOVE_REPEAT_CLOVER_SECONDS = 0.07f;
    public static final float CLIENT_MOVE_REPEAT_MUD_SECONDS = 0.25f;

    // ------------------------------------------------------------------
    // Portões: atalhos que abrem e fecham em ciclo
    // ------------------------------------------------------------------

    /** Quantidade de portões por corrida (sorteada entre o mínimo e o máximo). */
    public static final int GATE_MIN_COUNT = 6;
    public static final int GATE_MAX_COUNT = 8;

    /**
     * Ciclo de cada portão: ABERTO -> AVISO (piscando) -> FECHADO -> ABERTO...
     * O aviso dá tempo de o jogador passar correndo ou desistir do caminho.
     */
    public static final long GATE_OPEN_MS = 6000;
    public static final long GATE_WARNING_MS = 1500;
    public static final long GATE_CLOSED_MS = 6000;

    /** O primeiro aviso de cada portão acontece até este tempo (ms) depois da largada. */
    public static final long GATE_FIRST_CHANGE_MAX_MS = 12000;

    /** De quanto em quanto tempo (ms) o servidor confere se algum portão deve mudar. */
    public static final long GATE_TICK_MS = 100;

    /** Só tocamos o som de um portão se ele estiver a até esta distância (em casas) da minha lebre. */
    public static final int GATE_SOUND_DISTANCE = 6;

    // ------------------------------------------------------------------
    // Cores das lebres (uma para cada jogador, por isso 10 cores)
    // ------------------------------------------------------------------

    /**
     * A imagem da lebre é branca/cinza. Na hora de desenhar, "pintamos" ela
     * com uma destas cores (batch.setColor), assim um único PNG serve para
     * os 10 jogadores.
     */
    public static final Color[] PLAYER_COLORS = {
        new Color(1.00f, 1.00f, 1.00f, 1f), // 0 - branca (normalmente o host)
        new Color(1.00f, 0.35f, 0.35f, 1f), // 1 - vermelha
        new Color(0.40f, 0.60f, 1.00f, 1f), // 2 - azul
        new Color(0.45f, 0.95f, 0.45f, 1f), // 3 - verde
        new Color(1.00f, 0.90f, 0.30f, 1f), // 4 - amarela
        new Color(1.00f, 0.55f, 0.95f, 1f), // 5 - rosa
        new Color(0.40f, 1.00f, 1.00f, 1f), // 6 - ciano
        new Color(1.00f, 0.65f, 0.25f, 1f), // 7 - laranja
        new Color(0.75f, 0.55f, 1.00f, 1f), // 8 - roxa
        new Color(0.70f, 0.55f, 0.40f, 1f), // 9 - marrom
    };

    /** Retorna a cor do jogador com o id informado. */
    public static Color colorFor(int playerId) {
        return PLAYER_COLORS[Math.abs(playerId) % PLAYER_COLORS.length];
    }
}
