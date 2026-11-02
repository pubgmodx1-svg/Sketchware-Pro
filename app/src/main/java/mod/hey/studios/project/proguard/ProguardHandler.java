package mod.hey.studios.project.proguard;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.ProjectBuilder;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuildProgressReceiver;
import pro.sketchware.utility.FileUtil;

public class ProguardHandler {
    public static String ANDROID_PROGUARD_RULES_PATH = createAndroidRules();
    public static String DEFAULT_PROGUARD_RULES_PATH = "";
    private final String config_path;
    private final String fm_config_path;

    public ProguardHandler(String sc_id) {
        DEFAULT_PROGUARD_RULES_PATH = createDefaultRules(sc_id);
        config_path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/proguard";
        fm_config_path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/proguard_fm";

        if (!FileUtil.isExistFile(config_path)) {
            FileUtil.writeFile(config_path, getDefaultConfig());
        }
    }

    private static String createAndroidRules() {
        String rulePath = FileUtil.getExternalStorageDir() + "/.sketchware/libs/android-proguard-rules.pro";

        if (!FileUtil.isExistFile(rulePath)) {
            FileUtil.writeFile(rulePath, """
                    -dontusemixedcaseclassnames
                    -dontskipnonpubliclibraryclasses
                    -verbose
                    
                    -dontoptimize
                    -dontpreverify
                    
                    -keepattributes *Annotation*
                    -keep public class com.google.vending.licensing.ILicensingService
                    -keep public class com.android.vending.licensing.ILicensingService
                    
                    -keepclasseswithmembernames class * {
                        native <methods>;
                    }
                    
                    -keepclassmembers public class * extends android.view.View {
                       void set*(***);
                       *** get*();
                    }
                    
                    -keepclassmembers class * extends android.app.Activity {
                       public void *(android.view.View);
                    }
                    
                    -keepclassmembers enum * {
                        public static **[] values();
                        public static ** valueOf(java.lang.String);
                    }
                    
                    -keepclassmembers class * implements android.os.Parcelable {
                      public static final android.os.Parcelable$Creator CREATOR;
                    }
                    
                    -keepclassmembers class **.R$* {
                        public static <fields>;
                    }
                    
                    -dontwarn android.support.**
                    
                    -keep class android.support.annotation.Keep
                    
                    -keep @android.support.annotation.Keep class * {*;}
                    
                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <methods>;
                    }
                    
                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <fields>;
                    }
                    
                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <init>(...);
                    }
                    
                    -keepclassmembers class * {
                        @android.webkit.JavascriptInterface <methods>;\
                    }
                    
                    -dontwarn android.arch.**
                    -dontwarn android.lifecycle.**
                    -keep class android.arch.** { *; }
                    -keep class android.lifecycle.** { *; }
                    
                    -dontwarn androidx.arch.**
                    -dontwarn androidx.lifecycle.**
                    -keep class androidx.arch.** { *; }
                    -keep class androidx.lifecycle.** { *; }
                    """);
        }

        return rulePath;
    }

    private static String createDefaultRules(String sc_id) {
        String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/proguard-rules.pro";

        if (!FileUtil.isExistFile(path)) {
            FileUtil.writeFile(path, """
                    -repackageclasses
                    -ignorewarnings
                    -dontwarn
                    -dontnote
                    """);
        }

        return path;
    }

    private String getDefaultConfig() {
        HashMap<String, String> defaultConfig = new HashMap<>();

        defaultConfig.put("enabled", "false");
        defaultConfig.put("debug", "false");

        return new Gson().toJson(defaultConfig);
    }

    /**
     * Reads the existing per-project ProGuard settings without allowing a JSON
     * {@code null} value or malformed file to crash settings updates.
     */
    private HashMap<String, String> readConfig() {
        try {
            HashMap<String, String> config =
                    new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
            return config == null ? new HashMap<>() : config;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public String getCustomProguardRules() {
        return DEFAULT_PROGUARD_RULES_PATH;
    }

    public boolean isDebugFilesEnabled() {
        if (!FileUtil.isExistFile(config_path)) {
            return true;
        }

        HashMap<String, String> config = readConfig();
        if (!config.containsKey("debug")) {
            return false;
        }

        String debug = config.get("debug");
        return debug == null || debug.equals("true");
    }

    public boolean isShrinkingEnabled() {
        if (!FileUtil.isExistFile(config_path)) {
            return true;
        }

        String enabled = readConfig().get("enabled");
        return enabled != null && enabled.equals("true");
    }

    public void setProguardEnabled(boolean proguardEnabled) {
        HashMap<String, String> config = readConfig();
        config.put("enabled", String.valueOf(proguardEnabled));

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public boolean isR8Enabled() {
        if (!FileUtil.isExistFile(config_path)) {
            return true;
        }

        String enabled = readConfig().get("r8");
        return enabled != null && enabled.equals("true");
    }

    public void setR8Enabled(boolean r8Enabled) {
        HashMap<String, String> config = readConfig();
        config.put("r8", String.valueOf(r8Enabled));

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public boolean libIsProguardFMEnabled(String library) {
        boolean enabled;
        if (isShrinkingEnabled() && FileUtil.isExistFile(fm_config_path)) {
            String configContent = FileUtil.readFile(fm_config_path);

            if (configContent.isEmpty()) {
                return false;
            }

            try {
                ArrayList<String> config = new Gson().fromJson(configContent, Helper.TYPE_STRING);
                enabled = config.contains(library);
                return enabled;
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        HashMap<String, String> config = readConfig();
        config.put("debug", String.valueOf(debugEnabled));

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public void setProguardFMLibs(ArrayList<String> fullModeLibs) {
        FileUtil.writeFile(fm_config_path, new Gson().toJson(fullModeLibs));
    }

    public void start(BuildProgressReceiver progressReceiver, ProjectBuilder builder) throws IOException {
        if (!isShrinkingEnabled()) {
            return;
        }

        if (!isR8Enabled()) {
            progressReceiver.onProgress("ProGuarding classes...", 16);
            builder.runProguard();
            return;
        }

        progressReceiver.onProgress("Running R8 on classes...", 15);
        try {
            builder.runR8();
        } catch (OutOfMemoryError memoryError) {
            // R8 can exceed the Android app process heap on large user projects.
            // Switch this project to the existing ProGuard + D8 pipeline rather
            // than crashing the Sketchware process. The choice is persisted per
            // project so the next build does not repeat the same OOM.
            System.gc();
            progressReceiver.onProgress("R8 ran out of memory. Switching this project to the compatible ProGuard fallback...", 16);
            setR8Enabled(false);

            java.io.File dexOutput = new java.io.File(builder.yq.binDirectoryPath, "dex");
            deleteRecursively(dexOutput);
            builder.runProguard();
        }
    }

    private void deleteRecursively(java.io.File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        // Best effort: ProGuard/D8 will report a useful error if a stale file
        // cannot be removed and prevents the next build.
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
