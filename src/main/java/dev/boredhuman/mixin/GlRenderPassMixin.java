package dev.boredhuman.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.boredhuman.impl.VkRenderPass;
import dev.boredhuman.types.BufferHolder;
import dev.boredhuman.types.ImageViewHolder;
import dev.boredhuman.types.RenderPassHolder;
import dev.boredhuman.types.SamplerHolder;
import dev.boredhuman.vulkan.VkBuffer;
import dev.boredhuman.vulkan.VkImageView;
import dev.boredhuman.vulkan.VkSampler;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(GlRenderPass.class)
public class GlRenderPassMixin implements RenderPassHolder {

	@Unique
	private VkRenderPass tinyvk$renderPass;

	@Inject(method = "setPipeline", at = @At("TAIL"))
	private void tinyvk$setPipeline(RenderPipeline renderPipeline, CallbackInfo ci) {
		this.tinyvk$renderPass.setPipeline(renderPipeline);
	}

	@Inject(method = "bindTexture", at = @At("TAIL"))
	private void tinyvk$bindTexture(String uniformName, GpuTextureView gpuTextureView, GpuSampler gpuSampler, CallbackInfo ci) {
		VkImageView imageView = ((ImageViewHolder) gpuTextureView).tinyvk$getImageView();
		VkSampler sampler = ((SamplerHolder) gpuSampler).tinyvk$getSampler();

		this.tinyvk$renderPass.bindTexture(uniformName, imageView, sampler);
	}

	@Inject(method = "setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBuffer;)V", at = @At("TAIL"))
	private void tinyvk$setUniform(String uniformName, GpuBuffer gpuBuffer, CallbackInfo ci) {
		VkBuffer buffer = ((BufferHolder) gpuBuffer).tinyvk$getBuffer();
		this.tinyvk$renderPass.setUniform(uniformName, buffer);
	}

	@Inject(method = "setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("TAIL"))
	private void tinyvk$setUniform(String uniformName, GpuBufferSlice gpuBufferSlice, CallbackInfo ci) {
		VkBuffer buffer = ((BufferHolder) gpuBufferSlice.buffer()).tinyvk$getBuffer();
		this.tinyvk$renderPass.setUniform(uniformName, new GpuBufferSlice(buffer, gpuBufferSlice.offset(), gpuBufferSlice.length()));
	}

	@Inject(method = "setVertexBuffer", at = @At("TAIL"))
	private void tinyvk$setVertexBuffer(int slot, GpuBuffer gpuBuffer, CallbackInfo ci) {
		VkBuffer vkBuffer = ((BufferHolder) gpuBuffer).tinyvk$getBuffer();
		this.tinyvk$renderPass.setVertexBuffer(slot, vkBuffer);
	}

	@Inject(method = "setIndexBuffer", at = @At("TAIL"))
	private void tinyvk$setIndexBuffer(GpuBuffer gpuBuffer, VertexFormat.IndexType indexType, CallbackInfo ci) {
		VkBuffer vkBuffer = ((BufferHolder) gpuBuffer).tinyvk$getBuffer();
		this.tinyvk$renderPass.setIndexBuffer(vkBuffer, indexType);
	}

	@Inject(method = "drawIndexed", at = @At("TAIL"))
	private void tinyvk$drawIndexed(int baseVertex, int firstIndex, int indexCount, int instanceCount, CallbackInfo ci) {
		this.tinyvk$renderPass.drawIndexed(baseVertex, firstIndex, indexCount, instanceCount);
	}

	@Inject(method = "drawMultipleIndexed", at = @At("TAIL"))
	private <T> void tinyvk$drawMultiple(Collection<RenderPass.Draw<T>> draws, @Nullable GpuBuffer defaultIndexBuffer,
										 VertexFormat.@Nullable IndexType defaultIndexType, Collection<String> dynamicUniforms, T uniformArgument,
										 CallbackInfo ci) {
		if (defaultIndexBuffer != null) {
			defaultIndexBuffer = ((BufferHolder) defaultIndexBuffer).tinyvk$getBuffer();
		}
		this.tinyvk$renderPass.drawMultipleIndexed(draws, defaultIndexBuffer, defaultIndexType, dynamicUniforms, uniformArgument);
	}

	@Inject(method = "draw", at = @At("TAIL"))
	private void tinyvk$draw(int firstVertex, int vertexCount, CallbackInfo ci) {
		this.tinyvk$renderPass.draw(firstVertex, vertexCount);
	}

	@Override
	public void tinyvk$setRenderPass(VkRenderPass renderPass) {
		this.tinyvk$renderPass = renderPass;
	}
}
