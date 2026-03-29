package dev.boredhuman.shaders;

import dev.boredhuman.SpirvBinary;
import dev.boredhuman.SpirvFunction;
import dev.boredhuman.SpirvModule;
import spirv.enumerants.BuiltIn;
import spirv.enumerants.Decoration;
import spirv.enumerants.ExecutionMode;
import spirv.enumerants.StorageClass;
import spirv.instructions.Instruction;
import spirv.instructions.annotation.AnnotationInstruction;
import spirv.instructions.annotation.OpDecorate;
import spirv.instructions.arithmetic.OpFAdd;
import spirv.instructions.arithmetic.OpFMul;
import spirv.instructions.constantcreation.OpConstant;
import spirv.instructions.debug.OpName;
import spirv.instructions.function.OpFunction;
import spirv.instructions.memory.OpAccessChain;
import spirv.instructions.memory.OpLoad;
import spirv.instructions.memory.OpStore;
import spirv.instructions.memory.OpVariable;
import spirv.instructions.modesetting.OpExecutionMode;
import spirv.instructions.typedeclaration.OpTypeFloat;
import spirv.instructions.typedeclaration.OpTypeInt;
import spirv.instructions.typedeclaration.OpTypePointer;
import spirv.instructions.typedeclaration.OpTypeSampledImage;
import spirv.instructions.typedeclaration.OpTypeStruct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShaderHelper {

	private final SpirvBinary spirvBinary;
	public final SpirvModule spirvModule;
	// in
	private final Map<String, OpVariable> inputBuiltins = new HashMap<>();
	public final Map<OpVariable, OpDecorate> builtinsDecoration = new HashMap<>();
	public final Map<String, OpVariable> inputs = new HashMap<>();
	private final Map<OpVariable, OpDecorate> locations = new HashMap<>();
	private final Map<OpVariable, OpDecorate> bindings = new HashMap<>();
	// out
	public final Map<String, OpVariable> outputs = new HashMap<>();
	// struct name -> type
	private final Map<OpTypeStruct, String> structNames = new HashMap<>();
	public final Set<OpVariable> ubos = new HashSet<>();
	public final Map<String, OpVariable> samplers = new HashMap<>();

	public final Map<String, OpFunction> functionNames = new HashMap<>();
	private int bound;

	public ShaderHelper(SpirvBinary spirvBinary) {
		this.spirvBinary = spirvBinary;
		this.bound = spirvBinary.bound;
		this.spirvModule = new SpirvModule(spirvBinary);

		Set<OpVariable> builtins = new HashSet<>();

		for (AnnotationInstruction annotation : this.spirvModule.annotations) {
			if (!(annotation instanceof OpDecorate opDecorate)) {
				continue;
			}

			if (!(opDecorate.target instanceof OpVariable opVariable)) {
				continue;
			}

			if (opDecorate.decoration == Decoration.LOCATION) {
				this.locations.put(opVariable, opDecorate);
			}

			if (opDecorate.decoration == Decoration.BINDING) {
				this.bindings.put(opVariable, opDecorate);
			}

			if (opDecorate.decoration == Decoration.BUILT_IN) {
				builtins.add(opVariable);
				this.builtinsDecoration.put(opVariable, opDecorate);
			}
		}

		for (Instruction instruction : this.spirvModule.debugNaming) {
			if (!(instruction instanceof OpName opName)) {
				continue;
			}
			if (!(opName.target instanceof OpVariable opVariable)) {
				continue;
			}

			if (opVariable.idResultType instanceof OpTypePointer opTypePointer && opTypePointer.type instanceof OpTypeStruct opTypeStruct) {
				this.structNames.put(opTypeStruct, this.findName(opTypeStruct).name);
			}
		}

		for (Instruction instruction : this.spirvModule.debugNaming) {
			if (!(instruction instanceof OpName opName)) {
				continue;
			}
			if (opName.target instanceof OpFunction function) {
				this.functionNames.put(opName.name, function);

				continue;
			}
			if (!(opName.target instanceof OpVariable opVariable)) {
				continue;
			}
			if (opVariable.storageClass == StorageClass.INPUT) {
				if (builtins.contains(opVariable)) {
					this.inputBuiltins.put(opName.name, opVariable);
				} else {
					this.inputs.put(opName.name, opVariable);
				}
			} else if (opVariable.storageClass == StorageClass.OUTPUT) {
				if (opVariable.idResultType instanceof OpTypePointer opTypePointer && opTypePointer.type instanceof OpTypeStruct opTypeStruct) {
					if (this.structNames.get(opTypeStruct).equals("gl_PerVertex")) {
						continue;
					}
				}
				this.outputs.put(opName.name, opVariable);
			} else if (opVariable.storageClass == StorageClass.UNIFORM) {
				this.ubos.add(opVariable);
			} else if (opVariable.storageClass == StorageClass.UNIFORM_CONSTANT) {
				if (opVariable.idResultType instanceof OpTypePointer opTypePointer && opTypePointer.type instanceof OpTypeSampledImage) {
					this.samplers.put(opName.name, opVariable);
				} else {
					throw new RuntimeException("Unexpected uniform constant type");
				}
			}
		}
	}

	public void executionModeOriginUpperLeft() {
		OpExecutionMode opExecutionMode = this.spirvModule.executionModes.stream().filter(e -> e instanceof OpExecutionMode).map(OpExecutionMode.class::cast)
			.findFirst().orElseThrow();
		opExecutionMode.mode = ExecutionMode.ORIGIN_UPPER_LEFT;
	}

	public void removeLocationDecoration(OpVariable opVariable) {
		this.spirvModule.annotations.removeIf(
			e -> e instanceof OpDecorate opDecorate && opDecorate.decoration == Decoration.LOCATION && opDecorate.target == opVariable);
	}

	public void setDecorationArg(String name, BuiltIn builtIn) {
		this.builtinsDecoration.get(this.inputBuiltins.get(name)).decorationArg[0] = builtIn;
	}

	public int getVariableLocation(OpVariable variable) {
		OpDecorate locationDecoration = this.locations.get(variable);
		if (locationDecoration == null) {
			return -1;
		}
		return (Integer) locationDecoration.decorationArg[0];
	}

	public void setVariableLocation(OpVariable variable, int location) {
		OpDecorate decorate = this.locations.get(variable);
		decorate.decorationArg[0] = location;
	}

	public void setBinding(OpVariable variable, int binding) {
		this.bindings.get(variable).decorationArg[0] = binding;
	}

	public String uboName(OpVariable variable) {
		if (variable.idResultType instanceof OpTypePointer opTypePointer && opTypePointer.type instanceof OpTypeStruct opTypeStruct) {
			return this.structNames.get(opTypeStruct);
		}
		throw new RuntimeException("");
	}

	public OpName findName(Instruction instruction) {
		for (Instruction debugNaming : this.spirvModule.debugNaming) {
			if (debugNaming instanceof OpName opName && opName.target == instruction) {
				return opName;
			}
		}
		return null;
	}

	public void fixClipSpaceNew() {
		OpFunction mainFunction = this.functionNames.get("main");

		SpirvFunction function = this.spirvModule.functionDefinitions.stream().filter(e -> e.function == mainFunction).findFirst().orElseThrow();

		Instruction perVertexStruct = this.spirvModule.debugNaming.stream().filter(e -> e instanceof OpName opName && "gl_PerVertex".equals(opName.name))
			.map(OpName.class::cast).findFirst().orElseThrow().target;

		OpVariable perVertexVariable = this.spirvModule.typesConstantsGlobalsDebug.stream()
			.filter(
				e -> e instanceof OpVariable variable && variable.idResultType instanceof OpTypePointer opTypePointer && opTypePointer.type == perVertexStruct)
			.map(OpVariable.class::cast).findFirst().orElseThrow();

		Instruction accessType = this.spirvModule.typesConstantsGlobalsDebug.stream().filter(
			e -> e instanceof OpTypePointer opTypePointer && opTypePointer.storageClass == StorageClass.OUTPUT &&
				 opTypePointer.type instanceof OpTypeFloat opTypeFloat && opTypeFloat.width == 32).findFirst().orElse(null);

		if (accessType == null) {
			Instruction floatType = this.spirvModule.typesConstantsGlobalsDebug.stream()
				.filter(e -> e instanceof OpTypeFloat opTypeFloat && opTypeFloat.width == 32)
				.findFirst().orElse(null);

			if (floatType == null) {
				OpTypeFloat opTypeFloat = new OpTypeFloat();
				opTypeFloat.setResult(this.allocateId());
				opTypeFloat.width = 32;

				floatType = opTypeFloat;

				this.spirvModule.typesConstantsGlobalsDebug.add(opTypeFloat);
			}

			OpTypePointer opTypePointer = new OpTypePointer();
			opTypePointer.idResult = this.allocateId();
			opTypePointer.storageClass = StorageClass.OUTPUT;
			opTypePointer.type = floatType;

			accessType = opTypePointer;

			this.spirvModule.typesConstantsGlobalsDebug.add(opTypePointer);
		}

		OpTypeFloat floatType = (OpTypeFloat) (((OpTypePointer) accessType).type);

		List<Instruction> fixInstructions = new ArrayList<>();

		OpAccessChain glPositionZPointer = new OpAccessChain();
		glPositionZPointer.setResultType(accessType);
		glPositionZPointer.setResult(this.allocateId());
		glPositionZPointer.base = perVertexVariable;
		glPositionZPointer.indexes = new Instruction[] { this.findOrGetIntConstant(0, 1), this.findOrGetIntConstant(2, 0) };

		fixInstructions.add(glPositionZPointer);

		OpLoad loadPositionZ = new OpLoad();
		loadPositionZ.setResult(this.allocateId());
		loadPositionZ.setResultType(floatType);
		loadPositionZ.pointer = glPositionZPointer;

		fixInstructions.add(loadPositionZ);

		OpAccessChain glPositionWPointer = new OpAccessChain();
		glPositionWPointer.setResultType(accessType);
		glPositionWPointer.setResult(this.allocateId());
		glPositionWPointer.base = perVertexVariable;
		glPositionWPointer.indexes = new Instruction[] { this.findOrGetIntConstant(0, 1), this.findOrGetIntConstant(3, 0) };

		fixInstructions.add(glPositionWPointer);

		OpLoad loadPositionW = new OpLoad();
		loadPositionW.setResult(this.allocateId());
		loadPositionW.setResultType(floatType);
		loadPositionW.pointer = glPositionWPointer;

		fixInstructions.add(loadPositionW);

		OpFAdd addZW = new OpFAdd();
		addZW.setResultType(floatType);
		addZW.setResult(this.allocateId());
		addZW.operand1 = loadPositionZ;
		addZW.operand2 = loadPositionW;

		fixInstructions.add(addZW);

		Instruction halfConstant = this.spirvModule.typesConstantsGlobalsDebug.stream()
			.filter(e -> e instanceof OpConstant opConstant && opConstant.resultType() == floatType && opConstant.value[0] == Float.floatToRawIntBits(0.5F))
			.findFirst().orElse(null);

		if (halfConstant == null) {
			OpConstant opConstant = new OpConstant();
			opConstant.setResultType(floatType);
			opConstant.setResult(this.allocateId());
			opConstant.value = new int[] { Float.floatToRawIntBits(0.5F) };

			halfConstant = opConstant;

			this.spirvModule.typesConstantsGlobalsDebug.add(opConstant);
		}

		OpFMul halfZ = new OpFMul();
		halfZ.setResultType(floatType);
		halfZ.setResult(this.allocateId());
		halfZ.operand1 = halfConstant;
		halfZ.operand2 = addZW;

		fixInstructions.add(halfZ);

		OpAccessChain glPositionZPointerStore = new OpAccessChain();
		glPositionZPointerStore.setResultType(accessType);
		glPositionZPointerStore.setResult(this.allocateId());
		glPositionZPointerStore.base = perVertexVariable;
		glPositionZPointerStore.indexes = new Instruction[] { this.findOrGetIntConstant(0, 1), this.findOrGetIntConstant(2, 0) };

		fixInstructions.add(glPositionZPointerStore);

		OpStore storeZ = new OpStore();
		storeZ.pointer = glPositionZPointerStore;
		storeZ.object = halfZ;

		fixInstructions.add(storeZ);

		Instruction returnInstruction = function.body.removeLast();
		function.body.addAll(fixInstructions);
		function.body.add(returnInstruction);
	}

	private OpConstant findOrGetIntConstant(int value, int signed) {
		OpConstant constant = this.spirvModule.typesConstantsGlobalsDebug.stream().filter(
			e -> e instanceof OpConstant opConstant && opConstant.idResultType instanceof OpTypeInt opTypeInt && opTypeInt.signedness == signed &&
				 opConstant.value[0] == value).map(OpConstant.class::cast).findFirst().orElse(null);

		if (constant != null) {
			return constant;
		}

		OpTypeInt intType = this.spirvModule.typesConstantsGlobalsDebug.stream().filter(e -> e instanceof OpTypeInt opTypeInt && opTypeInt.signedness == signed)
			.map(OpTypeInt.class::cast).findFirst().orElse(null);

		if (intType == null) {
			intType = new OpTypeInt();
			intType.idResult = this.allocateId();
			intType.width = 32;
			intType.signedness = signed;

			this.spirvModule.typesConstantsGlobalsDebug.add(intType);
		}

		constant = new OpConstant();
		constant.idResultType = intType;
		constant.idResult = this.allocateId();
		constant.value = new int[] { value };

		this.spirvModule.typesConstantsGlobalsDebug.add(constant);

		return constant;
	}

	private int allocateId() {
		return this.bound++;
	}

	public SpirvBinary getUpdatedBinary() {
		SpirvBinary newBinary = new SpirvBinary();
		newBinary.magicNumber = this.spirvBinary.magicNumber;
		newBinary.majorVersion = this.spirvBinary.majorVersion;
		newBinary.minorVersion = this.spirvBinary.minorVersion;
		newBinary.generateMagicNumber = this.spirvBinary.generateMagicNumber;
		newBinary.bound = this.bound;
		newBinary.reserved = this.spirvBinary.reserved;
		newBinary.instructions = this.spirvModule.toInstructionList().toArray(Instruction[]::new);

		return newBinary;
	}
}
