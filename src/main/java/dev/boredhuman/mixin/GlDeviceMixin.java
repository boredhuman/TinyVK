package dev.boredhuman.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.boredhuman.types.BufferHolder;
import dev.boredhuman.types.ImageHolder;
import dev.boredhuman.types.ImageViewHolder;
import dev.boredhuman.types.SamplerHolder;
import dev.boredhuman.vulkan.VkBuffer;
import dev.boredhuman.vulkan.VkImage;
import dev.boredhuman.vulkan.VkImageView;
import dev.boredhuman.vulkan.VkSampler;
import dev.boredhuman.vulkan.VulkanDevice;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(GlDevice.class)
public class GlDeviceMixin {

	@Inject(method = "createSampler", at = @At("TAIL"))
	private void tinyvk$createSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter, int maxAnisotropy,
									  OptionalDouble maxLod, CallbackInfoReturnable<GpuSampler> cir) {
		VkSampler sampler = VulkanDevice.getInstance().getImpl().createSampler(addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
		((SamplerHolder) cir.getReturnValue()).tinyvk$setSampler(sampler);
	}

	@Inject(
		method = "createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/textures/TextureFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;", at = @At("TAIL")
	)
	private void tinyvk$createTexture(@Nullable String label, @GpuTexture.Usage int usage, TextureFormat textureFormat, int width, int height,
									  int depthOrLayers, int mipLevels,
									  CallbackInfoReturnable<GpuTexture> cir) {
		VkImage texture = VulkanDevice.getInstance().getImpl().createTexture(label, usage, textureFormat, width, height, depthOrLayers, mipLevels);
		GpuTexture gpuTexture = cir.getReturnValue();
		((ImageHolder) gpuTexture).tinyvk$setImage(texture);
	}

	@Inject(method = "createTextureView(Lcom/mojang/blaze3d/textures/GpuTexture;II)Lcom/mojang/blaze3d/textures/GpuTextureView;", at = @At("TAIL"))
	private void tinyvk$createTextureView(GpuTexture gpuTexture, int baseMipLevel, int levelCount, CallbackInfoReturnable<GpuTextureView> cir) {
		ImageViewHolder imageViewHolder = (ImageViewHolder) cir.getReturnValue();
		VkImage vkImage = ((ImageHolder) gpuTexture).tinyvk$getImage();
		VkImageView vkImageView = VulkanDevice.getInstance().getImpl().createTextureView(vkImage, baseMipLevel, levelCount);
		imageViewHolder.tinyvk$setImageView(vkImageView);
	}

	@Inject(method = "createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;", at = @At("TAIL"))
	private void tinyvk$createBuffer(@Nullable Supplier<String> supplier, @GpuBuffer.Usage int usage, ByteBuffer data, CallbackInfoReturnable<GpuBuffer> cir) {
		BufferHolder bufferHolder = (BufferHolder) cir.getReturnValue();
		VkBuffer vkBuffer = VulkanDevice.getInstance().getImpl().createBuffer(supplier, usage, data);
		bufferHolder.tinyvk$setBuffer(vkBuffer);
	}

	@Inject(method = "createBuffer(Ljava/util/function/Supplier;IJ)Lcom/mojang/blaze3d/buffers/GpuBuffer;", at = @At("TAIL"))
	private void tinyvk$createBuffer(@Nullable Supplier<String> supplier, @GpuBuffer.Usage int usage, long size, CallbackInfoReturnable<GpuBuffer> cir) {
		BufferHolder bufferHolder = (BufferHolder) cir.getReturnValue();
		VkBuffer vkBuffer = VulkanDevice.getInstance().getImpl().createBuffer(supplier, usage, size);
		bufferHolder.tinyvk$setBuffer(vkBuffer);
	}

	@Inject(method = "compilePipeline", at = @At("TAIL"))
	private void tinyVK$compileProgram(RenderPipeline renderPipeline, ShaderSource shaderSource, CallbackInfoReturnable<GlRenderPipeline> cir) {
		VulkanDevice.getInstance().createStorePipeline(renderPipeline, shaderSource);
	}
}
