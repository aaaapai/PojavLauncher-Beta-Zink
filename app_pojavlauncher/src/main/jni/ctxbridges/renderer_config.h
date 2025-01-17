//
// Created by Vera-Firefly on 2.12.2023.
// Definitions specific to the renderer
//

#define RENDERER_VK_ZINK_XXX1 6
#define RENDERER_VK_ZINK_XXX2 7
#define RENDERER_VK_ZINK_XXX3 8
#define RENDERER_VK_ZINK_XXX4 9

#define BRIDGE_TBL_XXX2 2
#define RENDERER_VIRGL 3
#define RENDERER_VK_ZINK 2

#ifndef __SPARE_RENDERER_CONFIG_H_
#define __SPARE_RENDERER_CONFIG_H_

int SpareBuffer(void);

#endif

#ifndef FRAME_BUFFER_SUPPOST
#define FRAME_BUFFER_SUPPOST

extern void *abuffer;
extern void *gbuffer;
extern void *mbuffer;


#endif
