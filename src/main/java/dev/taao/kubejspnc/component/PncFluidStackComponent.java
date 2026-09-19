package dev.taao.kubejspnc.component;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.FluidStackComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.UniqueIdBuilder;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.kubejs.util.OpsContainer;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.JSOptionalParam;
import dev.latvian.mods.rhino.type.JSObjectTypeInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.taao.kubejspnc.PncUtil;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * {@code pneumaticcraft:fluid_stack} / {@code :fluid_stack_optional} —— PnC 的流体<b>产出</b>
 * （{@code FluidStack}：{@code {"amount":N,"id":"…"}}）。
 *
 * <p>为什么要自己写一个：KubeJS 内置的 {@code fluid_stack} 在<b>位置参数</b>路径下不认 PnC 的
 * {@code {amount, id}} 形状 —— 实测（2026-09-19 首轮 Tier C 探针）报
 * {@code Failed to read FluidStack from {amount: 3.0, id: pneumaticcraft:diesel}: Fluid with ID minecraft: does not exist!}
 * （对象形态没事，因为那条路走的是 codec）。本组件把 PnC 的形状编进 {@code wrap()}，两条路都能写。
 *
 * <p>合法键：{@code id} / {@code fluid}（二选一）+ {@code amount}（可选，缺省 1000，&gt; 0）。
 * 流体产出必须是具体流体 id（{@code FluidStack} 表达不了标签）—— 写 {@code tag} 会明确报错。
 */
public final class PncFluidStackComponent implements RecipeComponent<FluidStack> {
	private static final TypeInfo TYPE_INFO = new JSObjectTypeInfo(List.of(
		new JSOptionalParam("id", TypeInfo.STRING, true),
		new JSOptionalParam("fluid", TypeInfo.STRING, true),
		new JSOptionalParam("amount", TypeInfo.INT, true)
	));

	private final RecipeComponentType<?> type;
	private final boolean optional;
	private final Codec<FluidStack> codec;

	private PncFluidStackComponent(RecipeComponentType<?> type, boolean optional) {
		this.type = type;
		this.optional = optional;
		this.codec = optional ? FluidStack.OPTIONAL_CODEC : FluidStack.CODEC;
	}

	public static PncFluidStackComponent required(RecipeComponentType<?> type) {
		return new PncFluidStackComponent(type, false);
	}

	public static PncFluidStackComponent optional(RecipeComponentType<?> type) {
		return new PncFluidStackComponent(type, true);
	}

	/** 给别的组件（thermo_outputs 等）内联调用。 */
	public static FluidStack wrapStack(RecipeScriptContext cx, Object from) {
		return new PncFluidStackComponent(null, false).wrap(cx, from);
	}

	public static boolean matchesStack(RecipeMatchContext cx, FluidStack value, ReplacementMatchInfo match) {
		return new PncFluidStackComponent(null, false).matches(cx, value, match);
	}

	public static FluidStack replaceStack(RecipeScriptContext cx, FluidStack original, ReplacementMatchInfo match, Object with) {
		return new PncFluidStackComponent(null, false).replace(cx, original, match, with);
	}

	@Override
	public RecipeComponentType<?> type() {
		return type;
	}

	@Override
	public Codec<FluidStack> codec() {
		return codec;
	}

	@Override
	public TypeInfo typeInfo() {
		return TYPE_INFO;
	}

	@Override
	public boolean allowEmpty() {
		return optional;
	}

	@Override
	public boolean isEmpty(FluidStack value) {
		return value.isEmpty();
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, Object from) {
		if (from instanceof FluidStack || from instanceof Fluid) {
			return true;
		}
		if (from instanceof CharSequence) {
			return true;
		}
		if (from instanceof Map<?, ?> map) {
			return (map.containsKey("id") || map.containsKey("fluid")) && !map.containsKey("count") && !map.containsKey("item");
		}
		return false;
	}

	@Override
	public FluidStack wrap(RecipeScriptContext cx, Object from) {
		Context c = cx.cx();
		String where = where();

		if (from == null) {
			if (optional) {
				return FluidStack.EMPTY;
			}
			throw new IllegalArgumentException(where + ": a fluid is required");
		}
		if (from instanceof FluidStack stack) {
			if (!optional && stack.isEmpty()) {
				throw new IllegalArgumentException(where + ": empty fluid stack is not allowed here");
			}
			return stack;
		}
		if (from instanceof Fluid fluid) {
			return new FluidStack(fluid, PncUtil.BUCKET);
		}
		if (from instanceof CharSequence) {
			ResourceLocation rl = PncUtil.parseId(c, from, where);
			return new FluidStack(PncUtil.fluid(rl, where), PncUtil.BUCKET);
		}

		Map<String, Object> map = PncUtil.stringMap(c, from);
		if (map != null) {
			PncUtil.rejectUnknownKeys(map, List.of("id", "fluid", "amount"), where);
			if (map.get("tag") != null) {
				throw new IllegalArgumentException(
					where + ": fluid outputs must be a concrete fluid id ('id'/'fluid') — PnC's FluidStack cannot express tags"
				);
			}
			if (map.get("id") == null && map.get("fluid") == null) {
				throw new IllegalArgumentException(where + ": one of 'id' or 'fluid' is required, got keys " + map.keySet());
			}
			ResourceLocation rl = PncUtil.parseId(c, map.get("id") != null ? map.get("id") : map.get("fluid"), where);
			int amount = map.get("amount") == null ? PncUtil.BUCKET : PncUtil.asInt(c, map.get("amount"), where + ".amount");
			if (amount <= 0) {
				throw new IllegalArgumentException(where + ".amount: must be > 0, got " + amount);
			}
			return new FluidStack(PncUtil.fluid(rl, where), amount);
		}

		List<Object> list = c.optionalListOf(from, TypeInfo.NONE);
		if (list != null && list.size() == 1) {
			return wrap(cx, list.getFirst());
		}

		throw new IllegalArgumentException(
			where + ": expected {id|fluid, amount?} or a fluid id string, got " + PncUtil.describe(c, from)
		);
	}

	@Override
	public boolean matches(RecipeMatchContext cx, FluidStack value, ReplacementMatchInfo match) {
		return FluidStackComponent.FLUID_STACK.instance().matches(cx, value, match);
	}

	@Override
	public FluidStack replace(RecipeScriptContext cx, FluidStack original, ReplacementMatchInfo match, Object with) {
		if (!matches(cx, original, match)) {
			return original;
		}
		// 只换"是什么流体"，数量以原值为准
		FluidStack replaced = wrap(cx, with);
		return replaced.isEmpty() ? original : replaced.copyWithAmount(original.getAmount());
	}

	@Override
	public void buildUniqueId(UniqueIdBuilder builder, FluidStack value) {
		if (!value.isEmpty()) {
			builder.append(BuiltInRegistries.FLUID.getKey(value.getFluid()));
		}
	}

	@Override
	public String toString(OpsContainer ops, FluidStack value) {
		return value.isEmpty() ? "empty" : value.getAmount() + "mB " + BuiltInRegistries.FLUID.getKey(value.getFluid());
	}

	@Override
	public String toString() {
		return where();
	}

	private String where() {
		return type == null ? "pneumaticcraft:fluid_stack" : type.toString();
	}
}
