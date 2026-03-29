package dev.boredhuman.vulkan;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.boredhuman.impl.SamplerCreationData;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;
import java.util.OptionalDouble;

public class VkSampler extends GpuSampler {

	private final SamplerCreationData samplerCreationData;
	public final long sampler;

	public VkSampler(SamplerCreationData samplerCreationData, long sampler) {
		this.samplerCreationData = samplerCreationData;
		this.sampler = sampler;
	}

	@Override
	public AddressMode getAddressModeU() {
		return this.samplerCreationData.addressModeU();
	}

	@Override
	public AddressMode getAddressModeV() {
		return this.samplerCreationData.addressModeV();
	}

	@Override
	public FilterMode getMinFilter() {
		return this.samplerCreationData.minFilter();
	}

	@Override
	public FilterMode getMagFilter() {
		return this.samplerCreationData.magFilter();
	}

	@Override
	public int getMaxAnisotropy() {
		return this.samplerCreationData.maxAnisotropy();
	}

	@Override
	public OptionalDouble getMaxLod() {
		return this.samplerCreationData.maxLod();
	}

	@Override
	public void close() {
		VulkanDevice.getInstance().onFrameEnd(() -> {
			VkDevice device = VulkanDevice.getInstance().vkDevice;
			VK10.vkDestroySampler(device, this.sampler, null);
		});
	}

	public static VkSampler create(SamplerCreationData samplerCreationData) {
		MemoryStack memoryStack = VkHelper.stackPush();

		VkSamplerCreateInfo samplerCreateInfo = VkSamplerCreateInfo.calloc()
			.sType$Default()
			.magFilter(VkSampler.filter(samplerCreationData.magFilter()))
			.minFilter(VkSampler.filter(samplerCreationData.minFilter()))
			.addressModeU(VkSampler.addressMode(samplerCreationData.addressModeU()))
			.addressModeV(VkSampler.addressMode(samplerCreationData.addressModeV()))
			.anisotropyEnable(samplerCreationData.maxAnisotropy() > 1)
			.maxAnisotropy(samplerCreationData.maxAnisotropy())
			.maxLod((float) samplerCreationData.maxLod().orElse(VK10.VK_LOD_CLAMP_NONE));

		LongBuffer samplerHandle = memoryStack.callocLong(1);

		if (VK10.vkCreateSampler(VulkanDevice.getInstance().vkDevice, samplerCreateInfo, null, samplerHandle) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		long sampler = samplerHandle.get(0);

		memoryStack.close();

		return new VkSampler(samplerCreationData, sampler);
	}

	public static int addressMode(AddressMode addressMode) {
		return switch(addressMode) {
			case REPEAT -> VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;
			case CLAMP_TO_EDGE -> VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
		};
	}

	public static int filter(FilterMode filterMode) {
		return switch(filterMode) {
			case LINEAR -> VK10.VK_FILTER_LINEAR;
			case NEAREST -> VK10.VK_FILTER_NEAREST;
		};
	}
}
