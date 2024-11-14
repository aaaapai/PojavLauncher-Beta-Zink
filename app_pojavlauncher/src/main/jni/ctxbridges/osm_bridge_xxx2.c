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
#include "osm_bridge_xxx2.h"
#include "osmesa_loader.h"
#include "renderer_config.h"

ANativeWindow_Buffer buf;
int32_t stride;

static bool hasCleaned = false;
void *abuffer;

void *xxx2OsmGetCurrentContext() {
    return (void *)OSMesaGetCurrentContext_p();
}

void xxx2OsmloadSymbols() {
    dlsym_OSMesa();
}

void xxx2OsmSwapBuffers() {
    OSMesaContext ctx = OSMesaGetCurrentContext_p();
    if (ctx == NULL)
        printf("Zink: attempted to swap buffers without context!");

    ANativeWindow_lock(pojav_environ->pojavWindow, &buf, NULL);
    OSMesaMakeCurrent_p(ctx, buf.bits, GL_UNSIGNED_BYTE, pojav_environ->savedWidth, pojav_environ->savedHeight);
    glFinish_p();

    if (buf.stride != stride)
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH, buf.stride);
    stride = buf.stride;

    ANativeWindow_unlockAndPost(pojav_environ->pojavWindow);
}

void xxx2OsmMakeCurrent(void *window) {
    printf("OSMDroid: making current\n");
    if (SpareBuffer())
    {
    #ifdef FRAME_BUFFER_SUPPOST
        OSMesaMakeCurrent_p((OSMesaContext)window,
                                abuffer,
                                GL_UNSIGNED_BYTE,
                                pojav_environ->savedWidth,
                                pojav_environ->savedHeight);
    #else
        printf("[ERROR]: Macro FRAME_BUFFER_SUPPOST is undefined\n");
    #endif
    } else OSMesaMakeCurrent_p((OSMesaContext)window,
                                   setbuffer,
                                   GL_UNSIGNED_BYTE,
                                   pojav_environ->savedWidth,
                                   pojav_environ->savedHeight);

    OSMesaPixelStore_p(OSMESA_Y_UP, 0);
    if (!hasCleaned) ANativeWindow_lock(pojav_environ->pojavWindow, &buf, NULL);

    if (buf.stride != stride)
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH, buf.stride);
    stride = buf.stride;

    printf("OSMDroid: vendor: %s\n", glGetString_p(GL_VENDOR));
    printf("OSMDroid: renderer: %s\n", glGetString_p(GL_RENDERER));
    if (!hasCleaned)
    {
        hasCleaned = true;
        glClear_p(GL_COLOR_BUFFER_BIT);
        glClearColor_p(0.4f, 0.4f, 0.4f, 1.0f);
        ANativeWindow_unlockAndPost(pojav_environ->pojavWindow);
    }
}

void *xxx2OsmCreateContext(void *contextSrc) {
    printf("OSMDroid: generating context\n");
    void *ctx = OSMesaCreateContext_p(OSMESA_RGBA, contextSrc);
    printf("OSMDroid: context=%p\n", ctx);
    return ctx;
}

void xxx2OsmSwapInterval(int interval) {
    // Nothing to do here
}

int xxx2OsmInit() {
    if (pojav_environ->config_bridge != BRIDGE_TBL_XXX2)
        return 0;

    if (SpareBuffer())
    {
    #ifdef FRAME_BUFFER_SUPPOST

        printf("OSMDroid: width=%i;height=%i, reserving %i bytes for frame buffer\n",
           pojav_environ->savedWidth, pojav_environ->savedHeight,
           pojav_environ->savedWidth * 4 * pojav_environ->savedHeight);
        abuffer = calloc(pojav_environ->savedWidth *4, pojav_environ->savedHeight +1);

        if (abuffer)
        {
            printf("OSMDroid: created frame buffer\n");
            return 1;
        } else {
            printf("OSMDroid: can't generate frame buffer\n");
            return 0;
        }
    #else
        printf("[WORNING]: Macro FRAME_BUFFER_SUPPOST is undefined,defult to close\n");
    #endif

    } else printf("OSMDroid: do not set frame buffer\n");

    return 0;
}