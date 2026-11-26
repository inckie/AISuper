#include <stdint.h>

#define STBI_ONLY_PNG
#define STBI_ONLY_JPEG
#define STBI_ONLY_BMP
#define STBI_ONLY_GIF
#define STBI_ONLY_TGA
#define STB_IMAGE_STATIC
#define STB_IMAGE_IMPLEMENTATION

#if defined(_MSC_VER)
#pragma warning(push)
#pragma warning(disable: 4996 4244 4456 4457)
#endif
#include "stb_image.h"
#if defined(_MSC_VER)
#pragma warning(pop)
#endif

uint8_t* aisuper_decode_image_rgba(const uint8_t* data, int len, int* w, int* h) {
    int comp = 0;
    return stbi_load_from_memory(data, len, w, h, &comp, 4);
}

void aisuper_free_image_rgba(uint8_t* px) {
    stbi_image_free(px);
}
