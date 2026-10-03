#version 460 core

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inTexCoord;

layout(location = 0) out vec3 fragWorldPos;
layout(location = 1) out vec3 fragNormal;
layout(location = 2) out vec2 fragTexCoord;

uniform mat4 model;
uniform mat4 viewProjection;
uniform mat3 normalMatrix;

void main()
{
    vec4 worldPos = model * vec4(inPosition, 1.0);
    fragWorldPos = worldPos.xyz;
    fragNormal = normalize(normalMatrix * inNormal);
    fragTexCoord = inTexCoord;

    gl_Position = viewProjection * worldPos;
}
