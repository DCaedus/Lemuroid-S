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

#ifndef LIBRETRODROID_VFS_H
#define LIBRETRODROID_VFS_H

#include "unistd.h"
#include "retro_common_api.h"
#include "libretro.h"
#include "vfs.h"
#include "vfsfile.h"

#include <vector>
#include <string>
#include <memory>
#include <optional>
#include <mutex>
#include <unordered_map>
#include <unordered_set>
#include <jni.h>

namespace libretrodroid {

class VFS {
public:
    static const int SUPPORTED_VERSION = 3;
    static VFS& getInstance()
    {
        static VFS instance;
        return instance;
    }
    VFS(VFS const&) = delete;
    void operator=(VFS const&) = delete;

    static retro_vfs_interface* getInterface();
    void initialize(std::vector<VFSFile> files);
    void deinitialize();

private:
    VFS() {}

    struct retro_vfs_file_handle* virtualOpen(const char *path, unsigned mode, unsigned hints);

    VFSFile* findVirtualFile(const char* path);

public:

    static const char* path(struct retro_vfs_file_handle *stream);
    static struct retro_vfs_file_handle* open(const char *path, unsigned mode, unsigned hints);
    static int close(struct retro_vfs_file_handle* stream);
    static int64_t size(struct retro_vfs_file_handle *stream);
    static int64_t tell(struct retro_vfs_file_handle *stream);
    static int64_t seek(struct retro_vfs_file_handle *stream, int64_t offset, int seek_position);
    static int64_t read(struct retro_vfs_file_handle *stream, void *s, uint64_t len);
    static int64_t write(struct retro_vfs_file_handle *stream, const void *s, uint64_t len);
    static int flush(struct retro_vfs_file_handle *stream);
    static int remove(const char *path);
    static int rename(const char *old_path, const char *new_path);

    static int64_t truncate(struct retro_vfs_file_handle *stream, int64_t length);

    /* VFS API v3 (Lemuroid-S): needed by cores such as Flycast. */
    static int stat(const char *path, int32_t *size);
    static int mkdir(const char *dir);
    static struct retro_vfs_dir_handle* opendir(const char *dir, bool include_hidden);
    static bool readdir(struct retro_vfs_dir_handle *dirstream);
    static const char* direntGetName(struct retro_vfs_dir_handle *dirstream);
    static bool direntIsDir(struct retro_vfs_dir_handle *dirstream);
    static int closedir(struct retro_vfs_dir_handle *dirstream);

private:
    std::vector<VFSFile> virtualFiles;

    // Lemuroid-S: open handles on virtual files. Each has its own position and reads with pread(),
    // so a core opening the same file twice (e.g. FBNeo with big romsets) can't disturb itself.
    struct VirtualHandle {
        int fd;
        int64_t position;
        int64_t size;
        bool writable = false;
    };
    // Directories opened through Android's folder access (saf:// paths).
    std::unordered_set<struct retro_vfs_dir_handle*> safDirectories;

    struct retro_vfs_file_handle* safOpen(const char* path, unsigned mode, unsigned hints);
    bool isSafDirectory(struct retro_vfs_dir_handle* dir);

public:
    /** Lemuroid-S: enables saf:// paths (RetroArch's SAF layer). [context] is an Android Context. */
    static bool initializeSaf(JNIEnv* env, jobject context);
    /** saf:// path for a content:// document URI inside a granted folder, or "" if it isn't one. */
    static std::string safPathForContentUri(const std::string& contentUri);
    static bool isSafPath(const char* path);
private:
    std::mutex handlesLock;
    std::unordered_map<struct retro_vfs_file_handle*, VirtualHandle> virtualHandles;

    bool isVirtual(struct retro_vfs_file_handle* stream, VirtualHandle* out = nullptr);

};

} // namespace libretrodroid

#endif //LIBRETRODROID_VFS_H
