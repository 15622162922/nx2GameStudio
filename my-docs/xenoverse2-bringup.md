# Dragon Ball Xenoverse 2 (010078D000F88000)：实验性移植，黑屏（2026-11-06）

DRAGON BALL XENOVERSE 2 FOR NINTENDO SWITCH ✓ —— 仓库里**没有档案** ✓，
移植时走 survey 自动猜了一个（provisional profile ✓，`profile/game.tsv` 头部写着
"not checked by hand" ✓），包名 `org.nx2a.t010078d000f88000` ✓（`t` 前缀 = 实验性 ✓）。

**现状**：移植成功 ✓，游戏能启动 ✓，**但画面全黑** ✗。

## 日志在哪

- 游戏的日志（宿主写的 ✓）：`<数据包目录>/log.txt` ✓ —— 本次是
  `/storage/emulated/0/Android/data/org.nx2a.t010078d000f88000/files/log.txt` ✓
- 移植器那边：`/data/user/0/com.nx.android/files/work/010078D000F88000_build/` ✓
  （`logs/` ✓ 各阶段 ✓；`profile/<tid>/game.tsv` ✓ 猜出来的档案 ✓）

## 日志里读出来的东西

| 线索 | 含义 |
|---|---|
| 8 个模块加载 ✓（main ✓ subsdk0/1/2/4/5/6 ✓ sdk ✓；subsdk3 不加载 ✓ 它是 libc ✓）| 布局对了 ✓ |
| `main: gates 167 patch_off 0x49d0000 keys 1/2/3/4` ✓ | 档案猜的偏移 ✓ |
| `[vk] GPU Adreno 830, Vulkan 1.3.284` ✓ | Vulkan 初始化 ✓ |
| `[vk] shader bundle .../shaders.dmsv: 578 shaders, 4.5 MB` ✓ | 着色器翻译过了 ✓ |
| `[nvn] WindowInitialize swap chain 2 textures, interval 1, first 1600x900 format 0x25` ✓ | 窗口 ✓ |
| `[nvn] init 0: 109 functions 7582 calls` ✓ | NVN 初始化调用 ✓ |
| `ProgramInitialize=610 ProgramSetShaders=610` ✓ | **610 个着色器程序** ✓ |
| **没有 ProgramFinalize** ✗ | **程序从未完成链接/编译** ✗ |
| `[vk] prewarm: 19 of 19 pipelines` ✓ | 只有移植时记录的那 19 条预热管线 ✓ |
| `[longframe] ... new pipelines 0 + shader libraries 0` ✓ | **运行期一条新管线都没建** ✗ |
| 每帧只有状态/清除，**一次 draw 都没有** ✗ | **所以画面全黑** ✗ |
| `audio #0 OpenAudioOut ... channels 6` ✓ | 音频开了 ✓（主循环在跑 ✓）|
| `oe notification popped by the game: 15` ✓ | 游戏往系统发了个通知 ✓（编号 15 未知 ✓）|
| `[RED] NVN address lookup of an unknown name: nvnDeviceWaitForError` ✓ | **我们后端没这个 NVN 函数** ✗，游戏查了两次 ✓ |

## 判断

游戏**跑到了渲染器之前**：模块 ✓、Vulkan ✓、着色器程序 ✓、主循环 ✓、音频 ✓，
但**从不进入绘制** ✗ —— 既没有 finalize 程序 ✓，也没有任何 draw ✓。
典型的实验性移植形态 ✓（移植时提示过"可能无法启动" ✓）。

## 下次 bring-up 的检查顺序（建议）

1. **先把 `nvnDeviceWaitForError` 补上**（空实现即可 ✓）—— 游戏查了它两次 ✓，
   说明它在等错误状态 ✓；没有它时游戏拿到 NULL ✓，可能就走进了等待分支 ✓
2. 查游戏在等什么：日志里没有别的 [RED] ✓，所以服务调用都实现了 ✓ 或还没被调到 ✓ ——
   可以在宿主的 HLE 层加"未实现调用"的日志 ✓，或者用调试器看它在哪个服务上阻塞 ✓
3. 对照仓库里正式档案的 bring-up 流程（docs/DEVELOPMENT_GUIDE.md "Adding a game" ✓）：
   正式档案的 patches.py ✓ 和这个游戏的差异 ✓ —— 实验性移植**没有补丁** ✓，
   缺的那套补丁往往就是让它画不出东西的原因 ✓
4. 610 个 program 没有 finalize ✓ —— 看它 finalize 的时机是不是被什么东西挡住了 ✓

## 顺便记下的两件事

- **权限要在路径检查之前**（坑 27 ✓）：游戏 App 没有“所有文件访问”时 ✓，
  共享存储连 `stat()` 都静默拒绝 ✓ → 烤进 APK 的数据包路径读不到 ✓ →
  游戏会安静地回落到旧布局的旧数据包 ✓（看着能跑 ✓，其实用的是老包 ✓）
- **实验性移植的数据包在 `Switch/Ports/` ✓**：本次新包在
  `/storage/emulated/0/Switch/Ports/DragonBallXenoverse2ForNintendoSwitch-data/` ✓，
  而游戏当时读的是旧布局的（08:29 那次 ✓）——内容一样 ✓，但别当巧合 ✓

# 第二次：画面出来了，52 秒后闪退（2026-11-06 晚）

用新打的 App 重新移植 ✓ → **不再黑屏** ✓：1500+ 帧 ✓、管线建起来了 ✓、
纹理池 ✓、音频 ✓、还在跑 ✓ —— 然后**闪退** ✗。

日志（还是 `<数据包目录>/log.txt` ✓，这次是
`/sdcard/Switch/Ports/DragonBallXenoverse2ForNintendoSwitch-data/log.txt` ✓）：

```
266: [RED] NVN unimplemented function called: nvnDeviceGetWindowOriginMode
267: [RED] NVN unimplemented function called: nvnDeviceGetDepthMode
272: CRASH sig=11 addr=0 pc=subsdk5+0x73b80 lr=subsdk4+0x1d5668
```

**崩因**：游戏启动时调过 `nvnDeviceSetWindowOriginMode(1)` ✓ 和
`nvnDeviceSetDepthMode(1)` ✓（日志有 `[nvn] window origin upper left` ✓、
`[nvn] depth mode near=0` ✓）—— 52 秒后它**把这两个值读回来** ✓，
而宿主**没实现这两个 getter** ✓ → 通用 stub 永远返回 0 ✓
（= lower left ✓ / near=-w ✓，跟游戏刚设的正好相反 ✓）→
游戏拿着错的模式往下走 ✓ → 拿到一个 NULL 对象 ✓ → 崩 ✓。

反汇编（`nso2elf.py` + capstone ✓，模块从手机的
`work/010078D000F88000_build/extract/v0/ExeFS/` 拉的 ✓）：

- `subsdk5+0x73b60` 是个**构造函数** ✓：`str x8, [x19]` ✓，x19 = 传入的对象 ✓ = NULL ✓
- 调用者 `subsdk4+0x1d5648` ✓：`bl`（PLT ✓）把 x0=NULL 传了进去 ✓

**修法**（核心层 ✓ 不是档案 ✓，`dm/gfx/nvn/hle_nvn.c` ✓）：
两个 getter 把 `dev_t_` 里 Set 存的值读回去 ✓（`->origin` ✓ / `->depth_mode` ✓）✓

枚举以真机的 `gfx/nvn/include/nvn.h` 为准 ✓：
`NVN_WINDOW_ORIGIN_MODE_LOWER_LEFT = 0` ✓、`UPPER_LEFT = 1` ✓、
`NVN_DEPTH_MODE_NEAR_IS_MINUS_W = 0` ✓、`NEAR_IS_ZERO = 1` ✓ ——
`nvn_mtl.h` 里那句注释把**参数**和**枚举**写反了 ✓，看着绕 ✓。

`nvnDeviceWaitForError` 查不到名字 ✓ → 宿主返回 NULL ✓ —— 这个**是对的** ✓：
真机的 NVN 也没这个函数 ✓（`names.txt` 里只有 `nvnQueueGetError` ✓），
游戏是在探测新 API ✓，拿到 NULL 应当自己处理 ✓。**不要**去补它 ✓。

## 另一个坑：手机上导入的驱动加载不了（已修）

日志里：`[RED] DM_VK_DRIVER ...: no libadrenotools in the APK` ✓ → 退回系统驱动 ✓。

原因链 ✓：

1. 玩家在**启动器页**把 Turnip 导进了**游戏 App 自己的**目录 ✓
   （`/data/user/0/org.nx2a.t010078d000f88000/files/drivers/` ✓）
2. 启动器把它写进 `env.txt` 的 `DM_VK_DRIVER` ✓
3. 但**游戏 APK 里没有 `libadrenotools.so`** ✓（`unzip -l` 只有 `libdmhost.so` ✓）→
   加载器没有 → 驱动加载不了 ✓

根因是 2026-11-06 11:00 的重构（952ada9 ✓）：它把手机 App 的"驱动文件夹"去掉了 ✓
（理由是对的 ✓：导入驱动不用再移植一次 ✓），可**移植时只有"这次自带驱动"才把加载器
打进 APK** ✓ → 手机的移植从此不带驱动 ✓ → 加载器再没有进过 APK ✓。

**修好了** ✓（见 `my-docs/game-launcher.md` ✓ "加载器要一直在游戏 APK 里" ✓）：
游戏 APK 现在**总是**带着加载器 ✓ —— 手机上用预编的 ✓
（`packaging/android-apk/build_adrenotools.py` ✓，跟 nx_extract 一个路子 ✓：
手机没有 C++ 编译器 ✓，NDK 的 `clang++` 是 x86_64 的 ✓，App 自带的 ARM64 LLVM 只有 clang ✓），
电脑上照旧从源码编 ✓（`dm/android/build.py --adrenotools` ✓）。

启动器那 5 个库不会出现在驱动列表里 ✓（`HOST_LIBS` ✓ 里列着 ✓）。

