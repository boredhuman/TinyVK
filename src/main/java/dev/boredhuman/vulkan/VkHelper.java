package dev.boredhuman.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

public class VkHelper {

	// Avoid potentially expensive TLS lookup
	private static final MemoryStack MAIN_THREAD_MEMORY_STACK = MemoryStack.stackGet();

	public static MemoryStack stackPush() {
		return VkHelper.MAIN_THREAD_MEMORY_STACK.push();
	}

	public static boolean deviceLocal(int usage) {
		if ((usage & GpuBuffer.USAGE_VERTEX) != 0) {
			return true;
		}
		if ((usage & GpuBuffer.USAGE_INDEX) != 0) {
			return true;
		}
		if ((usage & GpuBuffer.USAGE_UNIFORM) != 0) {
			return true;
		}
		if ((usage & GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER) != 0) {
			return true;
		}
		return false;
	}

	public static int getFormat(TextureFormat textureFormat) {
		return switch(textureFormat) {
			case RGBA8 -> VK10.VK_FORMAT_R8G8B8A8_UNORM;
			case RED8 -> VK10.VK_FORMAT_R8_UNORM;
			case RED8I -> VK10.VK_FORMAT_R8_SINT;
			case DEPTH32 -> VK10.VK_FORMAT_D32_SFLOAT;
		};
	}

	public static long createShader(int[] programBinary) {
		MemoryStack memoryStack = VkHelper.stackPush();

		ByteBuffer codeBuffer = memoryStack.malloc(4 * programBinary.length);
		codeBuffer.asIntBuffer().put(0, programBinary);

		VkShaderModuleCreateInfo shaderModuleCreateInfo = VkShaderModuleCreateInfo.calloc(memoryStack)
			.sType$Default()
			.pCode(codeBuffer);

		LongBuffer shaderHandle = memoryStack.callocLong(1);

		VkDevice device = VulkanDevice.getInstance().vkDevice;

		if (VK10.vkCreateShaderModule(device, shaderModuleCreateInfo, null, shaderHandle) != VK10.VK_SUCCESS) {
			throw new RuntimeException();
		}

		long shader = shaderHandle.get(0);

		memoryStack.close();

		return shader;
	}
}
