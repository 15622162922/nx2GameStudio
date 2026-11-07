package com.nx2a.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.ScrollView;
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
 *
 * The look: the game's own icon is the backdrop, blown up and blurred, and the choices sit on a card of frosted
 * glass over it. The icon is in the APK (the porter packs it as res/drawable-nodpi-v4/icon.png), so the page draws
 * itself without the data pack and without any permission: the player sees the game before the game sees the pack.
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
    private static final String S_PICK_DRIVER = "\u9009\u62E9\u9A71\u52A8\u6587\u4EF6\uFF08\u4ECE\u6587\u4EF6\u7BA1\u7406\u9009\uFF09";      // the driver file (from the file manager)
    private static final String S_IMPORTED = "\uFF08\u5DF2\u5BFC\u5165\uFF09";          // (imported)
    private static final String S_PICK_FAIL = "\u5BFC\u5165\u9A71\u52A8\u5931\u8D25\uFF1A";   // the import failed
    private static final String S_WRITE = "\u5199\u5165 env.txt \u5931\u8D25\uFF1A";            // the write failed
    private static final String S_START_FAIL = "\u542F\u52A8\u6E38\u620F\u5931\u8D25\uFF1A";  // the start failed
    private static final String S_NO_SETTINGS = "\u8FD9\u53F0\u8BBE\u5907\u6CA1\u6709\u53EF\u7528\u7684\u6743\u9650\u8BBE\u7F6E\u9875";

    private static final String SYSTEM_DRIVER = "";        // the device's own driver (no DM_VK_DRIVER)

    private static final int PICK_DRIVER = 42;               // the file manager's answer
    // The Switch's languages, for a pack made before the launcher (the pack's own list is better)
    private static final String[][] LANGUAGES = {
        {"en-US", "American English"}, {"en-GB", "British English"}, {"ja", "Japanese"}, {"fr", "French"},
        {"de", "German"}, {"es-419", "Latin American Spanish"}, {"es", "Spanish"}, {"it", "Italian"},
        {"nl", "Dutch"}, {"fr-CA", "Canadian French"}, {"pt", "Portuguese"}, {"ru", "Russian"},
        {"ko", "Korean"}, {"zh-Hant", "Traditional Chinese"}, {"zh-Hans", "Simplified Chinese"}, {"pt-BR", "Brazilian Portuguese"}};

    // The page's colours: white on dark glass
    private static final int C_TEXT = 0xFFFFFFFF;              // the game name
    private static final int C_LABEL = 0xCCFFFFFF;             // the labels
    private static final int C_INFO = 0x99FFFFFF;              // the pack's path and the like
    private static final int C_CARD = 0xB00E1116;              // the glass card's fill
    private static final int C_EDGE = 0x33FFFFFF;              // the card's border
    private static final int C_SCRIM = 0x5C000000;              // the darkening over the backdrop
    private static final int C_BUTTON = 0xFFFFFFFF;             // the start button's fill
    private static final int C_BUTTON_TEXT = 0xFF12141A;        // the start button's text
    private static final int C_SECONDARY = 0x1FFFFFFF;         // the secondary buttons' fill
    private static final int C_SECONDARY_EDGE = 0x4DFFFFFF;     // the secondary buttons' border

    // The host's own libraries: the rest of the APK's are the Vulkan drivers it carries
    private static final String[] HOST_LIBS = {"libdmhost.so", "libadrenotools.so", "libhook_impl.so", "libmain_hook.so",
        "libfile_redirect_hook.so", "libgsl_alloc_hook.so"};

    private File pack;                                      // the folder with the game's files
    private ChoiceField language;
    private ChoiceField driver;
    private TextView info;
    private TextView driverLabel;                          // the driver's label, hidden with the driver itself
    private FrameLayout pageRoot;                          // the page's root, for the window's insets
    private FrameLayout pageGroup;                        // the logo and the card, for the window's insets
    private final List<String> languageCodes = new ArrayList<>();
    private final List<String> driverLabels = new ArrayList<>();   // what the player sees
    private final List<String> driverValues = new ArrayList<>();   // what goes into env.txt

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The page is landscape whatever the game's profile says: it is a page of settings, and it reads better wide.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);   // the manifest's theme does this too (both, to be sure)
        pack = findPack();
        setContentView(page());
        edgeToEdge(pageRoot, pageGroup);   // after the content view: the window's decor view exists only then
        refresh();
    }

    // ---- the page ------------------------------------------------------------------------------------

    /** The whole page: the backdrop (the game's own icon, blurred), the logo, and the card of glass with the choices */
    private View page() {
        Bitmap icon = iconBitmap();

        FrameLayout root = new FrameLayout(this);
        if (icon != null) {
            ImageView backdrop = new ImageView(this);
            backdrop.setImageBitmap(backdropBitmap(icon));
            backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            root.addView(backdrop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            blur(backdrop);
        } else {
            root.setBackground(fallbackBackdrop());
        }

        View scrim = new View(this);
        scrim.setBackgroundColor(C_SCRIM);
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The logo on the left, the card on the right, the pair in the middle of the screen
        FrameLayout group = new FrameLayout(this);   // the insets keep the pair clear of the system bars
        root.addView(group, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        pageRoot = root;
        pageGroup = group;

        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        pair.setGravity(Gravity.CENTER_VERTICAL);
        group.addView(pair, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int logoSize = (int) Math.min(Math.min(screenW * 0.24f, screenH * 0.52f), dp(170));
        int cardWidth = (int) Math.min(dp(430), screenW - logoSize - dp(120));

        if (icon != null) {
            LogoView logo = new LogoView(this);
            logo.setLogo(icon);
            pair.addView(logo, new LinearLayout.LayoutParams(logoSize, logoSize));
        } else {
            // No icon in the APK (the port had no Pillow): the game's name stands in for the logo
            TextView name = new TextView(this);
            name.setText(gameName());
            name.setTextSize(26);
            name.setGravity(Gravity.CENTER);
            name.setTextColor(C_TEXT);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            pair.addView(name, new LinearLayout.LayoutParams(logoSize, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // The space between them
        View spacer = new View(this);
        pair.addView(spacer, new LinearLayout.LayoutParams(dp(30), 1));

        // The card: a sheet of glass over the backdrop, with the choices on it
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout host = new LinearLayout(this);   // the card's host: it centers the card, and scrolls when it does not fit
        host.setOrientation(LinearLayout.VERTICAL);
        host.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(20));
        card.setBackground(cardBackground());
        host.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(host, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pair.addView(scroll, new LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText(gameName());
        title.setTextSize(26);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(C_TEXT);
        title.setPadding(0, 0, 0, dp(2));
        card.addView(title);

        info = new TextView(this);
        info.setTextSize(12);
        info.setTextColor(C_INFO);
        info.setPadding(0, dp(2), 0, dp(12));
        card.addView(info);

        language = addChoice(card, S_LANGUAGE);
        driverLabel = addLabel(card, S_DRIVER);
        driver = addChoice(card, null);

        Button pick = secondaryButton(S_PICK_DRIVER);
        pick.setOnClickListener(v -> pickDriver());
        card.addView(pick, buttonParams(dp(12)));

        if (!packReadable()) {
            Button grant = secondaryButton(S_GRANT);
            grant.setOnClickListener(v -> grantAccess());
            card.addView(grant, buttonParams(dp(8)));
        }

        Button start = new Button(this);
        start.setText(S_START);
        start.setTextSize(17);
        start.setAllCaps(false);
        start.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        start.setTextColor(C_BUTTON_TEXT);
        start.setBackground(pill(C_BUTTON, 0x22000000));
        start.setOnClickListener(v -> launch());
        card.addView(start, buttonParams(dp(14)));

        return root;
    }

    /** The glass card: a dark translucent sheet with a thin bright edge */
    private GradientDrawable cardBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(C_CARD);
        d.setCornerRadius(dp(22));
        d.setStroke(dp(1), C_EDGE);
        return d;
    }

    /** A button as a pill of the given colour, with a ripple where the platform has one */
    private RippleDrawable pill(int fill, int edge) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(26));
        if (edge != 0) shape.setStroke(dp(1), edge);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(26));
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, mask);
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(C_TEXT);
        b.setBackground(pill(C_SECONDARY, C_SECONDARY_EDGE));
        return b;
    }

    private LinearLayout.LayoutParams buttonParams(int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        p.topMargin = topMargin;
        return p;
    }

    private TextView addLabel(LinearLayout card, String label) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(C_LABEL);
        l.setPadding(0, dp(10), 0, dp(4));
        card.addView(l);
        return l;
    }

    /** A field of the card that shows the current choice and opens the list of them */
    private ChoiceField addChoice(LinearLayout card, String label) {
        if (label != null) addLabel(card, label);
        ChoiceField f = new ChoiceField(this);
        card.addView(f, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        return f;
    }

    /** The field's own sheet of glass, like the secondary buttons */
    private android.graphics.drawable.Drawable fieldBackground() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(C_SECONDARY);
        shape.setCornerRadius(dp(10));
        shape.setStroke(dp(1), C_SECONDARY_EDGE);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(10));
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, mask);
    }

    /** The game's icon out of the APK (the porter packs it as res/drawable-nodpi-v4/icon.png); null without one */
    private Bitmap iconBitmap() {
        try {
            int id = getResources().getIdentifier("icon", "drawable", getPackageName());
            if (id == 0) return null;
            android.graphics.drawable.Drawable d = getResources().getDrawable(id);
            if (d instanceof android.graphics.drawable.BitmapDrawable) return ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
        } catch (Exception e) { /* no icon: the page falls back to a plain backdrop */ }
        return null;
    }

    /**
     * The backdrop: the icon, blurred and cropped to the screen's shape. The blur is a box blur on the icon itself
     * (it is 256 px, so it costs nothing), and the platform's own blur is added on top of it where the platform has
     * one (API 31 and later).
     */
    private Bitmap backdropBitmap(Bitmap icon) {
        Bitmap soft = blurBitmap(icon, 7);
        int w = Math.max(32, getResources().getDisplayMetrics().widthPixels / 8);
        int h = Math.max(32, getResources().getDisplayMetrics().heightPixels / 8);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float s = Math.max(w / (float) soft.getWidth(), h / (float) soft.getHeight());
        float dw = soft.getWidth() * s, dh = soft.getHeight() * s;
        c.drawBitmap(soft, null, new RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), p);
        return out;
    }

    /**
     * A box blur, three times over (which is close to a gaussian): the icon is a small bitmap, so this is cheap, and
     * it works on every Android, where the platform's own blur is only there from API 31.
     */
    private static Bitmap blurBitmap(Bitmap src, int radius) {
        int w = src.getWidth(), h = src.getHeight();
        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] line = new int[Math.max(w, h)];
        for (int pass = 0; pass < 3; pass++) {
            // horizontal
            for (int y = 0; y < h; y++) {
                int off = y * w;
                for (int x = 0; x < w; x++) {
                    int r = 0, g = 0, b = 0, a = 0, n = 0;
                    for (int k = -radius; k <= radius; k++) {
                        int xx = x + k;
                        if (xx < 0 || xx >= w) continue;
                        int c = pixels[off + xx];
                        a += (c >>> 24) & 0xff; r += (c >> 16) & 0xff; g += (c >> 8) & 0xff; b += c & 0xff;
                        n++;
                    }
                    line[x] = (a / n << 24) | (r / n << 16) | (g / n << 8) | (b / n);
                }
                System.arraycopy(line, 0, pixels, off, w);
            }
            // vertical
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    int r = 0, g = 0, b = 0, a = 0, n = 0;
                    for (int k = -radius; k <= radius; k++) {
                        int yy = y + k;
                        if (yy < 0 || yy >= h) continue;
                        int c = pixels[yy * w + x];
                        a += (c >>> 24) & 0xff; r += (c >> 16) & 0xff; g += (c >> 8) & 0xff; b += c & 0xff;
                        n++;
                    }
                    line[y] = (a / n << 24) | (r / n << 16) | (g / n << 8) | (b / n);
                }
                for (int y = 0; y < h; y++) pixels[y * w + x] = line[y];
            }
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(pixels, 0, w, 0, 0, w, h);
        return out;
    }

    /** The platform's own blur, where the platform has one (its API is 31; the older devices keep the scaled bitmap) */
    @android.annotation.TargetApi(31)
    private void blur(View backdrop) {
        if (Build.VERSION.SDK_INT < 31) return;
        backdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP));
    }

    /** The backdrop of a game whose APK carries no icon: a plain dark sheet */
    private android.graphics.drawable.Drawable fallbackBackdrop() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1A1F2B, 0xFF0A0C10});
        return d;
    }

    /** The window draws under the system bars, which stay translucent over the backdrop; the group keeps clear of them */
    private void edgeToEdge(View root, final View group) {
        if (Build.VERSION.SDK_INT >= 30) {
            edgeToEdgeNew(root, group);
        } else {
            edgeToEdgeOld(root, group);
        }
    }

    /** Android 11 and later: the window lays out edge to edge, and the bars' icons stay light over the dark backdrop */
    @android.annotation.TargetApi(30)
    private void edgeToEdgeNew(View root, final View group) {
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController c = getWindow().getDecorView().getWindowInsetsController();
        if (c != null) {
            c.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            group.setPadding(bars.left + dp(14), bars.top + dp(10), bars.right + dp(14), bars.bottom + dp(10));
            return insets;
        });
    }

    /** Android 10: the older flags draw under the bars, and the bars are made transparent */
    private void edgeToEdgeOld(View root, final View group) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            group.setPadding(insets.getSystemWindowInsetLeft() + dp(14), insets.getSystemWindowInsetTop() + dp(10),
                    insets.getSystemWindowInsetRight() + dp(14), insets.getSystemWindowInsetBottom() + dp(10));
            return insets;
        });
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * A field of the card: it shows the choice that is in force now, and it opens the list of the other choices.
     * The list is a sheet of glass of its own, and it is not taller than a few rows: a game with sixteen languages
     * must not fill the screen with its list (the platform's own spinner does).
     */
    private class ChoiceField extends LinearLayout {
        private final TextView text;
        private final ListPopupWindow popup;
        private final ChoiceAdapter adapter;
        private int selected = 0;

        ChoiceField(Activity a) {
            super(a);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setBackground(fieldBackground());
            setPadding(dp(14), 0, dp(12), 0);
            text = new TextView(a);
            text.setTextColor(C_TEXT);
            text.setTextSize(14);
            text.setSingleLine(true);
            addView(text, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            View arrow = new View(a);
            arrow.setBackground(new TriangleDrawable(C_LABEL, dp(14)));
            addView(arrow, new LayoutParams(dp(14), dp(14)));
            setOnClickListener(v -> show());
            popup = new ListPopupWindow(a);
            popup.setModal(true);
            popup.setBackgroundDrawable(popupBackground());
            popup.setHeight(dp(252));       // six rows; the rest scrolls
            adapter = new ChoiceAdapter(a);
            popup.setAdapter(adapter);
            android.widget.ListView list = popup.getListView();
            if (list != null) {
                // a scrollbar: the list shows a few of the choices and the rest are a scroll away
                list.setVerticalScrollBarEnabled(true);
                list.setScrollbarFadingEnabled(false);
            }
            popup.setOnItemClickListener((parent, view, position, id) -> {
                select(position);
                popup.dismiss();
            });
        }

        /** The choices, and the one that is in force now */
        void setChoices(List<String> items, int index) {
            adapter.setItems(items);
            selected = index < 0 || index >= items.size() ? 0 : index;
            text.setText(items.isEmpty() ? "" : items.get(selected));
            adapter.setSelected(selected);
            // the list is as tall as its rows, up to six of them: a short list does not leave a hole
            popup.setHeight(Math.min(dp(252), Math.max(1, items.size()) * dp(46)));
        }

        int selected() {
            return selected;
        }

        void select(int index) {
            selected = index;
            text.setText(adapter.getItem(index));
            adapter.setSelected(index);
        }

        private void show() {
            popup.setAnchorView(this);
            popup.setWidth(getWidth());
            popup.show();
        }
    }

    /** The list of the choices: a sheet of glass, white text, the chosen one marked */
    private static class ChoiceAdapter extends ArrayAdapter<String> {
        private int selected = 0;

        ChoiceAdapter(android.content.Context c) {
            super(c, 0);   // the list's own rows: the adapter draws them (a null context would break it)
        }

        void setItems(List<String> items) {
            setNotifyOnChange(false);
            clear();
            for (String s : items) add(s);
            notifyDataSetChanged();
        }

        void setSelected(int index) {
            selected = index;
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView t = convertView instanceof TextView ? (TextView) convertView : new TextView(getContext());
            t.setText(getItem(position));
            t.setTextColor(C_TEXT);
            t.setTextSize(14);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            float d = getContext().getResources().getDisplayMetrics().density;
            int pad = (int) (16 * d);
            int vpad = (int) (13 * d);
            t.setPadding(pad, vpad, pad, vpad);
            t.setBackground(position == selected ? selectedBackground() : null);
            return t;
        }

        /** The row that is in force now: a band across the list, not a rounded tile */
        private android.graphics.drawable.Drawable selectedBackground() {
            return new android.graphics.drawable.ColorDrawable(0x26FFFFFF);
        }
    }

    /** The list's own sheet of glass: the same card, a little darker. It is opaque: the card's own text would show
     *  through it, and the list has to be read. Its corners are small, so the row that is in force now (a band
     *  across the list) lines up with them. */
    private android.graphics.drawable.Drawable popupBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF1A1E26);
        d.setCornerRadius(dp(6));
        d.setStroke(dp(1), C_EDGE);
        return d;
    }

    /** A small triangle, the sign that the field opens a list */
    private static class TriangleDrawable extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int size;

        TriangleDrawable(int color, int size) {
            paint.setColor(color);
            this.size = size;
        }

        @Override
        public void draw(Canvas canvas) {
            float w = getBounds().width(), h = getBounds().height();
            path.reset();
            path.moveTo(w * 0.12f, h * 0.36f);
            path.lineTo(w * 0.88f, h * 0.36f);
            path.lineTo(w * 0.5f, h * 0.74f);
            path.close();
            canvas.drawPath(path, paint);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
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
        if (languages.isEmpty()) {          // a pack made before the launcher: the console's languages
            for (String[] l : LANGUAGES) { languageCodes.add(l[0]); languages.add(l[0] + "  " + l[1]); }
        }
        String[] env = env();
        int at = languageCodes.indexOf(env[0]);
        language.setChoices(languages, at < 0 ? 0 : at);

        List<String> drivers = new ArrayList<>();
        driverLabels.clear();
        driverValues.clear();
        drivers.add(S_SYSTEM);
        driverLabels.add(S_SYSTEM);
        driverValues.add(SYSTEM_DRIVER);
        // the drivers the APK carries (the pack's list, else the APK's own libraries)
        for (String name : apkDrivers()) {
            if (name.isEmpty() || name.equals(SYSTEM_DRIVER)) continue;
            drivers.add(name);
            driverLabels.add(name);
            driverValues.add(name);
        }
        // the ones the player imported (in the app's own folder: the host loads them from there)
        for (File f : importedDrivers()) {
            if (driverValues.contains(f.getAbsolutePath())) continue;
            drivers.add(f.getName());
            driverLabels.add(f.getName() + " " + S_IMPORTED);
            driverValues.add(f.getAbsolutePath());
        }
        int dr = driverValues.indexOf(env[1]);
        driver.setChoices(drivers, dr < 0 ? 0 : dr);
        boolean many = drivers.size() > 1;
        driver.setVisibility(many ? View.VISIBLE : View.GONE);
        driverLabel.setVisibility(many ? View.VISIBLE : View.GONE);
    }

    // ---- the game ------------------------------------------------------------------------------------

    /** Writes the choices into the pack's env.txt and starts the game */
    private void launch() {
        if (pack == null) { toast(S_NO_PACK); return; }
        String lang = languageCodes.isEmpty() ? "en-US" : languageCodes.get(language.selected());
        String drv = driverValues.isEmpty() ? SYSTEM_DRIVER : driverValues.get(driver.selected());
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

    // ---- the drivers ----------------------------------------------------------------------------------

    /** The Vulkan drivers the APK carries: the pack's list, else the APK's own libraries (the host's are not drivers) */
    private List<String> apkDrivers() {
        List<String> out = new ArrayList<>();
        for (String line : readLines(new File(pack == null ? new File("/dev/null") : pack, "drivers.txt"))) {
            String name = line.trim();
            if (!name.isEmpty()) out.add(name);
        }
        if (!out.isEmpty()) return out;
        File libs = new File(getApplicationInfo().nativeLibraryDir);
        File[] fs = libs.listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            String name = f.getName();
            if (!name.endsWith(".so")) continue;
            boolean host = false;
            for (String h : HOST_LIBS) if (h.equals(name)) host = true;
            if (!host) out.add(name);
        }
        return out;
    }

    /** The drivers the player imported into the app's own folder */
    private List<File> importedDrivers() {
        List<File> out = new ArrayList<>();
        File[] fs = new File(getFilesDir(), "drivers").listFiles();
        if (fs == null) return out;
        for (File f : fs) if (f.isFile() && f.getName().endsWith(".so")) out.add(f);
        return out;
    }

    /** The system file manager: the player picks a driver library, and it is copied into the app's own folder */
    private void pickDriver() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, PICK_DRIVER);
        } catch (Exception e) {
            toast(S_PICK_FAIL + e.getMessage());
        }
    }

    /** The device's "all files access" page, where the player gives the app the read on the pack */
    private void grantAccess() {
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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_DRIVER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri uri = data.getData();
            String name = fileName(uri);
            File dir = new File(getFilesDir(), "drivers");
            dir.mkdirs();
            String lower = name.toLowerCase();
            if (lower.endsWith(".zip")) {
                // the drivers come as archives (Mesa's Turnip for Adreno: libvulkan_freedreno.so in a zip)
                unzip(uri, dir);
            } else if (name.endsWith(".so")) {
                copy(uri, new File(dir, name));
            } else {
                toast(S_PICK_FAIL + name);
                return;
            }
            refresh();
            for (File f : importedDrivers()) {
                int at = driverValues.indexOf(f.getAbsolutePath());
                if (at >= 0) driver.select(at);
            }
        } catch (Exception e) {
            toast(S_PICK_FAIL + e.getMessage());
        }
    }

    /** The file behind a content URI, copied into the app's own folder */
    private void copy(Uri uri, File dst) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             java.io.OutputStream out = new FileOutputStream(dst, false)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        dst.setReadable(true, false);
        dst.setExecutable(true, false);   // the linker wants the executable bit
    }

    /** An archive of the driver, unpacked into the app's own folder (the entries' own folders are dropped: the driver is one file) */
    private void unzip(Uri uri, File dir) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(in)) {
            java.util.zip.ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String n = e.getName();
                int slash = n.lastIndexOf('/');
                if (slash >= 0) n = n.substring(slash + 1);
                if (n.isEmpty() || n.contains("/")) continue;
                File dst = new File(dir, n);
                try (java.io.OutputStream out = new FileOutputStream(dst, false)) {
                    byte[] buf = new byte[8192];
                    int k;
                    while ((k = zin.read(buf)) > 0) out.write(buf, 0, k);
                }
                dst.setReadable(true, false);
                dst.setExecutable(true, false);
            }
        }
    }

    /** The name of the file behind a content URI */
    private String fileName(Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (col >= 0) {
                    String n = c.getString(col);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Exception e) { /* no name: the path's own */ }
        String p = uri.getPath();
        if (p == null) return "driver.so";
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : "driver.so";
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

    /**
     * The game's logo: the icon in a rounded square, with the shadow of that shape under it. The shape is the view's
     * outline, so the platform draws the shadow along the rounded corners (a shadow layer would draw a rectangle).
     */
    private static class LogoView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private Bitmap icon;

        LogoView(Activity a) {
            super(a);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    RectF r = logoRect(view);
                    outline.setRoundRect((int) r.left, (int) r.top, (int) r.right, (int) r.bottom, view.getWidth() * 0.18f);
                }
            });
            setElevation(dpOf(a, 8));
            setClipToOutline(true);
        }

        void setLogo(Bitmap b) {
            icon = b;
            invalidate();
        }

        private static RectF logoRect(View v) {
            float pad = v.getWidth() * 0.05f;
            return new RectF(pad, pad, v.getWidth() - pad, v.getHeight() - pad);
        }

        private static int dpOf(Activity a, float value) {
            return (int) (value * a.getResources().getDisplayMetrics().density + 0.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (icon == null) return;
            rect.set(logoRect(this));
            float s = Math.max(rect.width() / (float) icon.getWidth(), rect.height() / (float) icon.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate(rect.left + (rect.width() - icon.getWidth() * s) / 2f,
                    rect.top + (rect.height() - icon.getHeight() * s) / 2f);
            BitmapShader shader = new BitmapShader(icon, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shader.setLocalMatrix(m);
            fill.setShader(shader);
            canvas.drawRect(rect, fill);
        }
    }
}
