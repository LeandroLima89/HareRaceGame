package com.badlogic.game.net;

/**
 * Define o PROTOCOLO de comunicação entre cliente e servidor.
 *
 * Protocolo = o "idioma" combinado entre os dois lados. Aqui usamos um
 * protocolo de TEXTO simples: cada mensagem é UMA LINHA, e os campos são
 * separados por ponto e vírgula (;). Isso facilita depurar, porque dá para
 * ler as mensagens no console.
 *
 * ===================== CLIENTE -> SERVIDOR =====================
 *   JOIN;nome               pede para entrar na sala
 *   START                   (só o host) inicia uma corrida
 *   MOVE;U|D|L|R            tenta mover a lebre (cima, baixo, esquerda, direita)
 *
 * ===================== SERVIDOR -> CLIENTE =====================
 *   WELCOME;meuId;hostId    entrada aceita, informa o id do jogador e quem é o host
 *   ERROR;mensagem          entrada recusada (sala cheia, corrida em andamento...)
 *   PLAYERS;id,nome,x,y|... lista completa de jogadores e posições
 *   MAZE;colunas;linhas;dados   o labirinto da nova corrida ("0" chão, "1" parede)
 *   COUNTDOWN;ms            a corrida começa daqui a "ms" milissegundos
 *   ITEMS;x,y,T|...         itens do labirinto (T = C trevo, M lama)
 *   GATES;x,y|x,y|...       posição dos portões (todos começam abertos)
 *   GATE;x;y;S              o portão em (x,y) mudou de estado (S = O aberto, A aviso, F fechado)
 *   POS;id;x;y              uma lebre se moveu
 *   ITEM;id;x;y;T;ms        a lebre "id" pisou no item T em (x,y); o efeito dura "ms"
 *   FINISH;id;nome;pos;tempoMs  uma lebre chegou na cenoura em "pos" lugar (1, 2, 3)
 *   CLOSING;ms              a 1a lebre chegou: a corrida fecha daqui a "ms" milissegundos
 *   END                     fim da corrida (o ranking já veio nas mensagens FINISH)
 *   LEFT;id                 um jogador saiu
 */
public final class Protocol {

    private Protocol() {
    }

    /** Separador entre os campos de uma mensagem. */
    public static final String SEP = ";";

    // Cliente -> Servidor
    public static final String JOIN = "JOIN";
    public static final String START = "START";
    public static final String MOVE = "MOVE";

    // Servidor -> Cliente
    public static final String WELCOME = "WELCOME";
    public static final String ERROR = "ERROR";
    public static final String PLAYERS = "PLAYERS";
    public static final String MAZE = "MAZE";
    public static final String COUNTDOWN = "COUNTDOWN";
    public static final String ITEMS = "ITEMS";
    public static final String GATES = "GATES";
    public static final String GATE = "GATE";
    public static final String POS = "POS";
    public static final String ITEM = "ITEM";
    public static final String FINISH = "FINISH";
    public static final String CLOSING = "CLOSING";
    public static final String END = "END";
    public static final String LEFT = "LEFT";

    /**
     * Mensagem "falsa" criada localmente pelo próprio cliente quando a conexão cai.
     * Ela nunca trafega na rede; serve só para avisar as telas.
     */
    public static final String DISCONNECTED = "DISCONNECTED";

    // Direções de movimento
    public static final String UP = "U";
    public static final String DOWN = "D";
    public static final String LEFT_DIR = "L";
    public static final String RIGHT_DIR = "R";

    // Tipos de item (um caractere cada, para a mensagem ficar curta)
    public static final char CLOVER = 'C'; // trevo: a lebre fica mais rápida
    public static final char MUD = 'M';    // lama: a lebre fica mais lenta
    public static final char NO_EFFECT = ' '; // sem efeito ativo

    // Estados de um portão
    public static final char GATE_OPEN = 'O';    // aberto: dá para passar
    public static final char GATE_WARNING = 'A'; // aviso: ainda aberto, mas vai fechar
    public static final char GATE_CLOSED = 'F';  // fechado: funciona como parede

    /**
     * Monta uma mensagem juntando as partes com ";".
     * Ex.: build("POS", 3, 5, 7) -> "POS;3;5;7"
     */
    public static String build(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(SEP);
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /**
     * Limpa o nome digitado pelo jogador, removendo caracteres que quebrariam
     * o protocolo (; , | e quebras de linha) e limitando o tamanho.
     * NUNCA confie em dados vindos da rede sem validar!
     */
    public static String sanitizeName(String raw) {
        if (raw == null) {
            raw = "";
        }
        String clean = raw.replaceAll("[;,|\\r\\n]", "").trim();
        if (clean.length() > 12) {
            clean = clean.substring(0, 12);
        }
        return clean.isEmpty() ? "Lebre" : clean;
    }
}
