# 手机 App 踩坑记录（nx2android on Android）

在手机上跑 nx2android（自带 glibc rootfs 的移植器）时踩过的坑。每条都是：**症状 → 原因 → 改法**。
按"最难查"排序：前三条都会让程序**看起来卡住或像是随机失败**，而第四条会**悄悄损坏文件**。

---

## 1. 子进程的 stdout 塞满管道 → `waitFor()` 永远不返回

**症状**：App 停在"正在准备：the interpreter paths"，几十秒没有任何动静，既不报错也不结束。

**原因**：`ProcessBuilder` 的 stdout / stderr 默认是管道（缓冲区 64 KB）。`patch_interp.py` 每改一个文件就打印一行，
几百个文件把管道写满了 → guest 程序阻塞在 write 上 → 永远不会退出 → 主线程 `waitFor()` 也就永远不返回。
**只读 stderr 不读 stdout，一样会死锁**（反过来也一样）。

**改法**：
- 需要长期运行的子进程，输出重定向到文件，不要走管道：
  `pb.redirectErrorStream(true)` + `pb.redirectOutput(logFile)`。
- 一次性的短命令，用 `pb.redirectErrorStream(true)` 把两个流合成一个，调用方只读一个流就不会漏读。

**教训**：任何"逐行读子进程输出"的代码，要么两个流都读，要么合并；长任务一律落盘。

---

## 2. Android 上 `File.setExecutable()` 是空操作 → 所有程序都是 0600

**症状**：
- `porter doctor` 报 `.NET SDK  no SDK`，但路径明明在；
- 手工跑 `dotnet --list-sdks` 得到 `Permission denied`（errno 126）；
- `ls -l` 显示 `dotnet` 是 `-rw-------`。

**原因**：解 tar 时用的是 `File.setReadable/setWritable/setExecutable`。**Android 的 `java.io.File.setExecutable()` 不改权限**
（libcore 里是 no-op），而应用进程的 umask 是 077，于是所有文件都是 0600。guest 程序靠 exec 启动 → 全部起不来。

**改法**：用 POSIX 权限（`java.nio.file.Files.setPosixFilePermissions`），它走 `Os.chmod` ✓。
见 `android/app/src/main/java/com/nx/android/Tar.kt` 的 `Tar.applyMode()`。

**顺带**：APK 里的 assets **不带权限位**，所以 `tool/`、`toolchain/` 目录从 assets 拷出来也会全部 0600。
构建时额外写一份 `modes.json`（路径 → mode），App 拷完后按这份清单恢复权限 ✓。

---

## 3. 用临时文件做原子替换 → 新文件是 0600，执行位没了

**症状**：第 2 条修好之后，`dotnet` 还是 `Permission denied`；但 `clang` 是好的。

**原因**：改解释器路径时为了避免"写到一半被杀 → 半截二进制"，脚本先写 `xxx.interp-tmp` 再 `os.replace()`。
临时文件是新建的，权限 0600 → 替换后执行位消失 → **越"安全"的写法越把程序弄坏**。

**改法**：
```python
tmp.write_bytes(data)
os.chmod(tmp, os.stat(path).st_mode & 0o777)   # 旧文件的权限
os.replace(tmp, path)
```
再加一道保险：脚本发现"有 PT_INTERP 但没有执行位"时补上执行位（幂等）✓。

---

## 4. 程序头偏移用了相对值 → 悄悄写坏 ELF 头

**症状**：
- 改完解释器路径后，guest 程序**还是**去 `/lib/ld-linux-aarch64.so.1` 找解释器；
- `python3.13` 的体积每次运行都变大（6804784 → 6805065 → 6805161 → 6806025 …）；
- `e_phoff` / `e_shoff` 变成乱七八糟的值。

**原因**：为了"只读文件头，不读整个文件"，把读程序头的代码改成了先 `seek(phoff)` 读一块，
然后用**块内偏移** `i * phentsize` 去写程序头 —— 但写的时候需要的是**文件内偏移** `phoff + i * phentsize`。
结果 8 字节被写到了 ELF 头里（`e_phoff`/`e_shoff` 被覆盖），解释器项却没改 → 下次再跑又改一遍 → 文件一直变大。

**改法**：两个偏移都要留着 —— 写文件用 `phoff + i * phentsize`，读内存块用 `i * phentsize`。

**教训**：这类"优化"必须配测试。`tests/test_patch_interp.py` 现在检查三件事：
改两次文件不再变大、改完 `e_type/e_phoff` 没坏、第二次执行幂等 ✓。

---

## 5. 权限全丢 → `porter doctor` 的报错会误导人

**症状**：doctor 只说 ".NET SDK no SDK → Download missing tools"，人会以为没装 SDK，去下载一个 221 MB 的包。

**原因**：doctor 用 `subprocess.run([dotnet, '--list-sdks'])` 判断 SDK；程序起不来 → `OSError` 被吞掉 → 报成"没装"。

**改法**：App 的"检查工具"流程改成 **先改解释器路径、再跑 doctor**；doctor 报缺工具时，
先确认那个文件**能不能执行**（`ls -l` 看执行位 ✓）再下结论。

---

## 6. 空间检查排在 survey 之后 → 先把盘写满再报错

**症状**：19 GB 的游戏 + 8.7 GB 空闲 → 手机卡死、存储 99% → 最后才报"空间不足"。

**原因**：`inputs()` 里 `check_space()` 在 `experimental_profile()` 之后，而 survey 会把游戏**解包**出来（这一步最吃空间）。

**改法**：把 `self.check_space()` 提到 experimental profile 之前 ✓（PC 端同样受益 ✓）。

---

## 7. 游戏的"目录"还是"文件"：用户选文件，工具用目录

**症状**：App 里选游戏目录 → 目录里有两个游戏时不知道是哪个 → 报 "more than one game in the folder"。

**原因**：移植器的模型是"一个文件夹放一个游戏（本体 + 更新 + DLC）"，但**用户手上通常只有一个游戏文件** ✓。

**改法**：
- 选**文件**（`porter port <文件>`，PC GUI 与 App 都改成选文件 ✓）；
- 工具把文件的父目录当工作目录，**只取这个游戏本体**的更新和 DLC（同目录下的别的游戏忽略）✓
  （`nsp.scan_nsps(..., base=选中的文件)` ✓）；
- 目录仍然被接受（老用法不破）✓。

---

## 8. 改了移植器的代码，手机上还是老代码

**症状**：本地 `porter` 改了，手机上跑出来还是旧的报错。

**原因**：App 只在**第一次**解包时把 `tool/` 从 assets 拷进自己的目录（`.nx2a-tool` 标记文件 ✓），
之后再更新 APK 也不会重拷 ✓。

**改法**：开发循环 = 「重置环境」→「准备环境」（约 25 秒 ✓，rootfs 9 s + 工具 2 s + SDK 2 s）；
发布时把代码同步进 `packaging/android-apk/.cache/assets/tool/` ✓ 再打 APK ✓。

---

## 9. Android 16 / ZUI 平板的两个系统坑

- **`/sdcard` 读不到**：应用默认拿不到外部存储，需要 `MANAGE_EXTERNAL_STORAGE` + 运行时检查
  `Environment.isExternalStorageManager()` ✓。
- **没有 DocumentsUI**：`ACTION_OPEN_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE` 直接抛
  `ActivityNotFoundException` ✓ → App 里捕获后回退到"应用内文件列表" ✓。

---

## 10. NuGet 的配置名在 Linux 上大小写敏感 → restore 去连网，然后一直等

**症状**：手机上 `dotnet build` 停在 "Determining projects to restore..."，十几分钟不动。

**原因**：`nx_extract` 依赖 NuGet 包 **LibHac**（.NET 的 C# 项目要 restore 依赖）。我们把包和源都放进
手机（`packages/libhac.0.19.0.nupkg` + `NuGet.config`），但 **NuGet 在 Linux 上只认小写的 `nuget.config`**
（Windows 大小写不敏感，所以 `NuGet.config` 在 PC 上没问题 ✗）→ 配置没被读到 → 走默认源 nuget.org →
手机没有 DNS → 一直等 ✗。

**改法**：文件名用小写 `nuget.config` ✓；配置里 `<clear />` 再只留本地目录源 ✓，这样根本不会去连网。

---

## 11. .NET 9+ 的 invariant globalization 必须写在 runtimeconfig 里，否则 SDK 自己崩

**症状**：`dotnet` 命令直接 Aborted（FailFast），栈里有 `GlobalizationMode.Settings..cctor()`；
MSBuild 的 worker 节点一崩，主节点就永远等它握手（线程停在 `wait_for_partner`）→ 表现成"卡死" ✗。

**原因**：rootfs 里没有 ICU ✗ → .NET 只能用 invariant 模式 → 但 .NET 9 起，
**只设 `DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1` 会 FailFast**，必须在 runtimeconfig 里声明
`"System.Globalization.Invariant": true` ✓（SDK 自己的 36 个 runtimeconfig ✗）。

**改法**：`packaging/android-apk/.cache/assets/tool/tools/fix_globalization.py` 给 SDK 的每个 runtimeconfig
补上这个声明 ✓，App 解包完就自动跑一次 ✓。

**顺带**：手机上的 .NET SDK 仍然编不了 C#（restore 要网 ✗）→ 所以 `nx_extract` 改成**在电脑上交叉编译
linux-arm64 版随 APK 打包**（`build_nx_extract.py` ✓），手机上直接跑（`NX2A_NX_EXTRACT` ✓），
一分钟的编译变成零秒 ✓。

---

## 12. Android 不允许在共享存储上执行程序 → build 目录必须放在 App 自己的目录里

**症状**：预编译的 `nx_extract` 复制到游戏旁边的 `<游戏目录>/<title>_build/tools/` 后，
`PermissionError: [Errno 13] Permission denied`（执行被拒 ✗）。

**原因**：SELinux 不允许 App 在 media 分区（`/sdcard` ✗）上 exec ✗；游戏的 build 目录正好在 SD 卡上 ✗。

**改法**：build 目录改到 App 自己的存储（`NX2A_WORK` ✓ → `files/work/<title>_build` ✓），
产物（APK 和数据包）仍然写到 `-o` 指的地方 ✓；空间检查也跟着改成看 build 目录所在的盘 ✓。
（注意：`check_space` 里 `shutil.disk_usage(self.work)` 在目录还没建时会抛 FileNotFoundError ✗，
要取最近的已存在的祖先目录 ✓。）

---

## 13. LibHac 遇到非 16 对齐的偏移就直接抛异常 → 改版 dump 读不了

**症状**：`nx_extract` 报 `System.ArgumentException: Offset must be a multiple of 16`
（`SectorStorage.ValidateSize` ✓），解包在 RomFS 那一步停住 ✓。

**原因（2026-11-05 查明，之前判断错了）**：**不是 dump 坏了，是工具挑错了 NCA** ✓。
重打包的卡带 dump（"super XCI"）把**本体和更新合并进了同一个包**：里面有两套完整 NCA ——
本体那套是 **卡带分布**（GameCard），更新那套是**下载分布**（Download），两套的 program NCA
**title ID 完全相同**（更新的 NCA 头里带的是本体的 ID）✓。

`nx_extract` 原来用"title ID 最小"挑 program NCA，两个 ID 一样时保留了**先遇到**的那个 ——
正好是那个 24.9 MB 的**更新** NCA ✓。更新的数据段是 BKTR **补丁**，不是 RomFS ✓，
当本体读就是一堆垃圾偏移 → `Offset must be a multiple of 16` ✓。

**改法**（`dm/tools/nx_extract/Program.cs`）：
- `PickGame`：**卡带分布**的 program NCA 才是本体（NSP 没有卡带 NCA，回落到原来那套）✓；
- `PickUpdate`：同包里**下载分布**、title ID 相同的那个 program NCA 才是更新 ✓；
- `PickControl`：控制数据按**分布**取 —— 属于更新的那份才带着更新版的版本号 ✓
  （本体卡带那份是 1.0.0，更新那份是 1.0.1 ✓）。
- 合并读取用 `OpenStorageWithPatch`（本体 + 更新补丁）✓：本体 3159 个文件 → 合并后 3162 个
  （更新加了 3 个 bgyml/eui 参数，改了 1 个 rsizetable）✓。

**porter 侧**（`scripts/build_ipa_from_nsp.py`）：`scan_nsps` 看一个 dump 里的 cnmt 有没有
`…800` 的那个（更新的 title ID）✓ —— 有就说明更新在包里，它**只算一个版本** ✓，
版本号取文件名的 `[v<N>]` 标记（profile 注册了才用）✓，否则取 profile 注册的最新更新 ✓
（提取后按 build ID 核对）✓。没有这一段时，porter 会把它当成 v0 本体，走"派生 profile"
那条路（重建全部 shader 表）✓。

**教训**：报错像"文件坏了"时，先把包里的 NCA 逐个列出来看（分布类型、内容类型、title ID、
FS 头）✓ —— 同一个 title ID 出现两次、而其中一份是补丁，就是合并更新的 dump ✓。

**另外**：`--allow-damaged` 是给**哈希校验失败**用的 ✓，跟这个无关 ✓。

**判断文件坏没坏的通用办法**：把文件拉到电脑上，用同一套 LibHac 跑一遍 ✓ → 两边一样报错就是文件的问题 ✓
（但先按上面那条把 NCA 逐个看清楚 ✓：报的错可能来自工具挑错了文件 ✓）。

---

## 14. 日志里全是原始 JSON，看不出在干什么

**症状**：进度条不动、日志刷 JSON，用户不知道程序在忙什么。

**改法**：App 把 `--progress json` 的事件解析掉 ✓：
`stage` → 状态栏显示"阶段 k/n：名字"✓；`progress` → 进度条 + 状态栏显示当前小步骤 ✓；
`say` → 日志 ✓；原始 JSON 不进日志 ✓。再加：
- 每秒刷新一次状态栏（经过时间 ✓）；
- 日志区固定高度、自己滚动 ✓；
- **「取消移植」按钮**（把 guest 进程和它起的 dotnet / clang 一起杀掉 ✓，否则它们会继续占着 CPU ✓）。

---

## 15. 一个语法错误就让**所有** Android host 编译失败（2026-11-05）

**症状**：`porter port` 走到第 6 步"host"就停：

```
[6/8] host · NDK 27.2.12479018 …
✗ [6/8] host (0:00)
  File "nxnative/dm/android/build.py", line 37
    _res = next(iter(sorted(...))))), None)
SyntaxError: unmatched ')'
```

**原因**：Python 的语法错误是**解析期**的，跟那行代码在不分支里跑无关 ✓ ——
只要文件里有一处括号不配对，整个文件就 import 不了 ✓，**任何机器上**的 Android host 编译都挂 ✓。
这一行是 `next(iter(...))` 的 `, None)` 写到了括号外面（2026-11-04 的 `2534756` 带入 ✓）。

**改法**：一行修好 ✓。**教训**：改完 `dm/android/` 或 `dm/tools/` 下的 Python，
先 `python3 -m py_compile <file>` ✓ 再跑移植（一步就编译，比跑到第 6 步才发现快得多 ✓）。

---

## 16. 改了"链工具"却不更新链版本头 → 仓库自带测试失败，Metal host 找不到库（2026-11-05）

**症状**：`pytest nxnative/dm/tools/tests` 里 `test_nsp_chain.py` 挂 4 条 ✓；
报错是 `dm/gfx/nvn/dmsh_chain.h is stale` ✓。

**原因**：shader 翻译链有一个版本号 V（`dmsh_chain.py` 算出来）✓，它由链上的那些文件
（`dmsh_*.py`、`dmsh_xlate/Program.cs`、`ios_metal.py`、`dmsh_chain.lock`）的内容哈希出来 ✓。
改了链上的文件却不跑 `dmsh_chain.py --header`，头里就还是旧的 V ✓（`v0.3.0` 那次提交就是这样 ✓）：
头写着 `07a4fa82de4a`，工具算出来是 `f6f2dc1d1903` ✓。

**后果**：Metal/iOS 的 host 只加载 `b-<V>/` 那个目录 ✓ —— 头是旧 V、库是按新 V 建的 ✓，
host 就找不到库（日志里 `[RED] shader library directory .../b-07a4fa82de4a does not exist` ✓）✓。
Android/Vulkan 这条路不打这个头（shader 打包成 `shaders.dmsv`）✓，所以手机端看不出来 ✓。

**改法**：`python3 nxnative/dm/gfx/tools/dmsh_chain.py --header` ✓ 然后把
`dm/gfx/nvn/dmsh_chain.h` 跟改动一起提交 ✓（`AGENTS.md` 的第 1 条 ✓）。
**代价**：V 变了 → 所有游戏的 shader 库要重建（每个游戏一次全量 `dmsh_build.py`）✓。

---

## 17. 打包顺序：资产会被清空，预编译的工具和它的权限都会丢（2026-11-05）

**症状**：手机上 `nx_extract` 跑不起来（"Permission denied" ✓）或根本没有这个工具 ✓；
APK 里 `assets/tool/tools/nx_extract/` 是空的 ✓。

**原因**：`build_assets.py` 会 `rmtree` 整个资产目录再重建 ✓ → 预编译给手机的
linux-arm64 `nx_extract`（`build_nx_extract.py` 写进同一个目录）✓ 会被一起删掉 ✓。
而且 APK 的 assets **不带权限位** ✓：权限写在 `modes.json` 里 ✓，
不在表里的文件在手机上落成 0644 ✓ → 那个可执行文件不可执行 ✓（坑 2 ✓）。

**改法**：
- `build_apk.sh` 在**资产之后**才编 `nx_extract` ✓；
- `build_nx_extract.py` 把自己那些文件的权限写进 `modes.json` ✓（`nx_extract` 是 0755 ✓）；
- 资产里跳过 `bin/`、`obj/`（本地编译残留，不该进手机 ✓）。

---

## 快速排查清单（手机上出问题时按这个顺序看）

```bash
# 1) guest 能不能跑
adb shell "run-as com.nx.android sh -c 'F=/data/data/com.nx.android/files; \
  LD_LIBRARY_PATH=$F/rootfs/usr/lib/aarch64-linux-gnu:$F/rootfs/usr/lib PYTHONHOME=$F/rootfs/usr \
  $F/rootfs/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1 $F/rootfs/usr/bin/python3.13 -c \"print(1)\"'"

# 2) 关键文件的权限（没有执行位就是第 2/3 条）
adb shell "run-as com.nx.android ls -l /data/data/com.nx.android/files/nx2a/toolchains/dotnet/dotnet \
  /data/data/com.nx.android/files/tool/toolchain/clang/bin/clang"

# 3) 解释器路径对不对（应该指向 app 自己的 rootfs）
adb shell "run-as com.nx.android sh -c 'strings ...'"   # 或用 probe.py 读 PT_INTERP

# 4) 磁盘
adb shell df -h /data

# 5) App 自己的日志
adb logcat -s Nx2aEnv

# 6) App 的日志文件（出错后直接拉走看）
adb shell "run-as com.nx.android cat /data/data/com.nx.android/files/nx2a/log.txt"
```

**判断是文件的问题还是工具的问题**：把游戏文件拉到电脑上，用同一套 LibHac 跑一遍
（`dotnet publish -r osx-arm64` 交叉编译 `nx_extract` ✓）→ 两边一样报错就是文件本身的问题 ✓。

---

## 18. 手机没有 /dev/shm → `multiprocessing` 的进程池起不来（2026-11-06）

**症状**：第 4 阶段（shader split）报
`FileNotFoundError: [Errno 2] No such file or directory`，栈在
`multiprocessing/synchronize.py` 的 `SemLock.__init__` ✓。

**原因**：**Android 根本没有 /dev/shm** ✓（`ls /dev/shm` → No such file ✓），
而 POSIX 信号量写死要它 ✓。`ProcessPoolExecutor` 建进程池时先建一把锁 → 失败 ✓。

**改法**：`dm/gfx/tools/shader_split.py` 的 `pool_map()` 先试进程池 ✓，
起不来就在本进程里串行做同样的活 ✓（打一行 note ✓）。
慢，但能过 ✓（这个游戏的 12,691 个 shader 串行也没多久 ✓）。

---

## 19. 手机的 .NET SDK 读 NuGet 设置会永远卡住 → C# 的工具只能随包预编译（2026-11-06）

**症状**：第 5 阶段（Vulkan shaders）在手机上编 shader 翻译器时，
`dotnet build` 挂 5 分钟被杀，日志只有 `Build FAILED. 0 Warning(s) 0 Error(s)` ✓；
手动跑 `dotnet restore` 停在 `Determining projects to restore...` ✓，
`dotnet nuget locals all --list` 也挂 ✓（线程停在 `wait_for_partner` ✓）。

**原因**：**手机的 .NET SDK 读自己的 NuGet 设置就卡住** ✓（空项目也一样 ✓，
所以与工程、包、网络都无关 ✓ —— 连显式 `--source` 给本地目录也照挂 ✓）。
加上手机**还原 NuGet 包需要网络**（`nuget.org` 被重定向到国内镜像 ✓，
而 repo 自己的坑 11 就写着"手机的 .NET SDK 编不了 C#" ✓）。

**改法**：**翻译器跟 nx_extract 一样，随 APK 预编译** ✓
（`packaging/android-apk/build_nx_extract.py` 现在也编 `dmsh_xlate` ✓，
放到资产的 `tool/tools/dmsh_xlate/` ✓；
`porter/pipeline.py` 的 `translator()` 优先用它 ✓：`NX2A_DMSH_XLATE` 或工具树里的那份 ✓）。

---

## 20. `fix_globalization.py` 不在仓库里 → SDK 的 runtimeconfig 没打补丁（2026-11-06）

**症状**：`dotnet` 在手机上 `Aborted`（FailFast）✓；
`.NET 9+` 只设环境变量不够，**每个 runtimeconfig 都要声明** ✓。

**原因**：`Env.kt` 找的是 `tool/tools/fix_globalization.py` ✓，
但**这个脚本在仓库里根本没有** ✓（它只在 `.cache` 里 ✓，
而 `.cache` 重建时就没了 ✓）→ App 的 `fixGlobalization()` 直接跳过 ✓ → SDK 的 36 个
runtimeconfig 全是默认值 ✓ → MSBuild 的 worker 一崩就"0 errors"✓。

**改法**：把脚本写进仓库（`packaging/android-apk/fix_globalization.py` ✓），
`build_assets.py` 把它和 `patch_interp.py` 一起放进资产的 `tool/tools/` ✓。
手机上直接跑一次（36 个 runtimeconfig ✓）。

---

## 21. 工具链自己的库不在 `LD_LIBRARY_PATH` 里 → glslc 起不来（2026-11-06）

**症状**：第 6 阶段（host）报
`glslc ... returned non-zero exit status 127` ✓；
手动跑报 `error while loading shared libraries: libshaderc.so.1: cannot open shared object file` ✓。

**原因**：glslc 的 RPATH 指向**别的 App** 的目录（`/data/data/ro.cobrabm.fexdroid/files/...` ✓，
那是工具链当初打包时用的路径 ✓），loader 一路试到最后去试设备的 `/lib` ✗（App 读不到 ✓）→ EACCES ✓。

**改法**：`porter/pipeline.py` 给工具的运行环境加上工具链自己的 `lib` 目录 ✓
（`$NX2A_REPO/toolchain/lib` ✓，排在 `LD_LIBRARY_PATH` 最前 ✓）。

---

## 22. 编译器包装脚本的 `#!/bin/sh` 在手机上指向 Android 的 shell（2026-11-06）

**症状**：第 6 阶段每个源文件都失败：
`CANNOT LINK EXECUTABLE "/bin/sh": ".../rootfs/usr/lib/aarch64-linux-gnu/libc.so" has bad ELF magic` ✓。

**原因**：`dm/android/build.py` 在 ARM64 主机（手机 ✓）上把编译器包成一个
`#!/bin/sh` 的脚本 ✓ —— 手机的 `/bin/sh` 是 **Android 的 shell** ✓，
它去加载 `LD_LIBRARY_PATH` 里的 **rootfs 的 libc** ✗ → "bad ELF magic" ✓
（这条报错看着像链接器坏了，其实是那个 shell 起不来 ✓）。

**改法**：不写包装脚本，**把 `--sysroot` / `-resource-dir` / `-rtlib` 直接放在命令行上** ✓
（`CC_EXTRA` ✓，编译和链接都用 ✓）。

---

## 23. 给安装器递 APK 的 provider：路径只取最后一段、而且没导出（2026-11-06）

**症状**：App 的「安装 APK」点了之后，系统安装器说
"解析软件包时出现问题" ✓（APK 本身是好的 ✓，aapt2 能解析 ✓）。

**原因**：`ApkProvider` 用 `uri.lastPathSegment` 取路径 ✓ →
只剩文件名、目录丢了 ✓ → 打开的是错文件 ✓ → 安装器读到空 ✓。
而且 provider 是 `exported="false"` ✓ → 安装器（另一个 uid ✓）根本读不到 ✓。

**改法**：用 `uri.path`（整条路径 ✓）；provider 导出 ✓，
但**只发 App 自己刚做的那个 APK** ✓（静态字段记着那个路径 ✓，别的路径一律拒绝 ✓）——
导出一个能读任意路径的 provider 就成了漏洞 ✓。

---

## 24. rootfs 是整个 Debian 用户态：706 MB 里九成没人加载（2026-11-06）

**症状**：APK 905 MB ✓；其中 `rootfs.pkg` 一个就 218 MB ✓ —— 里面是完整的
Debian 13 ARM64 用户态 ✓：mesa 的软件渲染和它的 LLVM（154 MB ✓）、z3 ✓、
ffmpeg ✓、Vulkan 的软件驱动 ✓、X11 ✓、perl ✓、locale ✓、图标 ✓。

**原因**：nixport 的 payload 里的 rootfs 是"给各种程序用的完整环境" ✓，
但移植器真正跑的只有 **Python 3.13** ✓、**bash** ✓ 和它们需要的库 ✓
（外加工具链和 C# 工具 ✓ —— 它们的库也在 rootfs 里 ✓，`LD_LIBRARY_PATH`
指的就是 rootfs 的库目录 ✓）。

**改法**：`packaging/android-apk/rootfs_trim.py` ✓ —— 按 ELF 的
`.dynamic` 段算出**闭包** ✓（`DT_NEEDED` 递归 ✓，含 Python 的
`lib-dynload` 扩展 ✓ 和它们的链接目标 ✓），只留闭包 ✓、Python 标准库 ✓、
一批 coreutils ✓ 和少数数据文件 ✓；构建时校验闭包完整 ✓（缺一个库就
**不让构建通过** ✓）。rootfs.pkg 218 MB → **24 MB** ✓，
设备上 726 MB → **72 MB** ✓。

**顺带**：软件渲染、X11、Tk 这些一去，Debian 的 `libtinfo`/`libncurses`
也就不再把 X11 那条链拖进来了 ✓。

---

## 25. 增量打包会在 APK 里留下 192 MB 的死空间（2026-11-06）

**症状**：APK 里所有条目压缩后合计只有 455 MB ✓，文件却有 648 MB ✓。

**原因**：gradle 的打包器（zipflinger ✓）**复用旧的 APK 文件** ✓：
根目录换小之后（rootfs.pkg 218 MB → 23 MB ✓），省出的空间没有被真正
腾掉 ✓，而是被写成**空条目标记**（`PK\x03\x04` 的空头 ✓）盖住 ✓ ——
安装时会跳过 ✓，但**下载和设备上的空间照占** ✓。

**改法**：**干净重打**（`./gradlew clean :app:assembleDebug` ✓）✓
→ 456 MB ✓。改资产之后（rootfs ✓、工具 ✓、dotnet ✓）都要 clean 一次 ✓。

---

## 26. 只跑 `build_assets.py` 会把随包的 C# 工具弄丢（2026-11-06）

**症状**：手机上点「移植」，第 5 步（Vulkan shaders ✓）卡住 5 分钟 ✓，
然后 `Build FAILED. 0 Error(s)` ✓；日志里只有 6 行 ✓（`-v quiet` ✓）。

**原因**：`build_assets.py` 开头会**清空**输出目录 ✓，而
`nx_extract` ✓ 和 **dmsh_xlate** ✓（shader 翻译器 ✓）是
`build_nx_extract.py` 产的 ✓（它需要 .NET SDK ✓）。只跑前者 ✓ →
两个工具都没了 ✓ → 手机只好**自己编译翻译器** ✓ → 还原
Humanizer/MsgPack.Cli/Gommon 那四个 NuGet 包要**联网** ✓ →
手机没网 ✓ → 重试到超时 ✓。手机的 .NET SDK 本来就编不了它们 ✓
（坑 19 ✓：读 NuGet 设置会永远卡住 ✓）。

**改法**：
- `build_apk.sh` 在工具那步之后**校验**两个工具都在 ✓，缺了就 `die` ✓
  （不再 `|| echo` 把失败吞掉 ✓）
- pipeline 在手机上发现预编译翻译器缺失且**既没有网也没有缓存**时
  **立刻报错** ✓（不再等 5 分钟 ✓）
- App **每次启动**都重拷 `tool/tools` ✓ 和 `tool/porter` ✓
  （App 更新后工具树不刷新 ✓ 这条也修了 ✓）

---

## 27. 游戏 App 没有存储权限时，共享存储连 `stat()` 都静默拒绝（2026-11-06）

**症状**：数据包明明在 `/sdcard/Switch/Ports/<名字>-data` ✓，游戏却读了
`Android/data/<包名>/files` 里的**旧包** ✓（日志第一行 ✓）—— 不报错 ✓，
只是悄悄用了旧的 ✓（改了 shader/env 也看不出来 ✓）。

**原因**：游戏的清单虽然申请了 `MANAGE_EXTERNAL_STORAGE` ✓，但**玩家还没授权** ✓；
没有它的话，共享存储对普通 App 是不透明的 ✓ —— 连 `stat()` 都会被拒绝 ✓，
路径检查失败后代码就回落到旧布局 ✓。

**改法**：**权限先要，路径后查** ✓ —— APK 里带着烤入路径的游戏 ✓，
启动时先检查“所有文件访问” ✓，没有就打开系统授权页 ✓（玩家授权后重开 ✓），
然后才去查那个路径 ✓。没有烤入路径的老游戏 ✓ 走旧布局 ✓，不受影响 ✓，
也不会去要权限 ✓。
