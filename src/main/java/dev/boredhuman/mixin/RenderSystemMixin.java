package dev.boredhuman.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.boredhuman.vulkan.VulkanDevice;
import dev.boredhuman.vulkan.VulkanInstance;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(RenderSystem.class)
public class RenderSystemMixin {

	@Shadow
	private static @Nullable GpuDevice DEVICE;

	@WrapOperation(
		method = "initRenderer", at = @At(value = "NEW", target = "(JIZLcom/mojang/blaze3d/shaders/ShaderSource;Z)Lcom/mojang/blaze3d/opengl/GlDevice;")
	)
	private static GlDevice tinyvk$init(long window, int logLevel, boolean syncLogs, ShaderSource shaderSource, boolean debugLabels, Operation<GlDevice> original) {
		VulkanInstance.create(shaderSource);
		if (VulkanInstance.DUAL_LAUNCH) {
			return original.call(window, logLevel, syncLogs, shaderSource, debugLabels);
		} else {
			return null;
		}
	}

	@ModifyArgs(
		method = "initRenderer", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlDevice;<init>(JIZLcom/mojang/blaze3d/shaders/ShaderSource;Z)V")
	)
	private static void tinyvk$addDebugLabels(Args args) {
		if (VulkanInstance.VALIDATION) {
			args.set(2, true);
			args.set(4, true);
		}
	}

	@Inject(
		method = "initRenderer",
		at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getDevice()Lcom/mojang/blaze3d/systems/GpuDevice;")
	)
	private static void tinyvk$init(long window, int logLevel, boolean syncLogs, ShaderSource shaderSource, boolean debugLabels, CallbackInfo ci) {
		if (!VulkanInstance.DUAL_LAUNCH) {
			RenderSystemMixin.DEVICE = VulkanDevice.getInstance().getImpl();
		}
	}

	@Redirect(method = "flipFrame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V"))
	private static void tinyvk$flipFrame(long window) {
		if (VulkanInstance.DUAL_LAUNCH) {
			GLFW.glfwSwapBuffers(window);
		}
	}
}
