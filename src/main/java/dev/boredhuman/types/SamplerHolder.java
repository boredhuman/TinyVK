package dev.boredhuman.types;

import dev.boredhuman.vulkan.VkSampler;

public interface SamplerHolder {
	void tinyvk$setSampler(VkSampler sampler);

	VkSampler tinyvk$getSampler();
}
