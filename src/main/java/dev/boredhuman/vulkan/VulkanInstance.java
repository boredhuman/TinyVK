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
import org.lwjgl.vulkan.KHRMaintenance2;
import org.lwjgl.vulkan.KHRMultiview;
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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VulkanInstance {
	public static final int FRAMES_IN_FLIGHT = 3;
	public static final boolean DUAL_LAUNCH = false;
	public static final boolean VALIDATION = false;
	public static final boolean DUMP_SHADERS = false;

	private static VulkanInstance INSTANCE;

	// extensions we need if only 1.0 is supported
	private static final Set<String> REQUIRED_EXTENSIONS_1_0 = Set.of(
		KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME,
		KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME,
		KHRDepthStencilResolve.VK_KHR_DEPTH_STENCIL_RESOLVE_EXTENSION_NAME, // Dependency of dynamic rendering,
		KHRCreateRenderpass2.VK_KHR_CREATE_RENDERPASS_2_EXTENSION_NAME, // Dependency of depth stencil resolve
		KHRMultiview.VK_KHR_MULTIVIEW_EXTENSION_NAME, // Dependency of create renderpass 2
		KHRMaintenance2.VK_KHR_MAINTENANCE2_EXTENSION_NAME, // Dependency of create render pass 2
		KHRPushDescriptor.VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME
	);

	// extensions we need if only 1.1 is supported
	private static final Set<String> REQUIRED_EXTENSIONS_1_1 = Set.of(
		KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME,
		KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME,
		KHRDepthStencilResolve.VK_KHR_DEPTH_STENCIL_RESOLVE_EXTENSION_NAME, // Dependency of dynamic rendering
		KHRCreateRenderpass2.VK_KHR_CREATE_RENDERPASS_2_EXTENSION_NAME, // Dependency of depth stencil resolve
		KHRPushDescriptor.VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME
	);

	// extensions we need if only 1.2 is supported
	private static final Set<String> REQUIRED_EXTENSIONS_1_2 = Set.of(
		KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME,
		KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME,
		KHRPushDescriptor.VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME
	);

	// extensions we need if only 1.3 is supported
	private static final Set<String> REQUIRED_EXTENSIONS_1_3 = Set.of(
		KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME,
		KHRPushDescriptor.VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME
	);

	private static final Set<String> REQUIRED_EXTENSIONS_1_4 = Set.of(
		KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME
	);

	// So we don't have to switch on VK13 / VK14 and the extension classes
	private static final Set<String> INCLUDE_DEPENDENCIES = Set.of(
		KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME,
		KHRPushDescriptor.VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME
	);

	private static final Map<Integer, Set<String>> REQUIRED_EXTENSIONS = Map.of(
		VK10.VK_MAKE_VERSION(1, 0, 0), VulkanInstance.REQUIRED_EXTENSIONS_1_0,
		VK10.VK_MAKE_VERSION(1, 1, 0), VulkanInstance.REQUIRED_EXTENSIONS_1_1,
		VK10.VK_MAKE_VERSION(1, 2, 0), VulkanInstance.REQUIRED_EXTENSIONS_1_2,
		VK10.VK_MAKE_VERSION(1, 3, 0), VulkanInstance.REQUIRED_EXTENSIONS_1_3,
		VK10.VK_MAKE_VERSION(1, 4, 0), VulkanInstance.REQUIRED_EXTENSIONS_1_4
	);

	private VkInstance vkInstance;
	private VkPhysicalDevice vkPhysicalDevice;
	private VkDevice vkDevice;
	private long vmaAllocator;

	public void init(ShaderSource shaderSource) {
		this.createInstance();
		this.choosePhysicalDevice(VulkanWindow.getInstance().getSurface());
		this.createDevice(shaderSource);
		VulkanWindow.getInstance().init();
	}

	private List<Map.Entry<Integer, Integer>> findWantedQueue(VkPhysicalDevice vkPhysicalDevice, long surface) {
		try (MemoryStack memoryStack = VkHelper.stackPush()) {
			IntBuffer queueFamilyCount = memoryStack.callocInt(1);

			VK10.vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, queueFamilyCount, null);

			VkQueueFamilyProperties.Buffer queueFamilyPropertiesList = VkQueueFamilyProperties.calloc(queueFamilyCount.get(0), memoryStack);

			VK10.vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, queueFamilyCount, queueFamilyPropertiesList);

			IntBuffer supported = memoryStack.callocInt(1);

			List<Map.Entry<Integer, Integer>> supportedQueueFamilies = new ArrayList<>();

			for (int i = 0, len = queueFamilyPropertiesList.remaining(); i < len; i++) {
				VkQueueFamilyProperties queueFamilyProperties = queueFamilyPropertiesList.get(i);

				if (KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(vkPhysicalDevice, i, surface, supported) != VK10.VK_SUCCESS) {
					throw new RuntimeException();
				}

				// want present support
				if (supported.get(0) == 0) {
					continue;
				}

				int queueCount = queueFamilyProperties.queueCount();

				if ((queueFamilyProperties.queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0) {
					supportedQueueFamilies.add(new AbstractMap.SimpleEntry<>(i, queueCount));
				}
			}

			return supportedQueueFamilies;
		}
	}

	private boolean doesDeviceHaveNeededExtensions(VkPhysicalDevice vkPhysicalDevice) {
		try (MemoryStack memoryStack = VkHelper.stackPush()) {
			VkPhysicalDeviceProperties physicalDeviceProperties = VkPhysicalDeviceProperties.calloc(memoryStack);

			VK10.vkGetPhysicalDeviceProperties(vkPhysicalDevice, physicalDeviceProperties);

			int apiVersion = physicalDeviceProperties.apiVersion();
			int major = VK10.VK_VERSION_MAJOR(apiVersion);
			int minor = VK10.VK_VERSION_MINOR(apiVersion);

			TinyVK.LOGGER.info("Device {} supports API version {}.{}", physicalDeviceProperties.deviceNameString(), major, minor);

			IntBuffer extensionCount = memoryStack.callocInt(1);

			if (VK10.vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, (CharSequence) null, extensionCount, null) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			VkExtensionProperties.Buffer extensions = VkExtensionProperties.calloc(extensionCount.get(0), memoryStack);

			if (VK10.vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, (CharSequence) null, extensionCount, extensions) != VK10.VK_SUCCESS) {
				throw new RuntimeException();
			}

			Set<String> extensionNames = new HashSet<>();

			for (int i = 0, len = extensionCount.get(0); i < len; i++) {
				VkExtensionProperties extensionProperties = extensions.get(i);

				extensionNames.add(extensionProperties.extensionNameString());
			}

			Set<String> requiredExtensions = this.getRequiredExtensions(physicalDeviceProperties.apiVersion());

			if (!extensionNames.containsAll(requiredExtensions)) {
				List<String> missingExtensions = new ArrayList<>();

				for (String requiredExtension : requiredExtensions) {
					if (!extensionNames.contains(requiredExtension)) {
						missingExtensions.add(requiredExtension);
					}
				}

				TinyVK.LOGGER.info("Device {} missing extensions {}", physicalDeviceProperties.deviceNameString(), missingExtensions);

				return false;
			}

			return true;
		}
	}

	private void createDevice(ShaderSource shaderSource) {
		MemoryStack memoryStack = VkHelper.stackPush();

		long surface = VulkanWindow.getInstance().getSurface();

		IntBuffer queueFamilyCount = memoryStack.callocInt(1);

		VK10.vkGetPhysicalDeviceQueueFamilyProperties(this.vkPhysicalDevice, queueFamilyCount, null);

		VkQueueFamilyProperties.Buffer queueFamilyPropertiesList = VkQueueFamilyProperties.calloc(queueFamilyCount.get(0), memoryStack);

		VK10.vkGetPhysicalDeviceQueueFamilyProperties(this.vkPhysicalDevice, queueFamilyCount, queueFamilyPropertiesList);

		List<Map.Entry<Integer, Integer>> wantedQueue = this.findWantedQueue(this.vkPhysicalDevice, surface);

		if (wantedQueue.isEmpty()) {
			throw new RuntimeException("Found no suitable queues!");
		}

		// find first queue family with 2 or more queues
		Map.Entry<Integer, Integer> useQueue = wantedQueue.stream().filter(e -> e.getValue() > 1).findFirst()
			.orElse(wantedQueue.getFirst());

		VkDeviceQueueCreateInfo.Buffer deviceQueueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, memoryStack)
			.sType$Default()
			.queueFamilyIndex(useQueue.getKey())
			.pQueuePriorities(memoryStack.floats(1.0F, 0.99F));

		Set<String> requiredExtensions = new HashSet<>(this.getRequiredExtensions(this.vkPhysicalDevice.getCapabilities().apiVersion));
		requiredExtensions.addAll(VulkanInstance.INCLUDE_DEPENDENCIES);

		PointerBuffer extensions = memoryStack.callocPointer(requiredExtensions.size());
		for (String requiredExtension : requiredExtensions) {
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
			.vulkanApiVersion(vkDevice.getCapabilitiesInstance().apiVersion);

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
		deviceLimits.supportsTimestamps = limits.timestampPeriod() != 0;

		VulkanDevice.getInstance().init(vkDevice, useQueue.getKey(), useQueue.getValue(), deviceLimits, shaderSource);
	}

	private void choosePhysicalDevice(long surface) {
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

			physicalDevices.removeIf(e -> !this.doesDeviceHaveNeededExtensions(e));

			if (physicalDevices.isEmpty()) {
				throw new RuntimeException("No devices support the needed extensions");
			}

			physicalDevices.removeIf(e -> this.findWantedQueue(e, surface).isEmpty());

			if (physicalDevices.isEmpty()) {
				throw new RuntimeException("No devices provided the needed queue types");
			}

			// sort by highest api version support first
			physicalDevices.sort(Collections.reverseOrder(Comparator.comparingInt(e -> e.getCapabilities().apiVersion)));

			VkPhysicalDeviceProperties.Buffer deviceProperties = VkPhysicalDeviceProperties.calloc(physicalDevices.size(), memoryStack);

			for (int i = 0; i < physicalDevices.size(); i++) {
				VkPhysicalDevice physicalDevice = physicalDevices.get(i);

				VK10.vkGetPhysicalDeviceProperties(physicalDevice, deviceProperties.get(i));
			}

			for (int i = 0, len = deviceProperties.remaining(); i < len; i++) {
				if (deviceProperties.get(i).deviceType() == VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
					TinyVK.LOGGER.info("Found discrete gpu {}", deviceProperties.deviceNameString());

					this.vkPhysicalDevice = physicalDevices.get(i);
					return;
				}
			}

			for (int i = 0, len = deviceProperties.remaining(); i < len; i++) {
				if (deviceProperties.get(i).deviceType() == VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) {
					TinyVK.LOGGER.info("Found integrated gpu {}", deviceProperties.deviceNameString());

					this.vkPhysicalDevice = physicalDevices.get(i);
					return;
				}
			}

			this.vkPhysicalDevice = physicalDevices.getFirst();
			TinyVK.LOGGER.info("Found no discrete or integrated graphics defaulting to first supported device");
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

		PointerBuffer extensions = memoryStack.callocPointer(glfwExtensions.remaining() + 3);
		extensions.put(glfwExtensions);
		// need this incase we only have 1.0 devices
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

	private Set<String> getRequiredExtensions(int deviceVersion) {
		int version = VK10.VK_MAKE_VERSION(1, Math.min(4, VK10.VK_VERSION_MINOR(deviceVersion)), 0);
		return VulkanInstance.REQUIRED_EXTENSIONS.get(version);
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
