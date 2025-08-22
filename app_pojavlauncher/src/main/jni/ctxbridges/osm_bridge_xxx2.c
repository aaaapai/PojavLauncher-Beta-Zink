//
// Created by Vera-Firefly on 02.08.2024.
//

#include <malloc.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <environ/environ.h>
#include "osm_bridge_xxx2.h"
#include "renderer_config.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>

static const char* egl_LogTag = "EGLBridge";
static __thread xxx2_osm_render_window_t* currentBundle;

// EGL 全局变量
static EGLDisplay eglDisplay = EGL_NO_DISPLAY;
static EGLConfig eglConfig = NULL;

void setNativeWindowSwapInterval(struct ANativeWindow* nativeWindow, int swapInterval);

bool xxx2_osm_init(void) {

    dlsym_EGL();

    // 设置环境变量强制使用 Zink 驱动和桌面 OpenGL
    setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", 1);
    setenv("GALLIUM_DRIVER", "zink", 1);
    setenv("MESA_GL_VERSION_OVERRIDE", "4.6", 1);
    setenv("MESA_GLSL_VERSION_OVERRIDE", "460", 1);
    
    printf("%s: Initializing EGL with Zink driver (OpenGL on Vulkan)\n", egl_LogTag);

    // 1. 获取默认显示
    eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (eglDisplay == EGL_NO_DISPLAY) {
        printf("%s: Failed to get EGL display\n", egl_LogTag);
        return false;
    }

    // 2. 初始化 EGL
    EGLint eglMajor, eglMinor;
    if (!eglInitialize(eglDisplay, &eglMajor, &eglMinor)) {
        printf("%s: Failed to initialize EGL\n", egl_LogTag);
        return false;
    }
    printf("%s: EGL initialized with version %d.%d\n", egl_LogTag, eglMajor, eglMinor);

    // 打印 EGL 信息
    printf("%s: EGL Vendor: %s\n", egl_LogTag, eglQueryString(eglDisplay, EGL_VENDOR));
    printf("%s: EGL Version: %s\n", egl_LogTag, eglQueryString(eglDisplay, EGL_VERSION));

    // 3. 选择 EGL 配置 - 使用桌面 OpenGL
    const EGLint configAttribs[] = {
        EGL_RENDERABLE_TYPE,    EGL_OPENGL_BIT, // 关键：桌面 OpenGL
        EGL_SURFACE_TYPE,       EGL_WINDOW_BIT,
        EGL_RED_SIZE,           8,
        EGL_GREEN_SIZE,         8,
        EGL_BLUE_SIZE,          8,
        EGL_ALPHA_SIZE,         8,
        EGL_DEPTH_SIZE,         24,
        EGL_STENCIL_SIZE,       8,
        EGL_CONFORMANT,         EGL_OPENGL_BIT, // 桌面 OpenGL 一致性
        EGL_NONE
    };
    
    EGLint numConfigs;
    if (!eglChooseConfig(eglDisplay, configAttribs, &eglConfig, 1, &numConfigs) || numConfigs == 0) {
        printf("%s: Failed to choose EGL config for OpenGL\n", egl_LogTag);
        eglTerminate(eglDisplay);
        eglDisplay = EGL_NO_DISPLAY;
        return false;
    }

    printf("%s: EGL configuration successful\n", egl_LogTag);
    return true;
}

xxx2_osm_render_window_t* xxx2_osm_get_current() {
    return currentBundle;
}

xxx2_osm_render_window_t* xxx2_osm_init_context(xxx2_osm_render_window_t* share) {
    xxx2_osm_render_window_t* render_window = malloc(sizeof(xxx2_osm_render_window_t));
    if (render_window == NULL) return NULL;

    printf("%s: Creating EGL context for desktop OpenGL\n", egl_LogTag);
    memset(render_window, 0, sizeof(xxx2_osm_render_window_t));

    EGLContext eglShareContext = (share != NULL) ? share->context : EGL_NO_CONTEXT;

    // 桌面 OpenGL 上下文属性
    const EGLint contextAttribs[] = {
        EGL_CONTEXT_MAJOR_VERSION, 4,
        EGL_CONTEXT_MINOR_VERSION, 6,
        EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT,
        EGL_NONE
    };

    // 创建 EGL 上下文
    render_window->context = eglCreateContext(eglDisplay, eglConfig, eglShareContext, contextAttribs);
    
    if (render_window->context == EGL_NO_CONTEXT) {
        // 尝试兼容性配置文件
        printf("%s: Failed to create core profile context, trying compatibility\n", egl_LogTag);
        const EGLint compatAttribs[] = {
            EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL_CONTEXT_OPENGL_COMPATIBILITY_PROFILE_BIT,
            EGL_NONE
        };
        render_window->context = eglCreateContext(eglDisplay, eglConfig, eglShareContext, compatAttribs);
    }

    if (render_window->context == EGL_NO_CONTEXT) {
        EGLint error = eglGetError();
        printf("%s: Failed to create EGL context: 0x%04X\n", egl_LogTag, error);
        free(render_window);
        return NULL;
    }

    printf("%s: EGL context=%p created successfully\n", egl_LogTag, render_window->context);
    return render_window;
}

// 创建 EGL 表面
static bool xxx2_osm_create_surface(xxx2_osm_render_window_t* bundle) {
    if (bundle->nativeSurface == NULL) {
        bundle->surface = EGL_NO_SURFACE;
        bundle->disable_rendering = true;
        return false;
    }

    // 设置窗口缓冲区几何属性
    EGLint format;
    eglGetConfigAttrib(eglDisplay, eglConfig, EGL_NATIVE_VISUAL_ID, &format);
    ANativeWindow_setBuffersGeometry(bundle->nativeSurface, 0, 0, format);

    // 创建 EGL 窗口表面
    const EGLint surfaceAttribs[] = { EGL_NONE };
    bundle->surface = eglCreateWindowSurface(eglDisplay, eglConfig, bundle->nativeSurface, surfaceAttribs);
    
    if (bundle->surface == EGL_NO_SURFACE) {
        EGLint error = eglGetError();
        printf("%s: Failed to create EGL surface: 0x%04X\n", egl_LogTag, error);
        bundle->disable_rendering = true;
        return false;
    }

    // 获取表面尺寸
    eglQuerySurface(eglDisplay, bundle->surface, EGL_WIDTH, &bundle->width);
    eglQuerySurface(eglDisplay, bundle->surface, EGL_HEIGHT, &bundle->height);

    bundle->disable_rendering = false;
    printf("%s: EGL surface=%p created (%dx%d)\n", egl_LogTag, bundle->surface, bundle->width, bundle->height);
    return true;
}

// 切换表面
static void xxx2_osm_swap_surfaces(xxx2_osm_render_window_t* bundle) {
    // 销毁旧表面
    if (bundle->surface != EGL_NO_SURFACE) {
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(eglDisplay, bundle->surface);
        bundle->surface = EGL_NO_SURFACE;
    }

    // 释放旧的 ANativeWindow
    if (bundle->nativeSurface != NULL && bundle->newNativeSurface != bundle->nativeSurface) {
        ANativeWindow_release(bundle->nativeSurface);
    }

    // 切换 ANativeWindow
    if (bundle->newNativeSurface != NULL) {
        printf("%s: Switching to new native surface\n", egl_LogTag);
        bundle->nativeSurface = bundle->newNativeSurface;
        bundle->newNativeSurface = NULL;
        ANativeWindow_acquire(bundle->nativeSurface);
        
        // 创建新的 EGL 表面
        xxx2_osm_create_surface(bundle);
    } else {
        printf("%s: No new native surface, disabling rendering\n", egl_LogTag);
        bundle->nativeSurface = NULL;
        bundle->disable_rendering = true;
    }
}

void xxx2_osm_release_window(void) {
    if (currentBundle) {
        currentBundle->newNativeSurface = NULL;
        xxx2_osm_swap_surfaces(currentBundle);
    }
}

// 检查 OpenGL 版本和驱动信息
static void xxx2_osm_check_opengl_version() {
    if (currentBundle == NULL || currentBundle->context == EGL_NO_CONTEXT) {
        return;
    }

    const GLubyte* glVersion = glGetString(GL_VERSION);
    const GLubyte* glVendor = glGetString(GL_VENDOR);
    const GLubyte* glRenderer = glGetString(GL_RENDERER);
    const GLubyte* glslVersion = glGetString(GL_SHADING_LANGUAGE_VERSION);
    
    printf("%s: OpenGL Vendor: %s\n", egl_LogTag, glVendor);
    printf("%s: OpenGL Renderer: %s\n", egl_LogTag, glRenderer);
    printf("%s: OpenGL Version: %s\n", egl_LogTag, glVersion);
    printf("%s: GLSL Version: %s\n", egl_LogTag, glslVersion);
    
    // 检查是否是 Zink
    if (strstr((const char*)glRenderer, "Zink") != NULL || strstr((const char*)glVendor, "Mesa")) {
        printf("%s: SUCCESS: Running OpenGL on Vulkan via Zink!\n", egl_LogTag);
    }
}

void xxx2_osm_make_current(xxx2_osm_render_window_t* bundle) {
    if (bundle == NULL) {
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        currentBundle = NULL;
        return;
    }

    currentBundle = bundle;

    // 处理主窗口绑定
    if (pojav_environ->mainWindowBundle == NULL) {
        printf("%s: Making current and setting main window bundle\n", egl_LogTag);
        pojav_environ->mainWindowBundle = (basic_render_window_t*) bundle;
        pojav_environ->mainWindowBundle->newNativeSurface = pojav_environ->pojavWindow;
    }

    // 检查是否需要切换表面
    if (bundle->state == STATE_RENDERER_NEW_WINDOW || bundle->surface == EGL_NO_SURFACE) {
        xxx2_osm_swap_surfaces(bundle);
        bundle->state = STATE_RENDERER_ALIVE;
    }

    // 绑定上下文和表面
    if (bundle->surface != EGL_NO_SURFACE && !bundle->disable_rendering) {
        if (eglMakeCurrent(eglDisplay, bundle->surface, bundle->surface, bundle->context)) {
            // 第一次成功绑定后检查版本
            static bool hasCheckedVersion = false;
            if (!hasCheckedVersion) {
                xxx2_osm_check_opengl_version();
                hasCheckedVersion = true;
            }
        } else {
            EGLint error = eglGetError();
            printf("%s: eglMakeCurrent failed: 0x%04X\n", egl_LogTag, error);
            bundle->disable_rendering = true;
        }
    } else {
        // 解绑当前上下文
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }
}

void xxx2_osm_swap_buffers() {
    if (currentBundle == NULL || currentBundle->surface == EGL_NO_SURFACE || currentBundle->disable_rendering) {
        return;
    }

    // EGL 自动处理所有缓冲区交换
    eglSwapBuffers(eglDisplay, currentBundle->surface);
}

void xxx2_osm_setup_window() {
    if (pojav_environ->mainWindowBundle != NULL) {
        printf("%s: Main window bundle is not NULL, changing state\n", egl_LogTag);
        pojav_environ->mainWindowBundle->state = STATE_RENDERER_NEW_WINDOW;
        pojav_environ->mainWindowBundle->newNativeSurface = pojav_environ->pojavWindow;
    }
}

void xxx2_osm_swap_interval(int swapInterval) {
    // 使用 EGL 的交换间隔控制
    eglSwapInterval(eglDisplay, swapInterval);
}

// 清理函数
void xxx2_osm_cleanup() {
    if (eglDisplay != EGL_NO_DISPLAY) {
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglTerminate(eglDisplay);
        eglDisplay = EGL_NO_DISPLAY;
    }
}