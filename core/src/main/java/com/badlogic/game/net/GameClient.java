package com.badlogic.game.net;

import com.badlogic.game.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * CLIENTE de rede. Todo jogador (inclusive o host) usa um GameClient para
 * conversar com o servidor.
 *
 * Problema importante: o libGDX desenha a tela em uma única thread (a thread
 * de renderização). Se a gente ficasse esperando mensagens da rede nela, o jogo
 * travaria. Solução:
 * - Uma thread separada fica lendo o socket e coloca cada linha recebida numa
 *   FILA thread-safe (ConcurrentLinkedQueue), a "caixa de entrada".
 * - A cada frame, a tela retira as mensagens da fila com poll() e as processa.
 */
public class GameClient {

    private Socket socket;
    private PrintWriter out;
    private final ConcurrentLinkedQueue<String> inbox = new ConcurrentLinkedQueue<String>();
    private volatile boolean connected;

    /**
     * Conecta ao servidor e envia o pedido de entrada (JOIN).
     * ATENÇÃO: este método pode demorar alguns segundos, então chame-o fora da
     * thread de renderização (veja MenuScreen).
     */
    public void connect(String host, int port, String playerName) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), Constants.CONNECT_TIMEOUT_MS);
        socket.setTcpNoDelay(true);

        final BufferedReader in = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        connected = true;

        Thread reader = new Thread(() -> readLoop(in), "HareRace-Leitor");
        reader.setDaemon(true);
        reader.start();

        send(Protocol.build(Protocol.JOIN, Protocol.sanitizeName(playerName)));
    }

    /** Roda na thread leitora: lê linhas do servidor e guarda na fila. */
    private void readLoop(BufferedReader in) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                inbox.add(line);
            }
        } catch (IOException e) {
            // conexão encerrada
        } finally {
            connected = false;
            inbox.add(Protocol.DISCONNECTED); // avisa a tela que a conexão caiu
        }
    }

    /** Retira a próxima mensagem recebida, ou null se não houver nenhuma. */
    public String poll() {
        return inbox.poll();
    }

    /** Envia uma linha de texto ao servidor. */
    public void send(String message) {
        if (out == null) {
            return;
        }
        synchronized (out) {
            out.print(message);
            out.print('\n');
            out.flush();
        }
    }

    public boolean isConnected() {
        return connected;
    }

    /** Fecha a conexão. A thread leitora termina sozinha logo depois. */
    public void close() {
        connected = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
