//
// Created by Vera-Firefly on 20.08.2024.
//

#include <android/native_window.h>
#include <stdio.h>
#include <unistd.h>
#include <pthread.h>
#include <dlfcn.h>
#include <assert.h>
#include <malloc.h>
#include <stdlib.h>
#include "environ/environ.h"
#include "virgl_bridge.h"
#include "egl_loader.h"
#include "gl_bridge.h"
#include "osmesa_loader.h"
#define TAG __FILE_NAME__
#include "renderer_config.h"

int (*vtest_main_p)(int argc, char **argv);
void (*vtest_swap_buffers_p)(void);

static EGLContext virgl_context;

void *egl_make_current(void *window) {
    if (pojav_environ->config_renderer == RENDERER_VIRGL)
    {
            eglMakeCurrent_p(potatoBridge.eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            eglDestroySurface_p(potatoBridge.eglDisplay, potatoBridge.eglSurface);
            eglDestroyContext_p(potatoBridge.eglDisplay, potatoBridge.eglContext);
            eglTerminate_p(potatoBridge.eglDisplay);
            eglReleaseThread_p();

            potatoBridge.eglContext = EGL_NO_CONTEXT;
            potatoBridge.eglDisplay = EGL_NO_DISPLAY;
            potatoBridge.eglSurface = EGL_NO_SURFACE;

            printf("EGLBridge: eglMakeCurrent() succeed!\n");

    
            printf("VirGL: vtest_main = %p\n", vtest_main_p);
            printf("VirGL: Calling VTest server's main function\n");
            vtest_main_p(4, (const char*[]){"vtest", "--use-gles", "multi-clients", NULL, "compat", NULL, NULL, NULL, NULL, NULL, NULL});
    } else {
        return NULL;
    }
}

bool loadSymbolsVirGL() {
    dlsym_EGL();
    char *fileName = calloc(1, 1024);

    sprintf(fileName, "%s/libvirgl_test_server.so", getenv("POJAV_NATIVEDIR"));
    void *handle = dlopen(fileName, RTLD_LAZY);
    printf("VirGL: libvirgl_test_server = %p\n", handle);
    if (!handle) {
        printf("VirGL: %s\n", dlerror());
        return false;
    }
    vtest_main_p = dlsym(handle, "vtest_main");
    vtest_swap_buffers_p = dlsym(handle, "vtest_swap_buffers");

    free(fileName);

    return true;
}

int virglInit() {
    if (pojav_environ->config_renderer != RENDERER_VIRGL)
        return 0;

    g_EglDisplay = eglGetDisplay_p(EGL_DEFAULT_DISPLAY);
    if (g_EglDisplay == EGL_NO_DISPLAY) {
        LOGE("%s", "eglGetDisplay_p(EGL_DEFAULT_DISPLAY) returned EGL_NO_DISPLAY");
        return 0;
    }
    if (eglInitialize_p(g_EglDisplay, 0, 0) != EGL_TRUE) {
        LOGE("eglInitialize_p() failed: %04x", eglGetError_p());
        return 0;
    }

    return gl_init_context();
}

void *virglCreateContext(void *contextSrc) {
    printf("OSMDroid: generating context\n");
    eglCreateContext_p();

}

void *virglGetCurrentContext() {
    return virgl_context;
}

static bool onMakeCurrent = false;

void virglSwapBuffers() {
    glFinish();
    gl_swap_buffers();
}

void virglMakeCurrent(void *window) {
    if (!onMakeCurrent)
        printf("OSMDroid: making current\n");

    EGLMakeCurrent_p(virgl_context, setbuffer, GL_UNSIGNED_BYTE, pojav_environ->savedWidth, pojav_environ->savedHeight);

    glClear(GL_COLOR_BUFFER_BIT);
    glClearColor(0.4f, 0.4f, 0.4f, 1.0f);

    int pixelsArr[4];
    glReadPixels(0, 0, 1, 1, GL_RGB, GL_INT, &pixelsArr);

    if (!onMakeCurrent)
    {
        onMakeCurrent = true;
        printf("OSMDroid: vendor: %s\n",glGetString_p(GL_VENDOR));
        printf("OSMDroid: renderer: %s\n",glGetString_p(GL_RENDERER));

        virglSwapBuffers();
    }
}

void virglSwapInterval(int interval) {
    gl_swap_interval(interval);
}
void virglSwapBuffers() {
    glFinish();
    vtest_swap_buffers_p();
}
