package dev.boredhuman.vulkan;

import com.mojang.blaze3d.platform.Window;
import dev.boredhuman.TinyVK;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

public class VulkanWindow {
	private static VulkanWindow INSTANCE;

	long window;
	long surface;
	public long swapChain;
	int width;
	int height;

	public long[] swapchainImages;
	public long[] imageSemaphores;

	public void init() {
		this.setupSwapChain();
	}

	public long getSurface() {
		if (this.surface != 0) {
			return this.surface;
		}

		Window window = Minecraft.getInstance().getWindow();

		this.width = window.getWidth();
		this.height = window.getHeight();
		if (VulkanInstance.DUAL_LAUNCH) {
			GLFW.glfwDefaultWindowHints();
			GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
			this.window = GLFW.glfwCreateWindow(this.width, this.height, "Vulkan", 0L, 0L);
		} else {
			this.window = window.handle();
		}

		VkInstance vulkanInstance = VulkanInstance.getInstance().getVulkanInstance();

		MemoryStack memoryStack = VkHelper.stackPush();
		LongBuffer surfaceHandle = memoryStack.callocLong(1);

		GLFWVulkan.glfwCreateWindowSurface(vulkanInstance, this.window, null, surfaceHandle);

		this.surface = surfaceHandle.get(0);

		memoryStack.close();

		return this.surface;
	}

	private void destroySwapchain() {
		VkDevice device = VulkanInstance.getInstance().getVulkanDevice();
		VkQueue queue = VulkanDevice.getInstance().getQueue();

		VK10.vkQueueWaitIdle(queue);

		MemoryStack memoryStack = VkHelper.stackPush();
		IntBuffer width = memoryStack.callocInt(1);
		IntBuffer height = memoryStack.callocInt(1);
		memoryStack.close();

		boolean windowClosing = GLFW.glfwWindowShouldClose(this.window);

		if (!windowClosing) {
			GLFW.glfwGetFramebufferSize(this.window, width, height);
			this.width = width.get(0);
			this.height = height.get(0);
		}

		KHRSwapchain.vkDestroySwapchainKHR(device, this.swapChain, null);

		if (windowClosing) {
			VulkanDevice.getInstance().presentManager.free();
		}
	}

	public void recreateSwapchain() {
		this.destroySwapchain();

		this.setupSwapChain();
	}

	private VkSurfaceFormatKHR findSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
		for (int i = 0, len = formats.capacity(); i < len; i++) {
			int format = formats.get(i).format();
			if (format == VK10.VK_FORMAT_B8G8R8A8_UNORM || format == VK10.VK_FORMAT_R8G8B8A8_UNORM) {
				TinyVK.LOGGER.info("Found preferred format {} ", format);

				return formats.get(i);
			}
		}

		if (formats.remaining() == 0) {
			throw new RuntimeException("No surface formats were provided");
		}

		String supportedFormats = formats.stream().map(k -> Integer.toString(k.format()))
			.reduce("Supported surfarce formats: ", (a, b) -> a + "," + b);

		TinyVK.LOGGER.info(supportedFormats);
		TinyVK.LOGGER.info("Failed to find preferred surface format using {} instead", formats.get(0).format());

		return formats.get(0);
	}

	private void setupSwapChain() {
		VkPhysicalDevice physicalDevice = VulkanInstance.getInstance().getVulkanPhysicalDevice();
		VkDevice device = VulkanInstance.getInstance().getVulkanDevice();

		MemoryStack memoryStack = VkHelper.stackPush();

		IntBuffer formatCounts = memoryStack.callocInt(1);

		if (KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, this.surface, formatCounts, null) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.calloc(formatCounts.get(0), memoryStack);

		if (KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, this.surface, formatCounts, formats) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		VkSurfaceCapabilitiesKHR surfaceCapabilitiesKHR = VkSurfaceCapabilitiesKHR.calloc(memoryStack);

		if (KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, this.surface, surfaceCapabilitiesKHR) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		IntBuffer presentModesCount = memoryStack.callocInt(1);

		if (KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, this.surface, presentModesCount, null) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		IntBuffer presentModes = memoryStack.callocInt(presentModesCount.get(0));

		if (KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, this.surface, presentModesCount, presentModes) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		VkSurfaceFormatKHR surfaceFormat = this.findSurfaceFormat(formats);

		int imageCount = Math.min(surfaceCapabilitiesKHR.maxImageCount(), VulkanInstance.FRAMES_IN_FLIGHT);

		VkSwapchainCreateInfoKHR createInfoKHR = VkSwapchainCreateInfoKHR.calloc(memoryStack)
			.sType$Default()
			.surface(this.surface)
			.minImageCount(imageCount)
			.imageFormat(surfaceFormat.format())
			.imageColorSpace(surfaceFormat.colorSpace())
			.imageExtent(VkExtent2D.calloc(memoryStack).width(this.width).height(this.height))
			.imageArrayLayers(1)
			.imageUsage(VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT)
			.imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
			.presentMode(KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR)
			.preTransform(surfaceCapabilitiesKHR.currentTransform())
			.compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);

		LongBuffer swapChainHandle = memoryStack.callocLong(1);

		KHRSwapchain.vkCreateSwapchainKHR(device, createInfoKHR, null, swapChainHandle);

		this.swapChain = swapChainHandle.get(0);

		IntBuffer swapchainImagesCount = memoryStack.callocInt(1);

		if (KHRSwapchain.vkGetSwapchainImagesKHR(device, this.swapChain, swapchainImagesCount, null) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		LongBuffer images = memoryStack.callocLong(swapchainImagesCount.get(0));

		if (KHRSwapchain.vkGetSwapchainImagesKHR(device, this.swapChain, swapchainImagesCount, images) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		this.swapchainImages = new long[images.capacity()];
		images.get(this.swapchainImages);

		this.imageSemaphores = new long[images.capacity()];
		for (int i = 0; i < this.swapchainImages.length; i++) {
			this.imageSemaphores[i] = VulkanDevice.getInstance().createSemaphore("Image Semaphore " + i);
		}

		memoryStack.close();
	}

	public void free() {
		this.destroySwapchain();
	}

	public static VulkanWindow getInstance() {
		if (VulkanWindow.INSTANCE == null) {
			VulkanWindow.INSTANCE = new VulkanWindow();
		}

		return VulkanWindow.INSTANCE;
	}
}
