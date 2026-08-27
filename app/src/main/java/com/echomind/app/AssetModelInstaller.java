package com.echomind.app;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class AssetModelInstaller {
    public static File ensureModel(Context context, String assetFolder) throws Exception {
        File root = new File(context.getFilesDir(), "offline-models/" + assetFolder);
        if (!root.exists()) root.mkdirs();
        copyRecursive(context.getAssets(), assetFolder, root);
        return root;
    }

    public static boolean bundledModelsPresent(Context context) {
        try {
            String[] en = context.getAssets().list("model-en");
            String[] spk = context.getAssets().list("model-spk");
            return contains(en, "am") && contains(en, "conf") && contains(spk, "final.ext.raw");
        } catch (Exception e) { return false; }
    }

    private static boolean contains(String[] arr, String value) {
        if (arr == null) return false;
        for (String s : arr) if (value.equals(s)) return true;
        return false;
    }

    private static void copyRecursive(AssetManager assets, String assetPath, File target) throws Exception {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            try (InputStream in = assets.open(assetPath); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return;
        }
        if (!target.exists()) target.mkdirs();
        for (String child : children) {
            String childAsset = assetPath + "/" + child;
            String[] sub = assets.list(childAsset);
            File childTarget = new File(target, child);
            if (sub != null && sub.length > 0) copyRecursive(assets, childAsset, childTarget);
            else {
                // Don't copy project instruction placeholders into runtime models.
                if (child.startsWith("PUT_VOSK_")) continue;
                try (InputStream in = assets.open(childAsset); FileOutputStream out = new FileOutputStream(childTarget)) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
            }
        }
    }
}
