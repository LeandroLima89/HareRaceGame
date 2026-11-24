import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * GERADOR DAS MÚSICAS DO JOGO (não faz parte do jogo em si).
 *
 * Este programa "compõe" e "toca" as músicas sozinho, calculando cada
 * amostra de som com matemática, e salva o resultado em arquivos .wav
 * dentro de assets/sounds/. Nenhum arquivo de música de terceiros é usado,
 * então não há problema de direitos autorais.
 *
 * Como rodar (na pasta do projeto, com Java 11 ou mais novo):
 *     java tools/GerarMusica.java
 *
 * Gera três arquivos:
 *   music_menu.wav      - música calma do menu, da sala de espera e do pódio (90 BPM)
 *   music_race.wav      - música animada da corrida (140 BPM)
 *   music_race_fast.wav - a MESMA música da corrida, só que mais rápida (175 BPM),
 *                         usada nos 20 s finais, depois que a 1a lebre chega
 *
 * ------------------------------------------------------------------
 * UM POUCO DE TEORIA (para entender o código)
 * ------------------------------------------------------------------
 * - Som digital é uma lista de números (AMOSTRAS) que dizem a posição do alto-
 *   falante a cada instante. Usamos 22050 amostras por segundo (SAMPLE_RATE).
 *   Isso é metade da qualidade de CD, suficiente para um som de videogame
 *   antigo e deixa o arquivo com metade do tamanho.
 * - Uma NOTA é uma onda que se repete com certa FREQUÊNCIA (em Hz). O Lá central
 *   (A4) tem 440 Hz. Cada semitono acima multiplica a frequência por 2^(1/12).
 * - O FORMATO da onda muda o "timbre" (o jeito do som):
 *     quadrada  -> som de videogame clássico (melodia da corrida)
 *     triângulo -> som mais suave (baixo e acompanhamento)
 *     seno      -> som "puro", parecido com flauta (melodia do menu)
 *     ruído     -> sons sem nota definida (bateria: chimbal e caixa)
 * - ENVELOPE: o volume de cada nota sobe rápido no começo (ataque) e desce no
 *   fim (release). Sem isso a nota começa e termina com um "clique".
 * - BPM (batidas por minuto) define a velocidade. Aqui a menor duração é a
 *   COLCHEIA (meia batida), e um compasso 4/4 tem 8 colcheias.
 */
public class GerarMusica {

    /** Amostras por segundo. */
    static final int SAMPLE_RATE = 22050;

    /** Pasta onde os arquivos são salvos (relativa à pasta do projeto). */
    static final String OUTPUT_DIR = "assets/sounds/";

    // ==================================================================
    // A COMPOSIÇÃO
    // Cada compasso da melodia é escrito como "NOTA:duração", com a duração
    // em colcheias (a soma de cada compasso dá 8). "-" é pausa (silêncio).
    // Os acordes dizem a harmonia de cada compasso: o baixo e o
    // acompanhamento são gerados automaticamente a partir deles.
    // ==================================================================

    /** Música do menu: 8 compassos, em Lá menor, calma. */
    static final String[] MENU_CHORDS = {"Am", "F", "C", "G", "Am", "F", "C", "G"};
    static final String[] MENU_MELODY = {
        "A4:2 C5:2 E5:4",
        "F5:2 E5:2 C5:4",
        "G4:2 C5:2 E5:3 D5:1",
        "D5:4 B4:4",
        "A4:2 C5:2 E5:2 A5:2",
        "G5:2 F5:2 E5:2 C5:2",
        "E5:3 D5:1 C5:2 E5:2",
        "D5:6 -:2",
    };

    /** Música da corrida: 16 compassos (parte A + parte B), em Dó maior, animada. */
    static final String[] RACE_CHORDS = {
        "C", "G", "Am", "F", "C", "G", "Am", "F",   // parte A
        "Am", "F", "C", "G", "Am", "F", "G", "G",   // parte B
    };
    static final String[] RACE_MELODY = {
        // Parte A: tema principal, sobe e desce pelas notas do acorde.
        "C5:1 E5:1 G5:2 E5:1 G5:1 C6:2",
        "B5:2 G5:1 D5:1 B4:2 D5:2",
        "A5:1 G5:1 E5:2 C5:2 E5:2",
        "F5:2 A5:2 G5:2 F5:2",
        "E5:1 G5:1 C6:2 G5:1 E5:1 C5:2",
        "D5:1 E5:1 D5:1 B4:1 G4:4",
        "A4:1 C5:1 E5:1 A5:1 G5:2 E5:2",
        "F5:3 E5:1 D5:2 C5:2",
        // Parte B: mais "tensa" (começa no acorde menor) e termina no Sol,
        // que "puxa" de volta para o Dó do início: assim o loop soa natural.
        "E5:2 E5:1 D5:1 C5:2 A4:2",
        "C5:2 C5:1 D5:1 F5:2 A5:2",
        "G5:2 E5:1 C5:1 E5:2 G5:2",
        "D5:2 B4:1 G4:1 B4:2 D5:2",
        "A5:2 G5:1 E5:1 A5:2 G5:1 E5:1",
        "F5:2 E5:1 F5:1 A5:2 C6:2",
        "B5:2 A5:1 G5:1 F5:2 D5:2",
        "G5:1 F5:1 E5:1 D5:1 B4:2 G4:2",
    };

    public static void main(String[] args) throws IOException {
        new File(OUTPUT_DIR).mkdirs();

        // Menu: melodia em seno (flauta), acompanhamento suave, sem bateria.
        float[] menu = compose(90, MENU_CHORDS, MENU_MELODY, Wave.SINE, false);
        save("music_menu.wav", menu);

        // Corrida: melodia em onda quadrada, baixo "pulsando" e bateria.
        float[] race = compose(140, RACE_CHORDS, RACE_MELODY, Wave.SQUARE, true);
        save("music_race.wav", race);

        // Reta final: exatamente a mesma partitura, só que 25% mais rápida.
        float[] fast = compose(175, RACE_CHORDS, RACE_MELODY, Wave.SQUARE, true);
        save("music_race_fast.wav", fast);
    }

    // ==================================================================
    // Montagem da música
    // ==================================================================

    /** Formatos de onda disponíveis. */
    enum Wave { SINE, SQUARE, TRIANGLE }

    /**
     * Junta todas as "faixas" (melodia, baixo, acompanhamento e bateria)
     * numa única lista de amostras.
     *
     * @param bpm     velocidade em batidas por minuto
     * @param drums   true para colocar bateria
     */
    static float[] compose(int bpm, String[] chords, String[] melody, Wave leadWave, boolean drums) {
        // Uma batida dura 60/bpm segundos; a colcheia é metade disso.
        // Arredondamos para um número inteiro de amostras, assim todos os
        // compassos têm exatamente o mesmo tamanho e o loop não "escorrega".
        int eighth = Math.round(SAMPLE_RATE * 60f / bpm / 2f);
        int bars = chords.length;
        float[] out = new float[eighth * 8 * bars];

        for (int bar = 0; bar < bars; bar++) {
            int barStart = bar * 8 * eighth;
            int[] chord = chordNotes(chords[bar]); // {fundamental, terça, quinta} em MIDI

            // ---- Melodia ----
            int pos = barStart;
            for (String token : melody[bar].trim().split("\\s+")) {
                String[] parts = token.split(":");
                int length = Integer.parseInt(parts[1]) * eighth;
                if (!parts[0].equals("-")) {
                    float volume = leadWave == Wave.SQUARE ? 0.13f : 0.30f;
                    addNote(out, pos, length, midiToFreq(noteToMidi(parts[0])), leadWave, volume);
                }
                pos += length;
            }

            if (drums) {
                // ---- Baixo da corrida: colcheias alternando a nota grave e a mesma nota
                // uma oitava acima (o famoso baixo "pulando" dos jogos antigos).
                for (int i = 0; i < 8; i++) {
                    int midi = chord[0] - 12 + (i % 2 == 0 ? 0 : 12);
                    addNote(out, barStart + i * eighth, eighth, midiToFreq(midi), Wave.TRIANGLE, 0.32f);
                }
                // ---- Bateria: bumbo nas batidas 1 e 3, caixa nas 2 e 4, chimbal em toda colcheia.
                for (int i = 0; i < 8; i++) {
                    int at = barStart + i * eighth;
                    if (i == 0 || i == 4) {
                        addKick(out, at);
                    }
                    if (i == 2 || i == 6) {
                        addSnare(out, at, bar * 8 + i);
                    }
                    addHiHat(out, at, bar * 8 + i);
                }
            } else {
                // ---- Baixo do menu: notas longas (meio compasso cada).
                addNote(out, barStart, 4 * eighth, midiToFreq(chord[0] - 12), Wave.TRIANGLE, 0.28f);
                addNote(out, barStart + 4 * eighth, 4 * eighth, midiToFreq(chord[0] - 12), Wave.TRIANGLE, 0.22f);
                // ---- Acompanhamento: ARPEJO, ou seja, as notas do acorde tocadas uma de
                // cada vez (fundamental, quinta, oitava, quinta, décima...).
                int[] arpeggio = {chord[0], chord[2], chord[0] + 12, chord[2], chord[1] + 12, chord[2], chord[0] + 12, chord[2]};
                for (int i = 0; i < 8; i++) {
                    addNote(out, barStart + i * eighth, eighth, midiToFreq(arpeggio[i]), Wave.TRIANGLE, 0.10f);
                }
            }
        }
        normalize(out);
        return out;
    }

    // ==================================================================
    // Instrumentos
    // ==================================================================

    /**
     * Soma uma nota na faixa, a partir da amostra "start", durando "length" amostras.
     * A nota soa por 90% da duração; os 10% finais ficam em silêncio para as
     * notas não "grudarem" umas nas outras.
     */
    static void addNote(float[] out, int start, int length, double freq, Wave wave, float volume) {
        int soundLength = (int) (length * 0.9f);
        int attack = SAMPLE_RATE / 200;                      // 5 ms para o volume subir
        int release = Math.min(SAMPLE_RATE / 25, soundLength / 3); // até 40 ms para descer
        for (int i = 0; i < soundLength && start + i < out.length; i++) {
            // "phase" vai de 0 a 1 ao longo de UM ciclo da onda.
            double t = (double) i / SAMPLE_RATE;
            double phase = (t * freq) % 1.0;
            double sample;
            switch (wave) {
                case SQUARE:
                    // Fica "em cima" 25% do ciclo e "embaixo" 75%: soa como os consoles antigos.
                    sample = phase < 0.25 ? 1.0 : -1.0;
                    break;
                case TRIANGLE:
                    // Sobe em linha reta e desce em linha reta.
                    sample = 4.0 * Math.abs(phase - 0.5) - 1.0;
                    break;
                default:
                    // Seno com um pouquinho do dobro da frequência: soa como flauta.
                    sample = Math.sin(2 * Math.PI * phase) + 0.2 * Math.sin(4 * Math.PI * phase);
                    break;
            }
            // Envelope: multiplica o volume por 0..1 no começo e no fim da nota.
            double envelope = 1.0;
            if (i < attack) {
                envelope = (double) i / attack;
            } else if (i > soundLength - release) {
                envelope = (double) (soundLength - i) / release;
            }
            // Um leve decaimento ao longo da nota deixa o som menos "robótico".
            envelope *= 1.0 - 0.3 * i / (double) soundLength;
            out[start + i] += (float) (sample * envelope * volume);
        }
    }

    /** Bumbo: um seno grave cuja frequência cai rapidamente (120 Hz -> 45 Hz), o "tum". */
    static void addKick(float[] out, int start) {
        int length = (int) (SAMPLE_RATE * 0.12);
        double phase = 0;
        for (int i = 0; i < length && start + i < out.length; i++) {
            double progress = (double) i / length;
            double freq = 45 + 75 * (1 - progress) * (1 - progress);
            phase += freq / SAMPLE_RATE;
            double envelope = Math.pow(1 - progress, 2);
            out[start + i] += (float) (Math.sin(2 * Math.PI * phase) * envelope * 0.55);
        }
    }

    /** Caixa: ruído (chiado) que some rápido, misturado com um tom grave. */
    static void addSnare(float[] out, int start, long seed) {
        Random random = new Random(seed); // mesma semente = mesmo som toda vez
        int length = (int) (SAMPLE_RATE * 0.10);
        for (int i = 0; i < length && start + i < out.length; i++) {
            double progress = (double) i / length;
            double envelope = Math.pow(1 - progress, 3);
            double noise = random.nextDouble() * 2 - 1;
            double tone = Math.sin(2 * Math.PI * 180 * i / SAMPLE_RATE);
            out[start + i] += (float) ((noise * 0.20 + tone * 0.08) * envelope);
        }
    }

    /**
     * Chimbal: ruído bem curto e agudo.
     * Para deixar o ruído agudo, usamos a DIFERENÇA entre duas amostras
     * seguidas (um "filtro passa-altas" bem simples): o que muda devagar some.
     */
    static void addHiHat(float[] out, int start, long seed) {
        Random random = new Random(seed * 31 + 7);
        int length = (int) (SAMPLE_RATE * 0.03);
        double previous = 0;
        for (int i = 0; i < length && start + i < out.length; i++) {
            double noise = random.nextDouble() * 2 - 1;
            double high = noise - previous;
            previous = noise;
            double envelope = 1 - (double) i / length;
            out[start + i] += (float) (high * envelope * 0.035);
        }
    }

    // ==================================================================
    // Notas e acordes
    // ==================================================================

    /**
     * Converte o nome da nota em número MIDI (padrão dos instrumentos digitais).
     * Ex.: "C4" = 60 (Dó central), "A4" = 69 (Lá 440 Hz), "C#5" = 73.
     */
    static int noteToMidi(String name) {
        int[] semitones = {9, 11, 0, 2, 4, 5, 7}; // A B C D E F G
        int index = semitones[name.charAt(0) - 'A'];
        int i = 1;
        if (name.charAt(1) == '#') {
            index++;
            i++;
        }
        int octave = Integer.parseInt(name.substring(i));
        return 12 * (octave + 1) + index;
    }

    /** Frequência em Hz: cada semitono multiplica por 2^(1/12); 69 (A4) = 440 Hz. */
    static double midiToFreq(int midi) {
        return 440.0 * Math.pow(2, (midi - 69) / 12.0);
    }

    /**
     * Notas do acorde na oitava 3: {fundamental, terça, quinta}.
     * Acorde maior: terça a 4 semitons. Menor (nome termina em "m"): 3 semitons.
     * A quinta fica sempre a 7 semitons.
     */
    static int[] chordNotes(String chord) {
        boolean minor = chord.endsWith("m");
        int root = noteToMidi(chord.replace("m", "") + "3");
        return new int[]{root, root + (minor ? 3 : 4), root + 7};
    }

    // ==================================================================
    // Arquivo WAV
    // ==================================================================

    /**
     * Ajusta o volume geral para o pico ficar em 85% do máximo. Somar várias
     * faixas pode passar de 1.0, o que causaria distorção ("estouro").
     */
    static void normalize(float[] samples) {
        float peak = 0;
        for (float s : samples) {
            peak = Math.max(peak, Math.abs(s));
        }
        if (peak > 0) {
            float gain = 0.85f / peak;
            for (int i = 0; i < samples.length; i++) {
                samples[i] *= gain;
            }
        }
    }

    /**
     * Salva as amostras num arquivo WAV (mono, 16 bits).
     * Um WAV é um CABEÇALHO de 44 bytes (que descreve o formato) seguido das
     * amostras, cada uma como um número inteiro de -32768 a 32767.
     * O formato WAV guarda os números em "little-endian" (byte menos
     * significativo primeiro), por isso os métodos writeIntLE/writeShortLE.
     */
    static void save(String fileName, float[] samples) throws IOException {
        File file = new File(OUTPUT_DIR + fileName);
        int dataSize = samples.length * 2; // 2 bytes por amostra
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeBytes("RIFF");
            writeIntLE(out, 36 + dataSize);     // tamanho do resto do arquivo
            out.writeBytes("WAVE");
            out.writeBytes("fmt ");
            writeIntLE(out, 16);                // tamanho do bloco "fmt "
            writeShortLE(out, 1);               // 1 = PCM (sem compressão)
            writeShortLE(out, 1);               // 1 canal (mono)
            writeIntLE(out, SAMPLE_RATE);       // amostras por segundo
            writeIntLE(out, SAMPLE_RATE * 2);   // bytes por segundo
            writeShortLE(out, 2);               // bytes por amostra
            writeShortLE(out, 16);              // bits por amostra
            out.writeBytes("data");
            writeIntLE(out, dataSize);
            for (float s : samples) {
                writeShortLE(out, Math.round(Math.max(-1f, Math.min(1f, s)) * 32767));
            }
        }
        System.out.printf("%s: %.1f s, %d KB%n", file.getPath(),
            samples.length / (float) SAMPLE_RATE, file.length() / 1024);
    }

    static void writeIntLE(DataOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    static void writeShortLE(DataOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
}
