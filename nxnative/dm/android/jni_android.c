// Android port: the player's entry (the app's :play process). One libdmplay.so per game (build.py --play): the
// host's objects statically linked with this file, so the JNI surface and the host travel together and the
// linker never resolves between two of the player's games. Java (com.nx.android.game.Native) hands over the
// Surface, raw input as snapshots (jni_android.h's dm_java_event — a plain Activity has no AInputQueue), the
// lifecycle and the start parameters; the shared core in front_android.c answers exactly as it does for the
// NativeActivity APK. There are no APK assets here: the gamepad's theme comes from the pack's overlay/
// (front_android.c dm_front_root, overlay_theme.c's file fallback).
#include "dm.h"
#include "jni_android.h"
#include "dm_framegen.h"
#include <android/input.h>

int dm_menu_get(int what);   // front_android.c: the menu rows' current values (0 overlay, 1 opacity, 2 rumble, 3 fps, 4 docked)
void dm_menu_overlay_set(int); void dm_menu_opacity_set(int); void dm_menu_rumble_set(int);
void dm_menu_fps_set(int); void dm_menu_mode_set(int); void dm_menu_edit_open(void);
char *dm_menu_saves(void); void dm_menu_export(void); int dm_menu_import(const char *name);
int dm_menu_export_note(char *out, int cap); int dm_menu_state(void);
#include <stdlib.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <string.h>

JNIEXPORT jboolean JNICALL
Java_com_nx_android_game_Native_start(JNIEnv *E, jclass c, jstring pack, jstring internal, jstring title_id, jint dpi, jobjectArray env, jobject ctx) {
    (void)c;
    // the launcher's env rows (KEY=VALUE; the frame generation's DM_LSFG* among them) go in before the host's own env.txt reads
    if (env) {
        jsize n = (*E)->GetArrayLength(E, env);
        for (jsize k = 0; k < n; k++) {
            jstring row = (jstring)(*E)->GetObjectArrayElement(E, env, k);
            const char *r = row ? (*E)->GetStringUTFChars(E, row, NULL) : NULL;
            if (r) { char key[128] = {0}; const char *eq = strchr(r, '='); if (eq && (size_t)(eq - r) < sizeof key) { memcpy(key, r, (size_t)(eq - r)); setenv(key, eq + 1, 1); } }
            if (r) (*E)->ReleaseStringUTFChars(E, row, r);
            if (row) (*E)->DeleteLocalRef(E, row);
        }
    }
    const char *p = pack ? (*E)->GetStringUTFChars(E, pack, NULL) : NULL, *i = internal ? (*E)->GetStringUTFChars(E, internal, NULL) : NULL,
               *t = title_id ? (*E)->GetStringUTFChars(E, title_id, NULL) : NULL;
    if (!p || !i) { if ((*E)->ExceptionCheck(E)) (*E)->ExceptionClear(E); LOG("[RED] [player] start without the pack or the app folder"); return JNI_FALSE; }
    int ok = dm_java_start(p, i, t && t[0] ? t : NULL, dpi, E, ctx);
    if (p) (*E)->ReleaseStringUTFChars(E, pack, p);
    if (i) (*E)->ReleaseStringUTFChars(E, internal, i);
    if (t) (*E)->ReleaseStringUTFChars(E, title_id, t);
    return ok ? JNI_TRUE : JNI_FALSE;   // 0: the save import asked to end — Java finishes without starting
}

JNIEXPORT void JNICALL
Java_com_nx_android_game_Native_setSurface(JNIEnv *E, jclass c, jobject surface) {
    (void)c;
    if (!surface) { dm_java_window_drop(); return; }
    ANativeWindow *w = ANativeWindow_fromSurface(E, surface);   // our reference; the window itself passes over owned
    if (!w) { if ((*E)->ExceptionCheck(E)) (*E)->ExceptionClear(E); dm_java_window_drop(); return; }
    dm_java_window_take(w);
    ANativeWindow_release(w);
}

// a motion snapshot: every pointer of the MotionEvent, and the ten axes from a controller (jni_android.h).
// Java sends only touchscreen and joystick/gamepad sources, as the NativeActivity queue would
JNIEXPORT jboolean JNICALL
Java_com_nx_android_game_Native_motion(JNIEnv *E, jclass c, jint source, jint device, jint action, jintArray ids, jfloatArray xs, jfloatArray ys, jfloatArray axis) {
    (void)c;
    dm_java_event e; memset(&e, 0, sizeof e);
    e.type = AINPUT_EVENT_TYPE_MOTION; e.source = source; e.device = device; e.action = action;
    jint n = ids ? (*E)->GetArrayLength(E, ids) : 0;
    jint ni = xs ? (*E)->GetArrayLength(E, xs) : 0, ny = ys ? (*E)->GetArrayLength(E, ys) : 0;
    if (ni < n) n = ni; if (ny < n) n = ny; if (n > 16) n = 16;
    e.pointers = n;
    jint *idv = n && ids ? (*E)->GetIntArrayElements(E, ids, NULL) : NULL;
    jfloat *xv = n && xs ? (*E)->GetFloatArrayElements(E, xs, NULL) : NULL, *yv = n && ys ? (*E)->GetFloatArrayElements(E, ys, NULL) : NULL;
    for (jint k = 0; k < n; k++) { e.ptr[k].id = idv[k]; e.ptr[k].x = xv[k]; e.ptr[k].y = yv[k]; }
    if (idv) (*E)->ReleaseIntArrayElements(E, ids, idv, JNI_ABORT);
    if (xv) (*E)->ReleaseFloatArrayElements(E, xs, xv, JNI_ABORT);
    if (yv) (*E)->ReleaseFloatArrayElements(E, ys, yv, JNI_ABORT);
    jfloat *av = axis && (*E)->GetArrayLength(E, axis) >= 10 ? (*E)->GetFloatArrayElements(E, axis, NULL) : NULL;
    if (av) { for (int k = 0; k < 10; k++) e.axis[k] = av[k]; (*E)->ReleaseFloatArrayElements(E, axis, av, JNI_ABORT); }
    return dm_java_input(&e) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_nx_android_game_Native_key(JNIEnv *E, jclass c, jint device, jint source, jint code, jint action, jint meta, jint repeat) {
    (void)E; (void)c;
    dm_java_event e; memset(&e, 0, sizeof e);
    e.type = AINPUT_EVENT_TYPE_KEY; e.source = source; e.device = device;
    e.key_code = code; e.key_action = action; e.key_meta = meta; e.key_repeat = repeat;
    return dm_java_input(&e) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_nx_android_game_Native_lifecycle(JNIEnv *E, jclass c, jint ev) {
    (void)E; (void)c;
    dm_java_lifecycle(ev);
}

// the drawer's menu rows: the host menu's own state and actions, driven from the app (front_android.c dm_menu_*)
JNIEXPORT jstring JNICALL
Java_com_nx_android_game_Native_menuState(JNIEnv *E, jclass c) {
    (void)E; (void)c;
    int st = dm_menu_state();
    char out[64]; snprintf(out, sizeof out, "%d", st);
    return (*E)->NewStringUTF(E, out);
}
JNIEXPORT jint JNICALL
Java_com_nx_android_game_Native_menuGet(JNIEnv *E, jclass c, jint what) {
    (void)E; (void)c;
    return dm_menu_get(what);
}
JNIEXPORT void JNICALL
Java_com_nx_android_game_Native_menuSet(JNIEnv *E, jclass c, jint what, jdouble v) {
    (void)E; (void)c;
    switch (what) {
    case 0: dm_menu_overlay_set((int)v); break;
    case 1: dm_menu_opacity_set((int)v); break;
    case 2: dm_menu_rumble_set((int)v); break;
    case 3: dm_menu_fps_set((int)v); break;
    case 4: dm_menu_mode_set((int)v); break;
    }
}
JNIEXPORT void JNICALL
Java_com_nx_android_game_Native_menuEditOpen(JNIEnv *E, jclass c) { (void)E; (void)c; dm_menu_edit_open(); }
JNIEXPORT jstring JNICALL
Java_com_nx_android_game_Native_menuSaves(JNIEnv *E, jclass c) {
    (void)E; (void)c;
    char *list = dm_menu_saves();
    jstring out = (*E)->NewStringUTF(E, list ? list : "[]");
    free(list);
    return out;
}
JNIEXPORT void JNICALL
Java_com_nx_android_game_Native_menuExport(JNIEnv *E, jclass c) { (void)E; (void)c; dm_menu_export(); }
JNIEXPORT jboolean JNICALL
Java_com_nx_android_game_Native_menuImport(JNIEnv *E, jclass c, jstring name) {
    (void)E; (void)c;
    const char *n = name ? (*E)->GetStringUTFChars(E, name, NULL) : NULL;
    int ok = dm_menu_import(n);
    if (n) (*E)->ReleaseStringUTFChars(E, name, n);
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jstring JNICALL
Java_com_nx_android_game_Native_menuExportNote(JNIEnv *E, jclass c) {
    (void)c;
    char buf[128] = {0};
    dm_menu_export_note(buf, (int)sizeof buf);
    return (*E)->NewStringUTF(E, buf);
}

// the drawer's frame generation: the live switch and the status line ("<state> <reason>", Banana's format)
JNIEXPORT void JNICALL
Java_com_nx_android_game_Native_frameGenSet(JNIEnv *E, jclass c, jboolean on, jint mult, jint target, jint flow) {
    (void)E; (void)c;
    dm_fg_set(on ? 1 : 0, mult, target, flow);
}

JNIEXPORT jstring JNICALL
Java_com_nx_android_game_Native_frameGenStatus(JNIEnv *E, jclass c) {
    (void)c;
    char buf[256] = {0};
    int st = dm_fg_status(buf, (int)sizeof buf - 16);
    char out[300]; snprintf(out, sizeof out, "%d %s", st, buf);
    return (*E)->NewStringUTF(E, out);
}
