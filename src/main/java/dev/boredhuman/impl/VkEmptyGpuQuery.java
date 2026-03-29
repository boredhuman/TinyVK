package dev.boredhuman.impl;

import com.mojang.blaze3d.systems.GpuQuery;

import java.util.OptionalLong;

/**
 * Used when the device doesn't support queries
 */
public class VkEmptyGpuQuery implements GpuQuery {
	@Override
	public OptionalLong getValue() {
		return OptionalLong.of(0);
	}

	@Override
	public void close() {

	}
}
