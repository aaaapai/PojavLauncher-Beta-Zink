//
// Created by Vera-Firefly on 02.08.2024.
//
#include <android/native_window.h>
#include <stdbool.h>
#ifndef __POJAVLAUNCHER_OSM_BRIDGE_XXX1_H_
#define __POJAVLAUNCHER_OSM_BRIDGE_XXX1_H_
#include "osmesa_loader.h"


typedef struct {
    char       state;
    struct ANativeWindow *nativeSurface;
    struct ANativeWindow *newNativeSurface;
    ANativeWindow_Buffer buffer;
    int32_t last_stride;
    bool disable_rendering;
    OSMesaContext context;
} xxx1_osm_render_window_t;

bool xxx1_osm_init(void);
xxx1_osm_render_window_t* xxx1_osm_get_current(void);
xxx1_osm_render_window_t* xxx1_osm_init_context(xxx1_osm_render_window_t* share);
void xxx1_osm_make_current(xxx1_osm_render_window_t* bundle);
void xxx1_osm_swap_buffers(void);
void xxx1_osm_setup_window(void);
void xxx1_osm_swap_interval(int swapInterval);

#endif //POJAVLAUNCHER_OSM_BRIDGE_XXX1_H
