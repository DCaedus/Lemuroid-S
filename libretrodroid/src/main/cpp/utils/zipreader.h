/*
 *     Lemuroid-S: minimal streaming zip reader, so zipped ROMs load without extracting them to storage.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#ifndef LIBRETRODROID_ZIPREADER_H
#define LIBRETRODROID_ZIPREADER_H

#include <string>
#include "utils.h"

namespace libretrodroid {

class ZipReader {
public:
    /** Unpacks [entryName] from the zip open at [fd] into memory. Throws if it can't. */
    static Utils::ReadResult readEntry(int fd, const std::string& entryName);

    /** Unpacks [entryName] from the zip open at [fd] into a new file at [path]. */
    static bool extractEntry(int fd, const std::string& entryName, const std::string& path);

    /** Unpacks [entryName] from the zip open at [fd], writing it to the open file [outputFd]. */
    static bool extractEntryToDescriptor(int fd, const std::string& entryName, int outputFd);
};

}

#endif //LIBRETRODROID_ZIPREADER_H
