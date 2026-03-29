package dev.boredhuman.mixin;

import com.mojang.blaze3d.opengl.GlSampler;
import dev.boredhuman.types.SamplerHolder;
import dev.boredhuman.vulkan.VkSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GlSampler.class)
public class GlSamplerMixin implements SamplerHolder {
	@Unique
	private VkSampler tinyvk$sampler;

	@Override
	public void tinyvk$setSampler(VkSampler sampler) {
		this.tinyvk$sampler = sampler;
	}

	@Override
	public VkSampler tinyvk$getSampler() {
		return this.tinyvk$sampler;
	}


}
