package com.badlogic.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

/**
 * Carrega e guarda as imagens (texturas) usadas no jogo.
 *
 * Os arquivos PNG ficam na pasta "assets" do projeto:
 *   hare.png   - a lebre (32x32, BRANCA, virada para a direita, fundo transparente)
 *   wall.png   - parede / cerca viva do labirinto (32x32)
 *   floor.png  - chão / caminho de terra (32x32)
 *   carrot.png - a cenoura da linha de chegada (32x32, fundo transparente)
 *   clover.png - trevo de quatro folhas, item que acelera (32x32, fundo transparente)
 *   mud.png    - poça de lama, item que atrasa (32x32, fundo transparente)
 *   gate.png   - portão de madeira que abre e fecha (32x32, fundo transparente)
 *
 * Se algum arquivo não existir, criamos uma imagem simples via código (Pixmap)
 * para o jogo não quebrar. Assim dá para testar antes de ter as artes prontas.
 */
public class Assets implements Disposable {

    public final Texture hare;
    public final Texture wall;
    public final Texture floor;
    public final Texture carrot;
    public final Texture clover;
    public final Texture mud;
    public final Texture gate;

    /** Textura de 1 pixel branco: serve para desenhar retângulos coloridos (fundo do HUD, etc.). */
    public final Texture pixel;

    public Assets() {
        hare = load("hare.png", Color.WHITE, true);
        wall = load("wall.png", new Color(0.13f, 0.40f, 0.16f, 1f), false);
        floor = load("floor.png", new Color(0.82f, 0.72f, 0.52f, 1f), false);
        carrot = load("carrot.png", Color.ORANGE, true);
        clover = load("clover.png", new Color(0.20f, 0.85f, 0.25f, 1f), true);
        mud = load("mud.png", new Color(0.40f, 0.26f, 0.13f, 1f), true);
        gate = load("gate.png", new Color(0.55f, 0.35f, 0.15f, 1f), false);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose(); // o Pixmap já foi copiado para a placa de vídeo, pode liberar
    }

    /**
     * Carrega um PNG. Se não existir, gera um substituto.
     *
     * @param fallbackColor cor do substituto
     * @param round         se true o substituto é um círculo, senão um quadrado
     */
    private Texture load(String fileName, Color fallbackColor, boolean round) {
        FileHandle file = Gdx.files.internal(fileName);
        if (file.exists()) {
            Texture texture = new Texture(file);
            // Nearest = mantém os pixels "nítidos" (ideal para pixel art).
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            return texture;
        }

        Gdx.app.log("Assets", "Arquivo " + fileName + " nao encontrado. Usando imagem gerada.");
        int size = Constants.TILE_SIZE;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(fallbackColor);
        if (round) {
            pm.fillCircle(size / 2, size / 2, size / 2 - 3);
        } else {
            pm.fill();
            pm.setColor(Color.BLACK);
            pm.drawRectangle(0, 0, size, size);
        }
        Texture texture = new Texture(pm);
        pm.dispose();
        return texture;
    }

    /** Libera a memória de vídeo. No libGDX, tudo que é Disposable precisa ser liberado manualmente. */
    @Override
    public void dispose() {
        hare.dispose();
        wall.dispose();
        floor.dispose();
        carrot.dispose();
        clover.dispose();
        mud.dispose();
        gate.dispose();
        pixel.dispose();
    }
}
