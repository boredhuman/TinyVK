package dev.boredhuman.mixin;

import com.mojang.blaze3d.opengl.GlTextureView;
import dev.boredhuman.types.ImageViewHolder;
import dev.boredhuman.vulkan.VkImageView;
import dev.boredhuman.vulkan.VulkanDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlTextureView.class)
public class GlTextureViewMixin implements ImageViewHolder {
	@Unique
	private VkImageView tinyvk$VkImageView;

	@Override
	public void tinyvk$setImageView(VkImageView vkImageView) {
		this.tinyvk$VkImageView = vkImageView;
	}

	@Override
	public VkImageView tinyvk$getImageView() {
		return this.tinyvk$VkImageView;
	}

	@Inject(method = "close", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlTextureView;texture()Lcom/mojang/blaze3d/opengl/GlTexture;"))
	public void tinyvk$close(CallbackInfo ci) {
		VulkanDevice.getInstance().onFrameEnd(this.tinyvk$VkImageView::close);
		this.tinyvk$VkImageView = null;
	}
}
