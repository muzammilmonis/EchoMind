package com.echomind.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SummaryEngine {
    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            "the","a","an","and","or","but","to","of","in","on","for","with","is","are","was","were",
            "i","you","we","they","he","she","it","this","that","these","those","be","been","being",
            "have","has","had","do","does","did","can","could","will","would","should","may","might",
            "my","your","our","their","me","us","them","so","if","then","than","at","as","from",
            "ka","ki","ke","ko","hai","hain","tha","thi","the","aur","ya","main","mein","hum","tum",
            "ye","wo","toh","bhi","se","kar","karo","karen","ek","nahi","haan","okay","ok"
    ));

    public static String transcript(List<TranscriptSegment> segments, Map<String,String> names) {
        StringBuilder sb = new StringBuilder();
        for (TranscriptSegment s : segments) {
            String name = names.getOrDefault(s.speakerId, "Speaker " + s.speakerId);
            sb.append('[').append(time(s.start)).append("] ")
                    .append(name).append(": ").append(s.text).append('\n');
        }
        return sb.toString().trim() + "\n";
    }

    public static String summary(List<TranscriptSegment> segments, Map<String,String> names, long durationMs) {
        Map<String,Double> talk = new LinkedHashMap<>();
        Map<String,Integer> freq = new HashMap<>();
        for (TranscriptSegment s : segments) {
            String name = names.getOrDefault(s.speakerId, "Speaker " + s.speakerId);
            talk.put(name, talk.getOrDefault(name, 0d) + Math.max(0, s.end - s.start));
            String clean = s.text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
            for (String w : clean.split("\\s+")) {
                if (w.length() >= 4 && !STOP.contains(w) && !w.matches("\\d+")) {
                    freq.put(w, freq.getOrDefault(w, 0) + 1);
                }
            }
        }

        List<Map.Entry<String,Integer>> words = new ArrayList<>(freq.entrySet());
        words.sort((a,b) -> Integer.compare(b.getValue(), a.getValue()));
        List<String> topics = new ArrayList<>();
        for (Map.Entry<String,Integer> e : words) {
            if (topics.size() == 8) break;
            topics.add(e.getKey());
        }

        String active = talk.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey).orElse("—");

        StringBuilder sb = new StringBuilder();
        sb.append("ECHO MIND — LOCAL SUMMARY\n\n");
        sb.append("Duration: ").append(time(durationMs / 1000.0)).append("\n");
        sb.append("Participants: ").append(talk.isEmpty() ? "—" : String.join(", ", talk.keySet())).append("\n");
        sb.append("Most active speaker: ").append(active).append("\n\n");
        sb.append("Top terms: ").append(topics.isEmpty() ? "No strong keywords detected" : String.join(", ", topics)).append("\n\n");
        sb.append("Speaking time:\n");
        for (Map.Entry<String,Double> e : talk.entrySet()) {
            sb.append("- ").append(e.getKey()).append(": ").append(time(e.getValue())).append("\n");
        }
        sb.append("\nNote: This summary is rule-based and generated fully on-device; it does not use a cloud LLM.\n");
        return sb.toString();
    }

    private static String time(double sec) {
        int total = Math.max(0, (int)Math.round(sec));
        int h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0 ? String.format(Locale.US, "%02d:%02d:%02d", h,m,s)
                : String.format(Locale.US, "%02d:%02d", m,s);
    }
}
