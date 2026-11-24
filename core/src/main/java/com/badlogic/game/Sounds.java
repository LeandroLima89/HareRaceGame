package com.badlogic.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

/**
 * Carrega e toca os efeitos sonoros do jogo.
 *
 * Os arquivos ficam em "assets/sounds/" (formato WAV, curtos):
 *   countdown.wav - bipe do "3, 2, 1"
 *   go.wav        - bipe mais agudo do "VAI!"
 *   clover.wav    - pegou um trevo (notas subindo)
 *   mud.wav       - pisou na lama (som grave)
 *   finish.wav    - alguém chegou na cenoura
 *   win.wav       - EU cheguei em 1o lugar (fanfarra)
 *   end.wav       - fim da corrida
 *   gate_warning.wav - um portão perto de mim vai fechar
 *   gate_close.wav   - um portão perto de mim fechou
 *
 * E as MÚSICAS (geradas pelo programa tools/GerarMusica.java):
 *   music_menu.wav      - calma: menu, sala de espera e pódio
 *   music_race.wav      - animada: durante a corrida
 *   music_race_fast.wav - a mesma da corrida, mais rápida: nos 20 s finais
 *
 * No libGDX existem dois tipos de áudio:
 * - Sound: efeito CURTO, carregado inteiro na memória e tocado na hora
 *   (pode tocar várias vezes ao mesmo tempo). Usamos para os efeitos.
 * - Music: arquivo LONGO (trilha sonora), lido aos poucos do disco enquanto
 *   toca, e que pode repetir sem parar (loop). Usamos para as músicas.
 *
 * Se algum arquivo não existir, o som fica null e simplesmente não toca,
 * para o jogo não quebrar.
 */
public class Sounds implements Disposable {

    /** Volume das músicas (0 a 1). Mais baixo que os efeitos para não abafá-los. */
    private static final float MUSIC_VOLUME = 0.35f;

    public final Music menuMusic;
    public final Music raceMusic;
    public final Music raceFastMusic;

    /** Música que está selecionada agora (pode estar pausada se o som estiver desligado). */
    private Music currentMusic;

    public final Sound countdown;
    public final Sound go;
    public final Sound clover;
    public final Sound mud;
    public final Sound finish;
    public final Sound win;
    public final Sound end;
    public final Sound gateWarning;
    public final Sound gateClose;

    /** Liga/desliga todos os sons, efeitos e música (tecla M durante a corrida). */
    public boolean enabled = true;

    public Sounds() {
        countdown = load("countdown.wav");
        go = load("go.wav");
        clover = load("clover.wav");
        mud = load("mud.wav");
        finish = load("finish.wav");
        win = load("win.wav");
        end = load("end.wav");
        gateWarning = load("gate_warning.wav");
        gateClose = load("gate_close.wav");

        menuMusic = loadMusic("music_menu.wav");
        raceMusic = loadMusic("music_race.wav");
        raceFastMusic = loadMusic("music_race_fast.wav");
    }

    /** Carrega uma música da pasta assets/sounds, já configurada para repetir. Null se não existir. */
    private Music loadMusic(String fileName) {
        FileHandle file = Gdx.files.internal("sounds/" + fileName);
        if (!file.exists()) {
            Gdx.app.log("Sounds", "Arquivo sounds/" + fileName + " nao encontrado. Musica desativada.");
            return null;
        }
        Music music = Gdx.audio.newMusic(file);
        music.setLooping(true); // quando termina, volta ao começo sozinha
        music.setVolume(MUSIC_VOLUME);
        return music;
    }

    /**
     * Diz qual música deve estar tocando. As telas chamam isto A CADA QUADRO
     * (60 vezes por segundo), então o método precisa ser "barato" e só agir
     * quando algo muda:
     * - se pedirem uma música DIFERENTE da atual, para a atual e troca;
     * - se o som estiver ligado e a música parada, dá play;
     * - se o som estiver desligado (tecla M), pausa.
     *
     * Por que não chamar só uma vez, quando muda a tela? Porque assim a tela
     * não precisa "lembrar" o que já fez: ela só diz o que quer, e a tecla M
     * passa a valer para a música automaticamente.
     *
     * @param music música desejada (null = nenhuma)
     */
    public void playMusic(Music music) {
        if (music != currentMusic) {
            if (currentMusic != null) {
                currentMusic.stop(); // stop volta a música atual para o começo
            }
            currentMusic = music;
        }
        if (currentMusic == null) {
            return;
        }
        if (enabled && !currentMusic.isPlaying()) {
            currentMusic.play();  // depois de um pause, continua de onde parou
        } else if (!enabled && currentMusic.isPlaying()) {
            currentMusic.pause();
        }
    }

    /** Carrega um som da pasta assets/sounds. Retorna null se o arquivo não existir. */
    private Sound load(String fileName) {
        FileHandle file = Gdx.files.internal("sounds/" + fileName);
        if (!file.exists()) {
            Gdx.app.log("Sounds", "Arquivo sounds/" + fileName + " nao encontrado. Som desativado.");
            return null;
        }
        return Gdx.audio.newSound(file);
    }

    /**
     * Toca um som, se os sons estiverem ligados.
     *
     * @param volume de 0 (mudo) a 1 (volume máximo)
     */
    public void play(Sound sound, float volume) {
        if (enabled && sound != null) {
            sound.play(volume);
        }
    }

    /** Libera a memória dos sons e das músicas (Sound e Music também são Disposable). */
    @Override
    public void dispose() {
        Sound[] all = {countdown, go, clover, mud, finish, win, end, gateWarning, gateClose};
        for (Sound s : all) {
            if (s != null) {
                s.dispose();
            }
        }
        Music[] musics = {menuMusic, raceMusic, raceFastMusic};
        for (Music m : musics) {
            if (m != null) {
                m.dispose();
            }
        }
    }
}
