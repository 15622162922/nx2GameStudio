# 手机移植笔记（nx2android on Android）

`phone-app-pitfalls.md` 记的是"踩坑"（症状 → 原因 → 改法）。这里记另外两件：**一次移植要多久**，
和**数据包放在哪里**。

---

## 1. 一次移植要多久

手机上从点「开始移植」到出 APK 和数据包（2026-11-06 实测，Mario vs. Donkey Kong 1.0.1，
Y700 / 骁龙 8 Elite / 3040×1904）：

| 阶段 | 第一次 | 之后（有缓存） | 说明 |
|---|---|---|---|
| 1 build nx_extract | 1 秒 | 1 秒 | 预编译的，随 APK 带来的，只是拷贝 ✓ |
| 2 extract v65536 | 30 秒 | 3 秒 | 解包 + 合并更新的 RomFS（1.3 GB）✓ |
| 3 rewrite | 13 秒 | 1 秒 | 重写 main / subsdk0 / sdk ✓ |
| 4 shader split | **10 秒** | 10 秒 | fork 出来的子进程（没有信号量的池 ✓，见下 ✓）；PC 上 2 秒 ✓ |
| 5 Vulkan shaders | **约 30 秒** | 30 秒 | 翻译 12,691 个 shader：本来就分块并行 ✓（`shader_vk.py` ✓）；PC 上 24 秒 ✓ |
| 6 host（NDK 编译） | 1–2 分钟 | 1–2 分钟 | 140 个源文件 ✓ |
| 7 APK | 1 秒 | 1 秒 | 纯 Python 打包 + 签名 ✓ |
| 8 数据包 | 6 秒 | 6 秒 | 3,183 个文件、1.6 GB ✓ |

**首次全程约 3–5 分钟，重跑 15 秒到 2 分钟** ✓（重跑时阶段 2、3、4、5、6 的产物都能复用 ✓；
实测：一次有缓存的移植 15 秒 ✓，其中第 4 阶段 10 秒 ✓、第 5 阶段 30 秒 ✓）。

**第 4 阶段怎么从 2–3 分钟变成 10 秒** ✓：进程池要 POSIX 信号量（在 /dev/shm ✓，Android 没有 ✓），
所以手机上只能用 `dm/gfx/tools/forkpool.py` ✓ —— **按块 fork 子进程、管道回传结果** ✓
（fork 是内核原语，不需要信号量 ✓；和 `shader_vk.py` 的分块同构 ✓）。
`multiprocessing.Pool` 的另外两个用户（`cb_extent.py` ✓、`nro_collect.py` ✓）也换成了它 ✓，
否则新游戏在手机上会撞同一堵墙 ✓。

**"0 errors" 的 5 分钟超时**：手机上的 `dotnet build` 卡住时，porter 会等满 5 分钟再杀掉 ✓
（`nsp.run` 的超时 ✓）。看到这个时间长度就知道是卡住，不是编译慢 ✓。

---

## 2. 数据包放在哪里

**结论：不是必须放 APK 的私有目录，必须的是"游戏 App 读得到"** ✓。

现在的约定（`porter/datapack.py` 的 `files_folder()` ✓）：

```
/sdcard/Android/data/<游戏包名>/files/     ← 数据包
```

选它的理由：**卸载游戏时 Android 自动删掉这份数据** ✓（数据跟着 App 走 ✓）。

**代价**（2026-11-06 踩到 ✓）：
- Android 11+ 的 FUSE 层规定，**别的 App 写不进这个目录** ✓ → 移植器 App 把数据包拷进去时，
  文件的属主是移植器的 uid ✓，游戏 App 读不到 ✓（"FATAL open .../mods/main.fixups.tsv" ✓）。
  最后是用 shell 拷 + `chmod a+rX` 才通的 ✓ —— 这不该是玩家的活 ✓。
- 移植器 App 的「复制数据包」按钮因此不可靠 ✓（它只有 MANAGE_EXTERNAL_STORAGE ✓，
  而那个权限并不保证能写别人的 Android/data ✓）。

四个方案：

| | 做法 | 好处 | 代价 |
|---|---|---|---|
| **A**（现状） | 写 `Android/data/<pkg>/files`，拷完 chmod 644 | 卸载即清 | 跨 App 权限问题 ✓（今天这个坑 ✓） |
| **B** | 数据包留在公共目录（如 `/sdcard/Switch/Ports/<名字>-data` ✓），游戏去那儿读 | 移植器写自己有权写的目录 ✓，权限问题消失 ✓；卸载不清数据 → 换机/重装不用重拷 1.6 GB ✓ | 要改 host：游戏端要探测数据目录 ✓（一两处 C 代码 ✓）；路径得让游戏知道 ✓（写在 APK 的 asset 里 ✓） |
| **C** | 1.6 GB 全打进 APK | 一个文件 | APK 巨大 ✓，Android 对 APK 大小有限制 ✓，安装慢 ✓ → 不可取 ✓ |
| **D** | 游戏首次启动用 SAF 让用户选目录 | 干净 | 要改游戏 App 的前端 ✓，多一步 ✓ |

**建议做 B** ✓：host 端（`dm/android/`）加一个探测顺序 ——
先看 `Android/data/<pkg>/files` ✓，再看 APK 里写着的公共路径 ✓；
移植器把数据目录写进 APK 的 asset（`mkapk.py` 一行 ✓）✓。
这样"数据跟着游戏卸载"这个好处会没有 ✓ —— 换成"重装不用重拷" ✓，我觉得更值 ✓。
（要保留卸载即清的话，B 可以只作为 A 的兜底 ✓：先试 A，A 读不到再读 B ✓。）

---

## 3. 中文菜单的字体（乱码成 "?"）

**症状**：游戏设成中文 ✓，一部分中文正常 ✓，但**菜单是 "???"** ✓。

**原因**：Switch 的系统字体有六个 ✓ —— 标准（拉丁 ✓）、简体中 ✓、繁��中 ✓、韩文 ✓、
中文扩展 ✓ 和 Nintendo 扩展（按键图标 ✓）。宿主没有它们 ✓，用**替身** ✓
（`dm/host/hle_sdk16.c` ✓）：标准字体是系统自己的拉丁字体（Android 上是 Roboto ✓），
**中文和韩文那几种也用同一个替身** ✓ —— Roboto 没有 CJK 字形 ✓，于是中文画成 "?" ✓。

**改法**：中文和韩文的类型（1–4 ✓）单独用**有 CJK 字形的字体** ✓：
`DM_SHARED_FONT_CJK` ✓（或 `$DM_FONT_DIR/cjk.ttf` ✓）。
移植器在打包时把一款这样的字体放进数据包 ✓（`dm/tools/cjk_font.py` ✓）——
用的是**随工具走的那一份** ✓（`dm/tools/DroidSansFallbackFull.ttf` ✓，AOSP 的 ✓，Apache-2.0 ✓），
所以每台机器都有 ✓。

**字体的路径**：env.txt 里的 `DM_SHARED_FONT_CJK` ✓ 和 `DM_SHARED_FONT_EXT` ✓ 指的是
**数据包自己的目录** ✓（`<输出目录>/<名字>-data/` ✓）—— 现在数据包**就地读取** ✓，
App 自己的外部目录**不再被填充** ✓（早先的版本指那里 ✓，于是字体找不到 ✓，
日志里一句 `[RED] no Chinese shared font` ✓）。

**按钮图标的字体**（`nintendo_ext.ttf` ✓，按键图标在游戏文字里画成 "?" ✓）需要一款**粗体**字体 ✓：
Windows 用 Arial Rounded Bold ✓，macOS 用 "Arial Rounded Bold" ✓，
**Android 用 `/system/fonts/DroidSans-Bold.ttf`** ✓（或 Roboto-Bold ✓ / SourceSansPro-Bold ✓）——
没列上这些路径时手机上会说 `[RED] no bold system font for the letters` ✓，
按键图标就都是 "?" ✓ ✓。

**注意字体的格式** ✓：字形必须是 TrueType 的 **glyf** 表 ✓（拉丁字体天天在读的那种 ✓）——
Android 10 以后系统自带的 `NotoSansCJK-Regular.ttc` ✓ 是 **CFF2**（轮廓在 CFF2 里 ✓），
SDK 的字体引擎**读不了** ✓（抽出来也是 CFF2 ✓）✓，所以**不用它** ✓。
