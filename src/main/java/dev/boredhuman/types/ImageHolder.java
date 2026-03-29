package dev.boredhuman.types;

import dev.boredhuman.vulkan.VkImage;

public interface ImageHolder {
	void tinyvk$setImage(VkImage vkImage);

	VkImage tinyvk$getImage();
}
