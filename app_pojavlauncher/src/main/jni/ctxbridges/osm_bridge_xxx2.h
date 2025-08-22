#ifndef OSM_BRIDGE_xxx2_H
#define OSM_BRIDGE_xxx2_H

#include <stdbool.h>
#include <android/native_window.h>
#include <EGL/egl.h>

typedef struct xxx2_osm_render_window_s {
    // 基础状态
    struct ANativeWindow* nativeSurface;
    struct ANativeWindow* newNativeSurface;
    bool disable_rendering;
    int state;
    
    // EGL 相关字段
    EGLContext context;
    EGLSurface surface;
    int width;
    int height;
    
} xxx2_osm_render_window_t;

// 函数声明
bool xxx2_osm_init(void);
xxx2_osm_render_window_t* xxx2_osm_get_current();
xxx2_osm_render_window_t* xxx2_osm_init_context(xxx2_osm_render_window_t* share);
void xxx2_osm_release_window(void);
void xxx2_osm_make_current(xxx2_osm_render_window_t* bundle);
void xxx2_osm_swap_buffers(void);
void xxx2_osm_setup_window(void);
void xxx2_osm_swap_interval(int swapInterval);
void xxx2_osm_cleanup(void);

#endif // OSM_BRIDGE_xxx2_H