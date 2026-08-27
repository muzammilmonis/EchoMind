package com.echomind.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.SpeakerModel;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class OfflineProcessor {
    public interface Progress { void onProgress(int percent, String status); }

    public static List<TranscriptSegment> process(Context context, File wav, Progress progress) throws Exception {
        if (!AssetModelInstaller.bundledModelsPresent(context)) {
            throw new IllegalStateException("Offline speech models are not bundled in this build.");
        }
        progress.onProgress(5, "Preparing offline models");
        File asrDir = AssetModelInstaller.ensureModel(context, "model-en");
        File spkDir = AssetModelInstaller.ensureModel(context, "model-spk");

        List<TranscriptSegment> segments = new ArrayList<>();
        SpeakerClusterer clusterer = new SpeakerClusterer();
        String[] lastSpeaker = {"A"};
        long dataSize = Math.max(1, wav.length() - 44);
        long readTotal = 0;

        progress.onProgress(12, "Loading local recognition engine");
        try (Model model = new Model(asrDir.getAbsolutePath());
             SpeakerModel speakerModel = new SpeakerModel(spkDir.getAbsolutePath());
             Recognizer recognizer = new Recognizer(model, WavRecorder.SAMPLE_RATE)) {
            recognizer.setWords(true);
            recognizer.setSpeakerModel(speakerModel);

            try (FileInputStream in = new FileInputStream(wav)) {
                long skipped = 0;
                while (skipped < 44) {
                    long n = in.skip(44 - skipped); if (n <= 0) break; skipped += n;
                }
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) {
                    readTotal += n;
                    if (recognizer.acceptWaveForm(buffer, n)) {
                        appendResult(recognizer.getResult(), segments, clusterer, lastSpeaker);
                    }
                    int pct = 15 + (int)Math.min(78, (readTotal * 78L) / dataSize);
                    progress.onProgress(pct, "Transcribing and separating speakers");
                }
                appendResult(recognizer.getFinalResult(), segments, clusterer, lastSpeaker);
            }
        }
        progress.onProgress(96, "Building local transcript");
        return mergeAdjacent(segments);
    }

    private static void appendResult(String json, List<TranscriptSegment> out,
                                     SpeakerClusterer clusterer, String[] lastSpeaker) {
        try {
            JSONObject o = new JSONObject(json);
            String text = o.optString("text", "").trim();
            if (text.isEmpty()) return;
            JSONArray words = o.optJSONArray("result");
            double start = 0, end = 0;
            if (words != null && words.length() > 0) {
                start = words.getJSONObject(0).optDouble("start", 0);
                end = words.getJSONObject(words.length()-1).optDouble("end", start);
            }
            double[] vector = null;
            JSONArray spk = o.optJSONArray("spk");
            if (spk != null && spk.length() > 0) {
                vector = new double[spk.length()];
                for (int i=0;i<spk.length();i++) vector[i]=spk.optDouble(i,0);
                lastSpeaker[0] = clusterer.assign(vector);
            }
            out.add(new TranscriptSegment(start, end, lastSpeaker[0], text));
        } catch (Exception ignored) {}
    }

    private static List<TranscriptSegment> mergeAdjacent(List<TranscriptSegment> input) {
        List<TranscriptSegment> out = new ArrayList<>();
        for (TranscriptSegment s : input) {
            if (out.isEmpty()) { out.add(s); continue; }
            TranscriptSegment p = out.get(out.size()-1);
            if (p.speakerId.equals(s.speakerId) && s.start - p.end < 1.25) {
                p.end = Math.max(p.end, s.end);
                p.text = (p.text + " " + s.text).trim();
            } else out.add(s);
        }
        return out;
    }
}
