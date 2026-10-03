#version 460 core

#ifndef ITEM1_GLSL
#define ITEM1_GLSL

#extension GL_GOOGLE_include_directive : require

#include "include/noise.glsl"

layout(location = 0) in vec2 inPosition;
layout(location = 1) in float inSeed;

layout(location = 0) out float outHeight;

uniform float time;
uniform float amplitude;

void main()
{
    float height = fbm(inPosition * 4.0 + inSeed + time * 0.2);
    outHeight = height;

    gl_Position = vec4(inPosition + vec2(0.0, height * amplitude), 0.0, 1.0);
}

#endif
