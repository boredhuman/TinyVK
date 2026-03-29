package dev.boredhuman.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ShaderResources(List<ShaderResourceSlot> shaderResourceSlots, long descriptorSetLayout, long pipelineLayout) {

	public void free() {
		VkDevice device = VulkanDevice.getInstance().vkDevice;

		VK10.vkDestroyPipelineLayout(device, this.pipelineLayout, null);
		VK10.vkDestroyDescriptorSetLayout(device, this.descriptorSetLayout, null);
	}

	public long createPool() {
		MemoryStack memoryStack = VkHelper.stackPush();

		Map<Integer, Integer> descriptorTypeCounts = this.shaderResourceSlots.stream()
			.collect(Collectors.groupingBy(
				ShaderResourceSlot::descriptorType,
				Collectors.mapping(ShaderResourceSlot::descriptorCount, Collectors.reducing(0, Integer::sum))
			));

		VkDescriptorPoolSize.Buffer descriptorPoolSizes = VkDescriptorPoolSize.calloc(descriptorTypeCounts.size(), memoryStack);

		int i = 0;
		for (Map.Entry<Integer, Integer> descriptorTypeCount : descriptorTypeCounts.entrySet()) {
			int descriptorType = descriptorTypeCount.getKey();
			int descriptorCount = descriptorTypeCount.getValue();

			descriptorPoolSizes.get(i).type(descriptorType).descriptorCount(descriptorCount);

			i++;
		}

		VkDescriptorPoolCreateInfo descriptorPoolCreateInfo = VkDescriptorPoolCreateInfo.calloc(memoryStack)
			.sType$Default()
			.maxSets(1)
			.pPoolSizes(descriptorPoolSizes);

		LongBuffer descriptorPoolHandle = memoryStack.callocLong(1);

		VkDevice device = VulkanDevice.getInstance().vkDevice;

		if (VK10.vkCreateDescriptorPool(device, descriptorPoolCreateInfo, null, descriptorPoolHandle) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		long descriptorPool = descriptorPoolHandle.get(0);

		memoryStack.close();

		return descriptorPool;
	}

	public static Builder create() {
		return new Builder();
	}

	public static class Builder {
		private final List<ShaderResourceSlot> shaderResourceSlots = new ArrayList<>();
		private int stage;
		private String label;

		public Builder stageVertFrag(boolean vertex, boolean frag) {
			this.stage = 0;
			if (vertex) {
				this.stage = VK10.VK_SHADER_STAGE_VERTEX_BIT;
			}
			if (frag) {
				this.stage |= VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
			}

			return this;
		}

		public Builder add(int binding, int descriptorType) {
			this.shaderResourceSlots.add(new ShaderResourceSlot(this.stage, binding, descriptorType, 1));
			return this;
		}

		public Builder label(String label) {
			this.label = label;
			return this;
		}

		public ShaderResources build() {
			MemoryStack memoryStack = VkHelper.stackPush();

			VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(this.shaderResourceSlots.size(), memoryStack);

			for (int i = 0; i < this.shaderResourceSlots.size(); i++) {
				ShaderResourceSlot shaderResourceSlot = this.shaderResourceSlots.get(i);

				bindings
					.get(i)
					.binding(shaderResourceSlot.binding())
					.descriptorType(shaderResourceSlot.descriptorType())
					.descriptorCount(shaderResourceSlot.descriptorCount())
					.stageFlags(shaderResourceSlot.stage());
			}

			VkDescriptorSetLayoutCreateInfo setLayoutCreateInfo = VkDescriptorSetLayoutCreateInfo.calloc(memoryStack)
				.sType$Default()
				.flags(KHRPushDescriptor.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR)
				.pBindings(bindings);

			LongBuffer descriptorSetLayoutHandle = memoryStack.callocLong(1);

			VkDevice device = VulkanDevice.getInstance().vkDevice;

			if (VK10.vkCreateDescriptorSetLayout(device, setLayoutCreateInfo, null, descriptorSetLayoutHandle) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			long descriptorSetLayout = descriptorSetLayoutHandle.get(0);

			if (this.label != null) {
				VkDebugUtilsObjectNameInfoEXT objectName = VkDebugUtilsObjectNameInfoEXT.calloc(memoryStack)
					.sType$Default()
					.objectType(VK10.VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT)
					.objectHandle(descriptorSetLayout)
					.pObjectName(memoryStack.UTF8(this.label));

				EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(device, objectName);
			}


			VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(memoryStack)
				.sType$Default()
				.pSetLayouts(memoryStack.longs(descriptorSetLayout));

			LongBuffer pipelineLayoutHandle = memoryStack.callocLong(1);

			if (VK10.vkCreatePipelineLayout(device, pipelineLayoutCreateInfo, null, pipelineLayoutHandle) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			long pipelineLayout = pipelineLayoutHandle.get(0);

			memoryStack.close();

			return new ShaderResources(this.shaderResourceSlots, descriptorSetLayout, pipelineLayout);
		}
	}
}
