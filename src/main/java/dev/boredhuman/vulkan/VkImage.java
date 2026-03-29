package dev.boredhuman.vulkan;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.boredhuman.impl.TextureCreationData;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkComponentMapping;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent3D;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.LongBuffer;

public class VkImage extends GpuTexture {
	public final TextureCreationData creationData;
	public final long image;
	public final long vmaAllocation;
	public final int viewType;
	public final int usage;
	public final int mipLevels;

	public int currentLayout;
	public int srcStage;
	public int srcAccess;
	public boolean closed = false;

	public final int imageAspect;

	public VkImage(TextureCreationData creationData, long image, long vmaAllocation, int viewType, int usage, int mipLevels) {
		super(
			creationData.usage(), creationData.label(), creationData.textureFormat(), creationData.width(), creationData.height(),
			creationData.depthOrLayers(),
			creationData.mipLevels()
		);

		this.creationData = creationData;
		this.image = image;
		this.vmaAllocation = vmaAllocation;
		this.mipLevels = mipLevels;
		this.viewType = viewType;
		this.usage = usage;

		this.currentLayout = VK10.VK_IMAGE_LAYOUT_UNDEFINED;
		this.srcStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;

		this.imageAspect = creationData.textureFormat() == TextureFormat.DEPTH32 ? VK10.VK_IMAGE_ASPECT_DEPTH_BIT : VK10.VK_IMAGE_ASPECT_COLOR_BIT;
	}

	public void transitionTransferDst() {
		this.transition(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
	}

	public void transitionTransferSrc() {
		this.transition(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_ACCESS_TRANSFER_READ_BIT);
	}

	public void transition() {
		if ((this.usage & VK10.VK_IMAGE_USAGE_SAMPLED_BIT) != 0) {
			this.transition(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT, VK10.VK_ACCESS_SHADER_READ_BIT);
		}
	}

	public void transition(int newLayout, int dstStage, int dstAccess) {
		if (this.currentLayout == newLayout) {
			return;
		}

		if (VulkanDevice.getInstance().inRenderPass) {
			throw new RuntimeException();
		}

		MemoryStack memoryStack = VkHelper.stackPush();

		VK10.vkCmdPipelineBarrier(
			VulkanDevice.getInstance().getCommandBuffer(), this.srcStage, dstStage, 0, null, null,
			VkImageMemoryBarrier.calloc(1, memoryStack)
				.sType$Default()
				.srcAccessMask(this.srcAccess)
				.dstAccessMask(dstAccess)
				.oldLayout(this.currentLayout)
				.newLayout(newLayout)
				.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
				.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
				.image(this.image)
				.subresourceRange(srr -> srr.aspectMask(this.imageAspect).levelCount(VK10.VK_REMAINING_MIP_LEVELS)
					.layerCount(VK10.VK_REMAINING_ARRAY_LAYERS))
		);

		memoryStack.close();

		this.currentLayout = newLayout;
		this.srcStage = dstStage;
		this.srcAccess = dstAccess;
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		VulkanDevice.getInstance().onFrameEnd(() -> {
			Vma.vmaDestroyImage(VulkanInstance.getInstance().getVmaAllocator(), this.image, this.vmaAllocation);
		});

		this.closed = true;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	public VkImageView createView(String label, int aspectMask, int baseMipLevel, int levelCount) {
		MemoryStack memoryStack = VkHelper.stackPush();

		VkImageViewCreateInfo imageViewCreateInfo = VkImageViewCreateInfo.calloc(memoryStack)
			.sType$Default()
			.image(this.image)
			.viewType(this.viewType)
			.format(VkHelper.getFormat(this.creationData.textureFormat()))
			.components(
				VkComponentMapping.calloc(memoryStack)
					.r(VK10.VK_COMPONENT_SWIZZLE_R)
					.g(VK10.VK_COMPONENT_SWIZZLE_G)
					.b(VK10.VK_COMPONENT_SWIZZLE_B)
					.a(VK10.VK_COMPONENT_SWIZZLE_A)
			)
			.subresourceRange(
				VkImageSubresourceRange.calloc(memoryStack)
					.aspectMask(aspectMask)
					.baseMipLevel(baseMipLevel)
					.levelCount(Math.min(levelCount, this.mipLevels - baseMipLevel))
					.baseArrayLayer(0)
					.layerCount(VK10.VK_REMAINING_ARRAY_LAYERS)
			);

		LongBuffer imageViewHandle = memoryStack.callocLong(1);

		VkDevice device = VulkanDevice.getInstance().vkDevice;
		if (VK10.vkCreateImageView(device, imageViewCreateInfo, null, imageViewHandle) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		long imageView = imageViewHandle.get(0);

		VkDebugUtilsObjectNameInfoEXT objectName = VkDebugUtilsObjectNameInfoEXT.calloc(memoryStack)
			.sType$Default()
			.objectType(VK10.VK_OBJECT_TYPE_IMAGE_VIEW)
			.objectHandle(imageView)
			.pObjectName(memoryStack.UTF8(label));

		if (EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(device, objectName) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		memoryStack.close();

		return new VkImageView(this, imageView, baseMipLevel, levelCount);
	}

	public static VkImage create(TextureCreationData creationData) {
		int vulkanUsage = VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;

		int usage = creationData.usage();
		TextureFormat textureFormat = creationData.textureFormat();

		if ((usage & GpuTexture.USAGE_COPY_DST) != 0) {
			vulkanUsage |= VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
		}
		if ((usage & GpuTexture.USAGE_COPY_SRC) != 0) {
			vulkanUsage |= VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
		}
		if ((usage & GpuTexture.USAGE_TEXTURE_BINDING) != 0) {
			vulkanUsage |= VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
		}
		if ((usage & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0) {
			if (textureFormat == TextureFormat.DEPTH32) {
				vulkanUsage |= VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
			} else {
				vulkanUsage |= VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
			}
		}

		int flags = 0;
		if ((usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0) {
			flags = VK10.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT;
		}

		int mipLevels = creationData.mipLevels();
		if (mipLevels > 1) {
			// 0b10000 -> 5
			int maxWidthMips = Integer.numberOfTrailingZeros(Integer.highestOneBit(creationData.width())) + 1;
			int maxHeightMips = Integer.numberOfTrailingZeros(Integer.highestOneBit(creationData.height())) + 1;
			mipLevels = Math.min(Math.min(maxWidthMips, maxHeightMips), mipLevels);
		}

		MemoryStack memoryStack = VkHelper.stackPush();

		VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc(memoryStack)
			.sType$Default()
			.flags(flags)
			.imageType(VK10.VK_IMAGE_TYPE_2D)
			.format(VkHelper.getFormat(textureFormat))
			.extent(VkExtent3D.calloc(memoryStack).width(creationData.width()).height(creationData.height()).depth(1))
			.mipLevels(mipLevels)
			.arrayLayers(creationData.depthOrLayers())
			.samples(1)
			.tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
			.usage(vulkanUsage)
			.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

		long vmaAllocator = VulkanInstance.getInstance().getVmaAllocator();

		VmaAllocationCreateInfo allocationCreateInfo = VmaAllocationCreateInfo.calloc(memoryStack);
		allocationCreateInfo.usage(Vma.VMA_MEMORY_USAGE_AUTO);

		LongBuffer imageHandle = memoryStack.callocLong(1);
		PointerBuffer vmaAllocation = memoryStack.callocPointer(1);

		if (Vma.vmaCreateImage(vmaAllocator, imageCreateInfo, allocationCreateInfo, imageHandle, vmaAllocation, null) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		long image = imageHandle.get(0);

		int viewType = (flags & VK10.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT) != 0 ? VK10.VK_IMAGE_VIEW_TYPE_CUBE : VK10.VK_IMAGE_VIEW_TYPE_2D;

		VkImage vkImageObj = new VkImage(creationData, image, vmaAllocation.get(0), viewType, vulkanUsage, mipLevels);

		VkDevice device = VulkanDevice.getInstance().vkDevice;

		if (creationData.label() != null) {
			VkDebugUtilsObjectNameInfoEXT objectName = VkDebugUtilsObjectNameInfoEXT.calloc(memoryStack)
				.sType$Default()
				.objectType(VK10.VK_OBJECT_TYPE_IMAGE)
				.objectHandle(image)
				.pObjectName(memoryStack.UTF8(creationData.label()));

			if (EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(device, objectName) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}
		}

		memoryStack.close();

		return vkImageObj;
	}
}
