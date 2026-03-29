package dev.boredhuman.mixin;

import com.mojang.blaze3d.opengl.GlBuffer;
import dev.boredhuman.types.BufferHolder;
import dev.boredhuman.vulkan.VkBuffer;
import dev.boredhuman.vulkan.VulkanDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlBuffer.class)
public class GlBufferMixin implements BufferHolder {
	@Unique
	private VkBuffer tinyvk$VkBuffer;

	@Override
	public void tinyvk$setBuffer(VkBuffer vkBuffer) {
		this.tinyvk$VkBuffer = vkBuffer;
	}

	@Override
	public VkBuffer tinyvk$getBuffer() {
		return this.tinyvk$VkBuffer;
	}

	@Inject(method = "close", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_glDeleteBuffers(I)V"))
	private void tinyvk$delete(CallbackInfo ci) {
		VulkanDevice.getInstance().onFrameEnd(this.tinyvk$VkBuffer::close);
		this.tinyvk$VkBuffer = null;
	}
}
