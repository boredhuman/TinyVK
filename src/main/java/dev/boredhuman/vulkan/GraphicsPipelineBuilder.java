package dev.boredhuman.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

public class GraphicsPipelineBuilder {

	private final List<Shader> shaders = new ArrayList<>();
	private final List<Attribute> attributes = new ArrayList<>();
	private final List<Binding> bindings = new ArrayList<>();
	private long pipelineLayout;
	private int depthCompareOp;
	private boolean depthTest;
	private int polygonMode;
	private int cullMode;
	private boolean enableLogicOp;
	private int logicOp;
	private boolean blend;
	private int srcColorBlendFactor;
	private int dstColorBlendFactor;
	private int srcAlphaBlendFactor;
	private int dstAlphaBlendFactor;
	private int writeColorMasks;
	private boolean depthWrite;
	private float depthBiasConstantFactor;
	private float depthBiasSlopeFactor;
	private boolean depthBias;
	private int topology;
	private String label;
	private VkPipelineRenderingCreateInfoKHR pipelineRenderingCreateInfoKHR;

	public static GraphicsPipelineBuilder create() {
		return new GraphicsPipelineBuilder();
	}

	public GraphicsPipelineBuilder shader(int stage, long module) {
		this.shaders.add(new Shader(stage, module));
		return this;
	}

	public GraphicsPipelineBuilder attribute(int location, int binding, int format, int offset) {
		this.attributes.add(new Attribute(location, binding, format, offset));
		return this;
	}

	public GraphicsPipelineBuilder binding(int binding, int stride, int inputRate) {
		this.bindings.add(new Binding(binding, stride, inputRate));
		return this;
	}

	public GraphicsPipelineBuilder pipelineLayout(long pipelineLayout) {
		this.pipelineLayout = pipelineLayout;
		return this;
	}

	public GraphicsPipelineBuilder depthCompareOp(int depthCompareOp) {
		this.depthCompareOp = depthCompareOp;
		return this;
	}

	public GraphicsPipelineBuilder depthTest(boolean depthTest) {
		this.depthTest = depthTest;
		return this;
	}

	public GraphicsPipelineBuilder polygonMode(int polygonMode) {
		this.polygonMode = polygonMode;
		return this;
	}

	public GraphicsPipelineBuilder cullMode(int cullMode) {
		this.cullMode = cullMode;
		return this;
	}

	public GraphicsPipelineBuilder logicOp(boolean logicOp) {
		this.enableLogicOp = logicOp;
		return this;
	}

	public GraphicsPipelineBuilder logicCompareOp(int logicCompareOp) {
		this.logicOp = logicCompareOp;
		return this;
	}

	public GraphicsPipelineBuilder blend(boolean blend) {
		this.blend = blend;
		return this;
	}

	public GraphicsPipelineBuilder srcColorBlendFactor(int srcColorBlendFactor) {
		this.srcColorBlendFactor = srcColorBlendFactor;
		return this;
	}

	public GraphicsPipelineBuilder dstColorBlendFactor(int dstColorBlendFactor) {
		this.dstColorBlendFactor = dstColorBlendFactor;
		return this;
	}

	public GraphicsPipelineBuilder srcAlphaBlendFactor(int srcAlphaBlendFactor) {
		this.srcAlphaBlendFactor = srcAlphaBlendFactor;
		return this;
	}

	public GraphicsPipelineBuilder dstAlphaBlendFactor(int dstAlphaBlendFactor) {
		this.dstAlphaBlendFactor = dstAlphaBlendFactor;
		return this;
	}

	public GraphicsPipelineBuilder writeColor(boolean writeColor) {
		if (writeColor) {
			this.writeColorMasks |= VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT | VK10.VK_COLOR_COMPONENT_B_BIT;
		} else {
			this.writeColorMasks &= ~(VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT | VK10.VK_COLOR_COMPONENT_B_BIT);
		}
		return this;
	}

	public GraphicsPipelineBuilder writeAlpha(boolean writeAlpha) {
		if (writeAlpha) {
			this.writeColorMasks |= VK10.VK_COLOR_COMPONENT_A_BIT;
		} else {
			this.writeColorMasks &= ~VK10.VK_COLOR_COMPONENT_A_BIT;
		}
		return this;
	}

	public GraphicsPipelineBuilder writeDepth(boolean writeDepth) {
		this.depthWrite = writeDepth;
		return this;
	}

	public GraphicsPipelineBuilder depthBiasConstantFactor(float depthBiasConstantFactor) {
		this.depthBiasConstantFactor = depthBiasConstantFactor;
		return this;
	}

	public GraphicsPipelineBuilder depthBiasSlopeFactor(float depthBiasSlopeFactor) {
		this.depthBiasSlopeFactor = depthBiasSlopeFactor;
		return this;
	}

	public GraphicsPipelineBuilder depthBias(boolean depthBias) {
		this.depthBias = depthBias;
		return this;
	}

	public GraphicsPipelineBuilder topology(int topology) {
		this.topology = topology;
		return this;
	}

	public GraphicsPipelineBuilder label(String label) {
		this.label = label;
		return this;
	}

	public GraphicsPipelineBuilder pipelineRenderingCreateInfoKHR(VkPipelineRenderingCreateInfoKHR pipelineRenderingCreateInfoKHR) {
		this.pipelineRenderingCreateInfoKHR = pipelineRenderingCreateInfoKHR;
		return this;
	}

	public long build() {
		MemoryStack memoryStack = VkHelper.stackPush();

		VkPipelineShaderStageCreateInfo.Buffer pipelineShaderStageCreateInfo;

		if (!this.shaders.isEmpty()) {
			pipelineShaderStageCreateInfo = VkPipelineShaderStageCreateInfo.calloc(this.shaders.size(), memoryStack);

			for (int i = 0; i < this.shaders.size(); i++) {
				Shader shader = this.shaders.get(i);

				pipelineShaderStageCreateInfo
					.get(i)
					.sType$Default()
					.stage(shader.stage)
					.module(shader.module)
					.pName(memoryStack.UTF8("main"));
			}
		} else {
			pipelineShaderStageCreateInfo = null;
		}

		VkVertexInputAttributeDescription.Buffer attributeDescriptions;

		if (!this.attributes.isEmpty()) {
			attributeDescriptions = VkVertexInputAttributeDescription.calloc(this.attributes.size(), memoryStack);

			for (int i = 0; i < this.attributes.size(); i++) {
				Attribute attribute = this.attributes.get(i);

				attributeDescriptions
					.get(i)
					.location(attribute.location)
					.binding(attribute.binding)
					.format(attribute.format)
					.offset(attribute.offset);
			}
		} else {
			attributeDescriptions = null;
		}

		VkVertexInputBindingDescription.Buffer bindingDescriptions;

		if (!this.bindings.isEmpty()) {
			bindingDescriptions = VkVertexInputBindingDescription.calloc(this.bindings.size(), memoryStack);

			for (int i = 0; i < this.bindings.size(); i++) {
				Binding binding = this.bindings.get(i);

				bindingDescriptions
					.get(i)
					.binding(binding.binding)
					.stride(binding.stride)
					.inputRate(binding.inputRate);
			}
		} else {
			bindingDescriptions = null;
		}

		VkPipelineVertexInputStateCreateInfo vertexInputStateCreateInfo = VkPipelineVertexInputStateCreateInfo.calloc(memoryStack)
			.sType$Default()
			.pVertexAttributeDescriptions(attributeDescriptions)
			.pVertexBindingDescriptions(bindingDescriptions);

		VkPipelineInputAssemblyStateCreateInfo inputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.calloc(memoryStack)
			.sType$Default()
			.topology(this.topology);

		VkPipelineViewportStateCreateInfo viewportStateCreateInfo = VkPipelineViewportStateCreateInfo.calloc(memoryStack)
			.sType$Default()
			.viewportCount(1)
			.scissorCount(1);

		VkPipelineRasterizationStateCreateInfo rasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.calloc(memoryStack)
			.sType$Default()
			.depthClampEnable(false)
			.rasterizerDiscardEnable(false)
			.polygonMode(this.polygonMode)
			.cullMode(this.cullMode)
			.frontFace(VK10.VK_FRONT_FACE_CLOCKWISE)
			.depthBiasEnable(this.depthBias)
			.lineWidth(1.0F)
			.depthBiasConstantFactor(this.depthBiasConstantFactor)
			.depthBiasSlopeFactor(this.depthBiasSlopeFactor);

		VkPipelineMultisampleStateCreateInfo multisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.calloc(memoryStack)
			.sType$Default()
			.rasterizationSamples(1)
			.sampleShadingEnable(false);

		VkPipelineDepthStencilStateCreateInfo depthStencilStateCreateInfo = VkPipelineDepthStencilStateCreateInfo.calloc(memoryStack)
			.sType$Default()
			.depthTestEnable(this.depthTest)
			.depthWriteEnable(this.depthWrite)
			.depthCompareOp(this.depthCompareOp)
			.stencilTestEnable(false);

		VkPipelineColorBlendStateCreateInfo colorBlendStateCreateInfo = VkPipelineColorBlendStateCreateInfo.calloc(memoryStack)
			.sType$Default()
			.logicOpEnable(this.enableLogicOp)
			.logicOp(this.logicOp)
			.pAttachments(
				VkPipelineColorBlendAttachmentState.calloc(1, memoryStack)
					.blendEnable(this.blend)
					.srcColorBlendFactor(this.srcColorBlendFactor)
					.dstColorBlendFactor(this.dstColorBlendFactor)
					.colorBlendOp(VK10.VK_BLEND_OP_ADD)
					.srcAlphaBlendFactor(this.srcAlphaBlendFactor)
					.dstAlphaBlendFactor(this.dstAlphaBlendFactor)
					.alphaBlendOp(VK10.VK_BLEND_OP_ADD)
					.colorWriteMask(this.writeColorMasks)
			).blendConstants(memoryStack.floats(1F, 1F, 1F, 1F));

		VkPipelineDynamicStateCreateInfo pipelineDynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.calloc(memoryStack)
			.sType$Default()
			.pDynamicStates(memoryStack.ints(VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR));

		VkGraphicsPipelineCreateInfo.Buffer graphicsPipelineCreateInfo = VkGraphicsPipelineCreateInfo.calloc(1, memoryStack)
			.sType$Default()
			.pNext(this.pipelineRenderingCreateInfoKHR)
			.stageCount(2)
			.pStages(pipelineShaderStageCreateInfo)
			.pVertexInputState(vertexInputStateCreateInfo)
			.pInputAssemblyState(inputAssemblyStateCreateInfo)
			.pViewportState(viewportStateCreateInfo)
			.pRasterizationState(rasterizationStateCreateInfo)
			.pMultisampleState(multisampleStateCreateInfo)
			.pDepthStencilState(depthStencilStateCreateInfo)
			.pColorBlendState(colorBlendStateCreateInfo)
			.pDynamicState(pipelineDynamicStateCreateInfo)
			.layout(this.pipelineLayout);

		LongBuffer graphicsPipelineHandle = memoryStack.callocLong(1);

		VkDevice device = VulkanDevice.getInstance().vkDevice;

		VK10.vkCreateGraphicsPipelines(device, VK10.VK_NULL_HANDLE, graphicsPipelineCreateInfo, null, graphicsPipelineHandle);

		long graphcisPipeline = graphicsPipelineHandle.get(0);

		if (this.label != null) {
			VkDebugUtilsObjectNameInfoEXT debugName = VkDebugUtilsObjectNameInfoEXT.calloc(memoryStack)
				.sType$Default()
				.objectType(VK10.VK_OBJECT_TYPE_PIPELINE)
				.objectHandle(graphcisPipeline)
				.pObjectName(memoryStack.UTF8(this.label));

			EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(device, debugName);
		}

		memoryStack.close();

		return graphcisPipeline;
	}

	private record Shader(int stage, long module) {}

	private record Attribute(int location, int binding, int format, int offset) {}

	private record Binding(int binding, int stride, int inputRate) {}
}
