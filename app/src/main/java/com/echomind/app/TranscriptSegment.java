package com.echomind.app;

import org.json.JSONException;
import org.json.JSONObject;

public class TranscriptSegment {
    public double start;
    public double end;
    public String speakerId;
    public String text;

    public TranscriptSegment(double start, double end, String speakerId, String text) {
        this.start = start;
        this.end = end;
        this.speakerId = speakerId;
        this.text = text;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("start", start);
        o.put("end", end);
        o.put("speaker", speakerId);
        o.put("text", text);
        return o;
    }

    public static TranscriptSegment fromJson(JSONObject o) {
        return new TranscriptSegment(
                o.optDouble("start", 0),
                o.optDouble("end", 0),
                o.optString("speaker", "A"),
                o.optString("text", "")
        );
    }
}
