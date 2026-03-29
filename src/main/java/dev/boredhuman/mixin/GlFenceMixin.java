package dev.boredhuman.mixin;

import com.mojang.blaze3d.opengl.GlFence;
import dev.boredhuman.vulkan.VulkanDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlFence.class)
public class GlFenceMixin {

	@Unique
	private long ownerFrame;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void tinyvk$init(CallbackInfo ci) {
		this.ownerFrame = VulkanDevice.getInstance().getCurrentFrame();
	}

	@Inject(method = "awaitCompletion", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_glClientWaitSync(JIJ)I"))
	private void tinyvk$wait(long timeout, CallbackInfoReturnable<Boolean> cir) {
		VulkanDevice.getInstance().ensureFrameFinished(this.ownerFrame, timeout);
	}
}
