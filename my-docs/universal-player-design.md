# 通用玩家 APK：把每游戏一个 APK 变成一个播放器（设计，2026-11-06）

**现状**：每移植一个游戏 = 一个 APK（包名 `org.nx2a.<游戏>` ✓）+ 一个数据包（1.6 GB ✓）。
**设想**：APK 只做一次（一个"玩家" ✓），每个游戏只留数据包（宿主 .so 也在里面 ✓），
玩家里选游戏启动 ✓。

本文记：机制、蓝图、坑、分期计划、代价。**还没开工** ✓（Phase 0 的可行性验证也没做 ✓）。

---

## 1. 现状：每游戏一个 APK 是怎么来的

游戏 APK 是 mkapk.py 造的"无代码的 NativeActivity 壳" ✓（`dm/android/apk/mkapk.py` ✓）：

```
<activity android:name="android.app.NativeActivity" ...>       # mkapk.py:44
    <meta-data android:name="android.app.lib_name" value="dmhost" />   # mkapk.py:45
```

启动时 Android 自己加载 `libdmhost.so`（从 APK 的 lib 目录 ✓），
再调它的 `ANativeActivity_onCreate` ✓（`dm/android/front_android.c:859` ✓）。

**这个 .so 是按游戏编译的** ✓：`build.py --game <title id>` 先生成该游戏的档案头
（`dm/games/game.py header` → `gen/<id>/game_profile.h` ✓，`dm/android/build.py:52` ✓），
再编进宿主 ✓ —— 内存布局、按键、补丁偏移这些每游戏不同的常量是**编译期**定死的 ✓
（游戏日志里那句 `main: gates 35 patch_off 0x1780000 keys 1/2/3/4` ✓ 就是它 ✓）。

于是"每游戏一个 APK"有三层原因 ✓：
1. **包名**不同 → 桌面上一个图标 ✓、并存安装 ✓、存档各自独立 ✓、数据目录各自独立 ✓
   （`Android/data/<包名>/` ✓）
2. **宿主 .so 按游戏编译** ✓
3. 图标（游戏的 icon.jpg ✓）、手柄主题（`mariovsdk` ✓）、版本号（跟随游戏版本 ✓）

**游戏本体不在 APK 里** ✓：重写后的代码（`mods/main.patched.bin` ✓）、RomFS ✓、
12,691 个翻译好的 shader（`shaders.dmsv` ✓）、`env.txt` ✓ 全在数据包 ✓，
宿主启动时从自己的数据目录加载 ✓（游戏日志第一行 ✓：
`files /storage/emulated/0/Android/data/org.nx2a.mariovsdonkeykong/files` ✓）。

---

## 2. 目标架构

```
玩家的 APK（装一次，org.nx2a.player）
├── libdmloader.so        # 极小的加载器（stub），见第 3 节
├── assets/…              # 玩家的图标、主题
└── (Kotlin) 游戏选择器 Activity

每个游戏的数据包（放在公共目录，/sdcard/nx2a-games/<游戏 id>/）
├── libdmhost.so          # 该游戏按档案编译的宿主（porter 已经编好了 ✓）
├── mods/                 # 重写后的游戏代码 ✓
├── RomFS/  shaders.dmsv  env.txt  nx2a-data.json  settings.txt
```

启动一个游戏时 ✓：
1. 选择器列出 `/sdcard/nx2a-games/` 下的游戏 ✓（名字和图标从 `nx2a-data.json` ✓）
2. 把该游戏的 `libdmhost.so` **拷进玩家的私有目录** ✓（sdcard 挂载是 noexec ✓，不能直接 dlopen ✓）
3. 写一行"选中哪个游戏" ✓（`files/selected_game.txt` ✓）
4. 启动 NativeActivity ✓ → Android 加载 APK 里的 stub ✓ → stub 转交真正的游戏 .so ✓

**每个游戏的数据包放公共目录**（而不是 `Android/data/<游戏包名>/` ✓）：
移植器 App 写不进**别的 App** 的私有外部目录 ✗（Android 11+ ✓，连"所有文件访问"都不保证 ✓），
这正是今天「复制数据包」按钮失败的根因 ✓（见 `phone-app-pitfalls.md` 坑 23 ✓）。
公共目录没有这个限制 ✓：移植器写自己有权写的 ✓，玩家用自己的"所有文件访问"读 ✓。

---

## 3. 关键机制与坑

### 3.1 stub 加载器（trampoline）

```c
// libdmloader.so：APK 里那个壳的 .so。它转交给玩家选中的那个游戏的 .so。
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
#include <android/log.h>
#include <android/native_activity.h>

typedef void (*on_create_fn)(ANativeActivity *, void *, size_t);

__attribute__((visibility("default"))) void ANativeActivity_onCreate(ANativeActivity *act, void *saved, size_t size) {
    // 玩家的私有目录（act->internalDataPath ✓ 就是它 ✓）
    char path[1024];
    snprintf(path, sizeof path, "%s/selected_game.txt", act->internalDataPath);
    FILE *f = fopen(path, "r");
    if (!f) { __android_log_print(ANDROID_LOG_ERROR, "dmloader", "no game chosen"); return; }
    char lib[512] = {0};
    if (!fgets(lib, sizeof lib, f)) { fclose(f); return; }
    fclose(f);
    lib[strcspn(lib, "\r\n")] = 0;                    // 去掉换行 ✓
    // 游戏 id 交给宿主（存档、缓存按它分目录 ✓）
    char *slash = strrchr(lib, '/');
    if (slash) setenv("DM_GAME_ID", slash + 1, 1);
    void *h = dlopen(lib, RTLD_NOW | RTLD_GLOBAL);
    if (!h) { __android_log_print(ANDROID_LOG_ERROR, "dmloader", "dlopen %s: %s", lib, dlerror()); return; }
    on_create_fn create = (on_create_fn)dlsym(h, "ANativeActivity_onCreate");
    if (!create) { __android_log_print(ANDROID_LOG_ERROR, "dmloader", "no ANativeActivity_onCreate in %s", lib); return; }
    create(act, saved, size);
}
```

**坑：SONAME 撞车** ⚠️ —— stub 和游戏的 .so **不能都叫 `libdmhost.so`** ✓：
游戏的 .so 的 SONAME 就是 `libdmhost.so` ✓（`dm/android/build.py:138` 链的时候写死 `-Wl,-soname,libdmhost.so` ✓），
而 Android 已经加载了 APK 里的那个 ✓ → `dlopen` 按 SONAME 找到**已加载的那个** ✓ → 递归调自己 ✗。
**解法**：stub 叫别的名字 ✓（`libdmloader.so` ✓，manifest 的 `android.app.lib_name` 写 `dmloader` ✓），
游戏的 .so 保持 `libdmhost.so` ✓ → SONAME 不撞 ✓。

### 3.2 必须 targetSdk 28

从 App 私有目录**执行/加载**代码，Android 10（API 29）起按 targetSdk 收紧 ✓：
targetSdk ≥ 29 的 App 没有 `app_data_file` 的执行权限 ✓ → 加载不了 ✓。
targetSdk < 29 的 App 保留旧行为 ✓ —— 移植器 App 就是 targetSdk 28 ✓
（`android/app/build.gradle.kts:24` ✓，那里的注释写着 W^X 的事 ✓），
它的 rootfs 里的程序就在私有目录里 exec ✓（一直在跑 ✓）——**这条已经被现成地证明了** ✓。

**代价**：永远上不了 Google Play ✓（本来就不上 ✓，README 里写着 ✓），
而且未来 Android 若收紧这条 ✓，玩家就装不上了 ✗ —— 这是这个方案**最大的长期风险** ✓。

### 3.3 为什么必须先拷进私有目录

`/sdcard`（FUSE）挂载是 **noexec** ✓ → 放在那里的 .so 不能 dlopen ✓。
`/data/data/<包名>/files` ✓（targetSdk 28 时 ✓）可以 ✓ → 启动前拷进去 ✓
（1.6 MB ✓，一次 ✓）。

### 3.4 宿主的数据要按游戏隔离

现在"一个 App = 一个游戏" ✓ → 存档、缓存、日志天然分开 ✓。
一个玩家装多个游戏后 ✗ → 宿主要用 `DM_GAME_ID` ✓（stub 用 `setenv` 传 ✓）
给 `files/saves/`、`files/log.txt`、Vulkan 缓存等**分子目录** ✓（宿主小改 ✓，
`dm/android/front_android.c` ✓ + `saves_android.c` ✓）。

### 3.5 签名与版本

玩家 APK 的签名按玩家 ✓（`porter/paths.py` 的 `signing_key()` ✓：一个玩家一把钥匙 ✓，
这样重装/升级自己的游戏时签名一致 ✓）。玩家 APK 只有一个 ✓ → 签名不变 ✓。

---

## 4. Phase 0 的具体做法（可行性验证，半天）

**手头已有的东西** ✓：
- 游戏的 `libdmhost.so` ✓：设备上已装 APK 里就有 ✓（`/data/app/…/lib/arm64/libdmhost.so` ✓，
  1,639,408 字节 ✓）—— 这正是将来要搬进数据包的那个文件 ✓
- 编译 stub 需要的工具链 ✓：**设备自己就有** ✓
  - `tool/toolchain/clang/bin/clang` ✓（aarch64 Android clang 18 ✓）
  - `tool/toolchain/ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot` ✓（`android/native_activity.h` ✓、`android/log.h` ✓）
  - `tool/toolchain/bin/glslc` ✓

**步骤** ✓：

1. 写 stub.c ✓（第 3.1 节那段 ✓），在设备上编 ✓：
   ```bash
   T=/data/data/com.nx.android/files/tool/toolchain
   $T/clang/bin/clang --sysroot=$T/ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot \
       -fPIC -shared -o libdmloader.so stub.c -llog
   ```
2. 用 mkapk.py 打一个**测试 APK** ✓（纯 Python ✓，Mac 上就能跑 ✓）：
   把 `android.app.lib_name` 从 `dmhost` 改成 `dmloader` ✓，塞进 `lib/arm64/libdmloader.so` ✓
3. 装上 ✓（`adb install` ✓），但**先不写选择器** ✓：手动把游戏的 .so 拷进私有目录 ✓
   （`adb shell run-as <pkg> cp …` ✓ 或用 root shell ✓），再写 `files/selected_game.txt` ✓
4. 启动 ✓ → stub `dlopen` 游戏的 .so ✓ → 宿主的 `ANativeActivity_onCreate` ✓ → 游戏起来 ✓
5. 看 logcat ✓：stub 的错误日志（`dlopen` 失败 ✓、符号找不到 ✓）或游戏日志 ✓
6. **判据**：游戏能启动出画面 ✓ = 加载器这条路可行 ✓ → 再投入 Phase 1–4 ✓；
   不行 = 早知道 ✓（省下几天 ✓）

**注意** ✓：
- stub 的名字**不能**是 `libdmhost.so` ✓（SONAME 撞车 ✓ 第 3.1 节 ✓）
- 游戏的 .so **必须**在私有目录里 ✓（sdcard noexec ✓）
- 这个测试 APK 的包名随便 ✓（别和已装的游戏包名冲突 ✓）

---

## 5. 改造清单（分期）

| 期 | 做什么 | 验收 | 量 |
|---|---|---|---|
| **0** | **可行性验证**：手工写 stub ✓（第 3.1 节那段 ✓）、拿现有游戏的 `libdmhost.so` ✓ 拷进私有目录 ✓、在 Y700 上跑起来 ✓ | 游戏能启动、能出画面 ✓（**最高风险的一步 ✓ 先证明它 ✓**）| 半天 |
| 1 | **玩家 App**：固定的包名 ✓、targetSdk 28 ✓、游戏选择器 Activity ✓、启动前拷贝 ✓、stub ✓、清单里 `android.app.lib_name=dmloader` ✓ | 能选游戏、能启动、能切换 ✓ | 1–2 天 |
| 2 | **porter 侧**：数据包多带 `libdmhost.so` ✓ 和元数据 ✓（名字/图标 ✓）；mkapk.py 加"玩家"模式 ✓；手机 App 的流程：装一次玩家 ✓，以后每游戏只拷数据 ✓（"添加游戏" ✓ 写进公共目录 ✓）| 手机上从 dump 到能玩 ✓（不再有"安装 APK"按钮 ✓）| 1 天 |
| 3 | **宿主隔离**：存档/日志/缓存按 `DM_GAME_ID` 分目录 ✓ | 两个游戏的存档互不干扰 ✓ | 半天 |
| 4 | 回归几个游戏 ✓ + `my-docs` 记录 ✓ + README/ARCHITECTURE 更新 ✓ | 26 个游戏里抽 5 个验一遍 ✓ | 1 天 |

**数据包的体积变化**：+1.6 MB（游戏的 .so ✓）✓，1.6 GB → 1.6 GB ✓。
**玩家 APK 的体积**：stub 几十 KB ✓ + 选择器 ✓ → 很小 ✓（不带 payload ✓）。

---

## 6. 代价与取舍（做之前要想清楚）

- **targetSdk 锁死 28** ✓：上不了 Play ✓（本来也不上 ✓）；未来 Android 收紧就是死路 ✓（**最大的风险 ✓**）
- **桌面体验变了** ✓：不是一个游戏一个图标 ✓，而是进玩家选游戏 ✓（喜欢这点的用户会觉得更好 ✓✓，
  讨厌的会觉得多一步 ✓）
- **一个崩溃域** ✓：游戏崩了回到玩家 ✓（现在崩了回到桌面 ✓ —— 差别不大 ✓）
- **存档隔离要自己保证** ✓（第 3.4 节 ✓）
- **数据不再随卸载而清** ✓：数据包在公共目录 ✓ → 卸载玩家不会清掉游戏 ✓（要清得自己删 ✓）；
  如果数据包放在玩家的私有目录 ✓ → 卸载会一次全清 ✗ → 所以选公共目录 ✓
- **26 个已验证游戏的回归** ✓：每个游戏的 .so 与现在 APK 里的**完全一样** ✓ → 风险集中在加载器 ✓
- **工作量几天级** ✓：不是一晚上的活 ✓

---

## 7. 这个方案解决什么

- **少装一堆 App** ✓：桌面上一个"nx2android 玩家" ✓，里面 26 个游戏 ✓
- **换机/重装方便** ✓：拷一个玩家 + 数据包目录 ✓
- **给别人分发方便** ✓：对方装一次玩家 ✓，之后只发数据包 ✓
- **顺带解决手机的跨 App 写入问题** ✓：数据包在公共目录 ✓，移植器写 ✓，玩家读 ✓（不再有"复制数据包"这个坑 ✓）

---

## 8. 待定

1. **动机确认**：主要想解决的是"少装 App"、"换机方便"、还是"分发方便"？→ 决定选择器的形态和存档策略 ✓
2. **数据目录的最终位置**：`/sdcard/nx2a-games/<游戏 id>/` ✓（公共 ✓，卸载不清 ✓）
   还是玩家的私有目录 ✓（卸载全清 ✓，但移植器写不进去 ✗ → 需要别的办法 ✓）？
3. **和已有的未推送改动的关系**：本文档写的时候，工作区有 7 个提交没 push ✓（合并 dump 的修复 ✓、
   手机端跑通 ✓、fork 池 ✓ 等 ✓）。这个大改动会叠在它们上面 ✓ —— 先 push 再开工？还是都不 push？✓

---

## 相关

- `my-docs/phone-app-pitfalls.md`：手机的坑（18–23 讲的就是 /dev/shm ✓、.NET SDK ✓、
  工具链的库 ✓、编译器的包装脚本 ✓、APK provider ✓）—— 通用玩家的每个坑都能在那里找到前车之鉴 ✓
- `my-docs/phone-port-notes.md`：各阶段耗时 ✓、数据包该放哪 ✓（本方案就是那个问题的答案之一 ✓）
- `docs/ARCHITECTURE.md`：移植流水线 ✓
