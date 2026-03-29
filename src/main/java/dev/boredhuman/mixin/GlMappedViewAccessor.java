package dev.boredhuman.mixin;

import com.mojang.blaze3d.opengl.GlBuffer;
import dev.boredhuman.types.MappedViewHolder;
import dev.boredhuman.vulkan.VkBuffer;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(GlBuffer.GlMappedView.class)
public abstract class GlMappedViewAccessor implements MappedViewHolder {
	@Shadow
	public abstract ByteBuffer data();

	@Unique
	private VkBuffer.VkMappedView tinyvk$mappedView;

	@Override
	public void tinyvk$setMappedView(VkBuffer.VkMappedView mappedView) {
		this.tinyvk$mappedView = mappedView;
	}

	// synchronize the two buffers
	@Inject(method = "close", at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V"))
	private void tinyvk$duplicate(CallbackInfo ci) {
		// make sure we have the writes
		if (this.tinyvk$mappedView.write) {
			ByteBuffer data = this.data();
			long src = MemoryUtil.memAddress0(data);
			long dst = MemoryUtil.memAddress0(this.tinyvk$mappedView.data());
			MemoryUtil.memCopy(src, dst, data.capacity());
		}
	}

	@Inject(method = "close", at = @At("TAIL"))
	private void tinyvk$close(CallbackInfo ci) {
		this.tinyvk$mappedView.close();
	}
}
