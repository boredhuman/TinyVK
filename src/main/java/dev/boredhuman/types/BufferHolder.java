package dev.boredhuman.types;

import dev.boredhuman.vulkan.VkBuffer;

public interface BufferHolder {
	void tinyvk$setBuffer(VkBuffer vkBuffer);

	VkBuffer tinyvk$getBuffer();
}
