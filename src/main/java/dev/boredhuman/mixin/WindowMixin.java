package dev.boredhuman.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.boredhuman.vulkan.VulkanInstance;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixin {
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
	public void tinyvk$createWindow(CallbackInfo ci) {
		if (!VulkanInstance.DUAL_LAUNCH) {
			GLFW.glfwDefaultWindowHints();
			GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
		}
	}
}
