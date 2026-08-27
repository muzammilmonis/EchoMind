package com.echomind.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Incremental on-device clustering of Vosk speaker embeddings.
 * No network, server, account or external API is involved.
 */
public class SpeakerClusterer {
    private static final double SIMILARITY_THRESHOLD = 0.63;
    private final List<Cluster> clusters = new ArrayList<>();

    public String assign(double[] vector) {
        if (vector == null || vector.length == 0) {
            return clusters.isEmpty() ? "A" : label(clusters.size() - 1);
        }

        int best = -1;
        double bestScore = -1;
        for (int i = 0; i < clusters.size(); i++) {
            double score = cosine(vector, clusters.get(i).centroid);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }

        if (best >= 0 && bestScore >= SIMILARITY_THRESHOLD) {
            clusters.get(best).add(vector);
            return label(best);
        }

        clusters.add(new Cluster(vector));
        return label(clusters.size() - 1);
    }

    public int count() { return clusters.size(); }

    private static String label(int index) {
        // A..Z then S27, S28... for unusually large meetings.
        return index < 26 ? String.valueOf((char) ('A' + index)) : "S" + (index + 1);
    }

    private static double cosine(double[] a, double[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            aa += a[i] * a[i];
            bb += b[i] * b[i];
        }
        if (aa == 0 || bb == 0) return -1;
        return dot / (Math.sqrt(aa) * Math.sqrt(bb));
    }

    private static class Cluster {
        double[] centroid;
        int samples = 1;

        Cluster(double[] v) { centroid = v.clone(); }

        void add(double[] v) {
            int n = Math.min(centroid.length, v.length);
            for (int i = 0; i < n; i++) {
                centroid[i] = (centroid[i] * samples + v[i]) / (samples + 1.0);
            }
            samples++;
        }
    }
}
