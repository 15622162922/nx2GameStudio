// Android port: the second front end's contract (the app's :play process, jni_android.c). front_android.c
// serves the code-less NativeActivity APK (the PC flow) and the app's player alike: one shared core (window,
// input, lifecycle, settings, the host's start), two entries. The NativeActivity's callbacks feed it as always;
// the player's plain Activity has no AInputQueue, so Java hands over raw events through this shape instead —
// a key event, or a motion snapshot (every pointer's place, as AMotionEvent would report it). No AInputEvent
// crosses this line: the NativeActivity path unpacks its queue's event into a dm_java_event
// (java_event_from_input, front_android.c) so both entries drive identical handlers.
#ifndef DM_JNI_ANDROID_H
#define DM_JNI_ANDROID_H

#include <jni.h>
#include <android/native_window.h>
#include <stddef.h>
#include <stdint.h>

typedef struct {
    int32_t type;      // AINPUT_EVENT_TYPE_KEY or AINPUT_EVENT_TYPE_MOTION
    int32_t source;    // AINPUT_SOURCE_* (a key: the KeyEvent's source; motion: the MotionEvent's)
    int32_t device;    // the input device's id
    int32_t key_code, key_action, key_meta, key_repeat;   // a key event
    int32_t action;    // motion: getAction() as Java carries it (the mask and the pointer index in place)
    int32_t pointers;  // motion: how many ptr[] entries hold this snapshot
    struct { int32_t id; float x, y; } ptr[16];
    float axis[10];    // AMOTION_EVENT_AXIS_X Y Z RZ LTRIGGER RTRIGGER HAT_X HAT_Y, then BRAKE, GAS (0 where the device has none)
} dm_java_event;

// the shared core, driven from the player (jni_android.c's JNI surface: Java class com.nx.android.game.Native)
int dm_java_input(const dm_java_event *e);      // one event into the game (1 consumed, as handle_event returns)
void dm_java_window_take(ANativeWindow *w);     // the app's Surface is here (the reference passes over)
void dm_java_window_drop(void);                 // it is going: the swapchain is destroyed before this returns
void dm_java_lifecycle(int ev);                 // 0 pause, 1 resume, 2 window focus, 3 window unfocus
int dm_java_start(const char *pack, const char *internal, const char *title_id, int dpi, JNIEnv *E, jobject ctx);
                                                // the pack folder, the app's internal folder, the game's title id
                                                // (saves, logs and temp go under it), the display density, the call's
                                                // JNIEnv and a Context (the Activity) for the haptics. 1 started, 0 the
                                                // save import asked to end: the game is not started

#endif
