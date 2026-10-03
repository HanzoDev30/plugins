#ifndef NOISE_GLSL
#define NOISE_GLSL

float hash11(float p)
{
    return fract(sin(p * 127.1) * 43758.5453123);
}

float valueNoise(vec2 p)
{
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);

    float a = hash11(dot(i, vec2(1.0, 0.0)));
    float b = hash11(dot(i, vec2(0.0, 1.0)));
    float c = hash11(dot(i + vec2(1.0, 0.0), vec2(1.0, 0.0)));
    float d = hash11(dot(i + vec2(0.0, 1.0), vec2(1.0, 0.0)));

    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p)
{
    float sum = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 5; octave++) {
        sum += valueNoise(p) * amplitude;
        p *= 2.03;
        amplitude *= 0.5;
    }
    return sum;
}

#endif
