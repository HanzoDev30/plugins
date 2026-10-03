#version 460 core

layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;

layout(location = 0) in vec3 inColor[];
layout(location = 0) out vec3 outColor;

uniform float thickness;
uniform mat4 viewProjection;

void emitVertex(vec3 position, vec3 color)
{
    gl_Position = viewProjection * vec4(position, 1.0);
    outColor = color;
    EmitVertex();
}

void main()
{
    for (int i = 0; i < 3; i++) {
        emitVertex(gl_in[i].gl_Position.xyz, inColor[i]);
    }
    EndPrimitive();
}
