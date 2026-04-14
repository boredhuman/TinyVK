package dev.boredhuman.impl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.boredhuman.shaders.ProgramData;
import dev.boredhuman.types.BufferHolder;
import dev.boredhuman.vulkan.PipelineGroup;
import dev.boredhuman.vulkan.VkBuffer;
import dev.boredhuman.vulkan.VkHelper;
import dev.boredhuman.vulkan.VkImageView;
import dev.boredhuman.vulkan.VkSampler;
import dev.boredhuman.vulkan.VulkanDevice;
import dev.boredhuman.vulkan.VulkanInstance;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class VkRenderPass implements RenderPass {
	private static ViewSampler DEFAULT_SAMPLER;

	private final int width;
	private final int height;
	private final Map<String, ViewSampler> samplers = new HashMap<>();
	private final Map<String, GpuBufferSlice> uniforms = new HashMap<>();
	private final Set<String> dirtyUniforms = new HashSet<>();
	private GpuBuffer indexBuffer;
	private RenderPipeline renderPipeline;
	private boolean scissor;
	private boolean scissorDirty = true;
	private int scissorX;
	private int scissorY;
	private int scissorWidth;
	private int scissorHeight;
	private boolean invalidDraw = false;

	public VkRenderPass(int width, int height) {
		this.width = width;
		this.height = height;
	}

	@Override
	public void pushDebugGroup(Supplier<String> supplier) {

	}

	@Override
	public void popDebugGroup() {

	}

	@Override
	public void setPipeline(RenderPipeline renderPipeline) {
		this.renderPipeline = renderPipeline;
		VulkanDevice.getInstance().bindPipeline(renderPipeline);
	}

	@Override
	public void bindTexture(String samplerName, @Nullable GpuTextureView gpuTextureView, @Nullable GpuSampler gpuSampler) {
		if (gpuTextureView == null) {
			this.invalidDraw = true;
			return;
		}

		if (gpuSampler == null) {
			this.samplers.remove(samplerName);
		} else {
			this.samplers.put(samplerName, new ViewSampler((VkImageView) gpuTextureView, (VkSampler) gpuSampler));
		}
		this.dirtyUniforms.add(samplerName);
	}

	@Override
	public void setUniform(String uniformName, GpuBuffer gpuBuffer) {
		this.setUniform(uniformName, gpuBuffer.slice());
		this.dirtyUniforms.add(uniformName);
	}

	@Override
	public void setUniform(String uniformName, GpuBufferSlice gpuBufferSlice) {
		this.uniforms.put(uniformName, gpuBufferSlice);
		this.dirtyUniforms.add(uniformName);
	}

	@Override
	public void enableScissor(int x, int y, int width, int height) {
		this.scissor = true;
		this.scissorX = x;
		this.scissorY = y;
		this.scissorWidth = width;
		this.scissorHeight = height;
		this.scissorDirty = true;
	}

	@Override
	public void disableScissor() {
		this.scissor = false;
		this.scissorDirty = true;
	}

	@Override
	public void setVertexBuffer(int slot, GpuBuffer gpuBuffer) {
		if (slot != 0) {
			throw new UnsupportedOperationException("Added support for multiple vertex buffers");
		}

		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		MemoryStack memoryStack = VkHelper.stackPush();

		VK10.vkCmdBindVertexBuffers(commandBuffer, 0, memoryStack.longs(((VkBuffer) gpuBuffer).buffer), memoryStack.longs(0));

		memoryStack.close();
	}

	@Override
	public void setIndexBuffer(GpuBuffer gpuBuffer, VertexFormat.IndexType indexType) {
		if (this.indexBuffer == gpuBuffer) {
			return;
		}
		this.indexBuffer = gpuBuffer;

		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		int indexKind = switch(indexType) {
			case SHORT -> VK10.VK_INDEX_TYPE_UINT16;
			case INT -> VK10.VK_INDEX_TYPE_UINT32;
		};

		VK10.vkCmdBindIndexBuffer(commandBuffer, ((VkBuffer) gpuBuffer).buffer, 0, indexKind);
	}

	@Override
	public void drawIndexed(int baseVertex, int firstIndex, int indexCount, int instanceCount) {
		if (this.invalidDraw) {
			return;
		}
		this.updateUniforms();
		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();
		VK10.vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, baseVertex, 0);
	}

	@Override
	public <T> void drawMultipleIndexed(Collection<Draw<T>> draws,
										@Nullable GpuBuffer defaultIndexBuffer,
										VertexFormat.@Nullable IndexType defaultIndexType,
										Collection<String> dynamicUniforms,
										T uniformArgument) {
		if (this.invalidDraw) {
			return;
		}
		this.updateUniforms();

		PipelineGroup boundPipelineGroup = VulkanDevice.getInstance().getBoundPipelineGroup();
		ProgramData programData = boundPipelineGroup.getProgramData();
		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		// fast path for singular dynamic uniform
		if (dynamicUniforms.size() == 1) {
			String uniformName = dynamicUniforms.iterator().next();

			int binding = programData.resourceNameBindings.get(uniformName);

			MemoryStack memoryStack = VkHelper.stackPush();

			VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, memoryStack);

			VkWriteDescriptorSet.Buffer writeDescriptorSets = VkWriteDescriptorSet.calloc(1, memoryStack)
				.sType$Default()
				.dstBinding(binding)
				.descriptorCount(1)
				.descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
				.pBufferInfo(bufferInfo);

			for (Draw<T> draw : draws) {
				GpuBuffer vertexBuffer = draw.vertexBuffer();

				if (VulkanInstance.DUAL_LAUNCH) {
					this.setVertexBuffer(0, ((BufferHolder) vertexBuffer).tinyvk$getBuffer());
				} else {
					this.setVertexBuffer(0, vertexBuffer);
				}

				GpuBuffer indexBuffer = draw.indexBuffer();

				if (indexBuffer != null) {
					if (VulkanInstance.DUAL_LAUNCH) {
						this.setIndexBuffer(((BufferHolder) indexBuffer).tinyvk$getBuffer(), draw.indexType());
					} else {
						this.setIndexBuffer(indexBuffer, draw.indexType());
					}
				} else {
					this.setIndexBuffer(defaultIndexBuffer, defaultIndexType);
				}

				draw.uniformUploaderConsumer().accept(uniformArgument, (name, uniformData) -> {
					VkBuffer buffer;

					if (VulkanInstance.DUAL_LAUNCH) {
						buffer = ((BufferHolder) uniformData.buffer()).tinyvk$getBuffer();
					} else {
						buffer = (VkBuffer) uniformData.buffer();
					}

					bufferInfo
						.buffer(buffer.buffer)
						.offset(uniformData.offset())
						.range(uniformData.length());

					KHRPushDescriptor.vkCmdPushDescriptorSetKHR(
						commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, boundPipelineGroup.getPipelineLayout(), 0, writeDescriptorSets);
				});

				VK10.vkCmdDrawIndexed(commandBuffer, draw.indexCount(), 1, draw.firstIndex(), 0, 0);
			}

			memoryStack.close();
		} else {
			for (Draw<T> draw : draws) {
				GpuBuffer vertexBuffer = draw.vertexBuffer();

				if (VulkanInstance.DUAL_LAUNCH) {
					this.setVertexBuffer(0, ((BufferHolder) vertexBuffer).tinyvk$getBuffer());
				} else {
					this.setVertexBuffer(0, vertexBuffer);
				}

				GpuBuffer indexBuffer = draw.indexBuffer();

				if (indexBuffer != null) {
					if (VulkanInstance.DUAL_LAUNCH) {
						this.setIndexBuffer(((BufferHolder) indexBuffer).tinyvk$getBuffer(), draw.indexType());
					} else {
						this.setIndexBuffer(indexBuffer, draw.indexType());
					}
				} else {
					this.setIndexBuffer(defaultIndexBuffer, defaultIndexType);
				}

				draw.uniformUploaderConsumer().accept(
					uniformArgument, (uniformName, uniformData) -> {
						MemoryStack memoryStack = VkHelper.stackPush();

						VkWriteDescriptorSet.Buffer writeDescriptorSets = VkWriteDescriptorSet.calloc(1, memoryStack);

						VkBuffer buffer;

						if (VulkanInstance.DUAL_LAUNCH) {
							buffer = ((BufferHolder) uniformData.buffer()).tinyvk$getBuffer();
						} else {
							buffer = (VkBuffer) uniformData.buffer();
						}

						int binding = programData.resourceNameBindings.get(uniformName);

						writeDescriptorSets.get(0)
							.sType$Default()
							.dstBinding(binding)
							.descriptorCount(1)
							.descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
							.pBufferInfo(
								VkDescriptorBufferInfo.calloc(1, memoryStack)
									.buffer(buffer.buffer)
									.offset(uniformData.offset())
									.range(uniformData.length())
							);

						KHRPushDescriptor.vkCmdPushDescriptorSetKHR(
							commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, boundPipelineGroup.getPipelineLayout(), 0, writeDescriptorSets);

						memoryStack.close();
					}
				);

				VK10.vkCmdDrawIndexed(commandBuffer, draw.indexCount(), 1, draw.firstIndex(), 0, 0);
			}
		}
	}

	@Override
	public void draw(int firstVertex, int vertexCount) {
		if (this.invalidDraw) {
			return;
		}
		this.updateUniforms();
		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();
		VK10.vkCmdDraw(commandBuffer, vertexCount, 1, firstVertex, 0);
	}

	private void updateUniforms() {
		PipelineGroup boundPipelineGroup = VulkanDevice.getInstance().getBoundPipelineGroup();
		ProgramData programData = boundPipelineGroup.getProgramData();

		MemoryStack memoryStack = VkHelper.stackPush();

		if (this.scissorDirty) {
			VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();
			VkRect2D.Buffer scissor = VkRect2D.calloc(1, memoryStack);

			if (this.scissor) {
				scissor.offset().set(this.scissorX, this.scissorY);
				scissor.extent().set(this.scissorWidth, this.scissorHeight);
			} else {
				scissor.extent().set(this.width, this.height);
			}

			VK10.vkCmdSetScissor(commandBuffer, 0, scissor);
		}

		int updateCount = 0;
		for (String dirtyUniform : this.dirtyUniforms) {
			Integer binding = programData.resourceNameBindings.get(dirtyUniform);
			if (binding == null) {
				continue;
			}
			updateCount++;
		}

		updateCount += boundPipelineGroup.getMissingSamplers().size();

		if (updateCount == 0) {
			return;
		}

		VkWriteDescriptorSet.Buffer writeDescriptorSets = VkWriteDescriptorSet.calloc(updateCount, memoryStack);

		int i = 0;
		for (String dirtyUniform : this.dirtyUniforms) {
			Integer binding = programData.resourceNameBindings.get(dirtyUniform);
			if (binding == null) {
				continue;
			}

			ProgramData.ResourceType resourceType = programData.resourceTypeMap.get(binding);

			if (resourceType == ProgramData.ResourceType.UNIFORM_BUFFER) {
				GpuBufferSlice gpuBufferSlice = this.uniforms.get(dirtyUniform);
				VkBuffer buffer = (VkBuffer) gpuBufferSlice.buffer();

				writeDescriptorSets.get(i)
					.sType$Default()
					.dstBinding(binding)
					.descriptorCount(1)
					.descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
					.pBufferInfo(
						VkDescriptorBufferInfo.calloc(1, memoryStack)
							.buffer(buffer.buffer)
							.offset(gpuBufferSlice.offset())
							.range(gpuBufferSlice.length())
					);
			} else if (resourceType == ProgramData.ResourceType.SAMPLER) {
				ViewSampler viewSampler = this.samplers.get(dirtyUniform);

				Integer samplerIndex = boundPipelineGroup.getSamplerIndexes().get(binding);

				if (samplerIndex == null || samplerIndex == 0) {
					VkRenderPass.DEFAULT_SAMPLER = viewSampler;
				}

				writeDescriptorSets.get(i)
					.sType$Default()
					.dstBinding(binding)
					.descriptorCount(1)
					.descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
					.pImageInfo(
						VkDescriptorImageInfo.calloc(1, memoryStack)
							.sampler(viewSampler.sampler().sampler)
							.imageView(viewSampler.view().imageView)
							.imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
					);
			} else if (resourceType == ProgramData.ResourceType.TEXEL_BUFFER) {
				GpuBufferSlice gpuBufferSlice = this.uniforms.get(dirtyUniform);
				VkBuffer buffer = (VkBuffer) gpuBufferSlice.buffer();

				long texelBufferView;

				foundUniform:
				if (buffer.texelBufferView != 0) {
					texelBufferView = buffer.texelBufferView;
				} else {
					for (RenderPipeline.UniformDescription uniform : this.renderPipeline.getUniforms()) {
						if (uniform.name().equals(dirtyUniform)) {
							texelBufferView = buffer.getTexelBufferView(VkHelper.getFormat(uniform.textureFormat()));
							break foundUniform;
						}
					}

					throw new RuntimeException("Didn't find texel buffer uniform");
				}

				writeDescriptorSets.get(i)
					.sType$Default()
					.dstBinding(binding)
					.descriptorCount(1)
					.descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER)
					.pTexelBufferView(memoryStack.longs(texelBufferView));
			} else {
				throw new RuntimeException();
			}

			i++;
		}

		for (int missingSampler : boundPipelineGroup.getMissingSamplers()) {
			writeDescriptorSets.get(i)
				.sType$Default()
				.dstBinding(missingSampler)
				.descriptorCount(1)
				.descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
				.pImageInfo(
					VkDescriptorImageInfo.calloc(1, memoryStack)
						.sampler(VkRenderPass.DEFAULT_SAMPLER.sampler().sampler)
						.imageView(VkRenderPass.DEFAULT_SAMPLER.view().imageView)
						.imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
				);
			i++;
		}

		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		KHRPushDescriptor.vkCmdPushDescriptorSetKHR(
			commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, boundPipelineGroup.getPipelineLayout(), 0, writeDescriptorSets);

		memoryStack.close();
	}

	@Override
	public void close() {
		VulkanDevice.getInstance().renderPassEnd();
	}
}
