package dev.boredhuman.shaders;

import dev.boredhuman.vulkan.AttributeType;
import spirv.enumerants.Dim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProgramData {
	public final Map<String, Integer> resourceNameBindings;
	public final Map<Integer, String> bindingResourceName;
	public final Set<Integer> vertexUbos;
	public final Set<Integer> vertexSamplers;
	public final Set<Integer> fragmentUbos;
	public final Set<Integer> fragmentSamplers;
	public final List<AttributeType> attributeTypeList;
	public final Map<Integer, Dim> samplerDimensions;
	public final Map<Integer, ResourceType> resourceTypeMap;
	public final long vertexShaderModule;
	public final long fragmentShaderModule;

	public ProgramData(Map<String, Integer> resourceNameBindings, Set<Integer> vertexUbos, Set<Integer> vertexSampler, Set<Integer> fragmentUbos,
					   Set<Integer> fragmentSamplers, List<AttributeType> attributeTypeList, Map<Integer, Dim> samplerDimensions, long vertexShaderModule,
					   long fragmentShaderModule) {
		this.resourceNameBindings = resourceNameBindings;
		this.bindingResourceName = resourceNameBindings.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
		this.vertexUbos = vertexUbos;
		this.vertexSamplers = vertexSampler;
		this.fragmentUbos = fragmentUbos;
		this.fragmentSamplers = fragmentSamplers;

		this.attributeTypeList = attributeTypeList;
		this.samplerDimensions = samplerDimensions;
		this.vertexShaderModule = vertexShaderModule;
		this.fragmentShaderModule = fragmentShaderModule;

		this.resourceTypeMap = new HashMap<>();

		for (Integer ubo : Stream.concat(vertexUbos.stream(), fragmentUbos.stream()).toList()) {
			this.resourceTypeMap.put(ubo, ResourceType.UNIFORM_BUFFER);
		}
		for (Integer sampler : Stream.concat(vertexSampler.stream(), fragmentSamplers.stream()).toList()) {
			Dim dim = samplerDimensions.get(sampler);
			if (dim == Dim.BUFFER) {
				this.resourceTypeMap.put(sampler, ResourceType.TEXEL_BUFFER);
			} else {
				this.resourceTypeMap.put(sampler, ResourceType.SAMPLER);
			}
		}
	}

	public enum ResourceType {
		UNIFORM_BUFFER,
		SAMPLER,
		TEXEL_BUFFER
	}
}
