package dev.boredhuman.impl;

import dev.boredhuman.vulkan.VkImageView;
import dev.boredhuman.vulkan.VkSampler;

public record ViewSampler(VkImageView view, VkSampler sampler) {
}
