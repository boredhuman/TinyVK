package dev.boredhuman.vulkan.present;

import dev.boredhuman.vulkan.AcquireResult;

public interface PresentManager {
	AcquireResult acquireImage();

	void present(AcquireResult acquireResult);

	void free();
}
