package com.echomind.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SessionStore {
    public static File createSessionFolder(Context context) {
        File base = new File(context.getExternalFilesDir(null), "EchoMind");
        if (!base.exists()) base.mkdirs();
        String id = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        File folder = new File(base, id);
        folder.mkdirs();
        return folder;
    }

    public static void save(File folder, long durationMs, List<TranscriptSegment> segments,
                            Map<String,String> names) throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "Echo Mind");
        root.put("durationMs", durationMs);
        root.put("createdAt", folder.getName());
        JSONObject speakers = new JSONObject();
        for (Map.Entry<String,String> e : names.entrySet()) speakers.put(e.getKey(), e.getValue());
        root.put("speakers", speakers);
        JSONArray arr = new JSONArray();
        for (TranscriptSegment s : segments) arr.put(s.toJson());
        root.put("segments", arr);

        write(new File(folder, "meeting.json"), root.toString(2));
        write(new File(folder, "transcript.txt"), SummaryEngine.transcript(segments, names));
        write(new File(folder, "summary.txt"), SummaryEngine.summary(segments, names, durationMs));
    }

    public static List<File> listSessions(Context context) {
        File base = new File(context.getExternalFilesDir(null), "EchoMind");
        List<File> out = new ArrayList<>();
        File[] dirs = base.listFiles(File::isDirectory);
        if (dirs != null) {
            java.util.Arrays.sort(dirs, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
            java.util.Collections.addAll(out, dirs);
        }
        return out;
    }

    public static SessionData load(File folder) throws Exception {
        File json = new File(folder, "meeting.json");
        String raw = read(json);
        JSONObject root = new JSONObject(raw);
        SessionData data = new SessionData();
        data.folder = folder;
        data.durationMs = root.optLong("durationMs", 0);
        JSONObject speakers = root.optJSONObject("speakers");
        if (speakers != null) {
            java.util.Iterator<String> it = speakers.keys();
            while (it.hasNext()) { String k=it.next(); data.names.put(k, speakers.optString(k, "Speaker "+k)); }
        }
        JSONArray arr = root.optJSONArray("segments");
        if (arr != null) for (int i=0;i<arr.length();i++) data.segments.add(TranscriptSegment.fromJson(arr.getJSONObject(i)));
        return data;
    }

    public static String readText(File file) {
        try { return read(file); } catch (Exception e) { return "Unable to read file: " + e.getMessage(); }
    }

    private static void write(File f, String s) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(s.getBytes(StandardCharsets.UTF_8)); }
    }

    private static String read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int)f.length()]; int off=0,n;
            while (off<b.length && (n=in.read(b,off,b.length-off))>0) off+=n;
            return new String(b,0,off,StandardCharsets.UTF_8);
        }
    }

    public static class SessionData {
        public File folder;
        public long durationMs;
        public List<TranscriptSegment> segments = new ArrayList<>();
        public Map<String,String> names = new LinkedHashMap<>();
    }
}
