/*
 *     Lemuroid-S: minimal streaming zip reader (stored and deflated entries, no zip64).
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#include "zipreader.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <zlib.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <functional>
#include <stdexcept>
#include <vector>

namespace libretrodroid {

namespace {

constexpr uint32_t EOCD_SIGNATURE = 0x06054b50;
constexpr uint32_t CENTRAL_SIGNATURE = 0x02014b50;
constexpr uint32_t LOCAL_SIGNATURE = 0x04034b50;
constexpr size_t CHUNK = 1 << 16;

struct Entry {
    uint16_t method = 0;
    uint64_t compressedSize = 0;
    uint64_t size = 0;
    uint64_t dataOffset = 0;
};

uint16_t read16(const uint8_t* p) { return static_cast<uint16_t>(p[0] | (p[1] << 8)); }

uint32_t read32(const uint8_t* p) {
    return static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8) |
           (static_cast<uint32_t>(p[2]) << 16) | (static_cast<uint32_t>(p[3]) << 24);
}

bool readAt(int fd, void* buffer, size_t size, uint64_t offset) {
    size_t done = 0;
    while (done < size) {
        ssize_t count = pread(fd, static_cast<uint8_t*>(buffer) + done, size - done, static_cast<off_t>(offset + done));
        if (count <= 0) return false;
        done += static_cast<size_t>(count);
    }
    return true;
}

std::string baseName(const std::string& path) {
    size_t slash = path.find_last_of('/');
    return slash == std::string::npos ? path : path.substr(slash + 1);
}

Entry findEntry(int fd, const std::string& name) {
    struct stat info {};
    if (fstat(fd, &info) != 0) throw std::runtime_error("Cannot read zip size");
    uint64_t fileSize = static_cast<uint64_t>(info.st_size);

    // End of central directory: last 22 bytes plus a comment of up to 64 KB.
    size_t tailSize = static_cast<size_t>(std::min<uint64_t>(fileSize, 22 + 0xFFFF));
    std::vector<uint8_t> tail(tailSize);
    if (tailSize < 22 || !readAt(fd, tail.data(), tailSize, fileSize - tailSize)) {
        throw std::runtime_error("Not a zip file");
    }
    long eocd = -1;
    for (long i = static_cast<long>(tailSize) - 22; i >= 0; i--) {
        if (read32(&tail[i]) == EOCD_SIGNATURE) {
            eocd = i;
            break;
        }
    }
    if (eocd < 0) throw std::runtime_error("Not a zip file");

    uint32_t directorySize = read32(&tail[eocd + 12]);
    uint32_t directoryOffset = read32(&tail[eocd + 16]);
    if (directoryOffset == 0xFFFFFFFF || directorySize == 0xFFFFFFFF) {
        throw std::runtime_error("Zip64 archives aren't supported");
    }

    std::vector<uint8_t> directory(directorySize);
    if (!readAt(fd, directory.data(), directorySize, directoryOffset)) {
        throw std::runtime_error("Cannot read zip directory");
    }

    // Exact entry name first; the same file name in another folder of the zip as a fallback.
    long found = -1;
    long fallback = -1;
    std::string wantedBase = baseName(name);
    for (size_t pos = 0; pos + 46 <= directory.size();) {
        const uint8_t* header = &directory[pos];
        if (read32(header) != CENTRAL_SIGNATURE) break;
        uint16_t nameLength = read16(header + 28);
        uint16_t extraLength = read16(header + 30);
        uint16_t commentLength = read16(header + 32);
        if (pos + 46 + nameLength > directory.size()) break;
        std::string entryName(reinterpret_cast<const char*>(header + 46), nameLength);
        if (entryName == name) {
            found = static_cast<long>(pos);
            break;
        }
        if (fallback < 0 && baseName(entryName) == wantedBase) fallback = static_cast<long>(pos);
        pos += 46 + nameLength + extraLength + commentLength;
    }
    if (found < 0) found = fallback;
    if (found < 0) throw std::runtime_error("Game not found in zip");

    const uint8_t* header = &directory[found];
    Entry entry;
    entry.method = read16(header + 10);
    entry.compressedSize = read32(header + 20);
    entry.size = read32(header + 24);
    uint32_t localOffset = read32(header + 42);
    if (entry.compressedSize == 0xFFFFFFFF || entry.size == 0xFFFFFFFF || localOffset == 0xFFFFFFFF) {
        throw std::runtime_error("Zip64 entries aren't supported");
    }

    uint8_t local[30];
    if (!readAt(fd, local, sizeof(local), localOffset) || read32(local) != LOCAL_SIGNATURE) {
        throw std::runtime_error("Broken zip entry");
    }
    entry.dataOffset = localOffset + 30 + read16(local + 26) + read16(local + 28);
    return entry;
}

/** Streams the unpacked entry to [sink] in chunks. */
void unpack(int fd, const Entry& entry, const std::function<bool(const uint8_t*, size_t)>& sink) {
    std::vector<uint8_t> input(CHUNK);
    uint64_t remaining = entry.compressedSize;
    uint64_t offset = entry.dataOffset;

    if (entry.method == 0) {
        while (remaining > 0) {
            size_t count = static_cast<size_t>(std::min<uint64_t>(remaining, CHUNK));
            if (!readAt(fd, input.data(), count, offset) || !sink(input.data(), count)) {
                throw std::runtime_error("Cannot read zip entry");
            }
            remaining -= count;
            offset += count;
        }
        return;
    }
    if (entry.method != 8) throw std::runtime_error("Unsupported zip compression");

    z_stream stream {};
    if (inflateInit2(&stream, -MAX_WBITS) != Z_OK) throw std::runtime_error("Cannot start unzip");
    std::vector<uint8_t> output(CHUNK);
    int status = Z_OK;
    while (status != Z_STREAM_END) {
        if (stream.avail_in == 0) {
            size_t count = static_cast<size_t>(std::min<uint64_t>(remaining, CHUNK));
            if (count == 0 || !readAt(fd, input.data(), count, offset)) {
                inflateEnd(&stream);
                throw std::runtime_error("Truncated zip entry");
            }
            remaining -= count;
            offset += count;
            stream.next_in = input.data();
            stream.avail_in = static_cast<uInt>(count);
        }
        stream.next_out = output.data();
        stream.avail_out = static_cast<uInt>(output.size());
        status = inflate(&stream, Z_NO_FLUSH);
        if (status != Z_OK && status != Z_STREAM_END) {
            inflateEnd(&stream);
            throw std::runtime_error("Broken zip data");
        }
        size_t produced = output.size() - stream.avail_out;
        if (produced > 0 && !sink(output.data(), produced)) {
            inflateEnd(&stream);
            throw std::runtime_error("Cannot write unzipped data");
        }
    }
    inflateEnd(&stream);
}

} // namespace

Utils::ReadResult ZipReader::readEntry(int fd, const std::string& entryName) {
    Entry entry = findEntry(fd, entryName);
    size_t size = static_cast<size_t>(entry.size);
    char* bytes = new char[size > 0 ? size : 1];
    size_t written = 0;
    try {
        unpack(fd, entry, [&](const uint8_t* data, size_t count) {
            if (written + count > size) return false;
            std::memcpy(bytes + written, data, count);
            written += count;
            return true;
        });
    } catch (...) {
        delete[] bytes;
        throw;
    }
    if (written != size) {
        delete[] bytes;
        throw std::runtime_error("Unexpected unzipped size");
    }
    return Utils::ReadResult { size, bytes };
}

uint64_t ZipReader::entrySize(int fd, const std::string& entryName) {
    return findEntry(fd, entryName).size;
}

bool ZipReader::extractEntryToDescriptor(int fd, const std::string& entryName, int outputFd) {
    Entry entry = findEntry(fd, entryName);
    unpack(fd, entry, [&](const uint8_t* data, size_t count) {
        size_t done = 0;
        while (done < count) {
            ssize_t w = write(outputFd, data + done, count - done);
            if (w <= 0) return false;
            done += static_cast<size_t>(w);
        }
        return true;
    });
    return true;
}

bool ZipReader::extractEntry(int fd, const std::string& entryName, const std::string& path) {
    int output = open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (output < 0) return false;
    try {
        extractEntryToDescriptor(fd, entryName, output);
    } catch (...) {
        close(output);
        unlink(path.c_str());
        throw;
    }
    close(output);
    return true;
}

} // namespace libretrodroid
