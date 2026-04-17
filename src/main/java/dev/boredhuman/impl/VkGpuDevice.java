package dev.boredhuman.impl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.boredhuman.vulkan.VkBuffer;
import dev.boredhuman.vulkan.VkHelper;
import dev.boredhuman.vulkan.VkImage;
import dev.boredhuman.vulkan.VkImageView;
import dev.boredhuman.vulkan.VkSampler;
import dev.boredhuman.vulkan.VulkanDevice;
import dev.boredhuman.vulkan.VulkanInstance;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VK10;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public class VkGpuDevice implements GpuDevice {

	@Override
	public CommandEncoder createCommandEncoder() {
		return VulkanDevice.getInstance().getCommandEncoder();
	}

	@Override
	public VkSampler createSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter, int maxAnisotropy,
									OptionalDouble maxLod) {
		SamplerCreationData samplerCreationData = new SamplerCreationData(addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);

		return VkSampler.create(samplerCreationData);
	}

	@Override
	public VkImage createTexture(@Nullable Supplier<String> labelSupplier, @GpuTexture.Usage int usage, TextureFormat textureFormat, int width,
								 int height, int depthOrLayers, int mipLevels) {
		String label = labelSupplier == null ? null : labelSupplier.get();
		TextureCreationData textureCreationData = new TextureCreationData(label, usage, textureFormat, width, height, depthOrLayers, mipLevels);
		return VkImage.create(textureCreationData);
	}

	@Override
	public VkImage createTexture(@Nullable String label, @GpuTexture.Usage int usage, TextureFormat textureFormat, int width,
								 int height, int depthOrLayers, int mipLevels) {
		return this.createTexture(label == null ? null : () -> label, usage, textureFormat, width, height, depthOrLayers, mipLevels);
	}

	@Override
	public VkImageView createTextureView(GpuTexture gpuTexture) {
		return this.createTextureView(gpuTexture, 0, gpuTexture.getMipLevels());
	}

	@Override
	public VkImageView createTextureView(GpuTexture gpuTexture, int baseMipLevel, int levelCount) {
		int aspect = gpuTexture.getFormat() == TextureFormat.DEPTH32 ? VK10.VK_IMAGE_ASPECT_DEPTH_BIT : VK10.VK_IMAGE_ASPECT_COLOR_BIT;
		return ((VkImage) gpuTexture).createView(gpuTexture.getLabel() + "-label", aspect, baseMipLevel, levelCount);
	}

	@Override
	public VkBuffer createBuffer(@Nullable Supplier<String> supplier, @GpuBuffer.Usage int usage, long size) {
		String label = supplier == null ? null : supplier.get();
		BufferCreationData creationData = new BufferCreationData(label, usage, size);

		return VkBuffer.create(creationData, false, VkHelper.deviceLocal(usage));
	}

	@Override
	public VkBuffer createBuffer(@Nullable Supplier<String> supplier, @GpuBuffer.Usage int usage, ByteBuffer data) {
		try {
			String label = supplier == null ? null : supplier.get();
			BufferCreationData creationData = new BufferCreationData(label, usage | GpuBuffer.USAGE_COPY_DST, data.remaining());

			VkBuffer buffer = VkBuffer.create(creationData, false, VkHelper.deviceLocal(usage));
			VulkanDevice.getInstance().getCommandEncoder().writeToBuffer(buffer.slice(), data);
			return buffer;
		} catch (Throwable err) {
			throw new RuntimeException(err);
		}
	}

	@Override
	public String getImplementationInformation() {
		return "";
	}

	@Override
	public List<String> getLastDebugMessages() {
		return List.of();
	}

	@Override
	public boolean isDebuggingEnabled() {
		return false;
	}

	@Override
	public String getVendor() {
		return "";
	}

	@Override
	public String getBackendName() {
		return "Vulkan";
	}

	@Override
	public String getVersion() {
		return "";
	}

	@Override
	public String getRenderer() {
		return "";
	}

	@Override
	public int getMaxTextureSize() {
		return VulkanDevice.getInstance().getDeviceLimits().maxTexture;
	}

	@Override
	public int getUniformOffsetAlignment() {
		return VulkanDevice.getInstance().getDeviceLimits().minUniformAlignment;
	}

	@Override
	public VkCompileResult precompilePipeline(RenderPipeline renderPipeline, @Nullable ShaderSource shaderSource) {
		return VulkanDevice.getInstance().createStorePipeline(renderPipeline, shaderSource);
	}

	@Override
	public void clearPipelineCache() {
		VulkanDevice.getInstance().clearPipelineCache();
	}

	@Override
	public List<String> getEnabledExtensions() {
		return List.of();
	}

	@Override
	public int getMaxSupportedAnisotropy() {
		return (int) Math.floor(VulkanDevice.getInstance().getDeviceLimits().maxAnisotropy);
	}

	@Override
	public void close() {
		VulkanInstance.getInstance().delete();
	}
}
