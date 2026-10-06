// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.device;
import com.mojang.blaze3d.GpuFormat;
/** Explicit constants from Apple's Metal SDK; legacy JNI format IDs 0..3 remain unchanged. */
final class MetalFormats {
    private MetalFormats() {}
    static int texture(GpuFormat f) { return 1000 + switch (f) {
        case R8_UNORM -> 10;
        case R8_SNORM -> 12;
        case R8_UINT -> 13;
        case R8_SINT -> 14;
        case R16_UNORM -> 20;
        case R16_SNORM -> 22;
        case R16_FLOAT -> 25;
        case RG8_UNORM -> 30;
        case RG8_SNORM -> 32;
        case RG8_UINT -> 33;
        case RG8_SINT -> 34;
        case R32_UINT -> 53;
        case R32_SINT -> 54;
        case R32_FLOAT -> 55;
        case RG16_UNORM -> 60;
        case RG16_SNORM -> 62;
        case RG16_UINT -> 63;
        case RG16_SINT -> 64;
        case RG16_FLOAT -> 65;
        case RGBA8_UNORM -> 70;
        case RGBA8_SNORM -> 72;
        case RGBA8_UINT -> 73;
        case RGBA8_SINT -> 74;
        case RGB10A2_UNORM -> 90;
        case RGB10A2_UINT -> 91;
        case RG11B10_FLOAT -> 92;
        case RG32_UINT -> 103;
        case RG32_SINT -> 104;
        case RG32_FLOAT -> 105;
        case RGBA16_UNORM -> 110;
        case RGBA16_SNORM -> 112;
        case RGBA16_FLOAT -> 115;
        case RGBA32_UINT -> 123;
        case RGBA32_SINT -> 124;
        case RGBA32_FLOAT -> 125;
        case D16_UNORM -> 250;
        case D32_FLOAT -> 252;
        default -> throw new UnsupportedOperationException("Unsupported Metal texture format: " + f);
    }; }
    static int vertex(GpuFormat f) { return switch (f) {
        case R8_UINT -> 45;
        case R8_SINT -> 46;
        case R8_UNORM -> 47;
        case R8_SNORM -> 48;
        case R16_UNORM -> 51;
        case R16_SNORM -> 52;
        case R16_FLOAT -> 53;
        case R32_FLOAT -> 28;
        case R32_SINT -> 32;
        case R32_UINT -> 36;
        case RGB10A2_UNORM -> 41;
        case RG11B10_FLOAT -> 54;
        case RG8_UINT -> 1;
        case RGB8_UINT -> 2;
        case RGBA8_UINT -> 3;
        case RG8_SINT -> 4;
        case RGB8_SINT -> 5;
        case RGBA8_SINT -> 6;
        case RG8_UNORM -> 7;
        case RGB8_UNORM -> 8;
        case RGBA8_UNORM -> 9;
        case RG8_SNORM -> 10;
        case RGB8_SNORM -> 11;
        case RGBA8_SNORM -> 12;
        case RG16_UINT -> 13;
        case RGB16_UINT -> 14;
        case RGBA16_UINT -> 15;
        case RG16_SINT -> 16;
        case RGB16_SINT -> 17;
        case RGBA16_SINT -> 18;
        case RG16_UNORM -> 19;
        case RGB16_UNORM -> 20;
        case RGBA16_UNORM -> 21;
        case RG16_SNORM -> 22;
        case RGB16_SNORM -> 23;
        case RGBA16_SNORM -> 24;
        case RG16_FLOAT -> 25;
        case RGB16_FLOAT -> 26;
        case RGBA16_FLOAT -> 27;
        case RG32_FLOAT -> 29;
        case RGB32_FLOAT -> 30;
        case RGBA32_FLOAT -> 31;
        case RG32_SINT -> 33;
        case RGB32_SINT -> 34;
        case RGBA32_SINT -> 35;
        case RG32_UINT -> 37;
        case RGB32_UINT -> 38;
        case RGBA32_UINT -> 39;
        default -> throw new UnsupportedOperationException("Unsupported Metal vertex format: " + f);
    }; }
}
