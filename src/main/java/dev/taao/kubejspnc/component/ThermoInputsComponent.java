package dev.taao.kubejspnc.component;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.fluid.FluidWrapper;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.IngredientComponent;
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
import dev.taao.kubejspnc.value.ThermoInputsValue;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * {@code pneumaticcraft:thermo_inputs} —— {@code thermo_plant} 的 {@code inputs}
 * （PnC {@code ThermoPlantRecipe.Inputs}：{@code fluid}? / {@code item}?，两者均可缺省）。
 *
 * <p>Tier A 只声明了 {@code outputs}，所以 24 条 {@code thermo_plant} 配方的"输入 + 温度"部分
 * 只能用 {@code event.custom} 写；本组件把它补全，并保留 {@code "item": []}（= 不消耗物品）这种 PnC 原生写法。
 */
public class ThermoInputsComponent implements RecipeComponent<ThermoInputsValue> {
	private static final TypeInfo TYPE_INFO = new JSObjectTypeInfo(List.of(
		new JSOptionalParam("fluid", FluidWrapper.SIZED_INGREDIENT_TYPE_INFO, true),
		new JSOptionalParam("item", IngredientComponent.INGREDIENT.instance().typeInfo(), true)
	));

	private final RecipeComponentType<?> type;

	public ThermoInputsComponent(RecipeComponentType<?> type) {
		this.type = type;
	}

	@Override
	public RecipeComponentType<?> type() {
		return type;
	}

	@Override
	public Codec<ThermoInputsValue> codec() {
		return ThermoInputsValue.CODEC;
	}

	@Override
	public TypeInfo typeInfo() {
		return TYPE_INFO;
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, Object from) {
		if (from instanceof ThermoInputsValue) {
			return true;
		}
		if (from instanceof Map<?, ?> map) {
			return map.containsKey("fluid") || map.containsKey("item");
		}
		return false;
	}

	@Override
	public ThermoInputsValue wrap(RecipeScriptContext cx, Object from) {
		Context c = cx.cx();
		String where = type.toString();
		if (from == null) {
			return ThermoInputsValue.of(null, null);
		}
		if (from instanceof ThermoInputsValue v) {
			return v;
		}
		Map<String, Object> map = PncUtil.stringMap(c, from);
		if (map == null) {
			throw new IllegalArgumentException(where + ": expected {fluid?: ..., item?: ...}, got " + PncUtil.describe(c, from));
		}
		PncUtil.rejectUnknownKeys(map, List.of("fluid", "item"), where);

		SizedFluidIngredient fluid = map.get("fluid") == null ? null : FluidIngredientComponent.wrapSized(cx, map.get("fluid"));
		Ingredient item = map.get("item") == null ? null : IngredientComponent.INGREDIENT.instance().wrap(cx, map.get("item"));
		ThermoInputsValue value = ThermoInputsValue.of(fluid, item);
		if (value.isEmpty()) {
			throw new IllegalArgumentException(where + ": at least one of 'fluid' / 'item' must be present (PnC rule)");
		}
		return value;
	}

	@Override
	public boolean matches(RecipeMatchContext cx, ThermoInputsValue value, ReplacementMatchInfo match) {
		return value.item().map(i -> IngredientComponent.INGREDIENT.instance().matches(cx, i, match)).orElse(false)
			|| value.fluid().map(f -> FluidIngredientComponent.matchesSized(cx, f, match)).orElse(false);
	}

	@Override
	public ThermoInputsValue replace(RecipeScriptContext cx, ThermoInputsValue original, ReplacementMatchInfo match, Object with) {
		return new ThermoInputsValue(
			original.fluid().map(f -> FluidIngredientComponent.matchesSized(cx, f, match)
				? FluidIngredientComponent.replaceSized(cx, f, match, with)
				: f),
			original.item().map(i -> IngredientComponent.INGREDIENT.instance().matches(cx, i, match)
				? IngredientComponent.INGREDIENT.instance().replace(cx, i, match, with)
				: i)
		);
	}

	@Override
	public String toString(OpsContainer ops, ThermoInputsValue value) {
		return value.toString();
	}

	@Override
	public String toString() {
		return type.toString();
	}
}
