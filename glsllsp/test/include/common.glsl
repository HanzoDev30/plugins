#ifndef COMMON_GLSL
#define COMMON_GLSL

#define TAU 6.28318530718
#define PI 3.14159265359
#define SATURATE(x) clamp(x, 0.0, 1.0)

vec3 srgbToLinear(vec3 color)
{
    return pow(color, vec3(2.2));
}

vec3 linearToSrgb(vec3 color)
{
    return pow(color, vec3(1.0 / 2.2));
}

float luminance(vec3 color)
{
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

float fresnelSchlick(float cosTheta, float f0)
{
    return f0 + (1.0 - f0) * pow(SATURATE(1.0 - cosTheta), 5.0);
}

#endif
