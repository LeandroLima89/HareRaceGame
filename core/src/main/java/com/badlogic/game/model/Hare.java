package com.badlogic.game.model;

import com.badlogic.game.net.Protocol;
import com.badlogic.gdx.utils.TimeUtils;

/**
 * Representa uma lebre (um jogador) do ponto de vista do CLIENTE,
 * ou seja, as informações necessárias para desenhá-la na tela.
 *
 * Temos duas posições:
 * - gridX/gridY: a posição "oficial" em células, informada pelo servidor.
 * - drawX/drawY: a posição usada para desenhar, que vai deslizando suavemente
 *   até a posição oficial (interpolação). Sem isso a lebre "teletransportaria"
 *   de uma célula para outra e o movimento ficaria travado.
 */
public class Hare {

    /** Identificador do jogador (0 a 9). Também define a cor da lebre. */
    public final int id;

    /** Nome digitado pelo jogador no menu. */
    public String name = "";

    /** Posição oficial (em células do labirinto). */
    public int gridX;
    public int gridY;

    /** Posição de desenho (em células, mas com casas decimais). */
    public float drawX;
    public float drawY;

    /** true quando a lebre está virada para a esquerda (para espelhar a imagem). */
    public boolean facingLeft;

    /** 0 = ainda correndo; 1, 2, 3... = posição em que chegou na cenoura. */
    public int finishPosition;

    /** Efeito ativo: Protocol.CLOVER, Protocol.MUD ou Protocol.NO_EFFECT. */
    public char effect = Protocol.NO_EFFECT;

    /** Até quando o efeito vale, no relógio LOCAL (TimeUtils.millis()). */
    public long effectEndMillis;

    public Hare(int id) {
        this.id = id;
    }

    /** Liga um efeito por "durationMillis" milissegundos a partir de agora. */
    public void setEffect(char effect, long durationMillis) {
        this.effect = effect;
        this.effectEndMillis = TimeUtils.millis() + durationMillis;
    }

    /** Efeito que vale AGORA (o efeito some sozinho quando o tempo acaba). */
    public char getActiveEffect() {
        return TimeUtils.millis() < effectEndMillis ? effect : Protocol.NO_EFFECT;
    }

    /** Volta ao estado de largada (chamado no começo de cada corrida). */
    public void resetForRace() {
        finishPosition = 0;
        effect = Protocol.NO_EFFECT;
        effectEndMillis = 0;
    }

    /**
     * Atualiza a posição oficial da lebre.
     *
     * @param snap se true, a lebre aparece direto no lugar (sem animação).
     *             Usamos isso no início da corrida, quando todos voltam para a largada.
     */
    public void setGridPosition(int x, int y, boolean snap) {
        // Vira a lebre para o lado em que ela está andando.
        if (x < gridX) {
            facingLeft = true;
        } else if (x > gridX) {
            facingLeft = false;
        }
        gridX = x;
        gridY = y;
        if (snap) {
            drawX = x;
            drawY = y;
        }
    }

    /**
     * Chamado a cada quadro (frame). Aproxima a posição de desenho da posição oficial.
     * A cada frame percorremos uma fração da distância que falta: isso gera um
     * movimento rápido no começo e suave no final.
     *
     * @param delta tempo (segundos) desde o último frame
     */
    public void update(float delta) {
        float alpha = Math.min(1f, delta * 18f);
        drawX += (gridX - drawX) * alpha;
        drawY += (gridY - drawY) * alpha;
    }
}
