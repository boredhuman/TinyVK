package dev.boredhuman.vulkan.present;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dev.boredhuman.vulkan.AcquireResult;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AsyncPresentManager implements PresentManager {
	private final ExecutorService presentThread = Executors.newSingleThreadExecutor(
		new ThreadFactoryBuilder().setNameFormat("Present-Thread").setThreadFactory(Executors.defaultThreadFactory()).setDaemon(true)
			.setPriority(Thread.MAX_PRIORITY).build());

	private final VkQueue presentQueue;

	private final long[] acquireSemaphores = new long[VulkanInstance.FRAMES_IN_FLIGHT];
	private final long[] copyFinishedSemaphores = new long[VulkanInstance.FRAMES_IN_FLIGHT];

	private int presentIndex = 0;
	private Future<AcquireResult> currentFetch;
	private volatile boolean updateSwapchain = false;
	private MemoryStack presentMemory;

	public AsyncPresentManager(VkQueue presentQueue) {
		this.presentQueue = presentQueue;

		VulkanDevice vulkanDevice = VulkanDevice.getInstance();

		for (int i = 0; i < VulkanInstance.FRAMES_IN_FLIGHT; i++) {
			this.acquireSemaphores[i] = vulkanDevice.createSemaphore("Present Acquire " + i);
			this.copyFinishedSemaphores[i] = vulkanDevice.createSemaphore("Copy Finished Semaphore " + i);
		}

		// acquire memory for present thread
		this.presentThread.submit(() -> {
			this.presentMemory = MemoryStack.stackGet();
		});
	}

	@Override
	public AcquireResult acquireImage() {
		if (this.updateSwapchain && this.currentFetch == null) {
			VulkanWindow.getInstance().recreateSwapchain();

			this.updateSwapchain = false;
		}

		// if present is null its the first frame
		if (this.currentFetch == null) {
			this.queueFetch();
			return null;
		} else {
			if (this.currentFetch.isDone()) {
				AcquireResult acquireResult = this.currentFetch.resultNow();
				if (!this.updateSwapchain) {
					this.queueFetch();
				} else {
					this.currentFetch = null;
				}
				return acquireResult;
			} else {
				return null;
			}
		}
	}

	private void queueFetch() {
		if (this.updateSwapchain) {
			return;
		}

		this.currentFetch = this.presentThread.submit(() -> {
			VkDevice device = VulkanDevice.getInstance().vkDevice;

			MemoryStack memoryStack = this.presentMemory.push();

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
		});
	}

	@Override
	public void present(AcquireResult acquireResult) {
		this.presentThread.submit(() -> {
			MemoryStack memoryStack = this.presentMemory.push();

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
		});
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
