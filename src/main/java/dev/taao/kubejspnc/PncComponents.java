package dev.taao.kubejspnc;

import dev.latvian.mods.kubejs.recipe.component.EnumComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.taao.kubejspnc.component.AmadronResourceComponent;
import dev.taao.kubejspnc.component.FluidIngredientComponent;
import dev.taao.kubejspnc.component.PncFluidStackComponent;
import dev.taao.kubejspnc.component.TemperatureRangeComponent;
import dev.taao.kubejspnc.component.ThermoInputsComponent;
import dev.taao.kubejspnc.component.ThermoOutputsComponent;
import dev.taao.kubejspnc.value.AmadronResourceValue;
import dev.taao.kubejspnc.value.FluidContainerValue;
import dev.taao.kubejspnc.value.TemperatureRangeValue;
import dev.taao.kubejspnc.value.ThermoInputsValue;
import dev.taao.kubejspnc.value.ThermoOutputsValue;
import java.util.List;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * 本联动件注册的全部配方组件类型（schema JSON 里用 {@code "type": "<id>"} 引用）。
 *
 * <p>命名空间统一用 {@code pneumaticcraft:}，与 kubejs-create / kubejs-mekanism 的做法一致
 * （组件类型是 KubeJS 自己的注册表，不占用 PnC 的注册表，因此不会和本体冲突）。
 */
public final class PncComponents {
	private PncComponents() {
	}

	public static final RecipeComponentType<TemperatureRangeValue> TEMPERATURE_RANGE =
		RecipeComponentType.unit(PncUtil.id("temperature_range"), TemperatureRangeComponent::new);

	public static final RecipeComponentType<SizedFluidIngredient> FLUID_INGREDIENT =
		RecipeComponentType.unit(PncUtil.id("fluid_ingredient"), FluidIngredientComponent::sizedFlat);

	public static final RecipeComponentType<FluidIngredient> FLUID_INGREDIENT_UNSIZED =
		RecipeComponentType.unit(PncUtil.id("fluid_ingredient_unsized"), FluidIngredientComponent::unsized);

	public static final RecipeComponentType<FluidContainerValue> FLUID_CONTAINER_INGREDIENT =
		RecipeComponentType.unit(PncUtil.id("fluid_container_ingredient"), FluidIngredientComponent::container);

	public static final RecipeComponentType<ThermoInputsValue> THERMO_INPUTS =
		RecipeComponentType.unit(PncUtil.id("thermo_inputs"), ThermoInputsComponent::new);

	public static final RecipeComponentType<ThermoOutputsValue> THERMO_OUTPUTS =
		RecipeComponentType.unit(PncUtil.id("thermo_outputs"), ThermoOutputsComponent::new);

	public static final RecipeComponentType<AmadronResourceValue> AMADRON_RESOURCE =
		RecipeComponentType.unit(PncUtil.id("amadron_resource"), AmadronResourceComponent::new);

	public static final RecipeComponentType<net.neoforged.neoforge.fluids.FluidStack> FLUID_STACK =
		RecipeComponentType.unit(PncUtil.id("fluid_stack"), PncFluidStackComponent::required);

	public static final RecipeComponentType<net.neoforged.neoforge.fluids.FluidStack> OPTIONAL_FLUID_STACK =
		RecipeComponentType.unit(PncUtil.id("fluid_stack_optional"), PncFluidStackComponent::optional);

	public static final RecipeComponentType<AssemblyProgram> ASSEMBLY_PROGRAM =
		EnumComponent.of(PncUtil.id("assembly_program"), AssemblyProgram.class);

	public static final List<RecipeComponentType<?>> ALL = List.of(
		TEMPERATURE_RANGE,
		FLUID_INGREDIENT,
		FLUID_INGREDIENT_UNSIZED,
		FLUID_CONTAINER_INGREDIENT,
		FLUID_STACK,
		OPTIONAL_FLUID_STACK,
		THERMO_INPUTS,
		THERMO_OUTPUTS,
		AMADRON_RESOURCE,
		ASSEMBLY_PROGRAM
	);

	public static void register(RecipeComponentTypeRegistry registry) {
		for (RecipeComponentType<?> type : ALL) {
			registry.register(type);
		}
	}
}
