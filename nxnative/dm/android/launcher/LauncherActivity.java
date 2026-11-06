package com.nx2a.launcher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The game's own launcher: the icon on the home screen opens this, and it starts the game.
 *
 * It shows what the player wants to know before the game starts: which game it is, where its files are, and it
 * lets the player pick the language and the Vulkan driver (the pack carries the drivers the port packed; the host
 * loads the chosen one through adrenotools, and falls back to the device's own when it does not load).
 *
 * The choices are written into the data pack's env.txt (the host reads them when it starts), so they last.
 *
 * The page is in Chinese (the player reads Chinese): the strings are written as escapes, like the game's own
 * Chinese data.
 */
public class LauncherActivity extends Activity {

    // The page's own strings
    private static final String S_LANGUAGE = "\u6E38\u620F\u8BED\u8A00";                    // the language
    private static final String S_DRIVER = "Vulkan \u9A71\u52A8";                            // the driver
    private static final String S_SYSTEM = "\u7CFB\u7EDF\u81EA\u5E26";                        // the device's own
    private static final String S_START = "\u25B6 \u542F\u52A8\u6E38\u620F";                  // the start button
    private static final String S_GRANT = "\u6388\u4E88\u5B58\u50A8\u8BBF\u95EE\u6743\u9650"      // the grant button
            + "\uFF08\u8BFB\u53D6\u6570\u636E\u5305\u9700\u8981\uFF09";                     // (reading the pack needs it)
    private static final String S_PACK = "\u6570\u636E\u5305\uFF1A";                          // the pack's path
    private static final String S_VERSION = "\u7248\u672C\uFF1A";                              // the version
    private static final String S_TITLE = "Title ID\uFF1A";                                   // the title id
    private static final String S_NONE = "\uFF08\u672A\u627E\u5230\uFF09";                    // (not found)
    private static final String S_NO_PACK = "\u627E\u4E0D\u5230\u6570\u636E\u5305";              // the pack is not there
    private static final String S_UNREADABLE = "\uFF08\u8BFB\u4E0D\u5230\uFF1A\u8BF7\u6388\u4E88\u5B58\u50A8\u8BBF\u95EE\u6743\u9650\uFF09";  // (cannot read it: give the access)
    private static final String S_WRITE = "\u5199\u5165 env.txt \u5931\u8D25\uFF1A";            // the write failed
    private static final String S_START_FAIL = "\u542F\u52A8\u6E38\u620F\u5931\u8D25\uFF1A";  // the start failed
    private static final String S_NO_SETTINGS = "\u8FD9\u53F0\u8BBE\u5907\u6CA1\u6709\u53EF\u7528\u7684\u6743\u9650\u8BBE\u7F6E\u9875";

    private static final String SYSTEM_DRIVER = "";        // the device's own driver (no DM_VK_DRIVER)

    private File pack;                                      // the folder with the game's files
    private Spinner language;
    private Spinner driver;
    private TextView info;
    private final List<String> languageCodes = new ArrayList<>();
    private final List<String> driverNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pack = findPack();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        page.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setTextSize(20);
        title.setPadding(0, 0, 0, pad / 2);
        page.addView(title);

        info = new TextView(this);
        info.setTextSize(13);
        info.setPadding(0, 0, 0, pad);
        page.addView(info);

        language = addSpinner(page, S_LANGUAGE);
        driver = addSpinner(page, S_DRIVER);
        title.setText(gameName());

        if (!packReadable()) {
            Button grant = new Button(this);
            grant.setText(S_GRANT);
            grant.setOnClickListener(v -> {
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    } catch (Exception e2) {
                        toast(S_NO_SETTINGS);
                    }
                }
            });
            page.addView(grant);
        }

        Button start = new Button(this);
        start.setText(S_START);
        start.setOnClickListener(v -> launch());
        page.addView(start);

        refresh();
    }

    // ---- the page ------------------------------------------------------------------------------------

    private Spinner addSpinner(LinearLayout page, String label) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setPadding(0, 0, 0, 4);
        page.addView(l);
        Spinner s = new Spinner(this);
        page.addView(s);
        return s;
    }

    /** The lists and the values on the page: the pack's own lists, and the choices the env.txt holds now */
    private void refresh() {
        StringBuilder text = new StringBuilder();
        text.append(S_PACK).append(pack == null ? S_NONE : pack.getAbsolutePath()).append('\n');
        if (pack != null && !packReadable()) text.append(S_UNREADABLE).append('\n');
        JSONObject meta = packMeta();
        if (meta != null) {
            String v = meta.optString("version", "");
            if (!v.isEmpty()) text.append(S_VERSION).append(v).append('\n');
            String id = meta.optString("title_id", "");
            if (!id.isEmpty()) text.append(S_TITLE).append(id).append('\n');
        }
        info.setText(text);

        List<String> languages = new ArrayList<>();
        languageCodes.clear();
        for (String line : readLines(new File(pack == null ? new File("/dev/null") : pack, "languages.txt"))) {
            String[] parts = line.split("\t", 2);
            if (parts[0].isEmpty()) continue;
            languageCodes.add(parts[0]);
            languages.add(parts.length > 1 && !parts[1].isEmpty() ? parts[0] + "  " + parts[1] : parts[0]);
        }
        if (languages.isEmpty()) { languages.add("en-US"); languageCodes.add("en-US"); }
        String[] env = env();
        language.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, languages));
        int at = languageCodes.indexOf(env[0]);
        language.setSelection(at < 0 ? 0 : at);

        List<String> drivers = new ArrayList<>();
        driverNames.clear();
        drivers.add(S_SYSTEM);
        driverNames.add(SYSTEM_DRIVER);
        for (String line : readLines(new File(pack == null ? new File("/dev/null") : pack, "drivers.txt"))) {
            String name = line.trim();
            if (name.isEmpty() || name.equals(SYSTEM_DRIVER)) continue;
            drivers.add(name);
            driverNames.add(name);
        }
        driver.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, drivers));
        int dr = driverNames.indexOf(env[1]);
        driver.setSelection(dr < 0 ? 0 : dr);
        driver.setVisibility(drivers.size() > 1 ? View.VISIBLE : View.GONE);
    }

    // ---- the game ------------------------------------------------------------------------------------

    /** Writes the choices into the pack's env.txt and starts the game */
    private void launch() {
        if (pack == null) { toast(S_NO_PACK); return; }
        String lang = languageCodes.isEmpty() ? "en-US" : languageCodes.get(language.getSelectedItemPosition());
        String drv = driverNames.isEmpty() ? SYSTEM_DRIVER : driverNames.get(driver.getSelectedItemPosition());
        if (!writeEnv(lang, drv)) { toast(S_WRITE + pack.getAbsolutePath()); return; }
        try {
            startActivity(new Intent(this, android.app.NativeActivity.class));
            finish();
        } catch (Exception e) {
            toast(S_START_FAIL + e.getMessage());
        }
    }

    private boolean writeEnv(String lang, String drv) {
        File file = new File(pack, "env.txt");
        List<String> lines = readLines(file);
        boolean haveLang = false, haveDrv = false;
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("DM_LANG=")) { out.append("DM_LANG=").append(lang).append('\n'); haveLang = true; continue; }
            if (t.startsWith("DM_VK_DRIVER=")) {
                if (drv.isEmpty()) continue;                       // the device's own driver: no line
                out.append("DM_VK_DRIVER=").append(drv).append('\n'); haveDrv = true; continue;
            }
            out.append(line).append('\n');
        }
        if (!haveLang) out.append("DM_LANG=").append(lang).append('\n');
        if (!haveDrv && !drv.isEmpty()) out.append("DM_VK_DRIVER=").append(drv).append('\n');
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            w.write(out.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The env.txt's language and driver as they are now */
    private String[] env() {
        String lang = "en-US", drv = SYSTEM_DRIVER;
        for (String line : readLines(new File(pack == null ? new File("/dev/null") : pack, "env.txt"))) {
            String t = line.trim();
            if (t.startsWith("DM_LANG=")) lang = t.substring("DM_LANG=".length()).trim();
            else if (t.startsWith("DM_VK_DRIVER=")) drv = t.substring("DM_VK_DRIVER=".length()).trim();
        }
        return new String[]{lang, drv};
    }

    // ---- where the files are -------------------------------------------------------------------------

    /** The folder with the game's files: the one the game remembered, or the one the port wrote into the APK */
    private File findPack() {
        File saved = new File(getFilesDir(), "data_dir.txt");
        if (saved.isFile()) {
            try {
                String path = new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8).trim();
                if (!path.isEmpty() && new File(path, "nx2a-data.json").isFile()) return new File(path);
            } catch (Exception e) { /* the port's path is the next one */ }
        }
        try (InputStream in = getAssets().open("nx2a-data.txt")) {
            String path = read(in).trim();
            if (!path.isEmpty()) return new File(path);
        } catch (Exception e) { /* an older port: the game asks for the folder itself */ }
        return null;
    }

    private JSONObject packMeta() {
        if (pack == null) return null;
        File f = new File(pack, "nx2a-data.json");
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return new JSONObject(read(in));
        } catch (Exception e) {
            return null;
        }
    }

    private String gameName() {
        JSONObject meta = packMeta();
        if (meta == null) return "nx2android";
        String name = meta.optString("name", "");
        return name.isEmpty() ? "nx2android" : name;
    }

    /** Whether the pack's files can be read: the all-files access is what the shared storage needs, and some
     * devices' isExternalStorageManager() says yes when the access is not there (the read is the truth) */
    private boolean packReadable() {
        if (pack == null) return false;
        try (InputStream in = new FileInputStream(new File(pack, "env.txt"))) {
            read(in);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- small helpers --------------------------------------------------------------------------------

    private static List<String> readLines(File f) {
        List<String> out = new ArrayList<>();
        if (f == null || !f.isFile()) return out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.add(line);
        } catch (Exception e) { /* an unreadable file is an empty list */ }
        return out;
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
