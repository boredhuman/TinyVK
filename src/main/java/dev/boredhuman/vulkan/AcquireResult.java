package dev.boredhuman.vulkan;

public record AcquireResult(long acquireSemaphore, int imageIndex, long swapchainImage, long copyFinishedSemaphore) {
}
