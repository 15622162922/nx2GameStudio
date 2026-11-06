# 手机 APK 的体积：现状、别人的做法和还能省什么（2026-11-06）

## 1. 现在多大

| | 之前 | 现在 |
|---|---|---|
| APK（debug ✓）| 905 MB | **456 MB** ✓ |
| `rootfs.pkg` | 218 MB | **24 MB** ✓（按 ELF 闭包裁剪 ✓）|
| 设备上的 rootfs | 726 MB | **72 MB** ✓ |

**两条已落地的** ✓：
- **rootfs 裁剪** ✓（坑 24 ✓）：payload 里的 rootfs 是完整 Debian 用户态 ✓，
  实际只用 Python/bash 和它们的库 ✓ → 按 `.dynamic` 的 `DT_NEEDED` 算闭包 ✓
  → `rootfs_trim.py` ✓ → 218 MB → 24 MB ✓
- **增量打包的死空间** ✓（坑 25 ✓）：zipflinger 复用旧 APK ✓，
  旧 rootfs 让出的 ~195 MB 被写成空条目标记留着 ✓ → **改完资产必须
  `gradlew clean` 重打** ✓ → 648 MB → 456 MB ✓

## 2. 456 MB 里是什么

APK 是 ZIP ✓，条目用 **DEFLATE（zlib ✓）**；`.so` 按 `noCompress` **不压缩** ✓。

| 部件 | 内容 | APK 里实际 | 压缩率 |
|---|---|---|---|
| toolchain | 285 M | **102 M** | 37% |
| tool（游戏档案 + pylibs）| 231 M | **92 M** | 40% |
| dotnet.pkg（.NET SDK）| 222 M | **221 M** | 100%（本身就是 gzip ✓）|
| dex（App 代码，debug 未混淆）| 58 M | **17 M** | 30% |
| rootfs.pkg | 23 M | 23 M | 99%（同上 ✓）|

## 3. NDK 部分（113 M 解包）

| 部分 | 大小 | 必需？|
|---|---|---|
| `sysroot/usr/include`（C/C++ 头 ✓）| 22.4 M | ✅ |
| `lib/clang/18/include`（clang 内建头 ✓）| 14.7 M | ✅ 我们的 `-resource-dir` 指这里 ✓ |
| `sysroot/usr/lib/aarch64-linux-android/31`（链接桩库 ✓）| 1 M | ✅（**只留 API 31** ✓）|
| `lib/clang/18/lib/linux/aarch64`（compiler-rt ✓）| 34.6 M | ✅ |
| `lib/clang/18/lib/linux/*.a`（**tsan/asan/fuzzer/hwasan/lsan/ubsan** ✓）| **~55 M** | ❌ 用不到 ✓ |

其余：`clang/bin` 165 M（clang 104 + ld.lld 63 + llvm-strip 6 ✓，**全 target** 的编译器 ✓）、
`lib` 8 M（libshaderc ✓）、`bin` 196 K（glslc ✓）。

`build_toolchain.py` 的 `keep()` 已经裁掉其他 ABI ✓（`'aarch64' in rest` ✓），
但**没区分 sanitizer** ✓ —— 那 55 MB 谁都带着 ✓。

## 4. 别人怎么裁的（A2redweb/nx2android-android-port，release "tools"）

那个仓库是**另一套平行实现** ✓（Java App ✓ + proot 跑完整发行版 ✓ + WebGUI ✓），
不是我们这条管线的分支 ✓。他们的 `tools/make_ndk_lite.sh`：

```bash
unzip -q ndk.zip "$N/source.properties" "$P/sysroot/*" "$P/lib/clang/*/lib/*"
for a in arm-linux-androideabi i686-linux-android x86_64-linux-android riscv64-linux-android; do
    rm -rf "$P/sysroot/usr/lib/$a" "$P/sysroot/usr/include/$a"; done
find "$P/lib/clang" -type f ! -path '*aarch64*' -delete
XZ_OPT='-9e -T0' tar -cJf ndk-lite-r27c.tar.xz "$N"
```

发布件 `ndk-lite-r27c.tar.xz` = **19.4 MB** ✓。

**逐项对比** ✓：

| | 我们的 NDK（113 M 解包 ✓）| 他们的 ndk-lite（19.4 M xz ✓）|
|---|---|---|
| 其他 ABI | 删了 ✓ | 删了 ✓ |
| sysroot 的 API 级别 | **只留 31** ✓ | **全留** ✗（多 ~15 M ✓）|
| clang 内建头 `include/` | **有 ✓**（14.7 M ✓）| **没有** ✗ —— 单独用它喂不了我们的 host 构建 ✓ |
| compiler-rt | aarch64 全留 ✓（含 sanitizer ✓）| 同样含 sanitizer ✓ |
| 打包 | 逐文件 DEFLATE ✓（APK 里约 41 M ✓）| **xz -9 单包 ✓** |
| 解包 | Java（`GZIPInputStream` ✓ + 自写 tar ✓）| 发行版里的 tar ✓（他们有 proot+发行版 ✓）|

**结论** ✓：他们“小”靠的是 **xz -9 单包** ✓，裁剪深度其实**比我们浅** ✓
（API 级别没删 ✓、缺 clang 内建头 ✗）；**两边都没删 sanitizer** ✓。

## 5. 以后要做的（按性价比）

| | 做什么 | APK 减 | 代价 |
|---|---|---|---|
| **A** | `build_toolchain.py` 的 `keep()` 再筛一层：**只留 `builtins` 和 `cxx`/`c++` 那些** ✓，删掉 tsan/asan/fuzzer/hwasan/lsan/ubsan ✓ | **~-20 M** ✓ | 半小时 ✓，零风险 ✓ |
| **B** | NDK 部分（做完 A ✓）打成**单个 xz** ✓ | 再 -10~15 M ✓ | App 要能解 xz ✓（rootfs 自带 xz 二进制 ✓，首次解包后可用 ✓；rootfs.pkg 保持 gzip 先解 ✓ 就没有鸡生蛋问题 ✓）|
| **C** | `dotnet.pkg`（222 M ✓）换 xz ✓ | **~-80 M** ✓ | 同样要 xz 解包 ✓ + 验手机上的 dotnet 仍能跑 ✓ |
| **D** | 自建 aarch64-only 的 clang+lld ✓（165 M → ~85 M ✓）| -50 M ✓ | 编一套工具链 ✓，工程量大 ✗ |

**先做 A** ✓（便宜、零风险 ✓）；B/C 要动 App 的解包逻辑 ✓，等 A 验证过再说 ✓；
D 不值得 ✗。

**别忘了**：动完资产一定要 `gradlew clean` 再打 ✓（坑 25 ✓），
否则 zipflinger 留死空间 ✓。

## 相关

- `my-docs/phone-app-pitfalls.md` 坑 24（rootfs 是整个 Debian ✓）、坑 25（死空间 ✓）、
  坑 26（只跑 build_assets.py 会丢预编译的 C# 工具 ✓）
- `my-docs/phone-port-notes.md`：各阶段耗时 ✓、数据包该放哪 ✓
