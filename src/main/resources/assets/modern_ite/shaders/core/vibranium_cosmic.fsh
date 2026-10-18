#version 150
// Independent procedural stars; view-ray/layer/mask approach studied in Re-Avaritia.
// No upstream artwork or star textures are used. Author: NingMeng123.
#moj_import <fog.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float CosmicTime;
uniform float CosmicYaw;
uniform float CosmicPitch;
in float vertexDistance;
in vec4 vertexColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 cosmicRay;
out vec4 fragColor;
const float PI = 3.14159265359;
mat2 turn(float a) { float s=sin(a),c=cos(a); return mat2(c,-s,s,c); }
float hash(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7))) * 43758.5453); }
float field(vec3 p) {
    return sin(p.x*3.1+sin(p.z*2.7))*sin(p.y*3.7+p.z*.8)*.5+.5;
}
void main() {
    vec4 armor=texture(Sampler0,texCoord0);
    if (armor.a<0.1) discard;
    // Native color is unused: every visible armor texel becomes a cosmic window.
    vec3 ray=normalize(cosmicRay);
    ray.yz=turn(CosmicPitch)*ray.yz;
    ray.xz=turn(-CosmicYaw)*ray.xz;
    float phase=CosmicTime*(2.0*PI/20.0);
    vec3 drift=vec3(sin(phase),cos(phase),sin(phase+1.4))*.16;
    float mist=field(ray*2.3+drift);
    float clouds=pow(mist,3.0)*(.55+.45*field(ray*6.0-drift));
    // Palette sampled from the original vibranium ingot: steel blue, alloy gray and sky blue.
    // Keep the moving cosmic material, without restoring the original armor's painted pattern.
    vec3 shadow=vec3(64.0,64.0,88.0)/255.0;
    vec3 steel=vec3(74.0,92.0,138.0)/255.0;
    vec3 alloy=vec3(110.0,110.0,134.0)/255.0;
    vec3 skyBlue=vec3(119.0,206.0,251.0)/255.0;
    vec3 color=mix(shadow,steel,.45+.55*smoothstep(.15,.85,mist));
    color=mix(color,alloy,.36*field(ray*5.2-drift));
    color=mix(color,skyBlue,clouds*.52);
    for (int layer=0; layer<8; layer++) {
        float n=float(layer);
        vec3 sky=ray;
        sky.xy=turn(n*1.731+.3)*sky.xy;
        sky.yz=turn(n*2.137+.6)*sky.yz;
        vec2 uv=vec2(atan(sky.z,sky.x)/(2.0*PI)+.5,asin(clamp(sky.y,-1.0,1.0))/PI+.5);
        uv*=vec2(43.0+n*9.0,26.0+n*6.0);
        uv+=vec2(sin(phase+n*.3),cos(phase+n*.3))*(.65+n*.11);
        vec2 cell=floor(uv);
        float seed=hash(cell+vec2(n*37.0,n*11.0));
        vec2 center=vec2(.2)+.6*vec2(hash(cell+n*17.0),hash(cell.yx+n*29.0));
        vec2 d=abs(fract(uv)-center);
        float size=.025+.045*hash(cell+n*67.0);
        float star=(1.0-smoothstep(size,size+.035,max(d.x,d.y)));
        // The rare cross is built from square arms, like a Minecraft star sprite.
        float cross=(1.0-smoothstep(.018,.035,min(d.x,d.y)))*(1.0-smoothstep(.09,.15,max(d.x,d.y)));
        star+=cross*step(.88,seed)*.55;
        star*=step(.72,seed)*(.65+.35*sin(phase*2.0+n+seed*20.0));
        vec3 tint=mix(skyBlue,vec3(216.0)/255.0,hash(cell.yx+n));
        color+=tint*star*(.48+n*.045);
    }
    color*=vec3(.8)+vertexColor.rgb*.2;
    color=mix(overlayColor.rgb,color,overlayColor.a);
    fragColor=linear_fog(vec4(clamp(color,0.0,1.0),1.0)*ColorModulator,vertexDistance,FogStart,FogEnd,FogColor);
}
