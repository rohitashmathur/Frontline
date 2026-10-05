package com.frontline.offline;

import java.io.ByteArrayInputStream;
import java.io.File;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

// Original eight-bar instrumental loop: soft pads, bass, and a restrained melody.
public final class MusicGenerator {
    private static final int RATE = 22050;
    private static final double BEAT = .625, BAR = BEAT*4;
    private static final int[][] CHORDS = {{45,52,57},{41,48,53},{48,55,60},{43,50,55}};
    private static final int[][] MELODY = {
        {69,-1,72,76,74,72,69,-1}, {69,72,-1,74,72,69,65,-1},
        {67,-1,72,76,79,76,72,-1}, {71,74,-1,76,74,71,67,-1},
        {69,72,76,-1,81,76,72,-1}, {77,76,72,-1,69,72,65,-1},
        {76,79,76,-1,72,67,72,-1}, {74,71,67,-1,71,67,64,-1}
    };

    public static void main(String[] args) throws Exception {
        byte[] pcm = compose();
        File file = new File(args[0]); file.getParentFile().mkdirs();
        AudioFormat format = new AudioFormat(RATE,16,1,true,false);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm),format,pcm.length/2)) {
            AudioSystem.write(stream,AudioFileFormat.Type.WAVE,file);
        }
        System.out.println("Original 20-second music loop generated: "+file);
    }

    static byte[] compose() {
        int frames = (int)(8*BAR*RATE);
        byte[] pcm = new byte[frames*2];
        for (int frame = 0; frame < frames; frame++) {
            double t = frame/(double)RATE, local = t%BAR;
            int bar = (int)(t/BAR);
            int[] chord = CHORDS[bar%4];
            double padEnvelope = envelope(local,BAR,.28,.38), sample = 0;
            for (int note : chord) {
                double phase = 2*Math.PI*frequency(note)*local;
                sample += .055*padEnvelope*(Math.sin(phase)+.16*Math.sin(phase*2));
            }
            sample += .09*padEnvelope*Math.sin(2*Math.PI*frequency(chord[0]-12)*local);
            int eighth = Math.min(7,(int)(local/(BEAT/2))), note = MELODY[bar][eighth];
            if (note >= 0) {
                double age = local-eighth*BEAT/2, phase = 2*Math.PI*frequency(note)*age;
                sample += .11*envelope(age,BEAT/2,.02,.12)*(Math.sin(phase)+.12*Math.sin(phase*3));
            }
            short value = (short)Math.round(sample*32767);
            pcm[frame*2] = (byte)value; pcm[frame*2+1] = (byte)(value>>8);
        }
        return pcm;
    }

    private static double frequency(int midi) { return 440*Math.pow(2,(midi-69)/12.0); }
    private static double envelope(double t,double duration,double attack,double release) {
        double level = Math.max(0,Math.min(1,Math.min(t/attack,(duration-t)/release)));
        return level*level*(3-2*level);
    }
}
