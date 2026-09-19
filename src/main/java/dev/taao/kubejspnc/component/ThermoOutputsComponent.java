package dev.taao.kubejspnc.component;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.FluidStackComponent;
import dev.latvian.mods.kubejs.recipe.component.ItemStackComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.kubejs.util.OpsContainer;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.JSOptionalParam;
import dev.latvian.mods.rhino.type.JSObjectTypeInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.taao.kubejspnc.PncUtil;
import dev.taao.kubejspnc.value.ThermoOutputsValue;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * {@code pneumaticcraft:thermo_outputs} —— {@code thermo_plant} 的 {@code outputs}
 * （PnC {@code ThermoPlantRecipe.Outputs}：{@code fluid_output}? / {@code item_output}?）。
 *
 * <p>相比 Tier A 的 {@code custom_object} 版本，这里额外实现了 {@code matches}/{@code replace}，
 * 所以 {@code {output: '...'}} 过滤与 {@code event.replaceOutput} 对 thermo_plant 也能用。
 */
public class ThermoOutputsComponent implements RecipeComponent<ThermoOutputsValue> {
	private static final TypeInfo TYPE_INFO = new JSObjectTypeInfo(List.of(
		new JSOptionalParam("fluid_output", FluidStackComponent.FLUID_STACK.instance().typeInfo(), true),
		new JSOptionalParam("item_output", ItemStackComponent.ITEM_STACK.instance().typeInfo(), true)
	));

	private final RecipeComponentType<?> type;

	public ThermoOutputsComponent(RecipeComponentType<?> type) {
		this.type = type;
	}

	@Override
	public RecipeComponentType<?> type() {
		return type;
	}

	@Override
	public Codec<ThermoOutputsValue> codec() {
		return ThermoOutputsValue.CODEC;
	}

	@Override
	public TypeInfo typeInfo() {
		return TYPE_INFO;
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, Object from) {
		if (from instanceof ThermoOutputsValue) {
			return true;
		}
		if (from instanceof Map<?, ?> map) {
			return map.containsKey("fluid_output") || map.containsKey("item_output");
		}
		return false;
	}

	@Override
	public ThermoOutputsValue wrap(RecipeScriptContext cx, Object from) {
		Context c = cx.cx();
		String where = type.toString();
		if (from == null) {
			throw new IllegalArgumentException(where + ": outputs are required");
		}
		if (from instanceof ThermoOutputsValue v) {
			return v;
		}
		if (from instanceof ItemStack stack) {
			return ThermoOutputsValue.of(null, stack);
		}
		if (from instanceof FluidStack stack) {
			return ThermoOutputsValue.of(stack, null);
		}
		Map<String, Object> map = PncUtil.stringMap(c, from);
		if (map == null) {
			throw new IllegalArgumentException(
				where + ": expected {item_output?: ...} / {fluid_output?: ...}, got " + PncUtil.describe(c, from)
			);
		}
		PncUtil.rejectUnknownKeys(map, List.of("fluid_output", "item_output"), where);

		FluidStack fluid = map.get("fluid_output") == null ? null : PncFluidStackComponent.wrapStack(cx, map.get("fluid_output"));
		ItemStack item = map.get("item_output") == null ? null : ItemStackComponent.ITEM_STACK.instance().wrap(cx, map.get("item_output"));
		ThermoOutputsValue value = ThermoOutputsValue.of(fluid, item);
		if (value.isEmpty()) {
			throw new IllegalArgumentException(where + ": at least one of 'fluid_output' / 'item_output' must be present");
		}
		return value;
	}

	@Override
	public boolean matches(RecipeMatchContext cx, ThermoOutputsValue value, ReplacementMatchInfo match) {
		return (!value.item().isEmpty() && ItemStackComponent.ITEM_STACK.instance().matches(cx, value.item(), match))
			|| (!value.fluid().isEmpty() && PncFluidStackComponent.matchesStack(cx, value.fluid(), match));
	}

	@Override
	public ThermoOutputsValue replace(RecipeScriptContext cx, ThermoOutputsValue original, ReplacementMatchInfo match, Object with) {
		ItemStack item = original.item().isEmpty() || !ItemStackComponent.ITEM_STACK.instance().matches(cx, original.item(), match)
			? original.item()
			: ItemStackComponent.ITEM_STACK.instance().replace(cx, original.item(), match, with);
		FluidStack fluid = original.fluid().isEmpty() || !PncFluidStackComponent.matchesStack(cx, original.fluid(), match)
			? original.fluid()
			: PncFluidStackComponent.replaceStack(cx, original.fluid(), match, with);
		return ThermoOutputsValue.of(fluid, item);
	}

	@Override
	public String toString(OpsContainer ops, ThermoOutputsValue value) {
		return value.toString();
	}

	@Override
	public String toString() {
		return type.toString();
	}
}
