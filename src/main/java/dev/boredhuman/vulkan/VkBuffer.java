package dev.boredhuman.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.boredhuman.impl.BufferCreationData;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkBufferViewCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;
import org.lwjgl.vulkan.VkDevice;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

public class VkBuffer extends GpuBuffer {
	public final BufferCreationData creationData;
	public final long buffer;
	public final long vmaAllocation;
	public final int usage;
	boolean closed = false;

	public long mappedMemory = 0;

	private int srcStage;
	private int srcAccess;

	public long texelBufferView;

	public VkBuffer(BufferCreationData creationData, long buffer, long vmaAllocation, int usage) {
		super(creationData.usage(), creationData.size());
		this.creationData = creationData;
		this.buffer = buffer;
		this.vmaAllocation = vmaAllocation;
		this.usage = usage;

		this.srcStage = VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
	}

	public VkMappedView map(long offset, int size, boolean write) {
		return new VkMappedView(offset, size, write);
	}

	public void syncForWrite() {
		this.sync(VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
	}

	public void syncForUsage() {
		if ((this.usage & VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT) != 0) {
			this.sync(VK10.VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK10.VK_ACCESS_INDEX_READ_BIT);
		} else if ((this.usage & VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT) != 0) {
			this.sync(VK10.VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK10.VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT);
		} else if ((this.usage & VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT) != 0 || (this.usage & VK10.VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT) != 0) {
			this.sync(VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT, VK10.VK_ACCESS_SHADER_READ_BIT);
		}
	}

	public void sync(int stage, int access) {
		if (stage == this.srcStage && access == this.srcAccess) {
			return;
		}

		MemoryStack memoryStack = VkHelper.stackPush();

		VkCommandBuffer commandBuffer = VulkanDevice.getInstance().getCommandBuffer();

		VK10.vkCmdPipelineBarrier(
			commandBuffer, this.srcStage, stage, 0, null,
			VkBufferMemoryBarrier.calloc(1, memoryStack)
				.sType$Default()
				.srcAccessMask(this.srcAccess)
				.dstAccessMask(access)
				.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
				.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
				.buffer(this.buffer)
				.size(VK10.VK_WHOLE_SIZE), null
		);

		memoryStack.close();

		this.srcStage = stage;
		this.srcAccess = access;
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;

		VulkanDevice.getInstance().onFrameEnd(() -> {
			Vma.vmaDestroyBuffer(VulkanInstance.getInstance().getVmaAllocator(), this.buffer, this.vmaAllocation);
		});
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	public long getTexelBufferView(int format) {
		MemoryStack memoryStack = VkHelper.stackPush();

		VkBufferViewCreateInfo bufferViewCreate = VkBufferViewCreateInfo.calloc(memoryStack)
			.sType$Default()
			.buffer(this.buffer)
			.format(format)
			.offset(0)
			.range(VK10.VK_WHOLE_SIZE);

		LongBuffer bufferViewHandle = memoryStack.callocLong(1);

		if (VK10.vkCreateBufferView(VulkanDevice.getInstance().vkDevice, bufferViewCreate, null, bufferViewHandle) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		this.texelBufferView = bufferViewHandle.get(0);

		memoryStack.close();

		return this.texelBufferView;
	}

	public class VkMappedView implements MappedView {
		public final boolean write;
		private final ByteBuffer view;
		private boolean closed;

		public VkMappedView(long offset, int size, boolean write) {
			this.write = write;
			this.view = MemoryUtil.memByteBuffer(VkBuffer.this.mappedMemory + offset, size);
		}

		@Override
		public ByteBuffer data() {
			return this.view;
		}

		@Override
		public void close() {
			if (!this.closed) {
				this.closed = true;
			}
		}
	}

	public static VkBuffer createTempBuffer(long size) {
		BufferCreationData creationData = new BufferCreationData("Temp Buffer", GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_SRC, size);
		return VkBuffer.create(creationData, true, false);
	}

	public static VkBuffer create(BufferCreationData creationData, boolean hostVisible, boolean deviceLocal) {
		int bufferUsage = 0;

		boolean mapped = false;
		int usage = creationData.usage();

		if ((GpuBuffer.USAGE_MAP_READ & usage) != 0) {
			mapped = true;
		}
		if ((GpuBuffer.USAGE_MAP_WRITE & usage) != 0) {
			mapped = true;
		}

		if ((GpuBuffer.USAGE_COPY_DST & usage) != 0) {
			bufferUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
		}
		if ((GpuBuffer.USAGE_COPY_SRC & usage) != 0) {
			bufferUsage |= VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
		}
		if ((GpuBuffer.USAGE_VERTEX & usage) != 0) {
			bufferUsage |= VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
		}
		if ((GpuBuffer.USAGE_INDEX & usage) != 0) {
			bufferUsage |= VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
		}
		if ((GpuBuffer.USAGE_UNIFORM & usage) != 0) {
			bufferUsage |= VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
		}
		if ((GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER & usage) != 0) {
			bufferUsage |= VK10.VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT;
		}

		VkDevice device = VulkanDevice.getInstance().vkDevice;

		MemoryStack memoryStack = VkHelper.stackPush();

		VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(memoryStack)
			.sType$Default()
			.size(creationData.size())
			.usage(bufferUsage)
			.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

		long vmaAllocator = VulkanInstance.getInstance().getVmaAllocator();

		VmaAllocationCreateInfo allocationCreateInfo = VmaAllocationCreateInfo.calloc(memoryStack);
		allocationCreateInfo.usage(Vma.VMA_MEMORY_USAGE_AUTO);

		int baseFlags = mapped ? Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT : 0;
		if ((creationData.usage() & GpuBuffer.USAGE_MAP_READ) != 0) {
			allocationCreateInfo.flags(baseFlags | Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT);
		} else {
			allocationCreateInfo.flags(baseFlags | Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT);
		}

		int requiredFlags = 0;

		if (hostVisible) {
			requiredFlags = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
		}
		if (deviceLocal) {
			requiredFlags |= VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
		}

		allocationCreateInfo.requiredFlags(requiredFlags);

		LongBuffer bufferHandle = memoryStack.callocLong(1);
		PointerBuffer vmaAllocation = memoryStack.callocPointer(1);
		VmaAllocationInfo allocationInfo = VmaAllocationInfo.calloc(memoryStack);

		if (Vma.vmaCreateBuffer(vmaAllocator, bufferCreateInfo, allocationCreateInfo, bufferHandle, vmaAllocation, allocationInfo) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		long buffer = bufferHandle.get(0);

		VkBuffer vkBufferObj = new VkBuffer(creationData, buffer, vmaAllocation.get(0), bufferUsage);
		vkBufferObj.mappedMemory = allocationInfo.pMappedData();

		if (creationData.label() != null) {
			VkDebugUtilsObjectNameInfoEXT objectName = VkDebugUtilsObjectNameInfoEXT.calloc(memoryStack)
				.sType$Default()
				.objectType(VK10.VK_OBJECT_TYPE_BUFFER)
				.objectHandle(buffer)
				.pObjectName(memoryStack.UTF8(creationData.label()));

			if (EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(device, objectName) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}
		}

		memoryStack.close();

		return vkBufferObj;
	}
}
