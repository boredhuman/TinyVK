package dev.boredhuman.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.boredhuman.impl.VkRenderPass;
import dev.boredhuman.types.ImageHolder;
import dev.boredhuman.types.ImageViewHolder;
import dev.boredhuman.types.MappedViewHolder;
import dev.boredhuman.types.RenderPassHolder;
import dev.boredhuman.vulkan.VkBuffer;
import dev.boredhuman.vulkan.VkImage;
import dev.boredhuman.vulkan.VkImageView;
import dev.boredhuman.vulkan.VulkanDevice;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

@Mixin(GlCommandEncoder.class)
public abstract class GlCommandEncoderMixin {

	@Inject(method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;)V", at = @At("TAIL"))
	private void tinyvk$writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage, CallbackInfo ci) {
		VkImage image = ((ImageHolder) gpuTexture).tinyvk$getImage();
		VulkanDevice.getInstance().getCommandEncoder().writeToTexture(image, nativeImage);
	}

	@Inject(method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;IIIIIIII)V", at = @At("TAIL"))
	private void tinyvk$writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage, int mipLevel, int layer, int destX, int destY, int width, int height,
									   int sourceX, int sourceY, CallbackInfo ci) {
		VkImage image = ((ImageHolder) gpuTexture).tinyvk$getImage();
		VulkanDevice.getInstance().getCommandEncoder().writeToTexture(image, nativeImage, mipLevel, layer, destX, destY, width, height, sourceX, sourceY);
	}

	@Inject(
		method = "writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Ljava/nio/ByteBuffer;Lcom/mojang/blaze3d/platform/NativeImage$Format;IIIIII)V",
		at = @At("TAIL")
	)
	private void tinykv$writeToTexture(GpuTexture gpuTexture, ByteBuffer byteBuffer, NativeImage.Format format, int mipLevel, int layer, int destX, int destY,
									   int width, int height, CallbackInfo ci) {
		VkImage image = ((ImageHolder) gpuTexture).tinyvk$getImage();
		VulkanDevice.getInstance().getCommandEncoder().writeToTexture(image, byteBuffer, format, mipLevel, layer, destX, destY, width, height);
	}

	@Inject(method = "writeToBuffer", at = @At("TAIL"))
	private void tinyvk$writeToBuffer(GpuBufferSlice gpuBufferSlice, ByteBuffer data, CallbackInfo ci) {
		VulkanDevice.getInstance().getCommandEncoder().writeToBuffer(gpuBufferSlice, data);
	}

	@Inject(method = "mapBuffer(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZZ)Lcom/mojang/blaze3d/buffers/GpuBuffer$MappedView;", at = @At("TAIL"))
	private void tinyvk$mapBuffer(GpuBufferSlice gpuBufferSlice, boolean read, boolean write, CallbackInfoReturnable<GpuBuffer.MappedView> cir) {
		MappedViewHolder mappedViewHolder = (MappedViewHolder) cir.getReturnValue();
		VkBuffer.VkMappedView mappedView = VulkanDevice.getInstance().getCommandEncoder().mapBuffer(gpuBufferSlice, read, write);
		mappedViewHolder.tinyvk$setMappedView(mappedView);
	}

	@Inject(method = "clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;DIIII)V", at = @At("TAIL"))
	private void tinyvk$clearColorDepthRegion(GpuTexture colorTexture, int colorClear, GpuTexture depthTexture, double clearDepth, int x, int y, int width,
											  int height, CallbackInfo ci) {
		VkImage colorImage = ((ImageHolder) colorTexture).tinyvk$getImage();
		VkImage depthImage = ((ImageHolder) depthTexture).tinyvk$getImage();

		VulkanDevice.getInstance().getCommandEncoder().clearColorAndDepthTextures(colorImage, colorClear, depthImage, clearDepth, x, y, width, height);
	}

	@Inject(method = "clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;D)V", at = @At("TAIL"))
	private void tinyvk$clearColorDepth(GpuTexture colorTexture, int clearColor, GpuTexture depthTexture, double clearDepth, CallbackInfo ci) {
		this.tinyvk$clearColor(colorTexture, clearColor, null);
		this.tinyvk$clearDepth(depthTexture, clearDepth, null);
	}

	@Inject(method = "clearColorTexture", at = @At("TAIL"))
	private void tinyvk$clearColor(GpuTexture colorTexture, int colorClear, CallbackInfo ci) {
		VkImage image = ((ImageHolder) colorTexture).tinyvk$getImage();
		VulkanDevice.getInstance().getCommandEncoder().clearColorTexture(image, colorClear);
	}

	@Inject(method = "clearDepthTexture", at = @At("TAIL"))
	private void tinyvk$clearDepth(GpuTexture depthTexture, double depthClear, CallbackInfo ci) {
		VkImage image = ((ImageHolder) depthTexture).tinyvk$getImage();
		VulkanDevice.getInstance().getCommandEncoder().clearDepthTexture(image, depthClear);
	}

	@Inject(
		method = "createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;" +
				 "Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;", at = @At("TAIL")
	)
	private void tinyvk$createRenderPass(Supplier<String> supplier, GpuTextureView colorView, OptionalInt clearColor, @Nullable GpuTextureView depthView,
										 OptionalDouble clearDepth, CallbackInfoReturnable<RenderPass> cir) {
		VkImageView vkColorView = ((ImageViewHolder) colorView).tinyvk$getImageView();
		VkImageView vkDepthView = depthView == null ? null : ((ImageViewHolder) depthView).tinyvk$getImageView();

		VkRenderPass renderPass = VulkanDevice.getInstance().getCommandEncoder().createRenderPass(supplier, vkColorView, clearColor, vkDepthView, clearDepth);

		((RenderPassHolder) cir.getReturnValue()).tinyvk$setRenderPass(renderPass);
	}

	@Inject(method = "finishRenderPass", at = @At("TAIL"))
	private void tinyvk$finishRenderPass(CallbackInfo ci) {
		VulkanDevice.getInstance().renderPassEnd();
	}

	@Inject(method = "presentTexture", at = @At("TAIL"))
	private void tinyvk$presentTexture(GpuTextureView gpuTextureView, CallbackInfo ci) {
		VkImageView vkImageView = ((ImageViewHolder) gpuTextureView).tinyvk$getImageView();
		VulkanDevice.getInstance().endFrame(vkImageView);
	}
}
