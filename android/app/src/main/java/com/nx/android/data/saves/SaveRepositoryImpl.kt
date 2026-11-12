package com.nx.android.data.saves

import com.nx.android.core.di.IoDispatcher
import com.nx.android.data.SaveBackup
import com.nx.android.domain.saves.SaveOperationException
import com.nx.android.domain.saves.SaveRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class SaveRepositoryImpl @Inject constructor(
    private val platform: SavePlatform,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val saveSwap: SaveSwap,
) : SaveRepository {
    private val locks = ConcurrentHashMap<String, Mutex>()

    override fun packagesDirectory(): File = platform.packagesDirectory()

    override fun titleIdInPackage(name: String): String? = SaveBackup.titleIdIn(name)

    override suspend fun listPackages(): List<File> = withContext(io) {
        packagesDirectory().listFiles()?.filter { it.isFile && it.extension.equals(SaveBackup.EXT, true) }
            ?.sortedByDescending { it.name }.orEmpty()
    }

    override suspend fun listBackups(packSaves: File): List<File> = withContext(io) {
        packSaves.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && !it.name.endsWith(".device") }
            ?.sortedByDescending { it.name }.orEmpty()
    }

    override suspend fun treeSize(file: File): Long = withContext(io) {
        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
    }

    override suspend fun export(titleId: String, gameName: String, packSaves: File): Result<File> = runResult {
        withTitleLock(titleId) {
            checkTitle(titleId)
            checkGameClosed()
            val live = SaveBackup.liveDir(platform.filesDir, titleId)
            val device = SaveBackup.deviceDir(platform.filesDir, titleId)
            val dest = File(packagesDirectory(), SaveBackup.packageName(gameName, titleId, stamp()))
            SaveBackup.export(live, device, dest)
                ?: throw SaveOperationException(SaveOperationException.Reason.NO_SAVES)
        }
    }

    override suspend fun import(titleId: String, packageFile: File, packSaves: File): Result<String?> = runResult {
        withTitleLock(titleId) {
            checkTitle(titleId)
            checkGameClosed()
            checkPackageIdentity(titleId, packageFile)
            val tmp = File(platform.cacheDir, "save-import-${titleId.lowercase()}")
            withContext(io) {
                tmp.deleteRecursively()
                try {
                    val unpacked = SaveBackup.unpack(packageFile, tmp)
                    checkGameClosed()
                    val backup = SaveBackup.backup(
                        SaveBackup.liveDir(platform.filesDir, titleId),
                        SaveBackup.deviceDir(platform.filesDir, titleId), packSaves, stamp(),
                    )
                    withContext(NonCancellable) {
                        saveSwap.install(unpacked.tree, unpacked.device,
                            SaveBackup.liveDir(platform.filesDir, titleId),
                            SaveBackup.deviceDir(platform.filesDir, titleId))
                    }
                    backup?.name
                } finally {
                    withContext(NonCancellable) { tmp.deleteRecursively() }
                }
            }
        }
    }

    override suspend fun restore(titleId: String, backup: File, packSaves: File): Result<String?> = runResult {
        withTitleLock(titleId) {
            checkTitle(titleId)
            checkGameClosed()
            checkBackupLocation(backup, packSaves)
            withContext(io) {
                val live = SaveBackup.liveDir(platform.filesDir, titleId)
                val device = SaveBackup.deviceDir(platform.filesDir, titleId)
                val kept = SaveBackup.backup(live, device, packSaves, stamp())
                val sourceDevice = File(backup.parentFile, backup.name + ".device").takeIf { it.isDirectory }
                checkGameClosed()
                withContext(NonCancellable) { saveSwap.install(backup, sourceDevice, live, device) }
                kept?.name
            }
        }
    }

    override suspend fun prepareBackupShare(
        titleId: String,
        gameName: String,
        backup: File,
        packSaves: File,
    ): Result<File> = runResult {
        withTitleLock(titleId) {
            checkTitle(titleId)
            checkBackupLocation(backup, packSaves)
            withContext(io) {
                val dir = packagesDirectory()
                val name = SaveBackup.packageName(gameName, titleId, stamp())
                val dest = SaveBackup.freeName(File(dir, name))
                val device = File(backup.parentFile, backup.name + ".device").takeIf { it.isDirectory }
                SaveBackup.writePackage(backup, device, dest.name.removeSuffix(".${SaveBackup.EXT}"), dest)
                dest
            }
        }
    }

    override suspend fun delete(file: File, packSaves: File): Boolean = withContext(io) {
        val canonical = file.canonicalFile
        val packageDir = packagesDirectory().canonicalFile
        val backupDir = packSaves.canonicalFile
        val isPackage = canonical.parentFile == packageDir && canonical.isFile && canonical.extension == SaveBackup.EXT
        val isBackup = canonical.parentFile == backupDir && canonical.isDirectory
        if (!isPackage && !isBackup) return@withContext false
        val device = File(canonical.parentFile, canonical.name + ".device")
        val removed = if (canonical.isDirectory) canonical.deleteRecursively() else canonical.delete()
        if (device.isDirectory) device.deleteRecursively()
        removed
    }

    private suspend fun <T> withTitleLock(titleId: String, block: suspend () -> T): T =
        locks.computeIfAbsent(titleId) { Mutex() }.withLock { block() }

    private fun checkTitle(titleId: String) {
        if (!TITLE_ID.matches(titleId)) throw SaveOperationException(SaveOperationException.Reason.INVALID_TITLE_ID)
    }

    private fun checkGameClosed() {
        if (platform.isGameRunning()) throw SaveOperationException(SaveOperationException.Reason.GAME_RUNNING)
    }

    private fun checkPackageIdentity(titleId: String, packageFile: File) {
        val packageTitleId = SaveBackup.titleIdIn(packageFile.name)
        if (packageTitleId != null && !packageTitleId.equals(titleId, ignoreCase = true)) {
            throw SaveOperationException(SaveOperationException.Reason.TITLE_MISMATCH, packageTitleId)
        }
    }

    private fun checkBackupLocation(backup: File, packSaves: File) {
        val parent = packSaves.canonicalFile
        if (backup.canonicalFile.parentFile != parent || !backup.isDirectory || backup.name.startsWith(".")) {
            throw SaveOperationException(SaveOperationException.Reason.INVALID_BACKUP)
        }
    }

    private suspend fun <T> runResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private companion object {
        val TITLE_ID = Regex("[0-9A-Fa-f]{16}")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SaveDataModule {
    @Binds
    @Singleton
    abstract fun bindSaveRepository(impl: SaveRepositoryImpl): SaveRepository
}
