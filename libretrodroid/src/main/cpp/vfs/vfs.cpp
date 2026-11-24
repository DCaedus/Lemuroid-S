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
            return target;
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
    // Game files are read-only.
    if (VFS::getInstance().isVirtual(stream)) return -1;
    return retro_vfs_file_write_impl(stream, s, len);
}

int VFS::flush(struct retro_vfs_file_handle *stream) {
    LOGV("VFS Calling flush");
    if (VFS::getInstance().isVirtual(stream)) return 0;
    return retro_vfs_file_flush_impl(stream);
}

int VFS::remove(const char *path) {
    LOGV("VFS Calling remove");
    return retro_vfs_file_remove_impl(path);
}

int VFS::rename(const char *old_path, const char *new_path) {
    LOGV("VFS Calling rename");
    return retro_vfs_file_rename_impl(old_path, new_path);
}

int64_t VFS::truncate(struct retro_vfs_file_handle* stream, int64_t length) {
    LOGV("VFS Calling truncate");
    if (VFS::getInstance().isVirtual(stream)) return -1;
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
    return retro_vfs_stat_impl(path, size);
}

int VFS::mkdir(const char *dir) {
    LOGV("VFS Calling mkdir: %s", dir);
    return retro_vfs_mkdir_impl(dir);
}

struct retro_vfs_dir_handle* VFS::opendir(const char *dir, bool include_hidden) {
    LOGV("VFS Calling opendir: %s", dir);
    return retro_vfs_opendir_impl(dir, include_hidden);
}

bool VFS::readdir(struct retro_vfs_dir_handle *dirstream) {
    return retro_vfs_readdir_impl(dirstream);
}

const char* VFS::direntGetName(struct retro_vfs_dir_handle *dirstream) {
    return retro_vfs_dirent_get_name_impl(dirstream);
}

bool VFS::direntIsDir(struct retro_vfs_dir_handle *dirstream) {
    return retro_vfs_dirent_is_dir_impl(dirstream);
}

int VFS::closedir(struct retro_vfs_dir_handle *dirstream) {
    return retro_vfs_closedir_impl(dirstream);
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