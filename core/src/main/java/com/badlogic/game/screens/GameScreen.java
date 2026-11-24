package com.badlogic.game.screens;

import com.badlogic.game.Constants;
import com.badlogic.game.HareRaceGame;
import com.badlogic.game.Session;
import com.badlogic.game.Sounds;
import com.badlogic.game.model.Gates;
import com.badlogic.game.model.Hare;
import com.badlogic.game.model.Items;
import com.badlogic.game.model.Maze;
import com.badlogic.game.model.RaceState;
import com.badlogic.game.net.Protocol;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Tela da CORRIDA. Aqui acontece o jogo de verdade:
 * - lê o teclado e envia os movimentos ao servidor;
 * - processa as respostas (posições, itens, chegadas);
 * - toca os sons;
 * - desenha labirinto, portões, itens, cenoura, lebres, HUD e o pódio.
 *
 * O loop de um jogo é sempre: LER ENTRADA -> ATUALIZAR -> DESENHAR,
 * repetido ~60 vezes por segundo (cada repetição é um "frame").
 */
public class GameScreen extends BaseScreen {

    private static final int TILE = Constants.TILE_SIZE;

    /** Tempo que falta para poder enviar o próximo movimento (tecla segurada). */
    private float moveCooldown;

    /** Último texto da contagem mostrado ("3", "2", "1", "VAI!"), para tocar o bipe só quando ele MUDA. */
    private String lastCountdownText;

    private static final Color HUD_BG = new Color(0f, 0f, 0f, 0.75f);
    private static final Color OVERLAY_BG = new Color(0f, 0f, 0f, 0.70f);
    private static final Color BANNER_BG = new Color(0f, 0f, 0f, 0.55f);
    private static final Color CLOVER_GREEN = new Color(0.45f, 1f, 0.45f, 1f);
    private static final Color MUD_BROWN = new Color(0.85f, 0.60f, 0.35f, 1f);

    /** Cores das medalhas: ouro, prata e bronze. */
    private static final Color[] MEDAL_COLORS = {
        new Color(1.00f, 0.84f, 0.00f, 1f),
        new Color(0.80f, 0.80f, 0.85f, 1f),
        new Color(0.85f, 0.52f, 0.25f, 1f),
    };

    public GameScreen(HareRaceGame game) {
        super(game);
    }

    @Override
    public void render(float delta) {
        Session session = game.session;
        RaceState state = session.state;

        // ---------- 1) Mensagens da rede ----------
        state.processMessages(session.client);
        if (state.disconnected) {
            game.closeSession();
            game.setScreen(new MenuScreen(game, "Conexao com o servidor perdida."));
            return;
        }

        // ---------- 2) Entrada (teclado) ----------
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.closeSession();
            game.setScreen(new MenuScreen(game, "Voce saiu da corrida."));
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            // Liga/desliga o som. A música obedece sozinha, porque o playMusic
            // (chamado logo abaixo, a cada quadro) pausa ou retoma conforme "enabled".
            game.sounds.enabled = !game.sounds.enabled;
        }
        if (state.phase == RaceState.Phase.FINISHED && state.isHost()
            && Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            session.client.send(Protocol.START); // host inicia outra corrida
        }
        handleMovement(delta, state, session);

        // ---------- 3) Atualização (animação das lebres e sons) ----------
        for (Hare hare : state.hares.values()) {
            hare.update(delta);
        }
        playEventSounds(state);
        playCountdownSounds(state);
        game.sounds.playMusic(chooseMusic(state));

        // ---------- 4) Desenho ----------
        beginFrame(0f, 0f, 0f);
        game.batch.begin();
        if (state.maze != null) {
            drawMaze(state.maze);
            drawGates(state.maze, state.gates);
            drawItems(state.maze, state.items);
            drawCarrot(state.maze);
            drawHares(state);
        }
        drawHud(state);
        drawCountdown(state);
        drawMyFinishBanner(state);
        drawPodium(state);
        game.batch.end();
    }

    /**
     * Lê as setas/WASD e envia MOVE ao servidor.
     * Se a tecla ficar pressionada, repete o envio num intervalo que depende
     * do efeito ativo (trevo = mais rápido, lama = mais devagar).
     *
     * Note que NÃO movemos a lebre aqui! Só pedimos ao servidor. A lebre anda
     * quando o servidor responder com POS. Em rede local isso leva poucos ms.
     */
    private void handleMovement(float delta, RaceState state, Session session) {
        moveCooldown -= delta;
        if (state.phase != RaceState.Phase.RACE || state.isCountingDown() || state.iHaveFinished()) {
            return;
        }

        String direction = null;
        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
            direction = Protocol.UP;
        } else if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
            direction = Protocol.DOWN;
        } else if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) {
            direction = Protocol.LEFT_DIR;
        } else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) {
            direction = Protocol.RIGHT_DIR;
        }

        if (direction == null) {
            moveCooldown = 0; // soltou a tecla: o próximo toque responde na hora
            return;
        }
        if (moveCooldown <= 0) {
            session.client.send(Protocol.build(Protocol.MOVE, direction));
            moveCooldown = moveRepeatSeconds(state.getMyHare());
        }
    }

    /** Intervalo de repetição do movimento conforme o efeito ativo da minha lebre. */
    private static float moveRepeatSeconds(Hare me) {
        char effect = me != null ? me.getActiveEffect() : Protocol.NO_EFFECT;
        if (effect == Protocol.CLOVER) {
            return Constants.CLIENT_MOVE_REPEAT_CLOVER_SECONDS;
        }
        if (effect == Protocol.MUD) {
            return Constants.CLIENT_MOVE_REPEAT_MUD_SECONDS;
        }
        return Constants.CLIENT_MOVE_REPEAT_SECONDS;
    }

    // ==================================================================
    // Sons
    // ==================================================================

    /** Esvazia a lista de acontecimentos do RaceState tocando o som de cada um. */
    private void playEventSounds(RaceState state) {
        Sounds sounds = game.sounds;
        boolean playedFinishSound = false;
        for (RaceState.Event event : state.events) {
            switch (event) {
                case MY_CLOVER:
                    sounds.play(sounds.clover, 0.8f);
                    break;
                case MY_MUD:
                    sounds.play(sounds.mud, 0.9f);
                    break;
                case I_WON:
                    sounds.play(sounds.win, 1f);
                    playedFinishSound = true;
                    break;
                case I_FINISHED:
                    sounds.play(sounds.finish, 1f);
                    playedFinishSound = true;
                    break;
                case OTHER_FINISHED:
                    sounds.play(sounds.finish, 0.5f); // mais baixo: não fui eu
                    playedFinishSound = true;
                    break;
                case GATE_WARNING_NEAR:
                    sounds.play(sounds.gateWarning, 0.6f);
                    break;
                case GATE_CLOSED_NEAR:
                    sounds.play(sounds.gateClose, 0.8f);
                    break;
                case RACE_OVER:
                    // Se a corrida acabou junto com uma chegada, a chegada já fez barulho
                    // suficiente. O som de fim fica para quando o TEMPO esgota.
                    if (!playedFinishSound) {
                        sounds.play(sounds.end, 0.8f);
                    }
                    break;
                default:
                    break;
            }
        }
        state.events.clear();
    }

    /**
     * Escolhe a música de acordo com a fase da corrida:
     * - corrida acabou (pódio): volta a música calma do menu;
     * - alguém já chegou e o tempo limite está correndo (closingEndMillis > 0,
     *   ligado pela mensagem CLOSING): a versão RÁPIDA, para dar tensão;
     * - no resto da corrida (inclusive na contagem regressiva): a música animada.
     */
    private Music chooseMusic(RaceState state) {
        Sounds sounds = game.sounds;
        if (state.phase == RaceState.Phase.FINISHED) {
            return sounds.menuMusic;
        }
        if (state.closingEndMillis > 0) {
            return sounds.raceFastMusic;
        }
        return sounds.raceMusic;
    }

    /** Toca um bipe a cada número da contagem e um bipe diferente no "VAI!". */
    private void playCountdownSounds(RaceState state) {
        String text = countdownText(state);
        if (text != null && !text.equals(lastCountdownText)) {
            if ("VAI!".equals(text)) {
                game.sounds.play(game.sounds.go, 1f);
            } else {
                game.sounds.play(game.sounds.countdown, 0.8f);
            }
        }
        lastCountdownText = text;
    }

    /** "3", "2", "1", depois "VAI!" por 0,7 s, e null no resto do tempo. */
    private static String countdownText(RaceState state) {
        if (state.phase != RaceState.Phase.RACE) {
            return null;
        }
        long remaining = state.countdownEndMillis - TimeUtils.millis();
        if (remaining > 0) {
            return String.valueOf((remaining / 1000) + 1); // 2999 ms -> "3"
        } else if (remaining > -700) {
            return "VAI!";
        }
        return null;
    }

    // ==================================================================
    // Desenho do mapa
    // ==================================================================

    /**
     * Converte a LINHA do labirinto (0 = topo) para a coordenada Y da tela.
     * No libGDX o Y cresce para CIMA (0 = parte de baixo da tela), por isso invertemos.
     */
    private float rowToScreenY(Maze maze, float row) {
        return (maze.getRows() - 1 - row) * TILE;
    }

    /** Desenha chão e paredes. */
    private void drawMaze(Maze maze) {
        for (int x = 0; x < maze.getCols(); x++) {
            for (int y = 0; y < maze.getRows(); y++) {
                Texture texture = maze.isWall(x, y) ? game.assets.wall : game.assets.floor;
                game.batch.draw(texture, x * TILE, rowToScreenY(maze, y), TILE, TILE);
            }
        }
    }

    /**
     * Desenha os portões conforme o estado:
     * - ABERTO: só um "fantasma" bem transparente, para o jogador saber que ali existe um portão;
     * - AVISO: pisca em vermelho, avisando que vai fechar;
     * - FECHADO: a barreira de madeira inteira.
     */
    private void drawGates(Maze maze, Gates gates) {
        if (gates == null) {
            return;
        }
        float time = TimeUtils.millis() / 1000f;
        for (Gates.Gate gate : gates.all()) {
            float px = gate.x * TILE;
            float py = rowToScreenY(maze, gate.y);
            if (gate.state == Protocol.GATE_CLOSED) {
                game.batch.draw(game.assets.gate, px, py, TILE, TILE);
            } else if (gate.state == Protocol.GATE_WARNING) {
                // sin vai de -1 a 1; transformamos em 0..1 para usar como transparência.
                float blink = 0.5f + 0.5f * MathUtils.sin(time * 20f);
                drawRect(px, py, TILE, TILE, new Color(1f, 0.15f, 0.1f, 0.25f + 0.25f * blink));
                game.batch.setColor(1f, 0.6f, 0.6f, 0.35f + 0.5f * blink);
                game.batch.draw(game.assets.gate, px, py, TILE, TILE);
                game.batch.setColor(Color.WHITE);
            } else {
                game.batch.setColor(1f, 1f, 1f, 0.22f);
                game.batch.draw(game.assets.gate, px, py, TILE, TILE);
                game.batch.setColor(Color.WHITE);
            }
        }
    }

    /** Desenha os trevos (balançando) e as poças de lama. */
    private void drawItems(Maze maze, Items items) {
        if (items == null) {
            return;
        }
        float time = TimeUtils.millis() / 1000f;
        for (int x = 0; x < items.getCols(); x++) {
            for (int y = 0; y < items.getRows(); y++) {
                char item = items.get(x, y);
                float px = x * TILE;
                float py = rowToScreenY(maze, y);
                if (item == Protocol.MUD) {
                    game.batch.draw(game.assets.mud, px, py, TILE, TILE);
                } else if (item == Protocol.CLOVER) {
                    // Sobe e desce um pouquinho para chamar atenção. Somar x e y no
                    // seno faz cada trevo balançar num momento diferente.
                    float bob = 2f * MathUtils.sin(time * 4f + x + y);
                    game.batch.draw(game.assets.clover, px, py + bob, TILE, TILE);
                }
            }
        }
    }

    /** Cenoura "pulsando" na chegada. */
    private void drawCarrot(Maze maze) {
        float pulse = 1f + 0.08f * MathUtils.sin(TimeUtils.millis() / 150f);
        float size = TILE * pulse;
        float cx = maze.getFinishX() * TILE + TILE / 2f;
        float cy = rowToScreenY(maze, maze.getFinishY()) + TILE / 2f;
        game.batch.draw(game.assets.carrot, cx - size / 2f, cy - size / 2f, size, size);
    }

    /**
     * Desenha as lebres. A minha é desenhada por último para ficar por cima das outras.
     *
     * Várias lebres podem estar na MESMA casa (a regra permite). Para nenhuma
     * esconder a outra, primeiro descobrimos quantas lebres há em cada casa e
     * qual a "vaga" de cada uma dentro dela; depois o drawHare desenha cada uma
     * menor e um pouco deslocada. Isso muda só o DESENHO: a posição oficial
     * (gridX/gridY, decidida pelo servidor) continua a mesma.
     */
    /**
     * Chave da casa -> quantas lebres há nela.
     * A chave junta x e y num número só: x * 1000 + y (o mapa tem bem menos de 1000 linhas).
     */
    private final Map<Integer, Integer> countPerCell = new HashMap<Integer, Integer>();

    /** Id da lebre -> vaga dela dentro da casa (0, 1, 2...). */
    private final Map<Integer, Integer> slotOf = new HashMap<Integer, Integer>();

    private void drawHares(RaceState state) {
        // Os dois mapas são campos reaproveitados: só limpamos a cada quadro, em
        // vez de criar mapas novos 60 vezes por segundo (menos lixo para o Java recolher).
        countPerCell.clear();
        slotOf.clear();
        // state.hares é um TreeMap (ordenado por id), então a ordem das vagas é
        // sempre a mesma em todos os computadores e não "pisca" de um quadro para outro.
        for (Hare hare : state.hares.values()) {
            int key = hare.gridX * 1000 + hare.gridY;
            Integer count = countPerCell.get(key);
            int slot = count == null ? 0 : count;
            slotOf.put(hare.id, slot);
            countPerCell.put(key, slot + 1);
        }

        for (Hare hare : state.hares.values()) {
            if (hare.id != state.myId) {
                int total = countPerCell.get(hare.gridX * 1000 + hare.gridY);
                drawHare(state.maze, hare, false, slotOf.get(hare.id), total);
            }
        }
        Hare me = state.getMyHare();
        if (me != null) {
            int total = countPerCell.get(me.gridX * 1000 + me.gridY);
            drawHare(state.maze, me, true, slotOf.get(me.id), total);
        }
    }

    /**
     * Desenha uma lebre.
     *
     * @param slot  vaga desta lebre dentro da casa (0 se estiver sozinha)
     * @param total quantas lebres estão nessa casa
     */
    private void drawHare(Maze maze, Hare hare, boolean isMe, int slot, int total) {
        // Canto inferior esquerdo da CASA (em pixels).
        float cellX = hare.drawX * TILE;
        float cellY = rowToScreenY(maze, hare.drawY);

        // Tamanho e posição da lebre DENTRO da casa.
        // Sozinha: ocupa a casa inteira. Acompanhada: fica com 70% do tamanho e
        // é empurrada para fora do centro, em volta de um círculo.
        float size = TILE;
        float offsetX = 0;
        float offsetY = 0;
        if (total > 1) {
            size = TILE * 0.7f;
            float radius = TILE * 0.22f;
            // Divide a volta (360 graus) em partes iguais. Somar 180 faz a vaga 0
            // começar à esquerda: com 2 lebres, uma fica à esquerda e a outra à direita.
            float angle = 180f + 360f * slot / total;
            offsetX = radius * MathUtils.cosDeg(angle);
            offsetY = radius * MathUtils.sinDeg(angle);
        }
        // (x, y) = canto inferior esquerdo da imagem da lebre já deslocada.
        float x = cellX + (TILE - size) / 2f + offsetX;
        float y = cellY + (TILE - size) / 2f + offsetY;
        char effect = hare.getActiveEffect();

        // Destaque amarelo atrás da MINHA lebre, para eu me achar no meio das outras.
        if (isMe) {
            drawRect(x + 2, y + 2, size - 4, size - 4, new Color(1f, 1f, 0f, 0.30f));
        }
        // Brilho verde piscando quando está com o trevo.
        if (effect == Protocol.CLOVER) {
            float alpha = 0.25f + 0.20f * MathUtils.sin(TimeUtils.millis() / 60f);
            drawRect(x, y, size, size, new Color(0.3f, 1f, 0.3f, alpha));
        }

        // "Pinta" a lebre branca com a cor do jogador e espelha se estiver indo para a esquerda.
        // Na lama, escurecemos a cor (multiplicar por 0.55) para parecer "suja".
        Color tint = new Color(Constants.colorFor(hare.id));
        if (effect == Protocol.MUD) {
            tint.mul(0.55f, 0.45f, 0.35f, 1f);
        }
        Texture tex = game.assets.hare;
        game.batch.setColor(tint);
        game.batch.draw(tex, x, y, size, size, 0, 0, tex.getWidth(), tex.getHeight(), hare.facingLeft, false);
        game.batch.setColor(Color.WHITE);

        // Ícone pequeno do efeito no canto da lebre.
        if (effect == Protocol.CLOVER) {
            game.batch.draw(game.assets.clover, x + size - 12, y + size - 12, 14, 14);
        }

        // Nome acima da CASA (com a posição, se já chegou). Se houver várias
        // lebres na mesma casa, os nomes ficam empilhados, um por linha
        // (vaga 0 embaixo, vaga 1 acima dela...), para não ficarem um em cima do outro.
        String label = hare.name;
        if (hare.finishPosition > 0) {
            label = hare.finishPosition + "o " + label;
        }
        game.font.getData().setScale(0.8f);
        float labelY = cellY + TILE + 12 + slot * 11;
        drawCenteredAt(game.font, label, cellX + TILE / 2f, labelY, isMe ? Color.YELLOW : Color.WHITE);
        game.font.getData().setScale(1f);
    }

    // ==================================================================
    // HUD e mensagens por cima do jogo
    // ==================================================================

    /** Barra superior com informações. */
    private void drawHud(RaceState state) {
        float top = Constants.WORLD_HEIGHT;
        float hudY = top - Constants.HUD_HEIGHT;
        drawRect(0, hudY, Constants.WORLD_WIDTH, Constants.HUD_HEIGHT, HUD_BG);

        Hare me = state.getMyHare();
        if (me != null) {
            game.batch.setColor(Constants.colorFor(me.id));
            game.batch.draw(game.assets.hare, 10, hudY + 12, 32, 32);
            game.batch.setColor(Color.WHITE);
            game.font.setColor(Color.YELLOW);
            game.font.draw(game.batch, "Voce: " + me.name, 50, top - 14);

            // Segunda linha: efeito ativo (com o tempo restante) ou o número de lebres.
            char effect = me.getActiveEffect();
            float secondsLeft = (me.effectEndMillis - TimeUtils.millis()) / 1000f;
            if (effect == Protocol.CLOVER) {
                game.font.setColor(CLOVER_GREEN);
                game.font.draw(game.batch, "TREVO! Mais rapido " + formatOneDecimal(secondsLeft) + "s", 50, top - 36);
            } else if (effect == Protocol.MUD) {
                game.font.setColor(MUD_BROWN);
                game.font.draw(game.batch, "LAMA! Mais lento " + formatOneDecimal(secondsLeft) + "s", 50, top - 36);
            } else {
                game.font.setColor(Color.WHITE);
                game.font.draw(game.batch, "Lebres na corrida: " + state.hares.size(), 50, top - 36);
            }
        }
        game.font.setColor(Color.WHITE);

        // Cronômetro: para quando EU chego (mostra o meu tempo).
        drawCenteredAt(game.font, "Tempo: " + raceClockText(state) + " s", Constants.WORLD_WIDTH / 2f, top - 14,
            Color.WHITE);

        // Tempo limite depois que a primeira lebre chegou.
        if (state.phase == RaceState.Phase.RACE && state.closingEndMillis > 0) {
            long left = state.closingEndMillis - TimeUtils.millis();
            long seconds = Math.max(0, (left + 999) / 1000); // arredonda para cima
            drawCenteredAt(game.font, "A corrida fecha em " + seconds + " s", Constants.WORLD_WIDTH / 2f, top - 36,
                Color.ORANGE);
        }

        game.font.setColor(Color.LIGHT_GRAY);
        game.font.draw(game.batch, "Setas/WASD: mover", 625, top - 14);
        game.font.draw(game.batch, "M: som " + (game.sounds.enabled ? "ON" : "OFF") + "   ESC: sair", 625, top - 36);
        game.font.setColor(Color.WHITE);
    }

    /** Texto do cronômetro do HUD. */
    private String raceClockText(RaceState state) {
        // Se eu já cheguei, o relógio "congela" no meu tempo.
        for (RaceState.FinishEntry entry : state.ranking) {
            if (entry.id == state.myId) {
                return formatSeconds(entry.timeMillis);
            }
        }
        if (state.phase == RaceState.Phase.FINISHED) {
            return state.ranking.isEmpty() ? "--" : formatSeconds(state.ranking.get(0).timeMillis);
        }
        if (state.isCountingDown()) {
            return "0.0";
        }
        return formatSeconds(TimeUtils.millis() - state.countdownEndMillis);
    }

    /** Mostra "3", "2", "1" e depois "VAI!". */
    private void drawCountdown(RaceState state) {
        String text = countdownText(state);
        if (text != null) {
            drawCentered(game.bigFont, text, Constants.WORLD_HEIGHT / 2f + 20, Color.YELLOW);
        }
    }

    /** Enquanto os outros ainda correm, avisa em que lugar EU cheguei. */
    private void drawMyFinishBanner(RaceState state) {
        if (state.phase != RaceState.Phase.RACE || !state.iHaveFinished()) {
            return;
        }
        int position = state.getMyHare().finishPosition;
        drawRect(0, 250, Constants.WORLD_WIDTH, 70, BANNER_BG);
        drawCentered(game.bigFont, "Voce chegou em " + position + "o!", 305, medalColor(position));
        drawCentered(game.font, "Aguardando as outras lebres...", 270, Color.WHITE);
    }

    /** Tela final por cima do jogo com o pódio (1o, 2o e 3o lugar). */
    private void drawPodium(RaceState state) {
        if (state.phase != RaceState.Phase.FINISHED) {
            return;
        }
        drawRect(0, 0, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT - Constants.HUD_HEIGHT, OVERLAY_BG);

        // Título: destaca se EU venci.
        boolean iWon = !state.ranking.isEmpty() && state.ranking.get(0).id == state.myId;
        drawCentered(game.bigFont, iWon ? "VOCE VENCEU!" : "FIM DA CORRIDA", 470, iWon ? Color.GOLD : Color.WHITE);

        if (state.ranking.isEmpty()) {
            drawCentered(game.font, "Ninguem chegou na cenoura.", 380, Color.LIGHT_GRAY);
        }

        // Uma linha por posição: "1o   [lebre]  Ana        12.3 s"
        float rowY = 400;
        for (RaceState.FinishEntry entry : state.ranking) {
            if (entry.position > Constants.PODIUM_PLACES) {
                break;
            }
            Color medal = medalColor(entry.position);
            boolean isMe = entry.id == state.myId;

            // Faixa de fundo destacando a minha linha.
            if (isMe) {
                drawRect(220, rowY - 30, 360, 40, new Color(1f, 1f, 0f, 0.15f));
            }
            game.font.getData().setScale(1.6f);
            game.font.setColor(medal);
            game.font.draw(game.batch, entry.position + "o", 235, rowY);

            game.batch.setColor(Constants.colorFor(entry.id));
            game.batch.draw(game.assets.hare, 290, rowY - 28, 32, 32);
            game.batch.setColor(Color.WHITE);

            game.font.setColor(isMe ? Color.YELLOW : Color.WHITE);
            game.font.draw(game.batch, entry.name, 335, rowY);
            game.font.draw(game.batch, formatSeconds(entry.timeMillis) + " s", 480, rowY);
            game.font.getData().setScale(1f);
            game.font.setColor(Color.WHITE);
            rowY -= 55;
        }

        // Mensagem para quem não entrou no pódio.
        boolean iFinished = false;
        for (RaceState.FinishEntry entry : state.ranking) {
            if (entry.id == state.myId) {
                iFinished = true;
            }
        }
        if (!iFinished) {
            drawCentered(game.font, "Voce nao chegou a tempo. Mais sorte na proxima!", 200, Color.LIGHT_GRAY);
        }

        if (state.isHost()) {
            drawCentered(game.font, "ENTER: nova corrida (novo labirinto)    ESC: sair", 150, Color.YELLOW);
        } else {
            drawCentered(game.font, "Aguardando o host iniciar uma nova corrida...    ESC: sair", 150, Color.YELLOW);
        }
    }

    /** Ouro, prata ou bronze (branco depois do 3o). */
    private static Color medalColor(int position) {
        if (position >= 1 && position <= MEDAL_COLORS.length) {
            return MEDAL_COLORS[position - 1];
        }
        return Color.WHITE;
    }

    /** 12345 ms -> "12.3" */
    private static String formatSeconds(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        return (millis / 1000) + "." + ((millis % 1000) / 100);
    }

    /** 2.34f -> "2.3" (sem depender do idioma do computador, que poderia usar vírgula). */
    private static String formatOneDecimal(float seconds) {
        return formatSeconds((long) (Math.max(0f, seconds) * 1000));
    }
}
