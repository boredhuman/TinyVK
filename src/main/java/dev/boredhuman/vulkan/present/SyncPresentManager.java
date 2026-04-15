package dev.boredhuman.vulkan.present;

import dev.boredhuman.vulkan.AcquireResult;
import dev.boredhuman.vulkan.VkHelper;
import dev.boredhuman.vulkan.VulkanDevice;
import dev.boredhuman.vulkan.VulkanInstance;
import dev.boredhuman.vulkan.VulkanWindow;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;

import java.nio.IntBuffer;

public class SyncPresentManager implements PresentManager {
	private final VkQueue presentQueue;

	private final long[] acquireSemaphores = new long[VulkanInstance.FRAMES_IN_FLIGHT];
	private final long[] copyFinishedSemaphores = new long[VulkanInstance.FRAMES_IN_FLIGHT];

	private int presentIndex = 0;
	private volatile boolean updateSwapchain = false;

	public SyncPresentManager(VkQueue presentQueue) {
		this.presentQueue = presentQueue;

		VulkanDevice vulkanDevice = VulkanDevice.getInstance();

		for (int i = 0; i < VulkanInstance.FRAMES_IN_FLIGHT; i++) {
			this.acquireSemaphores[i] = vulkanDevice.createSemaphore("Present Acquire " + i);
			this.copyFinishedSemaphores[i] = vulkanDevice.createSemaphore("Copy Finished Semaphore " + i);
		}
	}

	@Override
	public AcquireResult acquireImage() {
		if (this.updateSwapchain) {
			VulkanWindow.getInstance().recreateSwapchain();

			this.updateSwapchain = false;
		}

		VkDevice device = VulkanDevice.getInstance().vkDevice;

		MemoryStack memoryStack = VkHelper.stackPush();

		IntBuffer imageIndexHandle = memoryStack.callocInt(1);

		long acquireSemaphore = this.acquireSemaphores[this.presentIndex];

		int result = KHRSwapchain.vkAcquireNextImageKHR(
			device, VulkanWindow.getInstance().swapChain, -1, acquireSemaphore, VK10.VK_NULL_HANDLE, imageIndexHandle);

		if (result != VK10.VK_SUCCESS && result != KHRSwapchain.VK_SUBOPTIMAL_KHR) {
			throw new RuntimeException();
		}

		int imageIndex = imageIndexHandle.get(0);
		long copyFinishedSemaphore = this.copyFinishedSemaphores[imageIndex];
		long swapchainImage = VulkanWindow.getInstance().swapchainImages[imageIndex];

		memoryStack.close();

		AcquireResult acquireResult = new AcquireResult(acquireSemaphore, imageIndex, swapchainImage, copyFinishedSemaphore);

		this.presentIndex = (this.presentIndex + 1) % VulkanInstance.FRAMES_IN_FLIGHT;

		return acquireResult;
	}

	@Override
	public void present(AcquireResult acquireResult) {
		MemoryStack memoryStack = VkHelper.stackPush();

		VkPresentInfoKHR presentInfoKHR = VkPresentInfoKHR.calloc(memoryStack)
			.sType$Default()
			.pWaitSemaphores(memoryStack.longs(acquireResult.copyFinishedSemaphore()))
			.swapchainCount(1)
			.pSwapchains(memoryStack.longs(VulkanWindow.getInstance().swapChain))
			.pImageIndices(memoryStack.ints(acquireResult.imageIndex()));

		int result = KHRSwapchain.vkQueuePresentKHR(this.presentQueue, presentInfoKHR);
		if (result == KHRSwapchain.VK_SUBOPTIMAL_KHR || result == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
			this.updateSwapchain = true;
		} else if (result != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		memoryStack.close();
	}

	@Override
	public void free() {
		VK10.vkQueueWaitIdle(this.presentQueue);

		VkDevice device = VulkanDevice.getInstance().vkDevice;

		for (int i = 0; i < VulkanInstance.FRAMES_IN_FLIGHT; i++) {
			VK10.vkDestroySemaphore(device, this.acquireSemaphores[i], null);
			VK10.vkDestroySemaphore(device, this.copyFinishedSemaphores[i], null);
		}
	}
}
