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

#include <jni.h>

#include <EGL/egl.h>

#include <string>
#include <utility>
#include <vector>
#include <unordered_set>
#include <dirent.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <linux/memfd.h>

#include "libretrodroid.h"
#include "utils/libretrodroidexception.h"
#include "log.h"
#include "core.h"
#include "audio.h"
#include "video.h"
#include "renderers/renderer.h"
#include "fpssync.h"
#include "input.h"
#include "rumble.h"
#include "shadermanager.h"
#include "utils/javautils.h"
#include "environment.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es2/imagerendereres2.h"
#include "renderers/es3/imagerendereres3.h"
#include "utils/utils.h"
#include "utils/rect.h"
#include "errorcodes.h"
#include "vfs/vfs.h"
#include "utils/zipreader.h"

namespace libretrodroid {

uintptr_t LibretroDroid::callback_get_current_framebuffer() {
    return LibretroDroid::getInstance().handleGetCurrentFrameBuffer();
}

void LibretroDroid::callback_hw_video_refresh(
    const void *data,
    unsigned width,
    unsigned height,
    size_t pitch
) {
    LOGD("hw video refresh callback called %i %i", width, height);
    LibretroDroid::getInstance().handleVideoRefresh(data, width, height, pitch);
}

void LibretroDroid::callback_audio_sample(int16_t left, int16_t right) {
    LOGE("callback audio sample (left, right) has been called");
}

size_t LibretroDroid::callback_set_audio_sample_batch(const int16_t *data, size_t frames) {
    return LibretroDroid::getInstance().handleAudioCallback(data, frames);
}

void LibretroDroid::callback_retro_set_input_poll() {
    // Do nothing in here...
}

int16_t LibretroDroid::callback_set_input_state(
    unsigned int port,
    unsigned int device,
    unsigned int index,
    unsigned int id
) {
    return LibretroDroid::getInstance().handleSetInputState(port, device, index, id);
}

void LibretroDroid::updateAudioSampleRateMultiplier() {
    if (audio) {
        audio->setPlaybackSpeed(frameSpeed);
        audio->setVolume(audioVolume);
    }
}

// TODO... Do we really need this?
void LibretroDroid::resetGlobalVariables() {
    core = nullptr;
    audio = nullptr;
    video = nullptr;
    fpsSync = nullptr;
    input = nullptr;
    rumble = nullptr;
}

int LibretroDroid::availableDisks() {
    return Environment::getInstance().getRetroDiskControlCallback() != nullptr
           ? Environment::getInstance().getRetroDiskControlCallback()->get_num_images()
           : 0;
}

int LibretroDroid::currentDisk() {
    return Environment::getInstance().getRetroDiskControlCallback() != nullptr
           ? Environment::getInstance().getRetroDiskControlCallback()->get_image_index()
           : 0;
}

void LibretroDroid::changeDisk(unsigned int index) {
    if (Environment::getInstance().getRetroDiskControlCallback() == nullptr) {
        LOGE("Cannot swap disk. This platform does not support it.");
        return;
    }

    if (index < 0 || index >= Environment::getInstance().getRetroDiskControlCallback()->get_num_images()) {
        LOGE("Requested image index is not valid.");
        return;
    }

    if (Environment::getInstance().getRetroDiskControlCallback()->get_image_index() != index) {
        Environment::getInstance().getRetroDiskControlCallback()->set_eject_state(true);
        Environment::getInstance().getRetroDiskControlCallback()->set_image_index((unsigned) index);
        Environment::getInstance().getRetroDiskControlCallback()->set_eject_state(false);
    }
}

void LibretroDroid::updateVariable(const Variable& variable) {
    Environment::getInstance().updateVariable(variable.key, variable.value);
}

std::vector<Variable> LibretroDroid::getVariables() {
    return Environment::getInstance().getVariables();
}

std::vector<std::vector<struct Controller>> LibretroDroid::getControllers() {
    return Environment::getInstance().getControllers();
}

void LibretroDroid::setControllerType(unsigned int port, unsigned int type) {
    core->retro_set_controller_port_device(port, type);
}

bool LibretroDroid::unserializeState(int8_t *data, size_t size) {
    std::lock_guard<std::mutex> lock(coreLock);

    return core->retro_unserialize(data, size);
}

JNIEXPORT jboolean JNICALL LibretroDroid::unserializeSRAM(int8_t* data, size_t size) {
    std::lock_guard<std::mutex> lock(coreLock);

    size_t sramSize = core->retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *sramState = core->retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);

    if (sramState == nullptr) {
        LOGE("Cannot load SRAM: nullptr in retro_get_memory_data");
        return false;
    }

    if (size > sramSize) {
        // Lemuroid-S: some emulators (e.g. Pizza Boy) store extra data after the save itself.
        // Like RetroArch, load the part that fits instead of refusing the save.
        LOGI("SRAM file larger than the game's save memory (%zu > %zu): loading the first part", size, sramSize);
        size = sramSize;
    }

    memcpy(sramState, data, size);

    return true;
}

std::pair<int8_t*, size_t> LibretroDroid::serializeSRAM() {
    std::lock_guard<std::mutex> lock(coreLock);

    size_t size = core->retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    auto* data = new int8_t[size];
    memcpy(data, (int8_t*) core->retro_get_memory_data(RETRO_MEMORY_SAVE_RAM), size);

    return std::pair(data, size);
}

void LibretroDroid::onSurfaceChanged(unsigned int width, unsigned int height) {
    LOGD("Performing libretrodroid onSurfaceChanged");
    video->updateScreenSize(width, height);
}

void LibretroDroid::onSurfaceCreated() {
    LOGD("Performing libretrodroid onSurfaceCreated");

    struct retro_system_av_info system_av_info {};
    core->retro_get_system_av_info(&system_av_info);

    video = nullptr;

    Video::RenderingOptions renderingOptions {
        Environment::getInstance().isUseHwAcceleration(),
        system_av_info.geometry.base_width,
        system_av_info.geometry.base_height,
        Environment::getInstance().isUseDepth(),
        Environment::getInstance().isUseStencil(),
        openglESVersion,
        Environment::getInstance().getPixelFormat()
    };

    auto newVideo = new Video(
        renderingOptions,
        fragmentShaderConfig,
        Environment::getInstance().isBottomLeftOrigin(),
        Environment::getInstance().getScreenRotation(),
        skipDuplicateFrames,
        immersiveModeEnabled,
        viewportRect,
        immersiveModeConfig,
        viewportAlignment
    );

    video = std::unique_ptr<Video>(newVideo);

    if (Environment::getInstance().getHwContextReset() != nullptr) {
        Environment::getInstance().getHwContextReset()();
    }
}

void LibretroDroid::onMotionEvent(
    unsigned int port,
    unsigned int source,
    float xAxis,
    float yAxis
) {
    LOGD("Received motion event: %d %.2f, %.2f", source, xAxis, yAxis);
    if (input) {
        input->onMotionEvent(port, source, xAxis, yAxis);
    }
}

void LibretroDroid::onTouchEvent(float xAxis, float yAxis) {
    LOGD("Received touch event: %.2f, %.2f", xAxis, yAxis);
    if (input && video) {
        auto [x, y] = video->getLayout().getRelativePosition(xAxis, yAxis);
        input->onMotionEvent(0, Input::MOTION_SOURCE_POINTER, x, y);
    }
}

void LibretroDroid::onKeyEvent(unsigned int port, int action, int keyCode) {
    LOGD("Received key event with action (%d) and keycode (%d)", action, keyCode);
    if (input) {
        input->onKeyEvent(port, action, keyCode);
    }
}

void LibretroDroid::create(
    unsigned int GLESVersion,
    const std::string& soFilePath,
    const std::string& systemDir,
    const std::string& savesDir,
    std::vector<Variable> variables,
    const ShaderManager::Config& shaderConfig,
    float refreshRate,
    bool lowLatencyAudio,
    bool enableVirtualFileSystem,
    bool enableMicrophone,
    bool duplicateFrames,
    std::optional<ImmersiveMode::Config> immersiveModeConfig,
    const std::string& language
) {
    LOGD("Performing libretrodroid create");

    resetGlobalVariables();

    Environment::getInstance().initialize(systemDir, savesDir, &callback_get_current_framebuffer);
    Environment::getInstance().setLanguage(language);
    Environment::getInstance().setEnableVirtualFileSystem(enableVirtualFileSystem);
    Environment::getInstance().setEnableMicrophone(enableMicrophone);

    openglESVersion = GLESVersion;
    screenRefreshRate = refreshRate;
    skipDuplicateFrames = duplicateFrames;
    immersiveModeEnabled = GLESVersion >= 3 && immersiveModeConfig.has_value();
    this->immersiveModeConfig = immersiveModeConfig.value_or(ImmersiveMode::Config{});
    audioEnabled = true;
    audioVolume = 1.0f;
    frameSpeed = 1;

    core = std::make_unique<Core>(soFilePath);

    core->retro_set_video_refresh(&callback_hw_video_refresh);
    core->retro_set_environment(&Environment::callback_environment);
    core->retro_set_audio_sample(&callback_audio_sample);
    core->retro_set_audio_sample_batch(&callback_set_audio_sample_batch);
    core->retro_set_input_poll(&callback_retro_set_input_poll);
    core->retro_set_input_state(&callback_set_input_state);

    std::for_each(variables.begin(), variables.end(), [&](const Variable& v) {
        updateVariable(v);
    });

    core->retro_init();

    preferLowLatencyAudio = lowLatencyAudio;

    // HW accelerated cores are only supported on opengles 3.
    if (Environment::getInstance().isUseHwAcceleration() && openglESVersion < 3) {
        throw LibretroDroidError("OpenGL ES 3 is required for this Core", ERROR_GL_NOT_COMPATIBLE);
    }

    fragmentShaderConfig = shaderConfig;

    rumble = std::make_unique<Rumble>();
}

void LibretroDroid::loadGameFromPath(const std::string& gamePath) {
    LOGD("Performing libretrodroid loadGameFromPath");
    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    struct retro_game_info game_info {};
    game_info.path = Utils::cloneToCString(gamePath);
    game_info.meta = nullptr;

    if (system_info.need_fullpath) {
        game_info.data = nullptr;
        game_info.size = 0;
    } else {
        struct Utils::ReadResult file = Utils::readFileAsBytes(gamePath);
        game_info.data = file.data;
        game_info.size = file.size;
    }

    bool result = core->retro_load_game(&game_info);
    if (!result) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    afterGameLoad();
}

void LibretroDroid::loadGameFromBytes(const int8_t *data, size_t size) {
    LOGD("Performing libretrodroid loadGameFromBytes");

    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    struct retro_game_info game_info {};
    game_info.path = nullptr;
    game_info.meta = nullptr;

    if (system_info.need_fullpath) {
        game_info.data = nullptr;
        game_info.size = 0;
    } else {
        game_info.data = data;
        game_info.size = size;
    }

    bool result = core->retro_load_game(&game_info);
    if (!result) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    afterGameLoad();
}

/**
 * Lemuroid-S: the single way games are loaded. The app opens every game file through Android's
 * folder access (Storage Access Framework) and hands over file descriptors; nothing is copied:
 *  - cores that take the game's contents (most cartridge systems) get them read from the descriptor;
 *  - cores that open files themselves through the libretro VFS get virtual paths;
 *  - cores that need a real file path get one: a named link to the open descriptor
 *    (<linkDirectory>/<real name> -> /proc/self/fd/N), so they see the original file name.
 *    If a link can't be opened, the file is copied there instead.
 */
void LibretroDroid::loadGameFromVirtualFiles(
    std::vector<VFSFile> virtualFiles,
    const std::string& linkDirectory,
    const std::string& archiveEntry
) {
    LOGD("Performing libretrodroid loadGameFromVirtualFiles");
    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    if (virtualFiles.empty()) {
        LOGE("Calling loadGameFromVirtualFiles without any file.");
        throw std::runtime_error("Calling loadGameFromVirtualFiles without any file.");
    }

    // Links and unzipped files from the previous game are no longer needed.
    clearLinkDirectory(linkDirectory);

    bool singleFile = virtualFiles.size() == 1;

    // A ROM inside a zip (the zip is the only file; [archiveEntry] is the ROM's name in it).
    if (!archiveEntry.empty() && singleFile && !Utils::hasExtension(system_info.valid_extensions, "zip")) {
        std::string romName = archiveEntry.substr(archiveEntry.find_last_of('/') + 1);
        std::string virtualDirectory = virtualFiles[0].getFileName();
        virtualDirectory = virtualDirectory.substr(0, virtualDirectory.find_last_of('/') + 1);

        struct retro_game_info game_info {};
        game_info.meta = nullptr;
        if (!system_info.need_fullpath) {
            LOGI("Loading zipped game from memory");
            struct Utils::ReadResult file = ZipReader::readEntry(virtualFiles[0].getFD(), archiveEntry);
            game_info.path = Utils::cloneToCString(virtualDirectory + romName);
            game_info.data = file.data;
            game_info.size = file.size;
        } else {
            // The core needs a real file and can't read zips (Genesis Plus GX, PicoDrive): unpack it
            // into an in-memory file (nothing written to storage) and give the core a named link to
            // it. Only if the system has no in-memory files, unpack to a temporary file instead
            // (deleted when the next game starts).
            LOGI("Unpacking zipped game for a core that needs a file");
            mkdir(linkDirectory.c_str(), 0700);
            std::string path = linkDirectory + "/" + romName;
            if (!unpackToMemoryFile(virtualFiles[0].getFD(), archiveEntry, romName, path)) {
                LOGW("Unpacking %s to a temporary file", romName.c_str());
                unlink(path.c_str());
                if (!ZipReader::extractEntry(virtualFiles[0].getFD(), archiveEntry, path)) {
                    throw std::runtime_error("Cannot unpack game");
                }
            }
            game_info.path = Utils::cloneToCString(path);
            game_info.data = nullptr;
            game_info.size = 0;
        }

        bool result = core->retro_load_game(&game_info);
        if (!result) {
            LOGE("Cannot load game. Leaving.");
            throw std::runtime_error("Cannot load game");
        }
        afterGameLoad();
        return;
    }
    bool readContents = !system_info.need_fullpath && singleFile;
    bool useVFS = !readContents && Environment::getInstance().isVirtualFileSystemEnabled();

    struct retro_game_info game_info {};
    game_info.meta = nullptr;
    game_info.data = nullptr;
    game_info.size = 0;

    if (readContents) {
        LOGI("Loading game from memory");
        game_info.path = Utils::cloneToCString(virtualFiles[0].getFileName());
        struct Utils::ReadResult file = Utils::readFileDescriptorContents(virtualFiles[0].getFD());
        game_info.data = file.data;
        game_info.size = file.size;
    } else if (useVFS) {
        LOGI("Loading game through the virtual file system");
        game_info.path = Utils::cloneToCString(virtualFiles[0].getFileName());
        VFS::getInstance().initialize(std::move(virtualFiles));
    } else {
        LOGI("Loading game through file links");
        game_info.path = Utils::cloneToCString(linkGameFiles(virtualFiles, linkDirectory));
        if (!system_info.need_fullpath) {
            struct Utils::ReadResult file = Utils::readFileDescriptorContents(virtualFiles[0].getFD());
            game_info.data = file.data;
            game_info.size = file.size;
        }
        linkedGameFiles = std::move(virtualFiles);
    }

    bool result = core->retro_load_game(&game_info);
    if (!result) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    afterGameLoad();
}

static constexpr uint64_t MAX_MEMORY_FILE_SIZE = 64ull * 1024 * 1024;

bool LibretroDroid::unpackToMemoryFile(
    int zipFd,
    const std::string& entryName,
    const std::string& romName,
    const std::string& linkPath
) {
#ifdef __NR_memfd_create
    // Big files (e.g. zipped disc images) would take too much memory on weak phones.
    if (ZipReader::entrySize(zipFd, entryName) > MAX_MEMORY_FILE_SIZE) return false;
    int memoryFd = static_cast<int>(syscall(__NR_memfd_create, romName.c_str(), MFD_CLOEXEC));
    if (memoryFd < 0) return false;
    try {
        ZipReader::extractEntryToDescriptor(zipFd, entryName, memoryFd);
    } catch (...) {
        close(memoryFd);
        throw;
    }
    std::string target = "/proc/self/fd/" + std::to_string(memoryFd);
    bool linked = symlink(target.c_str(), linkPath.c_str()) == 0;
    if (linked) {
        int test = open(linkPath.c_str(), O_RDONLY);
        linked = test >= 0;
        if (test >= 0) close(test);
    }
    if (!linked) {
        close(memoryFd);
        return false;
    }
    // Kept open (and so alive) for the whole game session.
    linkedGameFiles.emplace_back(VFSFile(linkPath, memoryFd));
    return true;
#else
    return false;
#endif
}

void LibretroDroid::clearLinkDirectory(const std::string& linkDirectory) {
    if (DIR* dir = opendir(linkDirectory.c_str())) {
        while (struct dirent* entry = readdir(dir)) {
            std::string name = entry->d_name;
            if (name == "." || name == "..") continue;
            unlink((linkDirectory + "/" + name).c_str());
        }
        closedir(dir);
    }
}

std::string LibretroDroid::linkGameFiles(std::vector<VFSFile>& files, const std::string& linkDirectory) {
    mkdir(linkDirectory.c_str(), 0700);

    std::string firstPath;
    for (auto& file : files) {
        const std::string& virtualPath = file.getFileName();
        std::string name = virtualPath.substr(virtualPath.find_last_of('/') + 1);
        std::string linkPath = linkDirectory + "/" + name;
        std::string target = "/proc/self/fd/" + std::to_string(file.getFD());

        bool linked = symlink(target.c_str(), linkPath.c_str()) == 0;
        if (linked) {
            int test = open(linkPath.c_str(), O_RDONLY);
            linked = test >= 0;
            if (test >= 0) close(test);
        }

        if (!linked) {
            LOGW("Cannot link %s, copying it instead", name.c_str());
            unlink(linkPath.c_str());
            if (!Utils::copyFileDescriptorToPath(file.getFD(), linkPath)) {
                throw std::runtime_error("Cannot prepare game file " + name);
            }
        }

        if (firstPath.empty()) firstPath = linkPath;
    }
    return firstPath;
}

void LibretroDroid::destroy() {
    std::lock_guard<std::mutex> lock(coreLock);

    LOGD("Performing libretrodroid destroy");

    if (Environment::getInstance().getHwContextDestroy() != nullptr) {
        Environment::getInstance().getHwContextDestroy()();
    }

    core->retro_unload_game();
    linkedGameFiles.clear();
    core->retro_deinit();

    video = nullptr;
    core = nullptr;
    rumble = nullptr;
    fpsSync = nullptr;
    audio = nullptr;

    Environment::getInstance().deinitialize();
    VFS::getInstance().deinitialize();
}

void LibretroDroid::resume() {
    LOGD("Performing libretrodroid resume");

    input = std::make_unique<Input>();

    fpsSync->reset();
    audio->start();
    refreshAspectRatio();
}

void LibretroDroid::pause() {
    LOGD("Performing libretrodroid pause");
    audio->stop();

    input = nullptr;
}

void LibretroDroid::step() {
    std::lock_guard<std::mutex> lock(coreLock);

    LOGD("Stepping into retro_run()");

    unsigned frames = 1;
    if (fpsSync) {
        unsigned requestedFrames = fpsSync->advanceFrames();

        // If the application runs too slow it's better to just skip those frames.
        frames = std::min(requestedFrames, 2u);
    }

    // Lemuroid-S frameskip: when fast-forwarding, only the last frame of every other step gets a new
    // picture, so the game is shown at about 30 fps whatever the speed (x2 to x8). All other frames
    // run with video disabled, which saves GPU work and battery. On steps without a new picture the
    // screen simply shows the previous one again.
    const bool fastForwarding = frameSpeed > 1;
    const bool showNewPicture = !fastForwarding || (fastForwardStep++ % 2 == 0);
    const size_t totalRuns = frames * frameSpeed;
    for (size_t i = 0; i < totalRuns; i++) {
        skipVideoForCurrentRun = fastForwarding && (!showNewPicture || i + 1 < totalRuns);
        Environment::getInstance().setVideoEnabled(!skipVideoForCurrentRun);
        core->retro_run();
        emulatedFrames++;
    }
    skipVideoForCurrentRun = false;
    Environment::getInstance().setVideoEnabled(true);

    if (video && (!video->rendersInVideoCallback() || !showNewPicture)) {
        video->renderFrame();
    }

    if (fpsSync) {
        fpsSync->wait();
    }

    if (rumble && rumbleEnabled) {
        rumble->fetchFromEnvironment();
    }

    // Some games override the core geometry at runtime. These fields get updated in retro_run().
    if (video && Environment::getInstance().isGameGeometryUpdated()) {
        Environment::getInstance().clearGameGeometryUpdated();

        video->updateRendererSize(
            Environment::getInstance().getGameGeometryWidth(),
            Environment::getInstance().getGameGeometryHeight()
        );

        dirtyVideo = true;
    }

    if (video && Environment::getInstance().isScreenRotationUpdated()) {
        Environment::getInstance().clearScreenRotationUpdated();

        video->updateRotation(Environment::getInstance().getScreenRotation());
    }
}

float LibretroDroid::getAspectRatio() {
    float gameAspectRatio = Environment::getInstance().retrieveGameSpecificAspectRatio();
    return gameAspectRatio > 0 ? gameAspectRatio : defaultAspectRatio;
}

void LibretroDroid::refreshAspectRatio() {
    video->updateAspectRatio(getAspectRatio());
}

void LibretroDroid::setRumbleEnabled(bool enabled) {
    rumbleEnabled = enabled;
}

bool LibretroDroid::isRumbleEnabled() const {
    return rumbleEnabled;
}

void LibretroDroid::setFrameSpeed(unsigned int speed) {
    frameSpeed = speed;
    updateAudioSampleRateMultiplier();
}

void LibretroDroid::getFrameCounters(uint32_t* emulated, uint32_t* pictures, double* fps) {
    *emulated = emulatedFrames.load();
    *pictures = newPictures.load();
    *fps = targetFps.load();
}

void LibretroDroid::setAudioVolume(float volume) {
    audioVolume = volume;
    if (audio) {
        audio->setVolume(volume);
    }
}

void LibretroDroid::setAudioEnabled(bool enabled) {
    audioEnabled = enabled;
}

void LibretroDroid::setShaderConfig(ShaderManager::Config shaderConfig) {
    fragmentShaderConfig = std::move(shaderConfig);
    if (video) {
        video->updateShaderType(fragmentShaderConfig);
    }
}

void LibretroDroid::handleVideoRefresh(
    const void *data,
    unsigned int width,
    unsigned int height,
    size_t pitch
) {
    if (skipVideoForCurrentRun) {
        return;
    }

    if (data != nullptr) {
        newPictures++;
    }

    if (video) {
        video->onNewFrame(data, width, height, pitch);

        if (video->rendersInVideoCallback()) {
            video->renderFrame();
        }
    }
}

size_t LibretroDroid::handleAudioCallback(const int16_t *data, size_t frames) {
    if (audio && audioEnabled) {
        audio->write(data, frames);
    }
    return frames;
}

int16_t LibretroDroid::handleSetInputState(
    unsigned int port,
    unsigned int device,
    unsigned int index,
    unsigned int id
) {
    if (input) {
        return input->getInputState(port, device, index, id);
    }
    return 0;
}

uintptr_t LibretroDroid::handleGetCurrentFrameBuffer() {
    if (video) {
        return video->getCurrentFramebuffer();
    }
    return 0;
}

void LibretroDroid::reset() {
    std::lock_guard<std::mutex> lock(coreLock);

    core->retro_reset();
}

std::pair<int8_t*, size_t> LibretroDroid::serializeState() {
    std::lock_guard<std::mutex> lock(coreLock);

    size_t size = core->retro_serialize_size();
    auto data = new int8_t[size];

    core->retro_serialize(data, size);

    return std::pair(data, size);
}

void LibretroDroid::resetCheat() {
    std::lock_guard<std::mutex> lock(coreLock);

    core->retro_cheat_reset();
}

void LibretroDroid::setCheat(unsigned index, bool enabled, const std::string& code) {
    std::lock_guard<std::mutex> lock(coreLock);

    core->retro_cheat_set(index, enabled, Utils::cloneToCString(code));
}

bool LibretroDroid::requiresVideoRefresh() const {
    return dirtyVideo;
}

void LibretroDroid::clearRequiresVideoRefresh() {
    dirtyVideo = false;
}

void LibretroDroid::afterGameLoad() {
    struct retro_system_av_info system_av_info {};
    core->retro_get_system_av_info(&system_av_info);

    fpsSync = std::make_unique<FPSSync>(system_av_info.timing.fps, screenRefreshRate);
    targetFps = system_av_info.timing.fps;

    double inputSampleRate = system_av_info.timing.sample_rate * fpsSync->getTimeStretchFactor();

    audio = std::make_unique<Audio>(
        (int32_t) std::lround(inputSampleRate),
        system_av_info.timing.fps,
        preferLowLatencyAudio
    );

    updateAudioSampleRateMultiplier();

    defaultAspectRatio = findDefaultAspectRatio(system_av_info);
}

float LibretroDroid::findDefaultAspectRatio(const retro_system_av_info& system_av_info) {
    float result = system_av_info.geometry.aspect_ratio;
    if (result < 0) {
        result =
            (float) system_av_info.geometry.base_width / (float) system_av_info.geometry.base_height;
    }
    return result;
}

void LibretroDroid::handleRumbleUpdates(const std::function<void(int, float, float)> &handler) {
    if (rumble && rumbleEnabled) {
        rumble->handleRumbleUpdates(handler);
    }
}

void LibretroDroid::setViewport(Rect viewportRect) {
    this->viewportRect = viewportRect;

    if (video != nullptr) {
        video->updateViewportSize(viewportRect);
    }
}

void LibretroDroid::setViewportAlignment(unsigned int viewportAlignment) {
    this-> viewportAlignment = viewportAlignment;

    if (video) {
        this->video->updateViewportAlignment(viewportAlignment);
    }
}

} //namespace libretrodroid
