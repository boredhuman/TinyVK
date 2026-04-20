package dev.boredhuman.vulkan;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.LogicOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.boredhuman.shaders.ProgramData;
import org.lwjgl.vulkan.VK10;

import java.util.List;

public class PipelineConverter {

	private static final int[] F32 = new int[] {
		VK10.VK_FORMAT_R32_SFLOAT,
		VK10.VK_FORMAT_R32G32_SFLOAT,
		VK10.VK_FORMAT_R32G32B32_SFLOAT,
		VK10.VK_FORMAT_R32G32B32A32_SFLOAT
	};

	private static final int[] I32 = new int[] {
		VK10.VK_FORMAT_R32_SINT,
		VK10.VK_FORMAT_R32G32_SINT,
		VK10.VK_FORMAT_R32G32B32_SINT,
		VK10.VK_FORMAT_R32G32B32A32_SINT
	};

	private static final int[] I16 = new int[] {
		VK10.VK_FORMAT_R16_SINT,
		VK10.VK_FORMAT_R16G16_SINT,
		VK10.VK_FORMAT_R16G16B16_SINT,
		VK10.VK_FORMAT_R16G16B16A16_SINT
	};

	private static final int[] I8 = new int[] {
		VK10.VK_FORMAT_R8_SINT,
		VK10.VK_FORMAT_R8G8_SINT,
		VK10.VK_FORMAT_R8G8B8_SINT,
		VK10.VK_FORMAT_R8G8B8A8_SINT
	};

	private static final int[] U32 = new int[] {
		VK10.VK_FORMAT_R32_UINT,
		VK10.VK_FORMAT_R32G32_UINT,
		VK10.VK_FORMAT_R32G32B32_UINT,
		VK10.VK_FORMAT_R32G32B32A32_UINT
	};

	private static final int[] U16 = new int[] {
		VK10.VK_FORMAT_R16_UINT,
		VK10.VK_FORMAT_R16G16_UINT,
		VK10.VK_FORMAT_R16G16B16_UINT,
		VK10.VK_FORMAT_R16G16B16A16_UINT
	};

	private static final int[] U8 = new int[] {
		VK10.VK_FORMAT_R8_UINT,
		VK10.VK_FORMAT_R8G8_UINT,
		VK10.VK_FORMAT_R8G8B8_UINT,
		VK10.VK_FORMAT_R8G8B8A8_UINT
	};

	private static final int[] I16F = new int[] {
		VK10.VK_FORMAT_R16_SSCALED,
		VK10.VK_FORMAT_R16G16_SSCALED,
		VK10.VK_FORMAT_R16G16B16_SSCALED,
		VK10.VK_FORMAT_R16G16B16A16_SSCALED
	};

	private static final int[] U16F = new int[] {
		VK10.VK_FORMAT_R16_USCALED,
		VK10.VK_FORMAT_R16G16_USCALED,
		VK10.VK_FORMAT_R16G16B16_USCALED,
		VK10.VK_FORMAT_R16G16B16A16_USCALED
	};

	private static final int[] I8F = new int[] {
		VK10.VK_FORMAT_R8_SSCALED,
		VK10.VK_FORMAT_R8G8_SSCALED,
		VK10.VK_FORMAT_R8G8B8_SSCALED,
		VK10.VK_FORMAT_R8G8B8A8_SSCALED
	};

	private static final int[] U8F = new int[] {
		VK10.VK_FORMAT_R8_USCALED,
		VK10.VK_FORMAT_R8G8_USCALED,
		VK10.VK_FORMAT_R8G8B8_USCALED,
		VK10.VK_FORMAT_R8G8B8A8_USCALED
	};

	private static final int[] I16_NORM = new int[] {
		VK10.VK_FORMAT_R16_SNORM,
		VK10.VK_FORMAT_R16G16_SNORM,
		VK10.VK_FORMAT_R16G16B16_SNORM,
		VK10.VK_FORMAT_R16G16B16A16_SNORM,
	};

	private static final int[] I8_NORM = new int[] {
		VK10.VK_FORMAT_R8_SNORM,
		VK10.VK_FORMAT_R8G8_SNORM,
		VK10.VK_FORMAT_R8G8B8_SNORM,
		VK10.VK_FORMAT_R8G8B8A8_SNORM,
	};

	private static final int[] U16_NORM = new int[] {
		VK10.VK_FORMAT_R16_UNORM,
		VK10.VK_FORMAT_R16G16_UNORM,
		VK10.VK_FORMAT_R16G16B16_UNORM,
		VK10.VK_FORMAT_R16G16B16A16_UNORM,
	};
	
	private static final int[] U8_NORM = new int[] {
		VK10.VK_FORMAT_R8_UNORM,
		VK10.VK_FORMAT_R8G8_UNORM,
		VK10.VK_FORMAT_R8G8B8_UNORM,
		VK10.VK_FORMAT_R8G8B8A8_UNORM,
	};

	private static int depthTest(DepthTestFunction depthTestFunction) {
		return switch(depthTestFunction) {
			case NO_DEPTH_TEST -> VK10.VK_COMPARE_OP_ALWAYS;
			case EQUAL_DEPTH_TEST -> VK10.VK_COMPARE_OP_EQUAL;
			case LEQUAL_DEPTH_TEST -> VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
			case LESS_DEPTH_TEST -> VK10.VK_COMPARE_OP_LESS;
			case GREATER_DEPTH_TEST -> VK10.VK_COMPARE_OP_GREATER;
		};
	}

	private static int polygonMode(PolygonMode polygonMode) {
		return switch(polygonMode) {
			case FILL -> VK10.VK_POLYGON_MODE_FILL;
			case WIREFRAME -> VK10.VK_POLYGON_MODE_LINE;
		};
	}

	private static int logicCompareOp(LogicOp logicOp) {
		return switch(logicOp) {
			case NONE -> 0;
			case OR_REVERSE -> VK10.VK_LOGIC_OP_OR_REVERSE;
		};
	}

	private static int sourceBlendFactor(SourceFactor sourceFactor) {
		return switch(sourceFactor) {
			case CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA;
			case CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_CONSTANT_COLOR;
			case DST_ALPHA -> VK10.VK_BLEND_FACTOR_DST_ALPHA;
			case DST_COLOR -> VK10.VK_BLEND_FACTOR_DST_COLOR;
			case ONE -> VK10.VK_BLEND_FACTOR_ONE;
			case ONE_MINUS_CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
			case ONE_MINUS_CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
			case ONE_MINUS_DST_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
			case ONE_MINUS_DST_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
			case ONE_MINUS_SRC_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
			case ONE_MINUS_SRC_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
			case SRC_ALPHA -> VK10.VK_BLEND_FACTOR_SRC_ALPHA;
			case SRC_ALPHA_SATURATE -> VK10.VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
			case SRC_COLOR -> VK10.VK_BLEND_FACTOR_SRC_COLOR;
			case ZERO -> VK10.VK_BLEND_FACTOR_ZERO;
		};
	}

	private static int destBlendFactor(DestFactor destFactor) {
		return switch(destFactor) {
			case CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA;
			case CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_CONSTANT_COLOR;
			case DST_ALPHA -> VK10.VK_BLEND_FACTOR_DST_ALPHA;
			case DST_COLOR -> VK10.VK_BLEND_FACTOR_DST_COLOR;
			case ONE -> VK10.VK_BLEND_FACTOR_ONE;
			case ONE_MINUS_CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
			case ONE_MINUS_CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
			case ONE_MINUS_DST_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
			case ONE_MINUS_DST_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
			case ONE_MINUS_SRC_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
			case ONE_MINUS_SRC_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
			case SRC_ALPHA -> VK10.VK_BLEND_FACTOR_SRC_ALPHA;
			case SRC_COLOR -> VK10.VK_BLEND_FACTOR_SRC_COLOR;
			case ZERO -> VK10.VK_BLEND_FACTOR_ZERO;
		};
	}

	private static int topology(VertexFormat.Mode mode) {
		return switch(mode) {
			case LINES, TRIANGLES, QUADS -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
			case DEBUG_LINES -> VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
			case DEBUG_LINE_STRIP -> VK10.VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
			case POINTS -> VK10.VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
			case TRIANGLE_STRIP -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
			case TRIANGLE_FAN -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
		};
	}

	public static int getFormat(VertexFormatElement vertexFormatElement, AttributeType attributeType) {
		boolean normalized = false;
		boolean integer = false;
		int count = vertexFormatElement.count() - 1;

		switch (vertexFormatElement.usage()) {
			case POSITION:
			case GENERIC:
			case UV:
				if (vertexFormatElement.type() != VertexFormatElement.Type.FLOAT) {
					// attribute type might be null if it's unused
					// shader might define the input as a regular vec instead of integer vector
					integer = attributeType == null || attributeType.type() != VertexFormatElement.Type.FLOAT;
				}
				break;
			case NORMAL:
			case COLOR:
				normalized = true;
		}

		if (integer) {
			return switch(vertexFormatElement.type()) {
				case BYTE -> PipelineConverter.I8[count];
				case SHORT -> PipelineConverter.I16[count];
				case INT -> PipelineConverter.I32[count];
				case UBYTE -> PipelineConverter.U8[count];
				case USHORT -> PipelineConverter.U16[count];
				case UINT -> PipelineConverter.U32[count];
				default -> throw new RuntimeException();
			};
		}

		if (normalized) {
			return switch(vertexFormatElement.type()) {
				case BYTE -> PipelineConverter.I8_NORM[count];
				case SHORT -> PipelineConverter.I16_NORM[count];
				case UBYTE -> PipelineConverter.U8_NORM[count];
				case USHORT -> PipelineConverter.U16_NORM[count];
				default -> throw new RuntimeException();
			};
		}

		if (vertexFormatElement.type() == VertexFormatElement.Type.FLOAT) {
			return PipelineConverter.F32[count];
		}

		return switch(vertexFormatElement.type()) {
			case BYTE -> PipelineConverter.I8F[count];
			case SHORT -> PipelineConverter.I16F[count];
			case UBYTE -> PipelineConverter.U8F[count];
			case USHORT -> PipelineConverter.U16F[count];
			default -> throw new RuntimeException();
		};
	}

	public static GraphicsPipelineBuilder convert(RenderPipeline renderPipeline, ProgramData programData) {
		GraphicsPipelineBuilder graphicsPipelineBuilder = GraphicsPipelineBuilder.create()
			.depthCompareOp(PipelineConverter.depthTest(renderPipeline.getDepthTestFunction()))
			.depthTest(renderPipeline.getDepthTestFunction() != DepthTestFunction.NO_DEPTH_TEST)
			.polygonMode(PipelineConverter.polygonMode(renderPipeline.getPolygonMode()))
			.cullMode(renderPipeline.isCull() ? VK10.VK_CULL_MODE_BACK_BIT : VK10.VK_CULL_MODE_NONE)
			.blend(renderPipeline.getBlendFunction().isPresent())
			.srcColorBlendFactor(renderPipeline.getBlendFunction().map(e -> PipelineConverter.sourceBlendFactor(e.sourceColor())).orElse(0))
			.dstColorBlendFactor(renderPipeline.getBlendFunction().map(e -> PipelineConverter.destBlendFactor(e.destColor())).orElse(0))
			.srcAlphaBlendFactor(renderPipeline.getBlendFunction().map(e -> PipelineConverter.sourceBlendFactor(e.sourceAlpha())).orElse(0))
			.dstAlphaBlendFactor(renderPipeline.getBlendFunction().map(e -> PipelineConverter.destBlendFactor(e.destAlpha())).orElse(0))
			.writeColor(renderPipeline.isWriteColor())
			.writeAlpha(renderPipeline.isWriteAlpha())
			.writeDepth(renderPipeline.isWriteDepth())
			.depthBiasSlopeFactor(renderPipeline.getDepthBiasScaleFactor())
			.depthBiasConstantFactor(renderPipeline.getDepthBiasConstant())
			.depthBias(renderPipeline.getDepthBiasScaleFactor() != 0.0F || renderPipeline.getDepthBiasConstant() != 0.0F)
			.topology(PipelineConverter.topology(renderPipeline.getVertexFormatMode()));

		VertexFormat vertexFormat = renderPipeline.getVertexFormat();
		List<AttributeType> attributeTypeList = programData.attributeTypeList;

		for (int i = 0; i < vertexFormat.getElements().size(); i++) {
			VertexFormatElement element = vertexFormat.getElements().get(i);
			AttributeType attributeType = attributeTypeList.get(i);

			graphicsPipelineBuilder.attribute(i, 0, PipelineConverter.getFormat(element, attributeType), vertexFormat.getOffset(element));
		}

		graphicsPipelineBuilder.binding(0, vertexFormat.getVertexSize(), VK10.VK_VERTEX_INPUT_RATE_VERTEX);

		return graphicsPipelineBuilder;
	}
}
