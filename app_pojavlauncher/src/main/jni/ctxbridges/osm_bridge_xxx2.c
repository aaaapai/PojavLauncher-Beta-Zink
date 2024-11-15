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

ANativeWindow *nativeSurface;
ANativeWindow_Buffer buff;
int32_t stride;

static bool hasCleaned = false;
static bool hasSetNoRendererBuffer = false;
static char xxx2_no_render_buffer[4];
void *abuffer;

void xxx2_osm_set_no_render_buffer(ANativeWindow_Buffer* buf) {
    buf->bits = &xxx2_no_render_buffer;
    buf->width = pojav_environ->savedWidth;
    buf->height = pojav_environ->savedHeight;
    buf->stride = 0;
}

void *xxx2OsmGetCurrentContext() {
    return (void *)OSMesaGetCurrentContext_p();
}

void xxx2OsmloadSymbols() {
    dlsym_OSMesa();
}

void xxx2_osm_apply_current_l(ANativeWindow_Buffer* buf) {
    OSMesaContext ctx = OSMesaGetCurrentContext_p();
    if (ctx == NULL)
        printf("Zink: attempted to swap buffers without context!");

    OSMesaMakeCurrent_p(ctx, buf->bits, GL_UNSIGNED_BYTE, buf->width, buf->height);
    if (buf->stride != stride)
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH, buf->stride);
    stride = buf->stride;
}

void xxx2_osm_apply_current_ll(void* window, ANativeWindow_Buffer* buf) {
    if (SpareBuffer())
    {
    #ifdef FRAME_BUFFER_SUPPOST
        OSMesaMakeCurrent_p((OSMesaContext)window,
                                abuffer,
                                GL_UNSIGNED_BYTE,
                                buf->width,
                                buf->height);
    #else
        printf("[ERROR]: Macro FRAME_BUFFER_SUPPOST is undefined\n");
    #endif
    } else OSMesaMakeCurrent_p((OSMesaContext)window,
                                   setbuffer,
                                   GL_UNSIGNED_BYTE,
                                   buf->width,
                                   buf->height);

    if (buf->stride != stride)
        OSMesaPixelStore_p(OSMESA_ROW_LENGTH, buf->stride);
    stride = buf->stride;

}

void xxx2OsmSwapBuffers() {
    ANativeWindow_lock(nativeSurface, &buff, NULL);
    xxx2_osm_apply_current_l(&buff);
    glFinish_p();
    ANativeWindow_unlockAndPost(nativeSurface);
}

void xxx2OsmMakeCurrent(void *window) {
    printf("OSMDroid: making current\n");

    if (!hasCleaned)
    {
        nativeSurface = pojav_environ->pojavWindow;
        ANativeWindow_acquire(nativeSurface);
        ANativeWindow_setBuffersGeometry(nativeSurface, 0, 0, WINDOW_FORMAT_RGBX_8888);
        ANativeWindow_lock(nativeSurface, &buff, NULL);
    }

    if (!hasSetNoRendererBuffer)
    {
        hasSetNoRendererBuffer = true;
        xxx2_osm_set_no_render_buffer(&buff);
    }

    xxx2_osm_apply_current_ll(window, &buff);
    OSMesaPixelStore_p(OSMESA_Y_UP, 0);

    printf("OSMDroid: vendor: %s\n", glGetString_p(GL_VENDOR));
    printf("OSMDroid: renderer: %s\n", glGetString_p(GL_RENDERER));
    if (!hasCleaned)
    {
        hasCleaned = true;
        glClear_p(GL_COLOR_BUFFER_BIT);
        glClearColor_p(0.4f, 0.4f, 0.4f, 1.0f);
        ANativeWindow_unlockAndPost(nativeSurface);
    }
}

void *xxx2OsmCreateContext(void *contextSrc) {
    printf("OSMDroid: generating context\n");
    void *ctx = OSMesaCreateContext_p(OSMESA_RGBA, contextSrc);
    printf("OSMDroid: context=%p\n", ctx);
    return ctx;
}

void xxx2_osm_setup_window() {
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