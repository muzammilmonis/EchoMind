package com.echomind.app;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;

public class WavRecorder {
    public static final int SAMPLE_RATE = 16000;
    private AudioRecord audioRecord;
    private Thread worker;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private long startedAt;

    public void start(File output) throws Exception {
        if (recording.get()) return;
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(min, 4096);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("Microphone could not be initialized");
        }

        if (output.exists()) output.delete();
        writeEmptyHeader(output);
        recording.set(true);
        startedAt = System.currentTimeMillis();
        audioRecord.startRecording();

        worker = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            try (FileOutputStream fos = new FileOutputStream(output, true)) {
                while (recording.get()) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) fos.write(buffer, 0, read);
                }
            } catch (Exception ignored) {
            }
        }, "EchoMind-Recorder");
        worker.start();
    }

    public long stop(File output) throws Exception {
        if (!recording.getAndSet(false)) return 0;
        try { audioRecord.stop(); } catch (Exception ignored) {}
        if (worker != null) worker.join(2000);
        audioRecord.release();
        audioRecord = null;
        patchHeader(output);
        return Math.max(0, System.currentTimeMillis() - startedAt);
    }

    public boolean isRecording() { return recording.get(); }

    private static void writeEmptyHeader(File file) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] h = new byte[44];
            h[0]='R';h[1]='I';h[2]='F';h[3]='F';
            h[8]='W';h[9]='A';h[10]='V';h[11]='E';
            h[12]='f';h[13]='m';h[14]='t';h[15]=' ';
            writeIntLE(h,16,16); writeShortLE(h,20,(short)1); writeShortLE(h,22,(short)1);
            writeIntLE(h,24,SAMPLE_RATE); writeIntLE(h,28,SAMPLE_RATE*2);
            writeShortLE(h,32,(short)2); writeShortLE(h,34,(short)16);
            h[36]='d';h[37]='a';h[38]='t';h[39]='a';
            out.write(h);
        }
    }

    private static void patchHeader(File file) throws Exception {
        long data = Math.max(0, file.length() - 44);
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(4); writeIntLE(raf, (int)(36 + data));
            raf.seek(40); writeIntLE(raf, (int)data);
        }
    }

    private static void writeIntLE(byte[] b,int o,int v){b[o]=(byte)v;b[o+1]=(byte)(v>>8);b[o+2]=(byte)(v>>16);b[o+3]=(byte)(v>>24);}
    private static void writeShortLE(byte[] b,int o,short v){b[o]=(byte)v;b[o+1]=(byte)(v>>8);}
    private static void writeIntLE(RandomAccessFile r,int v)throws Exception{r.write(v);r.write(v>>8);r.write(v>>16);r.write(v>>24);}
}
