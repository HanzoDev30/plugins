#version 460 core

layout(quads, equal_spacing, ccw) in;

layout(location = 0) in vec3 inPosition[];
layout(location = 0) out vec3 outPosition;

void main()
{
    float p0 = gl_TessCoord.x;
    float p1 = gl_TessCoord.y;
    float p2 = gl_TessCoord.z;

    vec3 position = p0 * inPosition[0] + p1 * inPosition[1] + p2 * inPosition[2];
    outPosition = position;

    gl_Position = gl_in[0].gl_Position * gl_TessCoord.x
                + gl_in[1].gl_Position * gl_TessCoord.y
                + gl_in[2].gl_Position * gl_TessCoord.z;
}
