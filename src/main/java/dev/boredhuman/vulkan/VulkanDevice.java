package dev.boredhuman.vulkan;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import dev.boredhuman.impl.VkCommandEncoder;
import dev.boredhuman.impl.VkCompileResult;
import dev.boredhuman.impl.VkGpuDevice;
import dev.boredhuman.shaders.ProgramData;
import dev.boredhuman.shaders.ShaderCompiler;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class VulkanDevice {
	private static VulkanDevice INSTANCE;

	public VkDevice vkDevice;
	private DeviceLimits deviceLimits;

	private long commandPool;
	private VkQueue graphicsQueue;
	private int frameIndex;
	private long absoluteFrameIndex;
	private final VkCommandBuffer[] commandBuffers = new VkCommandBuffer[VulkanInstance.FRAMES_IN_FLIGHT];
	private final long[] commandFences = new long[VulkanInstance.FRAMES_IN_FLIGHT];
	private final List<List<Runnable>> frameFinishedTasks = IntStream.range(0, VulkanInstance.FRAMES_IN_FLIGHT)
		.mapToObj(i -> (List<Runnable>) new ArrayList<Runnable>()).toList();

	private VkCommandBuffer currentCommandBuffer;
	private final Map<RenderPipeline, PipelineGroup> pipelineGroups = new HashMap<>();
	private final Map<GlDevice.ShaderCompilationKey, String> cachedShaders = new HashMap<>();
	private VkImage colorTarget;
	private VkImage depthTarget;
	public boolean inRenderPass = false;
	private long boundPipeline;
	private PipelineGroup boundPipelineGroup;
	private VkGpuDevice impl;
	private VkCommandEncoder commandEncoder;
	private ShaderSource shaderSource;
	public PresentManager presentManager;

	public void init(VkDevice vkDevice, int queueFamilyIndex, DeviceLimits deviceLimits, ShaderSource shaderSource) {
		this.vkDevice = vkDevice;
		this.deviceLimits = deviceLimits;
		this.shaderSource = shaderSource;
		this.createCommandPool(queueFamilyIndex);
		this.graphicsQueue = this.getQueue(queueFamilyIndex, 0, "GraphicsQueue");

		for (int i = 0; i < VulkanInstance.FRAMES_IN_FLIGHT; i++) {
			this.commandFences[i] = this.createFence(true);
		}

		this.currentCommandBuffer = this.commandBuffers[0];

		this.impl = new VkGpuDevice();
		this.commandEncoder = new VkCommandEncoder();

		this.presentManager = new PresentManager(this.getQueue(queueFamilyIndex, 1, "PresentQueue"));

		this.startFrame();
	}

	public void startFrame() {
		long fence = this.commandFences[this.frameIndex];

		VK10.vkWaitForFences(this.vkDevice, fence, true, -1L);
		VK10.vkResetFences(this.vkDevice, fence);

		// copy incase there are chained deletions
		List<Runnable> frameFinishedTasks = new ArrayList<>(this.frameFinishedTasks.get(this.frameIndex));
		this.frameFinishedTasks.get(this.frameIndex).clear();
		for (Runnable frameFinishedTask : frameFinishedTasks) {
			frameFinishedTask.run();
		}

		MemoryStack memoryStack = VkHelper.stackPush();

		VkCommandBufferBeginInfo commandBufferBeginInfo = VkCommandBufferBeginInfo.calloc(memoryStack)
			.sType$Default()
			.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

		if (VK10.vkBeginCommandBuffer(this.currentCommandBuffer, commandBufferBeginInfo) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		memoryStack.close();
	}

	public void endFrame(VkImageView imageView) {
		MemoryStack memoryStack = VkHelper.stackPush();

		long fence = this.commandFences[this.frameIndex];

		AcquireResult acquireResult = this.presentManager.acquireImage();

		if (acquireResult == null) {
			if (VK10.vkEndCommandBuffer(this.currentCommandBuffer) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			VkSubmitInfo submitInfo = VkSubmitInfo.calloc(memoryStack)
				.sType$Default()
				.pCommandBuffers(memoryStack.pointers(this.currentCommandBuffer));

			if (VK10.vkQueueSubmit(this.graphicsQueue, submitInfo, fence) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}
		} else {
			this.endFrame(imageView, acquireResult);
		}

		memoryStack.close();

		this.frameIndex = (this.frameIndex + 1) % VulkanInstance.FRAMES_IN_FLIGHT;
		this.absoluteFrameIndex++;
		this.currentCommandBuffer = this.commandBuffers[this.frameIndex];
		this.boundPipeline = VK10.VK_NULL_HANDLE;

		this.startFrame();
	}

	public void endFrame(VkImageView vkImageView, AcquireResult acquireResult) {
		MemoryStack memoryStack = VkHelper.stackPush();

		int width = VulkanWindow.getInstance().width;
		int height = VulkanWindow.getInstance().height;

		vkImageView.vkImage.transitionTransferSrc();

		VK10.vkCmdPipelineBarrier(
			this.currentCommandBuffer, VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null,
			VkImageMemoryBarrier.calloc(1, memoryStack)
				.sType$Default()
				.srcAccessMask(0)
				.dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
				.oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
				.newLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
				.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
				.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
				.image(acquireResult.swapchainImage())
				.subresourceRange(
					srr -> srr.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).levelCount(VK10.VK_REMAINING_MIP_LEVELS).layerCount(VK10.VK_REMAINING_ARRAY_LAYERS))
		);

		VkImageBlit.Buffer imageBlits = VkImageBlit.calloc(1, memoryStack)
			.srcSubresource(subresourceLayers -> subresourceLayers.layerCount(1).aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT))
			.dstSubresource(subresourceLayers -> subresourceLayers.layerCount(1).aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT))
			.srcOffsets(0, offset -> offset.set(0, 0, 0))
			.srcOffsets(1, offset -> offset.set(vkImageView.vkImage.getWidth(0), vkImageView.vkImage.getHeight(0), 1))
			.dstOffsets(0, offset -> offset.set(0, height, 0))
			.dstOffsets(1, offset -> offset.set(width, 0, 1));

		VK10.vkCmdBlitImage(
			this.currentCommandBuffer, vkImageView.vkImage.image, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, acquireResult.swapchainImage(),
			VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, imageBlits, VK10.VK_FILTER_LINEAR
		);

		VK10.vkCmdPipelineBarrier(
			this.currentCommandBuffer, VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, 0, null, null,
			VkImageMemoryBarrier.calloc(1, memoryStack)
				.sType$Default()
				.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
				.dstAccessMask(0)
				.oldLayout(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
				.newLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
				.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
				.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
				.image(acquireResult.swapchainImage())
				.subresourceRange(
					srr -> srr.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).levelCount(VK10.VK_REMAINING_MIP_LEVELS).layerCount(VK10.VK_REMAINING_ARRAY_LAYERS))
		);

		if (VK10.vkEndCommandBuffer(this.currentCommandBuffer) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		VkSubmitInfo submitInfo = VkSubmitInfo.calloc(memoryStack)
			.sType$Default()
			.pCommandBuffers(memoryStack.pointers(this.currentCommandBuffer))
			.waitSemaphoreCount(1)
			.pWaitDstStageMask(memoryStack.ints(VK10.VK_PIPELINE_STAGE_TRANSFER_BIT))
			.pWaitSemaphores(memoryStack.longs(acquireResult.acquireSemaphore()))
			.pSignalSemaphores(memoryStack.longs(acquireResult.copyFinishedSemaphore()));

		long fence = this.commandFences[this.frameIndex];

		if (VK10.vkQueueSubmit(this.graphicsQueue, submitInfo, fence) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		this.presentManager.present(acquireResult);

		memoryStack.close();
	}

	public long createSemaphore(String label) {
		try (MemoryStack memoryStack = VkHelper.stackPush()) {
			VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(memoryStack)
				.sType$Default();

			LongBuffer semaphoreHandle = memoryStack.callocLong(1);

			if (VK10.vkCreateSemaphore(this.vkDevice, semaphoreCreateInfo, null, semaphoreHandle) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			long semaphore = semaphoreHandle.get(0);

			VkDebugUtilsObjectNameInfoEXT objectName = VkDebugUtilsObjectNameInfoEXT.calloc(memoryStack)
				.sType$Default()
				.objectType(VK10.VK_OBJECT_TYPE_SEMAPHORE)
				.objectHandle(semaphore)
				.pObjectName(memoryStack.UTF8(label));

			if (EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(this.vkDevice, objectName) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			return semaphore;
		}
	}

	private void createCommandPool(int graphicsQueue) {
		MemoryStack memoryStack = VkHelper.stackPush();

		LongBuffer commandPoolHandle = memoryStack.callocLong(1);

		VkCommandPoolCreateInfo commandPoolCreateInfo = VkCommandPoolCreateInfo.calloc(memoryStack);

		commandPoolCreateInfo
			.sType$Default()
			.flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
			.queueFamilyIndex(graphicsQueue);

		if (VK10.vkCreateCommandPool(this.vkDevice, commandPoolCreateInfo, null, commandPoolHandle) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		this.commandPool = commandPoolHandle.get(0);

		memoryStack.close();

		for (int i = 0; i < this.commandBuffers.length; i++) {
			this.commandBuffers[i] = this.createCommandBuffer();
		}
	}

	public long createFence(boolean signaled) {
		try (MemoryStack memoryStack = VkHelper.stackPush()) {
			VkFenceCreateInfo fenceCreateInfo = VkFenceCreateInfo.calloc(memoryStack)
				.sType$Default();

			if (signaled) {
				fenceCreateInfo.flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);
			}

			LongBuffer fenceHandle = memoryStack.callocLong(1);

			if (VK10.vkCreateFence(this.vkDevice, fenceCreateInfo, null, fenceHandle) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}
			return fenceHandle.get(0);
		}
	}

	private VkQueue getQueue(int graphicsQueue, int queueIndex, String label) {
		MemoryStack memoryStack = VkHelper.stackPush();

		PointerBuffer queueHandle = memoryStack.callocPointer(1);

		VK10.vkGetDeviceQueue(this.vkDevice, graphicsQueue, queueIndex, queueHandle);

		long queueObject = queueHandle.get(0);

		VkQueue queue = new VkQueue(queueObject, this.vkDevice);

		memoryStack.close();

		VkDebugUtilsObjectNameInfoEXT objectName = VkDebugUtilsObjectNameInfoEXT.calloc(memoryStack)
			.sType$Default()
			.objectType(VK10.VK_OBJECT_TYPE_QUEUE)
			.objectHandle(queueObject)
			.pObjectName(memoryStack.UTF8(label));

		if (EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(this.vkDevice, objectName) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		return queue;
	}

	public VkCommandBuffer createCommandBuffer() {
		MemoryStack memoryStack = VkHelper.stackPush();

		VkCommandBufferAllocateInfo allocateInfo = VkCommandBufferAllocateInfo.calloc(memoryStack)
			.sType$Default()
			.commandPool(this.commandPool)
			.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
			.commandBufferCount(1);

		PointerBuffer commandBuffer = memoryStack.callocPointer(1);

		VK10.vkAllocateCommandBuffers(this.vkDevice, allocateInfo, commandBuffer);

		long handle = commandBuffer.get(0);

		memoryStack.close();

		return new VkCommandBuffer(handle, this.vkDevice);
	}

	public VkCompileResult createStorePipeline(RenderPipeline renderPipeline, ShaderSource shaderSource) {
		VkCompileResult pipeline = this.createPipeline(renderPipeline, shaderSource);
		this.pipelineGroups.put(renderPipeline, pipeline.pipelineGroup());
		return pipeline;
	}

	public VkCompileResult createPipeline(RenderPipeline renderPipeline, ShaderSource shaderSource) {
		GlDevice.ShaderCompilationKey vertexShaderKey = new GlDevice.ShaderCompilationKey(
			renderPipeline.getVertexShader(), ShaderType.VERTEX, renderPipeline.getShaderDefines());

		GlDevice.ShaderCompilationKey fragmentShaderKey = new GlDevice.ShaderCompilationKey(
			renderPipeline.getFragmentShader(), ShaderType.FRAGMENT, renderPipeline.getShaderDefines());

		String vertexUnprocessed = this.cachedShaders.getOrDefault(vertexShaderKey, shaderSource.get(renderPipeline.getVertexShader(), ShaderType.VERTEX));
		String fragmentUnprocessed = this.cachedShaders.getOrDefault(
			fragmentShaderKey, shaderSource.get(renderPipeline.getFragmentShader(), ShaderType.FRAGMENT));

		if (vertexUnprocessed == null || fragmentUnprocessed == null) {
			return new VkCompileResult(VkCompileResult.Status.FAILED, null);
		}

		this.cachedShaders.put(vertexShaderKey, vertexUnprocessed);
		this.cachedShaders.put(fragmentShaderKey, fragmentUnprocessed);

		String vertexProcessed = GlslPreprocessor.injectDefines(vertexUnprocessed, renderPipeline.getShaderDefines());
		String fragmentProcessed = GlslPreprocessor.injectDefines(fragmentUnprocessed, renderPipeline.getShaderDefines());

		ProgramData programData = ShaderCompiler.getInstance()
			.convertProgram(
				vertexProcessed, renderPipeline.getVertexShader().getPath(), fragmentProcessed, renderPipeline.getFragmentShader().getPath(),
				renderPipeline.getVertexFormat()
			);

		return new VkCompileResult(VkCompileResult.Status.SUCCESS, new PipelineGroup(programData, renderPipeline));
	}

	public void onFrameEnd(Runnable task) {
		this.frameFinishedTasks.get(this.frameIndex).add(task);
	}

	public void renderPassBegin(VkImage color, VkImage depth) {
		this.colorTarget = color;
		this.depthTarget = depth;
	}

	public void renderPassEnd() {
		KHRDynamicRendering.vkCmdEndRenderingKHR(this.currentCommandBuffer);
		VulkanDevice.getInstance().inRenderPass = false;

		// sampled textures most of the are not used as render targets so keep them in layout shader read optimal
		// use the vertex stage since we don't for sure what stage it will be used on
		this.colorTarget.transition();
		if (this.depthTarget != null) {
			this.depthTarget.transition();
		}
	}

	public void bindPipeline(RenderPipeline renderPipeline) {
		PipelineGroup pipelineGroup = this.pipelineGroups.computeIfAbsent(
			renderPipeline, pipeline -> this.createPipeline(pipeline, this.shaderSource).pipelineGroup());

		int colorFormat = VkHelper.getFormat(this.colorTarget.creationData.textureFormat());
		int depthFormat = this.depthTarget == null ? VK10.VK_FORMAT_UNDEFINED : VkHelper.getFormat(this.depthTarget.creationData.textureFormat());
		long graphicsPipeline = pipelineGroup.bindPipeline(renderPipeline, new RenderPassFormat(colorFormat, depthFormat));

		if (graphicsPipeline == this.boundPipeline) {
			return;
		}

		VK10.vkCmdBindPipeline(this.currentCommandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
		this.boundPipeline = graphicsPipeline;
		this.boundPipelineGroup = pipelineGroup;
	}

	public DeviceLimits getDeviceLimits() {
		return this.deviceLimits;
	}

	public VkGpuDevice getImpl() {
		return this.impl;
	}

	public VkCommandEncoder getCommandEncoder() {
		return this.commandEncoder;
	}

	public PipelineGroup getBoundPipelineGroup() {
		return this.boundPipelineGroup;
	}

	public VkQueue getQueue() {
		return this.graphicsQueue;
	}

	public VkCommandBuffer getCommandBuffer() {
		return this.currentCommandBuffer;
	}

	public long getCurrentFrame() {
		return this.absoluteFrameIndex;
	}

	public boolean ensureFrameFinished(long frame, long timeout) {
		long dif = this.absoluteFrameIndex - frame;
		// current frame
		if (dif == 0) {
			return false;
			// super old frame
		} else if (dif >= VulkanInstance.FRAMES_IN_FLIGHT) {
			return true;
		} else {
			long fence = this.commandFences[(int) (frame % VulkanInstance.FRAMES_IN_FLIGHT)];

			MemoryStack memoryStack = VkHelper.stackPush();

			int result = VK10.vkWaitForFences(this.vkDevice, memoryStack.longs(fence), true, timeout);

			memoryStack.close();

			if (result == VK10.VK_SUCCESS) {
				return true;
			} else if (result == VK10.VK_TIMEOUT) {
				return false;
			} else {
				throw new RuntimeException("Bad wait");
			}
		}
	}

	public void free() {
		for (int i = 0; i < VulkanInstance.FRAMES_IN_FLIGHT; i++) {
			VK10.vkDestroyFence(this.vkDevice, this.commandFences[i], null);
			VK10.vkFreeCommandBuffers(this.vkDevice, this.commandPool, this.commandBuffers[i]);
		}

		VK10.vkDestroyCommandPool(this.vkDevice, this.commandPool, null);
		VK10.vkDestroyDevice(this.vkDevice, null);
	}

	public void clearPipelineCache() {
		// wait for graphics work to finish
		VK10.vkQueueWaitIdle(this.graphicsQueue);

		for (PipelineGroup pipelineGroup : this.pipelineGroups.values()) {
			pipelineGroup.free();
		}

		this.pipelineGroups.clear();
		this.cachedShaders.clear();
	}

	public static VulkanDevice getInstance() {
		if (VulkanDevice.INSTANCE == null) {
			VulkanDevice.INSTANCE = new VulkanDevice();
		}

		return VulkanDevice.INSTANCE;
	}
}
