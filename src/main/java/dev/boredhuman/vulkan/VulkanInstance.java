package dev.boredhuman.vulkan;

import com.mojang.blaze3d.shaders.ShaderSource;
import dev.boredhuman.TinyVK;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.KHRCreateRenderpass2;
import org.lwjgl.vulkan.KHRDepthStencilResolve;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkLayerProperties;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceDynamicRenderingFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceLimits;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VulkanInstance {
	public static final int FRAMES_IN_FLIGHT = 3;
	public static final boolean DUAL_LAUNCH = false;
	private static final boolean VALIDATION = false;
	public static final boolean DUMP_SHADERS = false;

	private static VulkanInstance INSTANCE;
	private static final List<String> REQUIRED_EXTENSIONS = List.of(
		KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME,
		KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME,
		KHRCreateRenderpass2.VK_KHR_CREATE_RENDERPASS_2_EXTENSION_NAME,
		KHRDepthStencilResolve.VK_KHR_DEPTH_STENCIL_RESOLVE_EXTENSION_NAME,
		KHRPushDescriptor.VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME
	);

	private VkInstance vkInstance;
	private VkPhysicalDevice vkPhysicalDevice;
	private VkDevice vkDevice;
	private long vmaAllocator;

	public void init(ShaderSource shaderSource) {
		this.createInstance();
		this.choosePhysicalDevice();
		this.createDevice(shaderSource);
		VulkanWindow.getInstance().init();
	}

	private void checkSupportedExtensions() {
		MemoryStack memoryStack = MemoryStack.stackPush();

		IntBuffer extensionCount = memoryStack.callocInt(1);

		if (VK10.vkEnumerateDeviceExtensionProperties(this.vkPhysicalDevice, (CharSequence) null, extensionCount, null) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		VkExtensionProperties.Buffer extensions = VkExtensionProperties.calloc(extensionCount.get(0), memoryStack);

		if (VK10.vkEnumerateDeviceExtensionProperties(this.vkPhysicalDevice, (CharSequence) null, extensionCount, extensions) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		Set<String> extensionNames = new HashSet<>();

		for (int i = 0, len = extensionCount.get(0); i < len; i++) {
			VkExtensionProperties extensionProperties = extensions.get(i);

			extensionNames.add(extensionProperties.extensionNameString());
		}

		if (!extensionNames.containsAll(VulkanInstance.REQUIRED_EXTENSIONS)) {
			List<String> missingExtensions = new ArrayList<>();

			for (String requiredExtension : VulkanInstance.REQUIRED_EXTENSIONS) {
				if (!extensionNames.contains(requiredExtension)) {
					missingExtensions.add(requiredExtension);
				}
			}

			throw new RuntimeException("Missing Extensions: " + missingExtensions.stream().reduce("", (a, b) -> a + "," + b));
		}

		memoryStack.close();
	}

	private void createDevice(ShaderSource shaderSource) {
		MemoryStack memoryStack = VkHelper.stackPush();

		long surface = VulkanWindow.getInstance().getSurface();

		IntBuffer queueFamilyCount = memoryStack.callocInt(1);

		VK10.vkGetPhysicalDeviceQueueFamilyProperties(this.vkPhysicalDevice, queueFamilyCount, null);

		VkQueueFamilyProperties.Buffer queueFamilyPropertiesList = VkQueueFamilyProperties.calloc(queueFamilyCount.get(0), memoryStack);

		VK10.vkGetPhysicalDeviceQueueFamilyProperties(this.vkPhysicalDevice, queueFamilyCount, queueFamilyPropertiesList);

		int queueFamilyIndex = -1;
		int timestampValidBits = 0;

		IntBuffer supported = memoryStack.callocInt(1);

		for (int i = 0, len = queueFamilyPropertiesList.remaining(); i < len; i++) {
			VkQueueFamilyProperties queueFamilyProperties = queueFamilyPropertiesList.get(0);

			if (KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(this.vkPhysicalDevice, i, surface, supported) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			if ((queueFamilyProperties.queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0 && supported.get(0) != 0) {
				queueFamilyIndex = i;
				timestampValidBits = queueFamilyProperties.timestampValidBits();
				break;
			}
		}

		if (queueFamilyIndex == -1) {
			throw new RuntimeException("Could not find queue family with graphics support");
		}

		VkDeviceQueueCreateInfo.Buffer deviceQueueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, memoryStack)
			.sType$Default()
			.queueFamilyIndex(queueFamilyIndex)
			.pQueuePriorities(memoryStack.floats(1.0F, 0.99F));

		this.checkSupportedExtensions();

		PointerBuffer extensions = memoryStack.callocPointer(VulkanInstance.REQUIRED_EXTENSIONS.size());
		for (String requiredExtension : VulkanInstance.REQUIRED_EXTENSIONS) {
			extensions.put(memoryStack.UTF8(requiredExtension, true));
		}

		extensions.flip();

		VkPhysicalDeviceDynamicRenderingFeatures physicalDeviceDynamicRenderingFeatures = VkPhysicalDeviceDynamicRenderingFeatures.calloc(memoryStack)
			.sType$Default()
			.dynamicRendering(true);

		VkPhysicalDeviceFeatures physicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc(memoryStack)
			.imageCubeArray(true);

		VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc(memoryStack)
			.sType$Default()
			.pNext(physicalDeviceDynamicRenderingFeatures)
			.pQueueCreateInfos(deviceQueueCreateInfo)
			.pEnabledFeatures(physicalDeviceFeatures)
			.ppEnabledExtensionNames(extensions);

		PointerBuffer deviceCreateHandle = memoryStack.callocPointer(1);

		if (VK10.vkCreateDevice(this.vkPhysicalDevice, deviceCreateInfo, null, deviceCreateHandle) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		VkDevice vkDevice = new VkDevice(deviceCreateHandle.get(0), this.vkPhysicalDevice, deviceCreateInfo);

		VmaAllocatorCreateInfo allocatorCreateInfo = VmaAllocatorCreateInfo.calloc(memoryStack)
			.physicalDevice(this.vkPhysicalDevice)
			.device(vkDevice)
			.instance(this.vkInstance)
			.pVulkanFunctions(VmaVulkanFunctions.calloc(memoryStack).set(this.vkInstance, vkDevice))
			.vulkanApiVersion(VK10.VK_API_VERSION_1_0);

		PointerBuffer vmaAllocatorHandle = memoryStack.callocPointer(1);

		if (Vma.vmaCreateAllocator(allocatorCreateInfo, vmaAllocatorHandle) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		this.vmaAllocator = vmaAllocatorHandle.get(0);

		memoryStack.close();

		this.vkDevice = vkDevice;

		VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
		VK10.vkGetPhysicalDeviceMemoryProperties(this.vkPhysicalDevice, memoryProperties);

		VkPhysicalDeviceProperties deviceProperties = VkPhysicalDeviceProperties.calloc(memoryStack);

		VK10.vkGetPhysicalDeviceProperties(this.vkPhysicalDevice, deviceProperties);

		VkPhysicalDeviceLimits limits = deviceProperties.limits();

		DeviceLimits deviceLimits = new DeviceLimits();
		deviceLimits.maxTexture = limits.maxImageDimension2D();
		deviceLimits.minUniformAlignment = (int) limits.minUniformBufferOffsetAlignment();
		deviceLimits.maxAnisotropy = limits.maxSamplerAnisotropy();
		deviceLimits.nonCoherentAtomSize = limits.nonCoherentAtomSize();
		deviceLimits.supportsTimestamps = limits.timestampPeriod() != 0 && timestampValidBits != 0;

		VulkanDevice.getInstance().init(vkDevice, queueFamilyIndex, memoryProperties, deviceLimits, shaderSource);
	}

	private void choosePhysicalDevice() {
		try (MemoryStack memoryStack = VkHelper.stackPush()) {
			IntBuffer devicesCount = memoryStack.callocInt(1);

			if (VK10.vkEnumeratePhysicalDevices(this.vkInstance, devicesCount, null) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			PointerBuffer devices = memoryStack.callocPointer(devicesCount.get(0));

			if (VK10.vkEnumeratePhysicalDevices(this.vkInstance, devicesCount, devices) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			List<VkPhysicalDevice> physicalDevices = new ArrayList<>();

			for (int i = 0, len = devicesCount.get(0); i < len; i++) {
				physicalDevices.add(new VkPhysicalDevice(devices.get(i), this.vkInstance));
			}

			VkPhysicalDeviceProperties.Buffer deviceProperties = VkPhysicalDeviceProperties.calloc(physicalDevices.size(), memoryStack);

			for (int i = 0; i < physicalDevices.size(); i++) {
				VkPhysicalDevice physicalDevice = physicalDevices.get(i);

				VK10.vkGetPhysicalDeviceProperties(physicalDevice, deviceProperties.get(0));
			}

			for (int i = 0, len = deviceProperties.remaining(); i < len; i++) {
				if (deviceProperties.get(i).deviceType() == VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
					TinyVK.LOGGER.info("Found discrete gpu {}", deviceProperties.deviceNameString());

					this.vkPhysicalDevice = physicalDevices.get(i);
				}
			}

			for (int i = 0, len = deviceProperties.remaining(); i < len; i++) {
				if (deviceProperties.get(i).deviceType() == VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) {
					TinyVK.LOGGER.info("Found integrated gpu {}", deviceProperties.deviceNameString());

					this.vkPhysicalDevice = physicalDevices.get(i);
				}
			}

			this.vkPhysicalDevice = physicalDevices.getFirst();
		}
	}

	private void createInstance() {
		MemoryStack memoryStack = VkHelper.stackPush();

		IntBuffer instanceExtensions = memoryStack.callocInt(1);

		VK10.vkEnumerateInstanceExtensionProperties((ByteBuffer) null, instanceExtensions, null);

		VkExtensionProperties.Buffer extensionProperties = VkExtensionProperties.calloc(instanceExtensions.get(0), memoryStack);

		VK10.vkEnumerateInstanceExtensionProperties((ByteBuffer) null, instanceExtensions, extensionProperties);

		boolean found = false;
		for (int i = 0, len = instanceExtensions.get(0); i < len; i++) {
			String instanceExtension = extensionProperties.get(i).extensionNameString();
			if (instanceExtension.equals(KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME)) {
				found = true;
			}
		}

		if (!found) {
			throw new RuntimeException("Missing " + KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME);
		}

		VkInstanceCreateInfo vkInstanceCreateInfo = VkInstanceCreateInfo.calloc(memoryStack)
			.sType$Default();

		if (VulkanInstance.VALIDATION) {
			IntBuffer layerCount = memoryStack.callocInt(1);

			if (VK10.vkEnumerateInstanceLayerProperties(layerCount, null) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			VkLayerProperties.Buffer layers = VkLayerProperties.create(layerCount.get(0));

			if (VK10.vkEnumerateInstanceLayerProperties(layerCount, layers) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			if (layers.stream().anyMatch(e -> e.layerNameString().equals("VK_LAYER_KHRONOS_validation"))) {
				PointerBuffer pointerBuffer = memoryStack.callocPointer(1);

				pointerBuffer.put(0, memoryStack.ASCII("VK_LAYER_KHRONOS_validation"));

				vkInstanceCreateInfo.ppEnabledLayerNames(pointerBuffer);
			} else {
				TinyVK.LOGGER.info("Failed to find validation layer");
			}
		}

		PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();

		PointerBuffer extensions = memoryStack.callocPointer(glfwExtensions.remaining() + 2);
		extensions.put(glfwExtensions);
		extensions.put(memoryStack.UTF8(KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME));
		extensions.put(memoryStack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME));

		extensions.flip();

		vkInstanceCreateInfo.ppEnabledExtensionNames(extensions);

		PointerBuffer vulkanInstance = memoryStack.callocPointer(1);
		if (VK10.vkCreateInstance(vkInstanceCreateInfo, null, vulkanInstance) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		memoryStack.close();

		this.vkInstance = new VkInstance(vulkanInstance.get(0), vkInstanceCreateInfo);
	}

	public VkInstance getVulkanInstance() {
		return this.vkInstance;
	}

	public VkPhysicalDevice getVulkanPhysicalDevice() {
		return this.vkPhysicalDevice;
	}

	public VkDevice getVulkanDevice() {
		return this.vkDevice;
	}

	public long getVmaAllocator() {
		return this.vmaAllocator;
	}

	public void delete() {
		VulkanWindow.getInstance().free();
		VulkanDevice.getInstance().free();
		VK10.vkDestroyInstance(this.vkInstance, null);
	}

	public static void create(ShaderSource shaderSource) {
		VulkanInstance.INSTANCE = new VulkanInstance();
		VulkanInstance.INSTANCE.init(shaderSource);
	}

	public static VulkanInstance getInstance() {
		return VulkanInstance.INSTANCE;
	}
}
