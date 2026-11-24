/*
 *     Copyright (C) 2019  Filippo Scognamiglio
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

#ifndef LIBRETRODROID_UTILS_H
#define LIBRETRODROID_UTILS_H

namespace libretrodroid {

class Utils {
public:
    struct ReadResult {
        size_t size;
        char* data;
    };

    static ReadResult readFileAsBytes(const std::string &filePath);
    static ReadResult readFileAsBytes(const int fileDescriptor);
    // Lemuroid-S: reads a whole file through a descriptor without closing it or moving its offset.
    static ReadResult readFileDescriptorContents(const int fileDescriptor);
    // Lemuroid-S: copies a file descriptor's contents to a new file at [path].
    static bool copyFileDescriptorToPath(const int fileDescriptor, const std::string& path);
    // Lemuroid-S: true if a libretro extension list ("md|bin|zip") contains [extension].
    static bool hasExtension(const char* extensions, const std::string& extension);

    static const char* cloneToCString(const std::string &input);

    static size_t getFileSize(FILE* file);
};

}

#endif //LIBRETRODROID_UTILS_H
