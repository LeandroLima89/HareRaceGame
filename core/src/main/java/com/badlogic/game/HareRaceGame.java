package com.badlogic.game;

import com.badlogic.game.screens.MenuScreen;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Classe principal do jogo "Corrida das Lebres".
 *
 * Ela estende {@link Game}, uma classe do libGDX que gerencia TELAS (Screens).
 * Cada tela cuida de uma parte do jogo:
 *   MenuScreen  -> digitar nome, IP e porta; criar ou entrar numa sala
 *   LobbyScreen -> sala de espera, mostra quem já entrou
 *   GameScreen  -> a corrida no labirinto
 *
 * Aqui criamos os objetos compartilhados por todas as telas (SpriteBatch,
 * fontes e imagens), pois criá-los várias vezes desperdiçaria memória.
 */
public class HareRaceGame extends Game {

    /** Desenha imagens na tela em "lotes" (muito mais rápido do que uma por vez). */
    public SpriteBatch batch;

    /** Fonte padrão do libGDX (não precisa de arquivo). */
    public BitmapFont font;

    /** Mesma fonte, só que maior, para títulos e contagem regressiva. */
    public BitmapFont bigFont;

    /** Imagens do jogo. */
    public Assets assets;

    /** Efeitos sonoros do jogo. */
    public Sounds sounds;

    /** Sessão de rede atual (null quando estamos no menu). */
    public Session session;

    /** Chamado UMA vez quando o jogo abre. */
    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        bigFont = new BitmapFont();
        bigFont.getData().setScale(2.5f);
        assets = new Assets();
        sounds = new Sounds();

        setScreen(new MenuScreen(this, ""));
    }

    /** Encerra a sessão de rede atual (sai da sala e, se for host, desliga o servidor). */
    public void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
    }

    /** Chamado quando o jogo fecha: liberamos tudo. */
    @Override
    public void dispose() {
        closeSession();
        if (getScreen() != null) {
            getScreen().dispose();
        }
        batch.dispose();
        font.dispose();
        bigFont.dispose();
        assets.dispose();
        sounds.dispose();
    }
}
