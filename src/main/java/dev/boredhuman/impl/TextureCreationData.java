package dev.boredhuman.impl;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import org.jspecify.annotations.Nullable;

public record TextureCreationData(@Nullable String label, @GpuTexture.Usage int usage, TextureFormat textureFormat, int width,
								  int height, int depthOrLayers, int mipLevels) {
}
