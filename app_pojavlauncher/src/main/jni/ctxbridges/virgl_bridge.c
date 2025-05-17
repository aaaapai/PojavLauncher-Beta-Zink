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
#include "../GL/gl.h"
#include "pojav/log.h"

int (*vtest_main_p)(int argc, char **argv);
void (*vtest_swap_buffers_p)(void);

static EGLContext virgl_context;
static EGLDisplay g_EglDisplay;

void *egl_make_current(void *window) {
    if (pojav_environ->config_renderer == RENDERER_NULL)
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
    char *fileName = calloc(1, 1024);

    sprintf(fileName, "%s/libvirgl_test_server.so", getenv("POJAV_NATIVEDIR"));
    void *handle = dlopen(fileName, RTLD_LAZY);
    printf("VirGL: libvirgl_test_server = %p\n", handle);
    if (!handle) {
        printf("VirGL: %s\n", dlerror());
        return false;
    }
    vtest_main_p = dlsym(handle, "vtest_main");

    free(fileName);

    return true;
}

void *virglGetCurrentContext() {
    return virgl_context;
}

static bool onMakeCurrent = false;

void virglSwapBuffers() {
    gl_swap_buffers();
}


void virglSwapInterval(int interval) {
    gl_swap_interval(interval);
}
