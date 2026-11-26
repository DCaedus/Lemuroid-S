/*
 *     Copyright (C) 2021  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "vfs.h"

#include <unistd.h>
#include <sys/stat.h>
#include <optional>

#include "vfs/vfs_implementation.h"
#include "saf/vfs_implementation_saf.h"
#include <cstring>
#include <pthread.h>
#include "../log.h"
#include "../utils/utils.h"

namespace libretrodroid {

const char *VFS::path(struct retro_vfs_file_handle* stream) {
    LOGV("VFS Calling path");
    if (VFS::getInstance().isVirtual(stream)) return stream->orig_path;
    return retro_vfs_file_get_path_impl(stream);
}

struct retro_vfs_file_handle* VFS::open(const char* path, unsigned int mode, unsigned int hints) {
    LOGV("VFS Calling open: %s %i", path, mode);
    auto result = VFS::getInstance().virtualOpen(path, mode, hints);
    if (result != nullptr) {
        return result;
    }

    if (isSafPath(path)) {
        return VFS::getInstance().safOpen(path, mode, hints);
    }

    return retro_vfs_file_open_impl(path, mode, hints);
}

int VFS::close(struct retro_vfs_file_handle *stream) {
    LOGV("VFS Calling close");
    VFS& vfs = VFS::getInstance();
    {
        std::lock_guard<std::mutex> lock(vfs.handlesLock);
        auto it = vfs.virtualHandles.find(stream);
        if (it != vfs.virtualHandles.end()) {
            ::close(it->second.fd);
            vfs.virtualHandles.erase(it);
            free(const_cast<char*>(stream->orig_path));
            delete stream;
            return 0;
        }
    }
    return retro_vfs_file_close_impl(stream);
}

int64_t VFS::size(struct retro_vfs_file_handle *stream) {
    LOGV("VFS Calling size");
    VirtualHandle handle {};
    if (VFS::getInstance().isVirtual(stream, &handle)) return handle.size;
    return retro_vfs_file_size_impl(stream);
}

int64_t VFS::tell(struct retro_vfs_file_handle *stream) {
    LOGV("VFS Calling tell");
    VirtualHandle handle {};
    if (VFS::getInstance().isVirtual(stream, &handle)) return handle.position;
    return retro_vfs_file_tell_impl(stream);
}

int64_t VFS::seek(struct retro_vfs_file_handle *stream, int64_t offset, int seek_position) {
    LOGV("VFS Calling seek");
    VFS& vfs = VFS::getInstance();
    {
        std::lock_guard<std::mutex> lock(vfs.handlesLock);
        auto it = vfs.virtualHandles.find(stream);
        if (it != vfs.virtualHandles.end()) {
            int64_t base = 0;
            if (seek_position == RETRO_VFS_SEEK_POSITION_CURRENT) base = it->second.position;
            else if (seek_position == RETRO_VFS_SEEK_POSITION_END) base = it->second.size;
            int64_t target = base + offset;
            if (target < 0) return -1;
            it->second.position = target;
            // Like libretro's reference VFS (fseek semantics): 0 on success, not the new position.
            return 0;
        }
    }
    return retro_vfs_file_seek_impl(stream, offset, seek_position);
}

int64_t VFS::read(struct retro_vfs_file_handle *stream, void *s, uint64_t len) {
    LOGV("VFS Calling read");
    VFS& vfs = VFS::getInstance();
    VirtualHandle handle {};
    if (vfs.isVirtual(stream, &handle)) {
        uint64_t done = 0;
        while (done < len) {
            ssize_t count = pread(handle.fd, static_cast<uint8_t*>(s) + done, len - done, handle.position + done);
            if (count < 0) return -1;
            if (count == 0) break;
            done += static_cast<uint64_t>(count);
        }
        std::lock_guard<std::mutex> lock(vfs.handlesLock);
        auto it = vfs.virtualHandles.find(stream);
        if (it != vfs.virtualHandles.end()) it->second.position = handle.position + static_cast<int64_t>(done);
        return static_cast<int64_t>(done);
    }
    return retro_vfs_file_read_impl(stream, s, len);
}

int64_t VFS::write(struct retro_vfs_file_handle *stream, const void *s, uint64_t len) {
    LOGV("VFS Calling write");
    VFS& vfs = VFS::getInstance();
    VirtualHandle handle {};
    if (vfs.isVirtual(stream, &handle)) {
        // Game files handed over by the app are read-only; saf:// files can be writable.
        if (!handle.writable) return -1;
        uint64_t done = 0;
        while (done < len) {
            ssize_t count = pwrite(handle.fd, static_cast<const uint8_t*>(s) + done, len - done, handle.position + done);
            if (count <= 0) return -1;
            done += static_cast<uint64_t>(count);
        }
        std::lock_guard<std::mutex> lock(vfs.handlesLock);
        auto it = vfs.virtualHandles.find(stream);
        if (it != vfs.virtualHandles.end()) {
            it->second.position = handle.position + static_cast<int64_t>(done);
            if (it->second.position > it->second.size) it->second.size = it->second.position;
        }
        return static_cast<int64_t>(done);
    }
    return retro_vfs_file_write_impl(stream, s, len);
}

int VFS::flush(struct retro_vfs_file_handle *stream) {
    LOGV("VFS Calling flush");
    VirtualHandle handle {};
    if (VFS::getInstance().isVirtual(stream, &handle)) return handle.writable ? fsync(handle.fd) : 0;
    return retro_vfs_file_flush_impl(stream);
}

int VFS::remove(const char *path) {
    LOGV("VFS Calling remove");
    if (isSafPath(path)) {
        struct libretro_vfs_implementation_saf_path_split_result split {};
        if (!retro_vfs_path_split_saf(&split, path)) return -1;
        int result = retro_vfs_file_remove_saf(split.tree, split.path);
        free(split.tree);
        free(split.path);
        return result;
    }
    return retro_vfs_file_remove_impl(path);
}

int VFS::rename(const char *old_path, const char *new_path) {
    LOGV("VFS Calling rename");
    if (isSafPath(old_path) || isSafPath(new_path)) {
        struct libretro_vfs_implementation_saf_path_split_result from {};
        struct libretro_vfs_implementation_saf_path_split_result to {};
        if (!retro_vfs_path_split_saf(&from, old_path)) return -1;
        if (!retro_vfs_path_split_saf(&to, new_path)) {
            free(from.tree);
            free(from.path);
            return -1;
        }
        int result = retro_vfs_file_rename_saf(from.tree, from.path, to.tree, to.path);
        free(from.tree);
        free(from.path);
        free(to.tree);
        free(to.path);
        return result;
    }
    return retro_vfs_file_rename_impl(old_path, new_path);
}

int64_t VFS::truncate(struct retro_vfs_file_handle* stream, int64_t length) {
    LOGV("VFS Calling truncate");
    VFS& vfs = VFS::getInstance();
    VirtualHandle handle {};
    if (vfs.isVirtual(stream, &handle)) {
        if (!handle.writable || ftruncate(handle.fd, static_cast<off_t>(length)) != 0) return -1;
        std::lock_guard<std::mutex> lock(vfs.handlesLock);
        auto it = vfs.virtualHandles.find(stream);
        if (it != vfs.virtualHandles.end()) it->second.size = length;
        return 0;
    }
    return retro_vfs_file_truncate_impl(stream, length);
}

retro_vfs_interface * VFS::getInterface() {
    return new retro_vfs_interface {
        /* Introduced in VFS API v1 */
        &VFS::path,
        &VFS::open,
        &VFS::close,
        &VFS::size,
        &VFS::tell,
        &VFS::seek,
        &VFS::read,
        &VFS::write,
        &VFS::flush,
        &VFS::remove,
        &VFS::rename,

        /* Introduced in VFS API v2 */
        &VFS::truncate,

        /* Introduced in VFS API v3 */
        &VFS::stat,
        &VFS::mkdir,
        &VFS::opendir,
        &VFS::readdir,
        &VFS::direntGetName,
        &VFS::direntIsDir,
        &VFS::closedir
    };
}

void VFS::initialize(std::vector<VFSFile> files) {
    this->virtualFiles = std::move(files);
}

void VFS::deinitialize() {
    virtualFiles.clear();
}

struct retro_vfs_file_handle* VFS::virtualOpen(const char *path, unsigned int mode, unsigned int hints) {
    LOGV("VFS Calling open: %s %i", path, mode);

    VFSFile* virtualFile = findVirtualFile(path);

    if (virtualFile == nullptr) {
        return nullptr;
    }

    LOGD("VFS Performing virtual file open: %s", virtualFile->getFileName().data());

    // A separate descriptor per handle, but positions are tracked per handle (see VirtualHandle):
    // dup() shares one file offset between all handles.
    int duplicateFD = dup(virtualFile->getFD());
    if (duplicateFD < 0) return nullptr;
    struct stat info {};
    int64_t size = fstat(duplicateFD, &info) == 0 ? static_cast<int64_t>(info.st_size) : 0;

    auto stream = new retro_vfs_file_handle {};
    stream->fd = duplicateFD;
    stream->hints = hints;
    stream->size = size;
    stream->buf = nullptr;
    stream->fp = nullptr;
    stream->orig_path = strdup(virtualFile->getFileName().data());
    stream->mappos = 0;
    stream->mapsize = 0;
    stream->mapped = nullptr;
    stream->scheme = VFS_SCHEME_NONE;

    std::lock_guard<std::mutex> lock(handlesLock);
    virtualHandles[stream] = VirtualHandle { duplicateFD, 0, size };
    return stream;
}

bool VFS::isVirtual(struct retro_vfs_file_handle* stream, VirtualHandle* out) {
    std::lock_guard<std::mutex> lock(handlesLock);
    auto it = virtualHandles.find(stream);
    if (it == virtualHandles.end()) return false;
    if (out != nullptr) *out = it->second;
    return true;
}

int VFS::stat(const char *path, int32_t *size) {
    LOGV("VFS Calling stat: %s", path);
    VFSFile* virtualFile = VFS::getInstance().findVirtualFile(path);
    if (virtualFile != nullptr) {
        // Game files given by the app (Android content URIs) only exist as file descriptors.
        struct ::stat info {};
        if (fstat(virtualFile->getFD(), &info) != 0) {
            return 0;
        }
        if (size != nullptr) {
            *size = static_cast<int32_t>(info.st_size);
        }
        return RETRO_VFS_STAT_IS_VALID;
    }
    if (isSafPath(path)) {
        struct libretro_vfs_implementation_saf_path_split_result split {};
        if (!retro_vfs_path_split_saf(&split, path)) return 0;
        int64_t size64 = 0;
        int flags = retro_vfs_stat_saf(split.tree, split.path, &size64);
        free(split.tree);
        free(split.path);
        if (size != nullptr) *size = static_cast<int32_t>(size64);
        return flags;
    }
    return retro_vfs_stat_impl(path, size);
}

int VFS::mkdir(const char *dir) {
    LOGV("VFS Calling mkdir: %s", dir);
    if (isSafPath(dir)) {
        struct libretro_vfs_implementation_saf_path_split_result split {};
        if (!retro_vfs_path_split_saf(&split, dir)) return -1;
        int result = retro_vfs_mkdir_saf(split.tree, split.path);
        free(split.tree);
        free(split.path);
        return result;
    }
    return retro_vfs_mkdir_impl(dir);
}

struct retro_vfs_dir_handle* VFS::opendir(const char *dir, bool include_hidden) {
    LOGV("VFS Calling opendir: %s", dir);
    if (isSafPath(dir)) {
        struct libretro_vfs_implementation_saf_path_split_result split {};
        if (!retro_vfs_path_split_saf(&split, dir)) return nullptr;
        auto result = retro_vfs_opendir_saf(split.tree, split.path, include_hidden);
        free(split.tree);
        free(split.path);
        if (result == nullptr) return nullptr;
        auto handle = reinterpret_cast<struct retro_vfs_dir_handle*>(result);
        VFS& vfs = VFS::getInstance();
        std::lock_guard<std::mutex> lock(vfs.handlesLock);
        vfs.safDirectories.insert(handle);
        return handle;
    }
    return retro_vfs_opendir_impl(dir, include_hidden);
}

bool VFS::readdir(struct retro_vfs_dir_handle *dirstream) {
    if (VFS::getInstance().isSafDirectory(dirstream)) {
        return retro_vfs_readdir_saf(reinterpret_cast<libretro_vfs_implementation_saf_dir*>(dirstream));
    }
    return retro_vfs_readdir_impl(dirstream);
}

const char* VFS::direntGetName(struct retro_vfs_dir_handle *dirstream) {
    if (VFS::getInstance().isSafDirectory(dirstream)) {
        return retro_vfs_dirent_get_name_saf(reinterpret_cast<libretro_vfs_implementation_saf_dir*>(dirstream));
    }
    return retro_vfs_dirent_get_name_impl(dirstream);
}

bool VFS::direntIsDir(struct retro_vfs_dir_handle *dirstream) {
    if (VFS::getInstance().isSafDirectory(dirstream)) {
        return retro_vfs_dirent_is_dir_saf(reinterpret_cast<libretro_vfs_implementation_saf_dir*>(dirstream));
    }
    return retro_vfs_dirent_is_dir_impl(dirstream);
}

int VFS::closedir(struct retro_vfs_dir_handle *dirstream) {
    VFS& vfs = VFS::getInstance();
    if (vfs.isSafDirectory(dirstream)) {
        {
            std::lock_guard<std::mutex> lock(vfs.handlesLock);
            vfs.safDirectories.erase(dirstream);
        }
        return retro_vfs_closedir_saf(reinterpret_cast<libretro_vfs_implementation_saf_dir*>(dirstream));
    }
    return retro_vfs_closedir_impl(dirstream);
}

// ---- Lemuroid-S: Android folder access (saf:// paths), using RetroArch's SAF layer ----

static JavaVM* safJavaVm = nullptr;
static bool safInitialized = false;

// Threads attached here are detached when they end (Android aborts the app if an attached native
// thread exits), like RetroArch's rjni_env().
static pthread_key_t safThreadKey;
static pthread_once_t safThreadKeyOnce = PTHREAD_ONCE_INIT;

static void safDetachThread(void*) {
    if (safJavaVm != nullptr) safJavaVm->DetachCurrentThread();
}

static void safCreateThreadKey() {
    pthread_key_create(&safThreadKey, safDetachThread);
}

static JNIEnv* safGetJniEnv() {
    if (safJavaVm == nullptr) return nullptr;
    JNIEnv* env = nullptr;
    if (safJavaVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
    // Cores may call the VFS from their own threads.
    if (safJavaVm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    pthread_once(&safThreadKeyOnce, safCreateThreadKey);
    pthread_setspecific(safThreadKey, reinterpret_cast<void*>(1));
    return env;
}

bool VFS::initializeSaf(JNIEnv* env, jobject context) {
    if (env->GetJavaVM(&safJavaVm) != JNI_OK) return false;
    safInitialized = retro_vfs_init_saf(&safGetJniEnv, context);
    LOGI("SAF layer %s", safInitialized ? "ready" : "unavailable");
    return safInitialized;
}

bool VFS::isSafPath(const char* path) {
    return safInitialized && path != nullptr && strncmp(path, "saf://", 6) == 0;
}

std::string VFS::safPathForContentUri(const std::string& contentUri) {
    if (!safInitialized) return "";
    struct libretro_vfs_implementation_saf_path_split_result split {};
    if (!retro_vfs_path_split_content_saf(&split, contentUri.c_str())) return "";
    char* joined = retro_vfs_path_join_saf(split.tree, split.path);
    free(split.tree);
    free(split.path);
    if (joined == nullptr) return "";
    std::string result = joined;
    free(joined);
    return result;
}

struct retro_vfs_file_handle* VFS::safOpen(const char* path, unsigned mode, unsigned hints) {
    struct libretro_vfs_implementation_saf_path_split_result split {};
    if (!retro_vfs_path_split_saf(&split, path)) return nullptr;
    int fd = retro_vfs_file_open_saf(split.tree, split.path, mode);
    free(split.tree);
    free(split.path);
    if (fd < 0) return nullptr;

    struct stat info {};
    int64_t size = fstat(fd, &info) == 0 ? static_cast<int64_t>(info.st_size) : 0;
    bool writable = (mode & RETRO_VFS_FILE_ACCESS_WRITE) != 0;

    auto stream = new retro_vfs_file_handle {};
    stream->fd = fd;
    stream->hints = hints;
    stream->size = size;
    stream->orig_path = strdup(path);
    stream->scheme = VFS_SCHEME_NONE;

    std::lock_guard<std::mutex> lock(handlesLock);
    virtualHandles[stream] = VirtualHandle { fd, 0, size, writable };
    return stream;
}

bool VFS::isSafDirectory(struct retro_vfs_dir_handle* dir) {
    std::lock_guard<std::mutex> lock(handlesLock);
    return safDirectories.count(dir) > 0;
}

VFSFile* VFS::findVirtualFile(const char *path) {
    for (auto& virtualFile : virtualFiles) {
        if (strcmp(path, virtualFile.getFileName().data()) == 0) {
            return &virtualFile;
        }
    }
    return nullptr;
}

} // namespace libretrodroid