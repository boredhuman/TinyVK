package dev.boredhuman.mixin;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VertexFormat.Builder.class)
public abstract class VertexFormatBuilderMixin {

	@Shadow
	private int offset;

	@Shadow
	public abstract VertexFormat.Builder padding(int i);

	@Unique
	private VertexFormatElement tinyvk$lastElement;

	@Inject(method = "add", at = @At("HEAD"))
	private void tinyvk$add(String string, VertexFormatElement vertexFormatElement, CallbackInfoReturnable<VertexFormat.Builder> cir) {
		if (this.tinyvk$lastElement != null && this.tinyvk$lastElement == VertexFormatElement.NORMAL) {
			if (this.offset % 4 != 0) {
				this.padding(1);
			}
		}
		this.tinyvk$lastElement = vertexFormatElement;
	}

	@Inject(method = "build", at = @At("HEAD"))
	private void tinyvk$build(CallbackInfoReturnable<VertexFormat> cir) {
		if (this.tinyvk$lastElement != null && this.tinyvk$lastElement == VertexFormatElement.NORMAL) {
			if (this.offset % 4 != 0) {
				this.padding(1);
			}
		}
	}
}
