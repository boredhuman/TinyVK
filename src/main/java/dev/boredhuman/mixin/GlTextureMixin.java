package dev.boredhuman.mixin;

import com.mojang.blaze3d.opengl.GlTexture;
import dev.boredhuman.types.ImageHolder;
import dev.boredhuman.vulkan.VkImage;
import dev.boredhuman.vulkan.VulkanDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlTexture.class)
public class GlTextureMixin implements ImageHolder {
	@Unique
	private VkImage tinyvk$VkImage;

	@Override
	public void tinyvk$setImage(VkImage vkImage) {
		this.tinyvk$VkImage = vkImage;
	}

	@Override
	public VkImage tinyvk$getImage() {
		return this.tinyvk$VkImage;
	}

	@Inject(method = "destroyImmediately", at = @At("TAIL"))
	private void tinyvk$deleteImmediately(CallbackInfo ci) {
		VulkanDevice.getInstance().onFrameEnd(this.tinyvk$VkImage::close);
		this.tinyvk$VkImage = null;
	}
}
