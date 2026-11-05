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

## 10. 日志里全是原始 JSON，看不出在干什么

**症状**：进度条不动、日志刷 JSON，用户不知道程序在忙什么。

**改法**：App 把 `--progress json` 的事件解析掉 ✓：
`stage` → 状态栏显示"阶段 k/n：名字"✓；`progress` → 进度条 + 状态栏显示当前小步骤 ✓；
`say` → 日志 ✓；原始 JSON 不进日志 ✓。再加：
- 每秒刷新一次状态栏（经过时间 ✓）；
- 日志区固定高度、自己滚动 ✓；
- **「取消移植」按钮**（把 guest 进程和它起的 dotnet / clang 一起杀掉 ✓，否则它们会继续占着 CPU ✓）。

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
```
