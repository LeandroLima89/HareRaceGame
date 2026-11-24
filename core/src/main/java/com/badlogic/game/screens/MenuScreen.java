package com.badlogic.game.screens;

import com.badlogic.game.Constants;
import com.badlogic.game.HareRaceGame;
import com.badlogic.game.Session;
import com.badlogic.game.net.GameClient;
import com.badlogic.game.net.GameServer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.TimeUtils;

import java.io.IOException;

/**
 * Tela inicial. O jogador digita o NOME, o IP e a PORTA e escolhe:
 *   - "Criar sala": inicia um servidor nesta máquina e conecta nele mesmo.
 *   - "Entrar na sala": conecta no servidor de outro colega pelo IP/porta.
 *
 * Controles: SETAS/TAB trocam o campo, digitar escreve, BACKSPACE apaga,
 * ENTER confirma, ESC fecha o jogo.
 */
public class MenuScreen extends BaseScreen {

    // Índices dos itens do menu
    private static final int ITEM_NAME = 0;
    private static final int ITEM_IP = 1;
    private static final int ITEM_PORT = 2;
    private static final int ITEM_HOST = 3;
    private static final int ITEM_JOIN = 4;
    private static final int ITEM_COUNT = 5;

    /*
     * "static" para lembrar o que foi digitado quando o jogador voltar ao menu
     * (ex.: após sair de uma sala), sem precisar digitar tudo de novo.
     */
    private static final StringBuilder name = new StringBuilder("Lebre" + MathUtils.random(10, 99));
    private static final StringBuilder ip = new StringBuilder("127.0.0.1");
    private static final StringBuilder port = new StringBuilder(String.valueOf(Constants.DEFAULT_PORT));

    private int selected = ITEM_NAME;

    /** Mensagem de status/erro mostrada na parte de baixo. */
    private String status;

    /** true enquanto estamos tentando conectar (bloqueia o teclado). */
    private boolean busy;

    /** @param message mensagem a exibir (ex.: "Conexao perdida"). Pode ser vazia. */
    public MenuScreen(HareRaceGame game, String message) {
        super(game);
        this.status = message;
    }

    /** Chamado quando a tela aparece: registramos quem vai receber o teclado. */
    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {

            /** Teclas "especiais" (setas, enter, esc...). */
            @Override
            public boolean keyDown(int keycode) {
                if (busy) {
                    return true;
                }
                switch (keycode) {
                    case Input.Keys.UP:
                        selected = (selected - 1 + ITEM_COUNT) % ITEM_COUNT;
                        return true;
                    case Input.Keys.DOWN:
                    case Input.Keys.TAB:
                        selected = (selected + 1) % ITEM_COUNT;
                        return true;
                    case Input.Keys.ENTER:
                    case Input.Keys.NUMPAD_ENTER:
                        if (selected == ITEM_HOST) {
                            hostGame();
                        } else if (selected == ITEM_JOIN) {
                            joinGame();
                        } else {
                            selected++; // nos campos de texto, ENTER pula para o próximo
                        }
                        return true;
                    case Input.Keys.ESCAPE:
                        Gdx.app.exit();
                        return true;
                    default:
                        return false;
                }
            }

            /** Caracteres digitados (letras, números...). Usado para escrever nos campos. */
            @Override
            public boolean keyTyped(char character) {
                if (busy) {
                    return true;
                }
                StringBuilder field = currentField();
                if (field == null) {
                    return false;
                }
                if (character == '\b') { // backspace
                    if (field.length() > 0) {
                        field.setLength(field.length() - 1);
                    }
                    return true;
                }
                if (acceptsChar(selected, character) && field.length() < maxLength(selected)) {
                    field.append(character);
                }
                return true;
            }
        });
    }

    /** Quando a tela some, paramos de receber teclado. */
    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    /** Retorna o campo de texto selecionado, ou null se o item for um botão. */
    private StringBuilder currentField() {
        if (selected == ITEM_NAME) {
            return name;
        } else if (selected == ITEM_IP) {
            return ip;
        } else if (selected == ITEM_PORT) {
            return port;
        }
        return null;
    }

    /** Filtra o que cada campo aceita (porta = só números, por exemplo). */
    private static boolean acceptsChar(int item, char c) {
        if (item == ITEM_PORT) {
            return Character.isDigit(c);
        }
        if (item == ITEM_IP) {
            return Character.isLetterOrDigit(c) || c == '.' || c == '-';
        }
        // Nome: qualquer caractere visível, exceto os separadores do protocolo.
        return c >= 32 && c != 127 && c != ';' && c != ',' && c != '|';
    }

    private static int maxLength(int item) {
        if (item == ITEM_NAME) {
            return 12;
        }
        if (item == ITEM_PORT) {
            return 5;
        }
        return 40;
    }

    /** Converte o texto da porta para número, validando a faixa. Retorna -1 se inválida. */
    private int parsePort() {
        try {
            int p = Integer.parseInt(port.toString());
            return (p >= 1024 && p <= 65535) ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "Criar sala": sobe o servidor e conecta nele mesmo (127.0.0.1 = esta máquina). */
    private void hostGame() {
        final int p = parsePort();
        if (p < 0) {
            status = "Porta invalida (use 1024 a 65535).";
            return;
        }
        startConnecting(true, "127.0.0.1", p, "Criando sala na porta " + p + "...");
    }

    /** "Entrar na sala": conecta no IP/porta digitados. */
    private void joinGame() {
        final int p = parsePort();
        if (p < 0) {
            status = "Porta invalida (use 1024 a 65535).";
            return;
        }
        if (ip.length() == 0) {
            status = "Digite o IP do host.";
            return;
        }
        startConnecting(false, ip.toString(), p, "Conectando em " + ip + ":" + p + "...");
    }

    /**
     * Faz a conexão numa THREAD SEPARADA, porque conectar pode levar alguns
     * segundos e não queremos congelar a tela.
     *
     * Ao terminar, usamos Gdx.app.postRunnable(...) para voltar à thread do
     * libGDX antes de trocar de tela (mexer em telas/OpenGL fora dela dá erro).
     */
    private void startConnecting(final boolean asHost, final String host, final int p, String message) {
        busy = true;
        status = message;
        final String playerName = name.toString();

        new Thread(() -> {
            final Session session = new Session();
            try {
                if (asHost) {
                    session.server = new GameServer(p);
                    session.server.start();
                }
                session.client = new GameClient();
                session.client.connect(host, p, playerName);

                // Deu certo: vai para a sala de espera (na thread do libGDX).
                Gdx.app.postRunnable(() -> {
                    game.session = session;
                    game.setScreen(new LobbyScreen(game));
                });
            } catch (IOException e) {
                session.close();
                final String error = asHost
                    ? "Nao foi possivel criar a sala: " + e.getMessage()
                    : "Nao foi possivel conectar: " + e.getMessage();
                Gdx.app.postRunnable(() -> {
                    busy = false;
                    status = error;
                });
            }
        }, "HareRace-Conectar").start();
    }

    @Override
    public void render(float delta) {
        game.sounds.playMusic(game.sounds.menuMusic); // música calma no menu
        beginFrame(0.10f, 0.16f, 0.10f);
        game.batch.begin();

        float centerX = Constants.WORLD_WIDTH / 2f;

        // Título com uma lebre desenhada de cada lado (pulando com uma senoide).
        drawCentered(game.bigFont, "CORRIDA DAS LEBRES", 540, Color.WHITE);
        float hop = Math.abs(MathUtils.sin(TimeUtils.millis() / 200f)) * 10f;
        game.batch.setColor(Constants.colorFor(1));
        game.batch.draw(game.assets.hare, 90, 500 + hop, 48, 48);
        game.batch.setColor(Constants.colorFor(2));
        game.batch.draw(game.assets.hare, 662, 500 + hop, 48, 48, 0, 0,
            game.assets.hare.getWidth(), game.assets.hare.getHeight(), true, false);
        game.batch.setColor(Color.WHITE);
        drawCentered(game.font, "Chegue primeiro na cenoura para vencer! (ate " + Constants.MAX_PLAYERS + " jogadores)",
            490, Color.LIGHT_GRAY);

        // Itens do menu
        boolean blink = (TimeUtils.millis() / 400) % 2 == 0;
        String[] labels = {"Nome: ", "IP do host: ", "Porta: ", "[ Criar sala (sou o host) ]", "[ Entrar na sala ]"};
        StringBuilder[] values = {name, ip, port, null, null};
        float y = 420;
        for (int i = 0; i < ITEM_COUNT; i++) {
            boolean isSelected = i == selected;
            String text = labels[i];
            if (values[i] != null) {
                text += values[i] + (isSelected && blink && !busy ? "_" : "");
            }
            if (isSelected) {
                drawRect(centerX - 210, y - 22, 420, 30, new Color(1f, 1f, 1f, 0.12f));
                text = "> " + text + " <";
            }
            drawCenteredAt(game.font, text, centerX, y, isSelected ? Color.YELLOW : Color.WHITE);
            y -= (i == ITEM_PORT) ? 60 : 42; // espaço maior antes dos botões
        }

        // Ajuda e mensagem de status
        drawCentered(game.font, "SETAS/TAB: trocar campo   ENTER: confirmar   ESC: sair", 100, Color.GRAY);
        drawCentered(game.font, "O IP so e usado para 'Entrar na sala'. O host informa o IP dele aos colegas.",
            75, Color.GRAY);
        if (status != null && !status.isEmpty()) {
            drawCentered(game.font, status, 40, busy ? Color.CYAN : Color.SALMON);
        }

        game.batch.end();
    }
}
