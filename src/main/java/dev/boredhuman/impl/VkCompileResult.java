package dev.boredhuman.impl;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import dev.boredhuman.vulkan.PipelineGroup;

public record VkCompileResult(Status status, PipelineGroup pipelineGroup) implements CompiledRenderPipeline {

	@Override
	public boolean isValid() {
		return this.status == Status.SUCCESS;
	}

	public enum Status {
		SUCCESS,
		FAILED
	}
}
