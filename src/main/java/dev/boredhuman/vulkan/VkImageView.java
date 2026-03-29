package dev.boredhuman.vulkan;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.vulkan.VK10;

public class VkImageView extends GpuTextureView {
	public final VkImage vkImage;
	public final long imageView;
	private boolean closed = false;

	public VkImageView(VkImage vkImage, long imageView, int baseMip, int mipLevels) {
		super(vkImage, baseMip, mipLevels);
		this.vkImage = vkImage;
		this.imageView = imageView;
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}
		VulkanDevice.getInstance().onFrameEnd(() -> VK10.vkDestroyImageView(VulkanDevice.getInstance().vkDevice, this.imageView, null));
		this.closed = true;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}
}
