package dev.boredhuman.impl;

import com.mojang.blaze3d.buffers.GpuFence;
import dev.boredhuman.vulkan.VulkanDevice;

public class VkFence implements GpuFence {

	private final long ownerFrame = VulkanDevice.getInstance().getCurrentFrame();
	private boolean closed;

	@Override
	public void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
	}

	@Override
	public boolean awaitCompletion(long timeout) {
		if (this.closed) {
			return true;
		} else {
			return VulkanDevice.getInstance().ensureFrameFinished(this.ownerFrame, timeout);
		}
	}
}
