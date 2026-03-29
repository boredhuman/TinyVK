package dev.boredhuman.impl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuQuery;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.boredhuman.types.BufferHolder;
import dev.boredhuman.vulkan.VkBuffer;
import dev.boredhuman.vulkan.VkHelper;
import dev.boredhuman.vulkan.VkImage;
import dev.boredhuman.vulkan.VkImageView;
import dev.boredhuman.vulkan.VulkanDevice;
import dev.boredhuman.vulkan.VulkanInstance;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkClearAttachment;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkClearRect;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkViewport;
import org.spongepowered.asm.mixin.Unique;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

public class VkCommandEncoder implements CommandEncoder {

	@Override
	public RenderPass createRenderPass(Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt) {
		return this.createRenderPass(supplier, gpuTextureView, optionalInt, null, OptionalDouble.empty());
	}

	@Override
	public VkRenderPass createRenderPass(Supplier<String> supplier, GpuTextureView colorTexture, OptionalInt clearColor, @Nullable GpuTextureView depthTexture,
									   OptionalDouble clearDepth) {
		MemoryStack memoryStack = VkHelper.stackPush();

		VkImageView colorTextureView = (VkImageView) colorTexture;

		VkRenderingAttachmentInfo.Buffer colorAttachments = VkRenderingAttachmentInfo.calloc(1, memoryStack).sType$Default()
			.imageView(colorTextureView.imageView).imageLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
			.loadOp(clearColor.isPresent() ? VK10.VK_ATTACHMENT_LOAD_OP_CLEAR : VK10.VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);

		if (clearColor.isPresent()) {
			int color = clearColor.getAsInt();
			float red = ARGB.redFloat(color);
			float green = ARGB.greenFloat(color);
			float blue = ARGB.blueFloat(color);
			float alpha = ARGB.alphaFloat(color);

			VkClearValue colorClearValue = VkClearValue.calloc(memoryStack);

			colorClearValue.color().float32(0, red).float32(1, green).float32(2, blue).float32(3, alpha);

			colorAttachments.clearValue(colorClearValue);
		}

		VkRenderingInfo renderingInfo = VkRenderingInfo.calloc(memoryStack)
			.sType$Default()
			.layerCount(1)
			.pColorAttachments(colorAttachments);

		renderingInfo.renderArea().extent().set(colorTextureView.vkImage.getWidth(0), colorTextureView.vkImage.getHeight(0));

		VkRenderingAttachmentInfo depthAttachmentInfo;

		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		VkImage depthVkImage;

		if (depthTexture != null) {
			VkImageView depthTextureView = (VkImageView) depthTexture;
			depthVkImage = depthTextureView.vkImage;

			depthAttachmentInfo = VkRenderingAttachmentInfo.calloc(memoryStack).sType$Default().imageView(depthTextureView.imageView)
				.imageLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
				.loadOp(clearDepth.isPresent() ? VK10.VK_ATTACHMENT_LOAD_OP_CLEAR : VK10.VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);

			VkClearValue depthClear;

			if (clearDepth.isPresent()) {
				depthClear = VkClearValue.calloc(memoryStack);
				depthClear.depthStencil().depth(((float) clearDepth.getAsDouble()));

				depthAttachmentInfo.clearValue(depthClear);
			}

			depthTextureView.vkImage.transition(
				VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
				VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
			);

			renderingInfo.pDepthAttachment(depthAttachmentInfo);
		} else {
			depthVkImage = null;
		}

		colorTextureView.vkImage.transition(
			VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
		);

		VulkanDevice.getInstance().inRenderPass = true;

		int width = colorTexture.getWidth(0);
		int height = colorTexture.getHeight(0);

		VK10.vkCmdSetViewport(
			commandBuffer, 0, VkViewport.calloc(1, memoryStack).width(width).height(height).minDepth(0).maxDepth(1)
		);

		KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, renderingInfo);

		memoryStack.close();

		VulkanDevice.getInstance().renderPassBegin(colorTextureView.vkImage, depthVkImage);

		return new VkRenderPass(width, height);
	}

	@Override
	public void clearColorTexture(GpuTexture colorTexture, int colorClear) {
		VkImage vkImage = (VkImage) colorTexture;

		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		vkImage.transitionTransferDst();

		MemoryStack memoryStack = VkHelper.stackPush();

		float red = ARGB.redFloat(colorClear);
		float green = ARGB.greenFloat(colorClear);
		float blue = ARGB.blueFloat(colorClear);
		float alpha = ARGB.alphaFloat(colorClear);

		VkClearColorValue clearColor = VkClearColorValue.calloc(memoryStack);

		clearColor.float32(0, red).float32(1, green).float32(2, blue).float32(3, alpha);

		VkImageSubresourceRange isrr = VkImageSubresourceRange.calloc(memoryStack).aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
			.levelCount(VK10.VK_REMAINING_MIP_LEVELS).layerCount(VK10.VK_REMAINING_ARRAY_LAYERS);

		VK10.vkCmdClearColorImage(commandBuffer, vkImage.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, clearColor, isrr);

		memoryStack.close();

		vkImage.transition();
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture colorTexture, int clearColor, GpuTexture depthTexture, double clearDepth) {
		this.clearColorTexture(colorTexture, clearColor);
		this.clearDepthTexture(depthTexture, clearDepth);
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture colorTexture, int colorClear, GpuTexture depthTexture, double clearDepth, int x, int y, int width,
										   int height) {
		VkImageView colorView = VulkanDevice.getInstance().getImpl().createTextureView(colorTexture);
		VkImageView depthView = VulkanDevice.getInstance().getImpl().createTextureView(depthTexture);

		MemoryStack memoryStack = VkHelper.stackPush();

		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		VkRenderingAttachmentInfo.Buffer colorAttachments = VkRenderingAttachmentInfo.calloc(1, memoryStack).sType$Default().imageView(colorView.imageView)
			.imageLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL).loadOp(VK10.VK_ATTACHMENT_LOAD_OP_LOAD).storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
			.clearValue(VkClearValue.calloc(memoryStack));

		VkRenderingAttachmentInfo depthAttachment = VkRenderingAttachmentInfo.calloc(memoryStack).sType$Default().imageView(depthView.imageView)
			.imageLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL).loadOp(VK10.VK_ATTACHMENT_LOAD_OP_LOAD)
			.storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE).clearValue(VkClearValue.calloc(memoryStack));

		VkRenderingInfo renderingInfo = VkRenderingInfo.calloc(memoryStack)
			.sType$Default()
			.layerCount(1)
			.pColorAttachments(colorAttachments)
			.pDepthAttachment(depthAttachment);

		renderingInfo.renderArea().extent().set(colorView.vkImage.getWidth(0), colorView.vkImage.getHeight(0));

		KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, renderingInfo);

		float red = ARGB.redFloat(colorClear);
		float green = ARGB.greenFloat(colorClear);
		float blue = ARGB.blueFloat(colorClear);
		float alpha = ARGB.alphaFloat(colorClear);

		VkClearValue clearValue = VkClearValue.calloc(memoryStack);

		clearValue.color().float32(0, red).float32(1, green).float32(2, blue).float32(3, alpha);
		clearValue.depthStencil().depth((float) clearDepth);

		VkClearAttachment.Buffer clearAttachments = VkClearAttachment.calloc(2, memoryStack);

		clearAttachments.get(0).aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).clearValue(clearValue);
		clearAttachments.get(1).aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT).clearValue(clearValue);

		VkClearRect.Buffer regions = VkClearRect.calloc(2, memoryStack);

		for (int i = 0; i < 2; i++) {
			regions.get(i).rect().offset().set(x, y);
			regions.get(i).rect().extent().set(width, height);
			regions.get(i).layerCount(1);
		}

		VK10.vkCmdClearAttachments(commandBuffer, clearAttachments, regions);

		KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);

		memoryStack.close();

		VulkanDevice.getInstance().onFrameEnd(() -> {
			colorView.close();
			depthView.close();
		});
	}

	@Override
	public void clearDepthTexture(GpuTexture depthTexture, double depthClear) {
		VkImage vkImage = (VkImage) depthTexture;

		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		vkImage.transitionTransferDst();

		MemoryStack memoryStack = VkHelper.stackPush();

		VkClearDepthStencilValue clearDepth = VkClearDepthStencilValue.calloc(memoryStack);
		clearDepth.depth((float) depthClear);

		VkImageSubresourceRange isrr = VkImageSubresourceRange.calloc(memoryStack).aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
			.levelCount(VK10.VK_REMAINING_MIP_LEVELS).layerCount(VK10.VK_REMAINING_ARRAY_LAYERS);

		VK10.vkCmdClearDepthStencilImage(commandBuffer, vkImage.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, clearDepth, isrr);

		memoryStack.close();

		vkImage.transition();
	}

	@Override
	public void writeToBuffer(GpuBufferSlice gpuBufferSlice, ByteBuffer data) {
		VkBuffer vkBuffer;

		if (gpuBufferSlice.buffer() instanceof VkBuffer buffer) {
			vkBuffer = buffer;
		} else {
			vkBuffer = ((BufferHolder) gpuBufferSlice.buffer()).tinyvk$getBuffer();
		}

		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		if (data.remaining() <= 65536) {
			vkBuffer.syncForWrite();

			VK10.vkCmdUpdateBuffer(commandBuffer, vkBuffer.buffer, gpuBufferSlice.offset(), data);

			vkBuffer.syncForUsage();
			return;
		}

		VkBuffer tempVkBuffer = VkBuffer.createTempBuffer(data.remaining());

		MemoryUtil.memCopy(MemoryUtil.memAddress(data), tempVkBuffer.mappedMemory, data.remaining());

		tempVkBuffer.sync(VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_ACCESS_TRANSFER_READ_BIT);
		vkBuffer.syncForWrite();

		MemoryStack memoryStack = VkHelper.stackPush();

		VkBufferCopy.Buffer bufferCopy = VkBufferCopy.calloc(1, memoryStack).dstOffset(gpuBufferSlice.offset()).size(data.remaining());

		VK10.vkCmdCopyBuffer(commandBuffer, tempVkBuffer.buffer, vkBuffer.buffer, bufferCopy);

		memoryStack.close();

		vkBuffer.syncForUsage();

		VulkanDevice.getInstance().onFrameEnd(tempVkBuffer::close);
	}

	@Override
	public GpuBuffer.MappedView mapBuffer(GpuBuffer gpuBuffer, boolean read, boolean write) {
		return this.mapBuffer(gpuBuffer.slice(), read, write);
	}

	@Override
	public VkBuffer.VkMappedView mapBuffer(GpuBufferSlice gpuBufferSlice, boolean read, boolean write) {
		VkBuffer buffer;
		if (gpuBufferSlice.buffer() instanceof VkBuffer vkBuffer) {
			buffer = vkBuffer;
		} else {
			buffer = ((BufferHolder) gpuBufferSlice.buffer()).tinyvk$getBuffer();
		}
		return buffer.map(gpuBufferSlice.offset(), (int) gpuBufferSlice.length(), write);
	}

	@Override
	public void copyToBuffer(GpuBufferSlice gpuBufferSlice, GpuBufferSlice gpuBufferSlice2) {
		// at time of writing this method is not used so a implementation is not made
		throw new RuntimeException("Unexpected usage of copyToBuffer");
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage) {
		long size = (long) nativeImage.format().components() * nativeImage.getWidth() * nativeImage.getHeight();

		this.writeToTexture(
			gpuTexture, nativeImage.getPointer(), size, 0, 0, 0, 0, nativeImage.getWidth(), nativeImage.getHeight(), nativeImage.getWidth(),
			nativeImage.getHeight()
		);
	}

	@Override
	public void writeToTexture(GpuTexture texture, NativeImage nativeImage, int mipLevel, int layer, int destX, int destY, int width, int height,
							   int sourceX, int sourceY) {
		long bytesPerTexel = nativeImage.format().components();

		int firstPixel = nativeImage.getWidth() * sourceY + sourceX;
		int rowsRead = height - 1;
		int span = rowsRead * nativeImage.getWidth() + width;
		long size = span * bytesPerTexel;

		long dataStart = nativeImage.getPointer() + firstPixel * bytesPerTexel;

		this.writeToTexture(texture, dataStart, size, mipLevel, layer, destX, destY, width, height, nativeImage.getWidth(), nativeImage.getHeight());
	}

	@Override
	public void writeToTexture(GpuTexture texture, ByteBuffer data, NativeImage.Format format, int mipLevel, int layer, int destX, int destY,
							   int width, int height) {
		this.writeToTexture(texture, MemoryUtil.memAddress(data), data.remaining(), mipLevel, layer, destX, destY, width, height, width, height);
	}

	@Unique
	private void writeToTexture(GpuTexture gpuTexture, long data, long size, int mipLevel, int layer, int destX, int destY, int width, int height,
								int srcWidth, int srcHeight) {
		VkImage vkImage = (VkImage) gpuTexture;
		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		VkBuffer tempVkBuffer = VkBuffer.createTempBuffer(size);

		MemoryUtil.memCopy(data, tempVkBuffer.mappedMemory, size);

		tempVkBuffer.sync(VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_ACCESS_TRANSFER_READ_BIT);

		vkImage.transitionTransferDst();

		MemoryStack memoryStack = VkHelper.stackPush();

		VkBufferImageCopy.Buffer copy = VkBufferImageCopy.calloc(1, memoryStack)
			.bufferRowLength(srcWidth)
			.bufferImageHeight(srcHeight);

		copy.imageSubresource().aspectMask(vkImage.imageAspect).baseArrayLayer(layer).layerCount(1).mipLevel(mipLevel);

		copy.imageOffset().set(destX, destY, 0);
		copy.imageExtent().set(width, height, 1);

		VK10.vkCmdCopyBufferToImage(commandBuffer, tempVkBuffer.buffer, vkImage.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copy);

		memoryStack.close();

		vkImage.transition();

		VulkanDevice.getInstance().onFrameEnd(tempVkBuffer::close);
	}

	@Override
	public void copyTextureToBuffer(GpuTexture src, GpuBuffer dst, long offset, Runnable callback, int mipLevel) {
		this.copyTextureToBuffer(src, dst, offset, callback, mipLevel, 0, 0, src.getWidth(mipLevel), src.getHeight(mipLevel));
	}

	@Override
	public void copyTextureToBuffer(GpuTexture src, GpuBuffer dst, long offset, Runnable callback, int mipLevel, int x, int y, int width, int height) {
		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		VkImage srcImage = (VkImage) src;
		VkBuffer dstBuffer = (VkBuffer) dst;

		srcImage.transitionTransferSrc();
		dstBuffer.sync(VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_ACCESS_TRANSFER_WRITE_BIT);

		MemoryStack memoryStack = VkHelper.stackPush();

		VkBufferImageCopy.Buffer copy = VkBufferImageCopy.calloc(1, memoryStack);
		copy.imageSubresource().aspectMask(srcImage.imageAspect).mipLevel(mipLevel).layerCount(1);

		VK10.vkCmdCopyImageToBuffer(commandBuffer, srcImage.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, dstBuffer.buffer, copy);

		srcImage.transition();

		// avoid running the task twice when dual launching
		if (!VulkanInstance.DUAL_LAUNCH) {
			RenderSystem.queueFencedTask(callback);
		}
	}

	@Override
	public void copyTextureToTexture(GpuTexture src, GpuTexture dst, int mipLevel, int destX, int destY, int sourceX, int sourceY, int width, int height) {
		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		VkImage srcImage = (VkImage) src;
		VkImage dstImage = (VkImage) dst;

		srcImage.transitionTransferSrc();
		dstImage.transitionTransferDst();

		MemoryStack memoryStack = VkHelper.stackPush();

		VkImageCopy.Buffer imageCopy = VkImageCopy.calloc(1, memoryStack);
		imageCopy.srcSubresource().aspectMask(srcImage.imageAspect).mipLevel(mipLevel).layerCount(1);
		imageCopy.srcSubresource().aspectMask(dstImage.imageAspect).mipLevel(mipLevel).layerCount(1);

		imageCopy.srcOffset().set(sourceX, sourceY, 0);
		imageCopy.dstOffset().set(destX, destY, 0);
		imageCopy.extent().set(width, height, 1);

		VK10.vkCmdCopyImage(
			commandBuffer, srcImage.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, dstImage.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
			imageCopy
		);

		memoryStack.close();

		srcImage.transition();
		dstImage.transition();
	}

	@Override
	public void presentTexture(GpuTextureView gpuTextureView) {
		VulkanDevice.getInstance().endFrame((VkImageView) gpuTextureView);
	}

	@Override
	public GpuFence createFence() {
		return new VkFence();
	}

	@Override
	public GpuQuery timerQueryBegin() {
		return new VkEmptyGpuQuery();
	}

	@Override
	public void timerQueryEnd(GpuQuery gpuQuery) {

	}
}
