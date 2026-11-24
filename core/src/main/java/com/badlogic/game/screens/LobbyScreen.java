package com.badlogic.game.screens;

import com.badlogic.game.Constants;
import com.badlogic.game.HareRaceGame;
import com.badlogic.game.Session;
import com.badlogic.game.model.Hare;
import com.badlogic.game.model.RaceState;
import com.badlogic.game.net.NetUtils;
import com.badlogic.game.net.Protocol;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;

import java.util.List;

/**
 * Sala de espera. Mostra a lista de lebres conectadas.
 * - O HOST vê o IP/porta para passar aos colegas e aperta ENTER para começar.
 * - Os outros jogadores aguardam o host iniciar.
 * Quando o servidor manda o labirinto + contagem, vamos para a GameScreen.
 */
public class LobbyScreen extends BaseScreen {

    /** IPs desta máquina (calculado uma vez só, pois é uma operação "lenta"). */
    private final List<String> localIps;

    public LobbyScreen(HareRaceGame game) {
        super(game);
        localIps = NetUtils.getLocalIPv4Addresses();
    }

    @Override
    public void render(float delta) {
        Session session = game.session;
        RaceState state = session.state;
        game.sounds.playMusic(game.sounds.menuMusic); // a mesma música calma do menu continua

        // 1) Lê as mensagens que chegaram do servidor.
        state.processMessages(session.client);

        // 2) Verifica se precisamos trocar de tela.
        if (state.errorMessage != null || state.disconnected) {
            String msg = state.errorMessage != null ? state.errorMessage : "Conexao com o servidor perdida.";
            game.closeSession();
            game.setScreen(new MenuScreen(game, msg));
            return;
        }
        if (state.phase == RaceState.Phase.RACE) {
            game.setScreen(new GameScreen(game));
            return;
        }

        // 3) Teclado.
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.closeSession();
            game.setScreen(new MenuScreen(game, "Voce saiu da sala."));
            return;
        }
        if (state.isHost() && Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            session.client.send(Protocol.START);
        }

        // 4) Desenho.
        beginFrame(0.10f, 0.14f, 0.18f);
        game.batch.begin();

        drawCentered(game.bigFont, "SALA DE ESPERA", 560, Color.WHITE);
        drawCentered(game.font, "Jogadores: " + state.hares.size() + " / " + Constants.MAX_PLAYERS,
            505, Color.LIGHT_GRAY);

        // Lista de jogadores em duas colunas (5 em cada).
        int index = 0;
        for (Hare hare : state.hares.values()) {
            float x = index < 5 ? 170 : 450;
            float y = 450 - (index % 5) * 48;
            game.batch.setColor(Constants.colorFor(hare.id));
            game.batch.draw(game.assets.hare, x, y - 26, 36, 36);
            game.batch.setColor(Color.WHITE);

            String label = hare.name;
            if (hare.id == state.hostId) {
                label += "  [host]";
            }
            if (hare.id == state.myId) {
                label += "  (voce)";
            }
            game.font.setColor(hare.id == state.myId ? Color.YELLOW : Color.WHITE);
            game.font.draw(game.batch, label, x + 46, y);
            game.font.setColor(Color.WHITE);
            index++;
        }

        // Instruções diferentes para o host e para os demais.
        if (state.isHost()) {
            // (session.server só é null se o host original saiu e nós herdamos o papel de host)
            int port = session.server != null ? session.server.getPort() : Constants.DEFAULT_PORT;
            String ips = localIps.isEmpty() ? "(nenhuma rede encontrada)" : String.join("  ou  ", localIps);
            drawCentered(game.font, "Peca para os colegas conectarem em:", 175, Color.LIGHT_GRAY);
            drawCentered(game.font, "IP: " + ips + "    Porta: " + port, 150, Color.CYAN);
            drawCentered(game.font, "Pressione ENTER para iniciar a corrida!", 100, Color.YELLOW);
        } else {
            drawCentered(game.font, "Aguardando o host iniciar a corrida...", 100, Color.YELLOW);
        }
        drawCentered(game.font, "ESC: sair da sala", 50, Color.GRAY);

        game.batch.end();
    }
}
