#version 460 core
#extension GL_GOOGLE_include_directive : require

#include "include/common.glsl"

layout(location = 0) in vec3 fragWorldPos;
layout(location = 1) in vec3 fragNormal;
layout(location = 2) in vec2 fragTexCoord;

layout(location = 0) out vec4 outColor;

uniform sampler2D albedoMap;
uniform sampler2D normalMap;
uniform sampler2D metallicRoughnessMap;

uniform vec3 cameraPos;
uniform vec3 lightDirection;
uniform vec3 lightColor;
uniform float lightIntensity;

uniform float metallic;
uniform float roughness;
uniform float ambientOcclusion;

struct Surface
{
    vec3 albedo;
    vec3 normal;
    float metallic;
    float roughness;
};

Surface sampleSurface()
{
    Surface surface;
    surface.albedo = srgbToLinear(texture(albedoMap, fragTexCoord).rgb);
    surface.normal = normalize(texture(normalMap, fragTexCoord).rgb * 2.0 - 1.0);
    vec2 metallicRoughness = texture(metallicRoughnessMap, fragTexCoord).rg;
    surface.metallic = metallicRoughness.x;
    surface.roughness = metallicRoughness.y;
    return surface;
}

vec3 fresnelSchlickRoughness(float cosTheta, vec3 f0, float roughness)
{
    vec3 f90 = max(vec3(1.0 - roughness), f0);
    return f0 + (f90 - f0) * pow(SATURATE(1.0 - cosTheta), 5.0);
}

vec3 shadeCookTorrance(Surface surface, vec3 viewDir, vec3 lightDir, vec3 radiance)
{
    vec3 halfDir = normalize(viewDir + lightDir);

    float nDotL = max(dot(surface.normal, lightDir), 0.0);
    float nDotV = max(dot(surface.normal, viewDir), 1e-4);
    float nDotH = max(dot(surface.normal, halfDir), 0.0);
    float vDotH = max(dot(viewDir, halfDir), 0.0);

    float alpha = max(surface.roughness * surface.roughness, 0.001);
    float alphaSquared = alpha * alpha;
    float denominator = nDotH * nDotH * (alphaSquared - 1.0) + 1.0;
    float distribution = alphaSquared / (PI * denominator * denominator);

    float k = (surface.roughness + 1.0) * (surface.roughness + 1.0) / 8.0;
    float geometry = nDotV / (nDotV * (1.0 - k) + k) * (nDotL / (nDotL * (1.0 - k) + k));

    vec3 f0 = mix(vec3(0.04), surface.albedo, surface.metallic);
    vec3 fresnel = fresnelSchlickRoughness(vDotH, f0, surface.roughness);

    vec3 numerator = distribution * geometry * fresnel;
    float denominatorSpecular = max(4.0 * nDotV * nDotL, 1e-4);
    vec3 specular = numerator / denominatorSpecular;

    vec3 diffuseWeight = (vec3(1.0) - fresnel) * (1.0 - surface.metallic);
    return (diffuseWeight * surface.albedo / PI + specular) * radiance * nDotL;
}

void main()
{
    Surface surface = sampleSurface();
    vec3 viewDir = normalize(cameraPos - fragWorldPos);
    vec3 lightDir = normalize(lightDirection);
    vec3 radiance = lightColor * lightIntensity;

    vec3 color = shadeCookTorrance(surface, viewDir, lightDir, radiance);
    color += surface.albedo * ambientOcclusion * 0.03;
    color = linearToSrgb(color);

    outColor = vec4(color, 1.0);
}
