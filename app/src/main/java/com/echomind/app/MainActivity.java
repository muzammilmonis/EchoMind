package com.echomind.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 41;
    private final WavRecorder recorder = new WavRecorder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout root;
    private Button recordButton;
    private TextView timer;
    private TextView engineStatus;
    private File currentFolder, currentWav;
    private long startedAt, durationMs;
    private Runnable ticker;

    private int bg, surface, accent, text, muted, danger;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        bg = getColor(com.echomind.app.R.color.bg);
        surface = getColor(com.echomind.app.R.color.surface);
        accent = getColor(com.echomind.app.R.color.accent);
        text = getColor(com.echomind.app.R.color.text);
        muted = getColor(com.echomind.app.R.color.muted);
        danger = getColor(com.echomind.app.R.color.danger);
        showHome();
    }

    private void showHome() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(bg);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22),dp(18),dp(22),dp(30));
        scroll.addView(root); setContentView(scroll);

        TextView brand = label("ECHO MIND", 28, text, true); brand.setLetterSpacing(.08f); root.addView(brand);
        TextView sub = label("Private voice memory • 100% local", 14, muted, false); sub.setPadding(0,dp(3),0,dp(20)); root.addView(sub);

        LinearLayout hero = card(); hero.setPadding(dp(20),dp(22),dp(20),dp(22));
        TextView state = label("Ready to remember", 22, text, true); hero.addView(state);
        TextView hint = label("Record a conversation. Echo Mind separates speakers as A, B, C… and builds a local transcript after you name them.", 14, muted, false);
        hint.setPadding(0,dp(8),0,dp(18)); hero.addView(hint);

        timer = label("00:00", 46, text, true); timer.setGravity(Gravity.CENTER); timer.setPadding(0,dp(8),0,dp(14)); hero.addView(timer, matchWrap());
        recordButton = button("Start recording", true); recordButton.setOnClickListener(v -> onRecordTap()); hero.addView(recordButton, match(dp(58)));
        root.addView(hero, marginBottom(dp(16)));

        engineStatus = label("", 13, muted, false); engineStatus.setPadding(dp(4),0,dp(4),dp(20)); root.addView(engineStatus);
        refreshEngineStatus();

        TextView recent = label("RECENT RECORDINGS", 13, muted, true); recent.setLetterSpacing(.08f); recent.setPadding(0,0,0,dp(10)); root.addView(recent);
        List<File> sessions = SessionStore.listSessions(this);
        if (sessions.isEmpty()) {
            LinearLayout empty = card(); empty.addView(padded(label("No recordings yet. Your sessions will stay on this device.", 14, muted, false),16));
            root.addView(empty);
        } else {
            for (File f : sessions) addSessionCard(f);
        }
    }

    private void refreshEngineStatus() {
        if (AssetModelInstaller.bundledModelsPresent(this)) {
            engineStatus.setText("● Offline engine ready • no network required");
            engineStatus.setTextColor(accent);
        } else {
            engineStatus.setText("○ Recording works, but offline Vosk model assets are not bundled in this build.");
            engineStatus.setTextColor(muted);
        }
    }

    private void onRecordTap() {
        if (recorder.isRecording()) { stopRecording(); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC); return;
        }
        startRecording();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startRecording();
        else Toast.makeText(this, "Microphone permission is required to record.", Toast.LENGTH_LONG).show();
    }

    private void startRecording() {
        try {
            currentFolder = SessionStore.createSessionFolder(this);
            currentWav = new File(currentFolder, "audio.wav");
            recorder.start(currentWav);
            startedAt = System.currentTimeMillis();
            recordButton.setText("Stop & process"); recordButton.setTextColor(Color.WHITE); recordButton.setBackgroundColor(danger);
            ticker = new Runnable() { public void run() {
                timer.setText(formatDuration(System.currentTimeMillis()-startedAt));
                if (recorder.isRecording()) handler.postDelayed(this, 250);
            }};
            handler.post(ticker);
        } catch (Exception e) { toast("Could not start recording: " + e.getMessage()); }
    }

    private void stopRecording() {
        recordButton.setEnabled(false);
        new Thread(() -> {
            try { durationMs = recorder.stop(currentWav); }
            catch (Exception e) { handler.post(() -> toast("Recording stop failed: " + e.getMessage())); return; }
            handler.post(() -> beginProcessing());
        }, "EchoMind-Stop").start();
    }

    private void beginProcessing() {
        if (!AssetModelInstaller.bundledModelsPresent(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Audio saved locally")
                    .setMessage("The recording is safe in its Echo Mind folder. This source build does not contain the offline language/speaker model files yet, so transcription cannot run in this build.")
                    .setPositiveButton("OK", (d,w)->showHome()).show();
            return;
        }
        showProcessing();
        new Thread(() -> {
            try {
                List<TranscriptSegment> segs = OfflineProcessor.process(this, currentWav,
                        (pct,status) -> handler.post(() -> updateProcessing(pct,status)));
                handler.post(() -> askSpeakerNames(segs));
            } catch (Exception e) {
                handler.post(() -> new AlertDialog.Builder(this).setTitle("Processing failed")
                        .setMessage(e.getMessage()).setPositiveButton("Back",(d,w)->showHome()).show());
            }
        }, "EchoMind-OfflineProcess").start();
    }

    private ProgressBar processBar; private TextView processText;
    private void showProcessing() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER); box.setPadding(dp(28),dp(70),dp(28),dp(28)); box.setBackgroundColor(bg);
        TextView t = label("Processing locally",28,text,true); t.setGravity(Gravity.CENTER); box.addView(t,matchWrap());
        TextView p = label("Your audio never leaves this device.",14,muted,false); p.setGravity(Gravity.CENTER); p.setPadding(0,dp(8),0,dp(28)); box.addView(p,matchWrap());
        processBar = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); processBar.setMax(100); processBar.setProgress(3); box.addView(processBar,match(dp(8)));
        processText = label("Preparing recording…",14,muted,false); processText.setGravity(Gravity.CENTER); processText.setPadding(0,dp(16),0,0); box.addView(processText,matchWrap());
        setContentView(box);
    }
    private void updateProcessing(int p,String s){ if(processBar!=null)processBar.setProgress(p);if(processText!=null)processText.setText(s+" • "+p+"%"); }

    private void askSpeakerNames(List<TranscriptSegment> segments) {
        if (segments.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("No speech detected").setMessage("The audio was saved, but no clear speech was transcribed.")
                    .setPositiveButton("Back",(d,w)->showHome()).show(); return;
        }
        Set<String> ids = new LinkedHashSet<>(); for (TranscriptSegment s:segments) ids.add(s.speakerId);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(18),dp(8),dp(18),0);
        Map<String,EditText> fields = new LinkedHashMap<>();
        for(String id:ids){
            TextView l=label("Speaker "+id,13,muted,true); l.setPadding(0,dp(10),0,dp(4));form.addView(l);
            EditText e=new EditText(this); e.setHint("Enter name");e.setTextColor(text);e.setHintTextColor(muted);e.setSingleLine(true);fields.put(id,e);form.addView(e,match(dp(52)));
        }
        ScrollView sw=new ScrollView(this);sw.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Who was who?")
                .setMessage("Rename the locally detected speakers before Echo Mind writes the final files.")
                .setView(sw).setNegativeButton("Use A/B/C",null)
                .setPositiveButton("Save transcript",null).create();
        dialog.setOnShowListener(x->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                Map<String,String> names=new LinkedHashMap<>();
                for(Map.Entry<String,EditText> e:fields.entrySet()){
                    String n=e.getValue().getText().toString().trim(); names.put(e.getKey(),n.isEmpty()?"Speaker "+e.getKey():n);
                }
                dialog.dismiss(); saveFinal(segments,names);
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
                Map<String,String> names=new LinkedHashMap<>();for(String id:ids)names.put(id,"Speaker "+id);dialog.dismiss();saveFinal(segments,names);
            });
        });
        dialog.show();
    }

    private void saveFinal(List<TranscriptSegment> segs, Map<String,String> names) {
        try {
            SessionStore.save(currentFolder,durationMs,segs,names);
            showResult(currentFolder,segs,names);
        } catch(Exception e){toast("Could not save transcript: "+e.getMessage());showHome();}
    }

    private void showResult(File folder,List<TranscriptSegment> segs,Map<String,String> names){
        ScrollView sw=new ScrollView(this);sw.setBackgroundColor(bg);LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(22),dp(22),dp(22),dp(30));sw.addView(b);setContentView(sw);
        b.addView(label("Saved to Echo Mind",26,text,true));
        TextView path=label(folder.getAbsolutePath(),12,muted,false);path.setPadding(0,dp(5),0,dp(18));b.addView(path);
        TextView summaryTitle=label("LOCAL SUMMARY",12,accent,true);summaryTitle.setLetterSpacing(.08f);b.addView(summaryTitle);
        TextView summary=label(SummaryEngine.summary(segs,names,durationMs),14,text,false);summary.setBackgroundResource(R.drawable.card);summary.setPadding(dp(16),dp(16),dp(16),dp(16));summary.setTextIsSelectable(true);b.addView(summary,marginBottom(dp(14)));
        TextView trTitle=label("TRANSCRIPT",12,accent,true);trTitle.setLetterSpacing(.08f);b.addView(trTitle);
        TextView tr=label(SummaryEngine.transcript(segs,names),14,text,false);tr.setBackgroundResource(R.drawable.card);tr.setPadding(dp(16),dp(16),dp(16),dp(16));tr.setTextIsSelectable(true);b.addView(tr,marginBottom(dp(14)));
        Button back=button("Done",true);back.setOnClickListener(v->showHome());b.addView(back,match(dp(56)));
    }

    private void addSessionCard(File folder) {
        LinearLayout c=card();c.setPadding(dp(16),dp(15),dp(16),dp(15));
        String title=folder.getName().replace('_',' ');
        TextView t=label(title,16,text,true);c.addView(t);
        boolean ready=new File(folder,"transcript.txt").exists();
        TextView m=label(ready?"Transcript + summary saved locally":"Audio saved • transcription pending",13,ready?accent:muted,false);m.setPadding(0,dp(4),0,dp(8));c.addView(m);
        c.setOnClickListener(v->{ if(ready) openSession(folder); else toast("This recording has audio only."); });
        root.addView(c,marginBottom(dp(10)));
    }

    private void openSession(File folder) {
        try { SessionStore.SessionData d=SessionStore.load(folder); showResult(folder,d.segments,d.names); }
        catch(Exception e){toast("Could not open session: "+e.getMessage());}
    }

    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackgroundResource(R.drawable.card);return l;}
    private TextView label(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setLineSpacing(0,1.12f);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setTextColor(primary?Color.rgb(4,34,25):text);b.setBackgroundResource(primary?R.drawable.button_primary:R.drawable.button_secondary);return b;}
    private View padded(View v,int p){v.setPadding(dp(p),dp(p),dp(p),dp(p));return v;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams match(int h){return new LinearLayout.LayoutParams(-1,h);}
    private LinearLayout.LayoutParams marginBottom(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=m;return p;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private static String formatDuration(long ms){long s=Math.max(0,ms/1000),m=s/60,h=m/60;s%=60;m%=60;return h>0?String.format(Locale.US,"%02d:%02d:%02d",h,m,s):String.format(Locale.US,"%02d:%02d",m,s);}

    @Override protected void onDestroy(){super.onDestroy();if(recorder.isRecording()&&currentWav!=null){try{recorder.stop(currentWav);}catch(Exception ignored){}}}
}
