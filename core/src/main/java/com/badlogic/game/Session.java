package com.badlogic.game;

import com.badlogic.game.model.RaceState;
import com.badlogic.game.net.GameClient;
import com.badlogic.game.net.GameServer;

/**
 * Agrupa tudo que pertence a UMA participação em sala:
 * o cliente de rede, o estado da corrida e, se formos o host, o servidor.
 *
 * Ela passa de tela em tela (Menu -> Sala de espera -> Corrida) e é
 * encerrada quando o jogador sai.
 */
public class Session {

    /** Só existe na máquina de quem criou a sala. Nos outros jogadores é null. */
    public GameServer server;

    /** Conexão com o servidor (todo mundo tem uma, inclusive o host). */
    public GameClient client;

    /** O que sabemos sobre a partida. */
    public final RaceState state = new RaceState();

    /** Fecha a conexão e, se formos o host, desliga o servidor. */
    public void close() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }
}
