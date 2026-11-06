# 游戏 APK 的启动页（LauncherActivity）

每个游戏 APK 里都有一个**自己的启动页** ✓：桌面的图标点进去先看到它 ✓，
显示游戏的信息、数据包的位置和语言与 Vulkan 驱动的选择 ✓，
点「▶ 启动游戏」才跳到游戏的原生 Activity（`android.app.NativeActivity`）✓。

## 为什么是它

- 玩家点图标**不该直接进游戏** ✓：游戏要多-files 的路径、多语言、多驱动的选择 ✓，
  这些以前要么在游戏里（原生对话框 ✓），要么根本没法选 ✓。
- 之前试过 SAF 的文件夹选择器 ✓（`ACTION_OPEN_DOCUMENT_TREE` ✓）——
  **原生 Activity 收不到 `onActivityResult`** ✓（AOSP 的 `NativeActivity.java` 没有覆盖它 ✓），
  于是永远循环弹框 ✓。换成真正的 Java Activity 就没问题了 ✓（它有 `onActivityResult` ✓）。
- 启动页和游戏**在同一个 APK、同一个 uid** ✓：它们共用内部目录 ✓
  （游戏把记住的数据包路径写在 `files/data_dir.txt` ✓ —— 启动页读同一个文件 ✓）✓，
  也共用存储权限 ✓（授权一次两边都有效 ✓）。

## 构成

| 件 | 在哪 | 干什么 |
|---|---|---|
| `LauncherActivity.java` | `nxnative/dm/android/launcher/` | 页面本身 ✓（纯代码建视图 ✓，没有 XML ✓，免 aapt ✓） |
| `build_launcher.py` | `packaging/android-apk/` | 用 **javac + d8** 把 Java 编译成 `classes.dex` ✓ |
| `mkapk.py --dex` | `nxnative/dm/android/apk/` | 把 dex 放进 APK ✓，清单改成 `hasCode=true` ✓，加启动页入口 ✓ |
| `pipeline.py` | `porter/` | 找 dex ✓、传 `--dex` ✓、写 `languages.txt` / `drivers.txt` ✓ |

**dex 在电脑上编译** ✓（和 `nx_extract` 一样 ✓）：手机上没有 JDK ✓，也没有 Android SDK ✓
（手机的 .NET SDK 连自己的 globalization 模式都过不去 ✓）。
`build_assets.py` 顺带把它编好 ✓，放进工具树的 `tool/tools/launcher.dex` ✓，
App 启动刷新工具时带上它 ✓。

## 清单长什么样

```
<application hasCode=true>                                  ← 原来 false（无代码的 APK ✓）
  <activity com.nx2a.launcher.LauncherActivity>             ← 图标进这里 ✓
    <intent-filter> MAIN + LAUNCHER ✓>
  <activity android.app.NativeActivity>                     ← 启动页跳过去 ✓
    <meta-data android.app.lib_name = dmhost ✓>             ← 不再是入口 ✓
```

**没有 dex 的老 APK 不变** ✓：`hasCode=false` ✓，`NativeActivity` 还是入口 ✓，
已经装在手机上的老游戏照常玩 ✓。

## 页面上的东西

- **游戏名** ✓：数据包里的 `nx2a-data.json` 的 `name` ✓（移植器写的清单 ✓）。
- **数据包位置** ✓：游戏记住的路径（`files/data_dir.txt` ✓）✓，
  没有就用地��里烤进去的那个（`assets/nx2a-data.txt` ✓）✓。
- **语言** ✓：游戏自己支持的语言（`control.nacp` 的 `SupportedLanguageFlag` ✓）✓，
  写进数据包的 `languages.txt` ✓（一行 `代码<TAB>名字` ✓）。
- **Vulkan 驱动** ✓：APK 里带的驱动（`drivers.txt` ✓）✓，
  第一项是**系统自带** ✓；选了哪个就写 `DM_VK_DRIVER` ✓，加载失败自动回落系统的 ✓
  （`dm/android/gfx_vk_core.c` 的 `vk_init` ✓）。
- **存储权限** ✓：读不到数据包时给一个按钮 ✓，点开是系统的“所有文件访问”页 ✓。

点「▶ 启动游戏」→ 改写数据包的 `env.txt`（`DM_LANG` ✓ `DM_VK_DRIVER` ✓）✓
→ `startActivity(NativeActivity)` ✓。

## 玩家自己带驱动

驱动不用打包进 APK ✓：**页面上的「选择驱动文件」按钮** ✓ 打开**系统文件管理器** ✓，
玩家挑一个文件 ✓（Mesa 的 Turnip 就是 `libvulkan_freedreno.so` ✓，下载下来是 **zip** ✓，
选 zip 的话**自动解开** ✓），它被**复制进 App 的私有目录** ✓（`files/drivers/` ✓）✓，
然后出现在驱动列表里 ✓。

选中的驱动写进 `env.txt` 的是**完整路径** ✓（`DM_VK_DRIVER=/data/user/0/<包名>/files/drivers/xxx.so` ✓）——
宿主（`dm/android/gfx_vk_core.c` ✓）看见路径里的斜杠 ✓，就把那个目录交给 adrenotools ✓
（它在自己的 linker namespace 里按名字加载那个库 ✓），hook 的库**仍然**在 APK 的 lib 目录里 ✓ ✓。

加载失败（驱动不匹配 ✓、没有 Vulkan 1.3 的 GPU ✓）时**自动回落到系统的驱动** ✓ ✓。

**加载器（`libadrenotools.so` ✓ + 4 个 hook ✓）要一直在游戏 APK 里** ✓ ——
没有它 ✓，玩家导入的驱动根本加载不了 ✓（日志里一句
`[RED] DM_VK_DRIVER ...: no libadrenotools in the APK` ✓，然后安静地换系统驱动 ✓）。
2026-11-06 踩过 ✓：**移植时只有"这次自带了驱动"才把加载器打进 APK** ✓
（`porter/pipeline.py` ✓ 的 `drivers()` ✓），而 11:00 的重构（952ada9 ✓）
把手机 App 的"驱动文件夹"去掉了 ✓（理由是对的 ✓：导入驱动不用再移植一次 ✓）→
手机的移植从此不带驱动 ✓ → **APK 里再没有加载器** ✓ → 导入的驱动一直是死的 ✓。

现在**游戏 APK 总是带着加载器** ✓（`porter/pipeline.py` ✓ 的 `loader_libs()` ✓）：
- 手机上用的是**预编好的** ✓（`packaging/android-apk/build_adrenotools.py` ✓，
  跟 nx_extract 一个路子 ✓：手机编不了 C++ ✓），
  在 App 的资产里 ✓（`tool/tools/adrenotools/` ✓，1.3 MB ✓）
- 电脑上照旧从源码编 ✓（`dm/android/build.py --adrenotools` ✓）

启动器的驱动列表里**不会**出现这 5 个库 ✓（`HOST_LIBS` ✓ 里列着 ✓）。

## 驱动从哪来

**玩家自己在游戏里选** ✓（上面的「玩家自己带驱动」✓）—— 不用重新移植 ✓，
所以手机 App 里**没有**驱动文件夹的选项 ✓。

打包进去也可以 ✓（命令行 ✓）：`--vulkan-driver-dir <目录>` ✓（里面的 `.so` 全部打进 APK ✓，
游戏页面上就有它们 ✓）；单个驱动用 `--vulkan-driver <文件>` ✓（它也是 env.txt 的默认值 ✓）。
桌面 GUI（`gui/app.py` ✓）有对应的输入框 ✓。

## 已知的坑

- **有些设备的 `Environment.isExternalStorageManager()` 会说谎** ✓（联想的 ROM ✓：
  UID 的 op 是 allow ✓，App 自己的 op 是 default ✓，读 `/sdcard` 会被拒 ✓，
  但那个函数返回 true ✓）。所以启动页**不看它** ✓，而是**直接读数据包的 `env.txt`** ✓
  —— 读得到就是有权限 ✓，读不到就给授权按钮 ✓。
- 启动页是**竖屏**的普通 Activity ✓，游戏的原生 Activity 才是横屏 ✓；
  两者在同一个 task 里 ✓，横竖切换不会重建游戏 ✓。
