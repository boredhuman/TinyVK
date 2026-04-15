package dev.boredhuman.shaders;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.boredhuman.SpirvBinary;
import dev.boredhuman.SpirvBinaryLoader;
import dev.boredhuman.SpirvBinaryWriter;
import dev.boredhuman.TinyVK;
import dev.boredhuman.vulkan.AttributeType;
import dev.boredhuman.vulkan.VkHelper;
import dev.boredhuman.vulkan.VulkanDevice;
import dev.boredhuman.vulkan.VulkanInstance;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;
import org.lwjgl.vulkan.VkDevice;
import spirv.enumerants.BuiltIn;
import spirv.enumerants.Dim;
import spirv.instructions.Instruction;
import spirv.instructions.memory.OpVariable;
import spirv.instructions.typedeclaration.OpTypeFloat;
import spirv.instructions.typedeclaration.OpTypeImage;
import spirv.instructions.typedeclaration.OpTypeInt;
import spirv.instructions.typedeclaration.OpTypePointer;
import spirv.instructions.typedeclaration.OpTypeSampledImage;
import spirv.instructions.typedeclaration.OpTypeVector;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShaderCompiler {
	private static ShaderCompiler INSTANCE;

	private final long compiler;
	private final long compilerOptions;

	public ShaderCompiler() {
		this.compiler = Shaderc.shaderc_compiler_initialize();
		this.compilerOptions = Shaderc.shaderc_compile_options_initialize();

		Shaderc.shaderc_compile_options_set_auto_map_locations(this.compilerOptions, true);
		Shaderc.shaderc_compile_options_set_auto_bind_uniforms(this.compilerOptions, true);
		Shaderc.shaderc_compile_options_set_target_env(this.compilerOptions, Shaderc.shaderc_target_env_opengl, Shaderc.shaderc_env_version_opengl_4_5);
		Shaderc.shaderc_compile_options_set_target_spirv(this.compilerOptions, Shaderc.shaderc_spirv_version_1_0);
		// Use 4.6 for better compatibility
		Shaderc.shaderc_compile_options_set_forced_version_profile(this.compilerOptions, 460, Shaderc.shaderc_profile_core);
	}

	public ProgramData convertProgram(String vertexShader, String vertexLabel, String fragmentShader, String fragmentLabel, VertexFormat vertexFormat) {
		byte[] vertexBinary = this.toSpirvBinary(vertexShader, Shaderc.shaderc_vertex_shader);
		byte[] fragmentBinary = this.toSpirvBinary(fragmentShader, Shaderc.shaderc_fragment_shader);

		SpirvBinary vertexParsed = SpirvBinaryLoader.loadBinary(vertexBinary);
		SpirvBinary fragmentParsed = SpirvBinaryLoader.loadBinary(fragmentBinary);

		ShaderHelper vertexShaderHelper = new ShaderHelper(vertexParsed);
		ShaderHelper fragmentShaderHelper = new ShaderHelper(fragmentParsed);

		vertexShaderHelper.escapeSpecialNames();
		fragmentShaderHelper.escapeSpecialNames();

		vertexShaderHelper.setDecorationArg("gl_VertexID", BuiltIn.VERTEX_INDEX);
		vertexShaderHelper.setDecorationArg("gl_InstanceID", BuiltIn.INSTANCE_INDEX);

		Map<String, Integer> resourceBindings = new Object2IntOpenHashMap<>();
		Set<Integer> vertexUbos = new HashSet<>();
		Map<Integer, Dim> samplerDimensions = new Int2ObjectOpenHashMap<>();

		// make sure vertex and fragment shader are sharing bindings when possible
		int binding = 0;
		for (OpVariable variable : vertexShaderHelper.ubos) {
			String uboName = vertexShaderHelper.uboName(variable);
			vertexUbos.add(binding);
			resourceBindings.put(uboName, binding);

			vertexShaderHelper.setBinding(variable, binding);
			binding++;
		}

		Set<Integer> vertexSamplers = new IntOpenHashSet();

		for (Map.Entry<String, OpVariable> namedSampler : vertexShaderHelper.samplers.entrySet()) {
			String samplerName = namedSampler.getKey();
			OpVariable sampler = namedSampler.getValue();

			Dim dim = ((OpTypeImage) ((OpTypeSampledImage) ((OpTypePointer) sampler.resultType()).type).imageType).dim;

			samplerDimensions.put(binding, dim);

			resourceBindings.put(samplerName, binding);
			vertexShaderHelper.setBinding(sampler, binding);
			vertexSamplers.add(binding);

			// remove location from sampler
			vertexShaderHelper.removeLocationDecoration(sampler);

			binding++;
		}

		Set<Integer> fragmentUbos = new IntOpenHashSet();

		for (OpVariable ubo : fragmentShaderHelper.ubos) {
			String fragmentUboName = fragmentShaderHelper.uboName(ubo);
			Integer existingBinding = resourceBindings.get(fragmentUboName);

			if (existingBinding != null) {
				fragmentShaderHelper.setBinding(ubo, existingBinding);
				fragmentUbos.add(existingBinding);
			} else {
				int uboBinding = binding++;
				fragmentShaderHelper.setBinding(ubo, uboBinding);
				resourceBindings.put(fragmentUboName, uboBinding);
				fragmentUbos.add(uboBinding);
			}
		}

		Set<Integer> fragmentSamplers = new IntOpenHashSet();

		for (Map.Entry<String, OpVariable> namedSampler : fragmentShaderHelper.samplers.entrySet()) {
			String samplerName = namedSampler.getKey();
			OpVariable sampler = namedSampler.getValue();

			Dim dim = ((OpTypeImage) ((OpTypeSampledImage) ((OpTypePointer) sampler.resultType()).type).imageType).dim;

			Integer existingBinding = resourceBindings.get(samplerName);

			if (existingBinding != null) {
				fragmentShaderHelper.setBinding(sampler, existingBinding);
				fragmentSamplers.add(existingBinding);
			} else {
				int samplerBinding = binding++;
				fragmentShaderHelper.setBinding(sampler, samplerBinding);

				resourceBindings.put(samplerName, samplerBinding);
				samplerDimensions.put(samplerBinding, dim);
				fragmentSamplers.add(samplerBinding);
			}

			// remove location from sampler
			fragmentShaderHelper.removeLocationDecoration(sampler);
		}

		// make sure in locations of fragment shader match those of vertex shader
		for (Map.Entry<String, OpVariable> namedInput : fragmentShaderHelper.inputs.entrySet()) {
			String inputName = namedInput.getKey();

			OpVariable vertexOut = vertexShaderHelper.outputs.get(inputName);
			int location = vertexShaderHelper.getVariableLocation(vertexOut);
			if (location == -1) {
				continue;
			}

			fragmentShaderHelper.setVariableLocation(namedInput.getValue(), location);
		}

		List<AttributeType> attributeTypeList = new ArrayList<>();

		Set<OpVariable> assignedLocations = Collections.newSetFromMap(new IdentityHashMap<>());

		int location = 0;
		for (VertexFormatElement element : vertexFormat.getElements()) {
			String elementName = vertexFormat.getElementName(element);

			OpVariable opVariable = vertexShaderHelper.inputs.get(elementName);

			// not all shaders uses all the passed vertex attributes
			if (opVariable == null) {
				attributeTypeList.add(null);
				continue;
			}

			if (opVariable.idResultType instanceof OpTypePointer pointer) {
				if (pointer.type instanceof OpTypeVector vector) {
					attributeTypeList.add(new AttributeType(this.getType(vector.componentType), vector.componentCount));
				} else {
					attributeTypeList.add(new AttributeType(this.getType(pointer.type), 1));
				}
			} else {
				throw new RuntimeException("Unsupport vertex input " + opVariable.idResultType.getClass());
			}

			vertexShaderHelper.setVariableLocation(opVariable, location);
			assignedLocations.add(opVariable);

			location++;
		}

		// make sure the auto assigned locations don't get in the way
		for (OpVariable variable : vertexShaderHelper.inputs.values()) {
			if (!assignedLocations.contains(variable)) {
				vertexShaderHelper.setVariableLocation(variable, location);
				location++;
			}
		}

		fragmentShaderHelper.executionModeOriginUpperLeft();

		vertexShaderHelper.fixClipSpaceNew();

		long vertexShadermodule = this.toModule(vertexShaderHelper.getUpdatedBinary(), vertexLabel + "-vsh");
		long fragmentShaderModule = this.toModule(fragmentShaderHelper.getUpdatedBinary(), fragmentLabel + "-fsh");

		VkDevice device = VulkanDevice.getInstance().vkDevice;

		MemoryStack memoryStack = VkHelper.stackPush();

		VkDebugUtilsObjectNameInfoEXT vertexObjectLabel = VkDebugUtilsObjectNameInfoEXT.calloc(memoryStack)
			.sType$Default()
			.objectType(VK10.VK_OBJECT_TYPE_SHADER_MODULE)
			.objectHandle(vertexShadermodule)
			.pObjectName(memoryStack.UTF8(vertexLabel));

		EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(device, vertexObjectLabel);

		VkDebugUtilsObjectNameInfoEXT fragmentObjectLabel = VkDebugUtilsObjectNameInfoEXT.calloc(memoryStack)
			.sType$Default()
			.objectType(VK10.VK_OBJECT_TYPE_SHADER_MODULE)
			.objectHandle(fragmentShaderModule)
			.pObjectName(memoryStack.UTF8(fragmentLabel));

		EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(device, fragmentObjectLabel);

		memoryStack.close();

		return new ProgramData(
			resourceBindings, vertexUbos, vertexSamplers, fragmentUbos, fragmentSamplers, attributeTypeList, samplerDimensions, vertexShadermodule,
			fragmentShaderModule
		);
	}

	public long toModule(SpirvBinary binary, String label) {
		ByteArrayOutputStream vertexShaderByteArray = new ByteArrayOutputStream();
		SpirvBinaryWriter.write(vertexShaderByteArray, binary);

		if (VulkanInstance.DUMP_SHADERS) {
			try (FileOutputStream fos = new FileOutputStream(label.replace("/", "-") + ".spirv")) {
				fos.write(vertexShaderByteArray.toByteArray());
			} catch (Throwable err) {
				throw new RuntimeException(err);
			}

			TinyVK.LOGGER.info("Dumped {}", label);
		}

		int[] vulkanVertexBinary = new int[vertexShaderByteArray.size() / 4];
		ByteBuffer.wrap(vertexShaderByteArray.toByteArray()).order(ByteOrder.nativeOrder()).asIntBuffer().get(vulkanVertexBinary);

		return VkHelper.createShader(vulkanVertexBinary);
	}

	private VertexFormatElement.Type getType(Instruction instruction) {
		if (instruction instanceof OpTypeFloat) {
			return VertexFormatElement.Type.FLOAT;
		} else if (instruction instanceof OpTypeInt opTypeInt) {
			if (opTypeInt.signedness == 0) {
				return VertexFormatElement.Type.UINT;
			} else {
				return VertexFormatElement.Type.INT;
			}
		} else {
			throw new RuntimeException("Unsupported type input " + instruction.getClass());
		}
	}

	private byte[] toSpirvBinary(String source, int shaderKind) {
		long result = Shaderc.shaderc_compile_into_spv(this.compiler, source, shaderKind, "", "main", this.compilerOptions);

		if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
			throw new RuntimeException(Shaderc.shaderc_result_get_error_message(result));
		}

		ByteBuffer resultBytes = Shaderc.shaderc_result_get_bytes(result);
		if (resultBytes == null) {
			throw new RuntimeException();
		}

		byte[] spirv = new byte[resultBytes.remaining()];
		resultBytes.get(spirv);

		Shaderc.shaderc_result_release(result);

		return spirv;
	}

	public static ShaderCompiler getInstance() {
		if (ShaderCompiler.INSTANCE == null) {
			ShaderCompiler.INSTANCE = new ShaderCompiler();
		}

		return ShaderCompiler.INSTANCE;
	}
}
