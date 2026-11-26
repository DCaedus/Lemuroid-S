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

#include <iostream>
#include <fstream>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <stdexcept>
#include <strings.h>

#include "utils.h"
#include "../log.h"

namespace libretrodroid {

Utils::ReadResult Utils::readFileAsBytes(const std::string &filePath) {
    std::ifstream fileStream(filePath);
    fileStream.seekg(0, std::ios::end);
    size_t size = fileStream.tellg();
    char* bytes = new char[size];
    fileStream.seekg(0, std::ios::beg);
    fileStream.read(bytes, size);
    fileStream.close();

    return ReadResult { size, bytes };
}

Utils::ReadResult Utils::readFileAsBytes(const int fileDescriptor) {
    FILE* file = fdopen(fileDescriptor, "r");
    size_t size = getFileSize(file);

    char* bytes = new char[size];
    fread(bytes, sizeof(char), size, file);
    close(fileDescriptor);
    return ReadResult { size, bytes };
}

Utils::ReadResult Utils::readFileDescriptorContents(const int fileDescriptor) {
    struct stat info {};
    if (fstat(fileDescriptor, &info) != 0 || info.st_size < 0) {
        throw std::runtime_error("Cannot read game file size");
    }
    size_t size = static_cast<size_t>(info.st_size);
    char* bytes = new char[size > 0 ? size : 1];
    size_t done = 0;
    while (done < size) {
        ssize_t count = pread(fileDescriptor, bytes + done, size - done, static_cast<off_t>(done));
        if (count <= 0) {
            delete[] bytes;
            throw std::runtime_error("Cannot read game file");
        }
        done += static_cast<size_t>(count);
    }
    return ReadResult { size, bytes };
}

bool Utils::copyFileDescriptorToPath(const int fileDescriptor, const std::string& path) {
    int output = open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (output < 0) return false;
    char buffer[1 << 16];
    off_t offset = 0;
    bool ok = true;
    while (true) {
        ssize_t count = pread(fileDescriptor, buffer, sizeof(buffer), offset);
        if (count < 0) { ok = false; break; }
        if (count == 0) break;
        ssize_t written = 0;
        while (written < count) {
            ssize_t w = write(output, buffer + written, static_cast<size_t>(count - written));
            if (w <= 0) { ok = false; break; }
            written += w;
        }
        if (!ok) break;
        offset += count;
    }
    close(output);
    return ok;
}

bool Utils::hasExtension(const char* extensions, const std::string& extension) {
    if (extensions == nullptr) return false;
    std::string list = extensions;
    size_t start = 0;
    while (start <= list.size()) {
        size_t end = list.find('|', start);
        if (end == std::string::npos) end = list.size();
        std::string item = list.substr(start, end - start);
        if (strcasecmp(item.c_str(), extension.c_str()) == 0) return true;
        start = end + 1;
    }
    return false;
}

size_t Utils::getFileSize(FILE* file) {
    fseek(file, 0, SEEK_SET);
    fseek(file, 0, SEEK_END);
    size_t size = ftell(file);
    fseek(file, 0, SEEK_SET);
    return size;
}

const char* Utils::cloneToCString(const std::string &input) {
    char* result = new char[input.length() + 1];
    std::strcpy(result, input.c_str());
    return result;
}

} //namespace libretrodroid