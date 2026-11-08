// Frame generation in the present (Lossless Scaling's LSFG; the chain in lsfg/ is Banana Native's port of Eden,
// GPL-3.0-or-later): the present thread hands each game frame here before presenting it. The frame's picture
// (the window texture's crop) is copied into the chain's input, the shared passes run, and the generated frames
// between the previous game frame and this one land in images of their own, which the present shows before the
// game frame, each held for an equal share of the game's measured frame interval (display timing). One command
// buffer per game frame on the one queue: the submission lands after the game's own (queue order makes the
// texture read safe — the game cannot redraw the texture before NVN's acquire, which waits for the present's
// ticket), so no extra semaphore wait is needed. Off by default; the user's own Lossless.dll (the app finds it
// and passes DM_LSFG_DLL) is read once. Without NX_LSFG every call reports "not in this build".
// Settings: DM_LSFG=1, DM_LSFG_MULT=2..4, DM_LSFG_TARGET=0|fps, DM_LSFG_FLOW=0 (auto) or 25..100 (%), changed
// while running by dm_fg_set (the drawer, live over DM_CTL) or the env rows at the start.
#ifndef DM_FRAMEGEN_H
#define DM_FRAMEGEN_H

#include "gfx_vk.h"

enum { DM_FG_OFF = 0, DM_FG_GENERATING = 1, DM_FG_ON_NOT_GENERATING = 2, DM_FG_UNAVAILABLE = -1, DM_FG_NOT_IN_BUILD = -2 };

// The present thread's per-frame entry: fills gen[0..return) with the generated frames' images (stable until the
// next rebuild), *hold is the interval each presented image holds, *out_period the rate the window should ask
// for. 0 generated = the frame is presented alone.
int dm_fg_frame(vkimg_t *im, const int *crop, double period, VkImage gen[3], double *hold, double *out_period);

// The drawer's live switch and status line (the reason string follows the state int, Banana's "<state> <reason>")
void dm_fg_set(int on, int multiplier, int target_rate, int flow_pct);
int dm_fg_status(char *buf, int n);

// The refresh the display timing last saw (ns; 0 without VK_GOOGLE_display_timing): the refresh cap's input
uint64_t vk_refresh_cycle_ns(void);

#endif
