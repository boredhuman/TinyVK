package dev.boredhuman.vulkan;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public record AttributeType(VertexFormatElement.Type type, int count) {
}
