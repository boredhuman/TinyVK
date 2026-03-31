package dev.boredhuman.vulkan;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.boredhuman.TinyVK;
import dev.boredhuman.shaders.ProgramData;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import spirv.enumerants.Dim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class PipelineGroup implements CompiledRenderPipeline {
	private final ProgramData programData;
	private final ShaderResources shaderResources;
	private final Map<RenderPassFormat, Long> graphicPipelines = new HashMap<>();
	private final Map<Integer, Integer> samplerIndexes = new HashMap<>();
	private final Set<Integer> missingSamplers = new HashSet<>();

	public PipelineGroup(ProgramData programData, RenderPipeline renderPipeline) {
		this.programData = programData;
		this.shaderResources = this.createShaderResources(renderPipeline.getLocation().getPath());

		int samplerIndex = 0;
		for (String sampler : renderPipeline.getSamplers()) {
			Integer resourceBinding = programData.resourceNameBindings.get(sampler);
			if (resourceBinding != null) {
				this.samplerIndexes.put(resourceBinding, samplerIndex);
			}
			samplerIndex++;
		}

		// missing samplers use the sampler bound to the zero texture unit
		for (int sampler : Stream.concat(programData.vertexSamplers.stream(), programData.fragmentSamplers.stream()).toList()) {
			// texture buffers case
			if (programData.resourceTypeMap.get(sampler) != ProgramData.ResourceType.SAMPLER) {
				continue;
			}
			String name = programData.bindingResourceName.get(sampler);
			if (!renderPipeline.getSamplers().contains(name)) {
				TinyVK.LOGGER.info("Render pipeline {} uses texture {} without declaring its usage", renderPipeline, name);
				this.missingSamplers.add(sampler);
			}
		}
	}

	private ShaderResources createShaderResources(String label) {
		ShaderResources.Builder shaderResourcesBuilder = ShaderResources.create();

		for (Map.Entry<String, Integer> resourceBinding : this.programData.resourceNameBindings.entrySet()) {
			int binding = resourceBinding.getValue();

			boolean vertexUbo = this.programData.vertexUbos.contains(binding);
			boolean fragmentUbo = this.programData.fragmentUbos.contains(binding);

			if (vertexUbo || fragmentUbo) {
				shaderResourcesBuilder
					.stageVertFrag(vertexUbo, fragmentUbo)
					.add(binding, VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);

				continue;
			}

			boolean vertexSampler = this.programData.vertexSamplers.contains(binding);
			boolean fragmentSampler = this.programData.fragmentSamplers.contains(binding);

			Dim dim = this.programData.samplerDimensions.get(binding);

			int descriptorType;
			if (dim == Dim.DIM_2D || dim == Dim.CUBE) {
				descriptorType = VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
			} else if (dim == Dim.BUFFER) {
				descriptorType = VK10.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER;
			} else {
				throw new RuntimeException("Added support for texture type " + dim.name());
			}

			shaderResourcesBuilder
				.stageVertFrag(vertexSampler, fragmentSampler)
				.add(binding, descriptorType);
		}

		return shaderResourcesBuilder.label(label).build();
	}

	public long bindPipeline(RenderPipeline renderPipeline, RenderPassFormat renderPassFormat) {
		return this.graphicPipelines.computeIfAbsent(renderPassFormat, r -> this.createPipeline(renderPipeline, r));
	}

	private long createPipeline(RenderPipeline renderPipeline, RenderPassFormat renderPassFormat) {
		MemoryStack memoryStack = VkHelper.stackPush();

		VkPipelineRenderingCreateInfoKHR pipelineRenderingCreateInfoKHR = VkPipelineRenderingCreateInfoKHR.calloc(memoryStack)
			.sType$Default()
			.pColorAttachmentFormats(memoryStack.ints(renderPassFormat.colorFormat()))
			.depthAttachmentFormat(renderPassFormat.depthFormat());

		GraphicsPipelineBuilder graphicsPipelineBuilder = PipelineConverter.convert(renderPipeline, this.programData);

		long graphicsPipeline = graphicsPipelineBuilder
			.shader(VK10.VK_SHADER_STAGE_VERTEX_BIT, this.programData.vertexShaderModule)
			.shader(VK10.VK_SHADER_STAGE_FRAGMENT_BIT, this.programData.fragmentShaderModule)
			.pipelineLayout(this.shaderResources.pipelineLayout())
			.label(renderPipeline.getLocation().getPath())
			.pipelineRenderingCreateInfoKHR(pipelineRenderingCreateInfoKHR)
			.build();

		memoryStack.close();

		return graphicsPipeline;
	}

	@Override
	public boolean isValid() {
		return true;
	}

	public void free() {
		VkDevice device = VulkanDevice.getInstance().vkDevice;

		for (Long graphicsPipeline : this.graphicPipelines.values()) {
			VK10.vkDestroyPipeline(device, graphicsPipeline, null);
		}

		this.shaderResources.free();
	}

	public ProgramData getProgramData() {
		return this.programData;
	}

	public long getPipelineLayout() {
		return this.shaderResources.pipelineLayout();
	}

	public Set<Integer> getMissingSamplers() {
		return this.missingSamplers;
	}

	public Map<Integer, Integer> getSamplerIndexes() {
		return this.samplerIndexes;
	}
}
