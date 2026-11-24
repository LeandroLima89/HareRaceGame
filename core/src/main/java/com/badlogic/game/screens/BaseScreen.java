package com.badlogic.game.screens;

import com.badlogic.game.Constants;
import com.badlogic.game.HareRaceGame;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * Classe base com o que TODAS as telas têm em comum: câmera, viewport e
 * algumas funções de desenho de texto. As outras telas herdam dela.
 *
 * Viewport: define um "mundo" fixo de 800x600. Se a janela for redimensionada,
 * o FitViewport aumenta/diminui a imagem mantendo a proporção (com faixas pretas
 * se necessário). Assim todos os jogadores veem o mesmo enquadramento.
 */
public abstract class BaseScreen extends ScreenAdapter {

    protected final HareRaceGame game;
    protected final OrthographicCamera camera;
    protected final FitViewport viewport;

    /** Objeto reaproveitado para medir textos (para centralizar). */
    private final GlyphLayout layout = new GlyphLayout();

    protected BaseScreen(HareRaceGame game) {
        this.game = game;
        this.camera = new OrthographicCamera();
        this.viewport = new FitViewport(Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT, camera);
        // Posiciona a câmera de forma que (0,0) seja o canto inferior esquerdo.
        this.viewport.update(Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT, true);
    }

    /** O libGDX chama isto quando a janela muda de tamanho. */
    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    /** Limpa a tela e prepara o batch para desenhar usando a nossa câmera. */
    protected void beginFrame(float r, float g, float b) {
        ScreenUtils.clear(r, g, b, 1f);
        viewport.apply();
        game.batch.setProjectionMatrix(camera.combined);
    }

    /** Desenha um texto centralizado horizontalmente na posição y. */
    protected void drawCentered(BitmapFont font, String text, float y, Color color) {
        font.setColor(color);
        layout.setText(font, text);
        font.draw(game.batch, text, (Constants.WORLD_WIDTH - layout.width) / 2f, y);
        font.setColor(Color.WHITE);
    }

    /** Desenha um texto centralizado em torno de centerX. */
    protected void drawCenteredAt(BitmapFont font, String text, float centerX, float y, Color color) {
        font.setColor(color);
        layout.setText(font, text);
        font.draw(game.batch, text, centerX - layout.width / 2f, y);
        font.setColor(Color.WHITE);
    }

    /** Desenha um retângulo colorido usando a textura de 1 pixel. */
    protected void drawRect(float x, float y, float w, float h, Color color) {
        game.batch.setColor(color);
        game.batch.draw(game.assets.pixel, x, y, w, h);
        game.batch.setColor(Color.WHITE);
    }
}
