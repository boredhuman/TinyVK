package dev.boredhuman.impl;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;

import java.util.OptionalDouble;

public record SamplerCreationData(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter, int maxAnisotropy,
								  OptionalDouble maxLod) {
}
