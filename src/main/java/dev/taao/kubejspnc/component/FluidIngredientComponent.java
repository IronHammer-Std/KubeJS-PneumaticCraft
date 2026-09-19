package dev.taao.kubejspnc.component;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.fluid.FluidWrapper;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.UniqueIdBuilder;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.FluidMatch;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.kubejs.util.OpsContainer;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.JSOptionalParam;
import dev.latvian.mods.rhino.type.JSObjectTypeInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.taao.kubejspnc.PncUtil;
import dev.taao.kubejspnc.value.FluidContainerValue;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * PnC 的三种"流体输入"形态，一个类三种模式（PnC 侧其实是三套不同的 codec，写混了 PnC 只会静默忽略或报 either 树错误）：
 *
 * <table>
 *   <tr><th>模式</th><th>注册 id</th><th>PnC 侧 codec</th><th>合法 JSON 键</th><th>用在</th></tr>
 *   <tr><td>{@link Mode#SIZED_FLAT}</td><td>{@code pneumaticcraft:fluid_ingredient}</td>
 *       <td>{@code SizedFluidIngredient.FLAT_CODEC}</td><td>{@code fluid}|{@code tag} + 可选 {@code amount}(&gt;0，缺省 1000)</td>
 *       <td>refinery.input / fluid_mixer.input1,2 / thermo_plant.inputs.fluid</td></tr>
 *   <tr><td>{@link Mode#UNSIZED}</td><td>{@code pneumaticcraft:fluid_ingredient_unsized}</td>
 *       <td>{@code FluidIngredient.CODEC_NON_EMPTY}</td><td>{@code fluid}|{@code tag}（<b>不接受 amount</b>）</td>
 *       <td>fuel_quality.fluid</td></tr>
 *   <tr><td>{@link Mode#CONTAINER}</td><td>{@code pneumaticcraft:fluid_container_ingredient}</td>
 *       <td>{@code either(FluidStack.CODEC, TagWithAmount)}</td><td>{@code id}|{@code tag} + <b>必填</b> {@code amount}(&gt;0)</td>
 *       <td>heat_frame_cooling.input.fluid</td></tr>
 * </table>
 *
 * 这三条规则都来自 PnC / NeoForge 的源码实读（见联动件 README 的"依据"一节），
 * 组件把它们搬到 KubeJS 层：写错键、写错 amount 的位置，会在 reload 时就报出合法键列表。
 */
public final class FluidIngredientComponent<T> implements RecipeComponent<T> {
	public enum Mode {
		SIZED_FLAT,
		UNSIZED,
		CONTAINER
	}

	private static final TypeInfo CONTAINER_TYPE_INFO = new JSObjectTypeInfo(List.of(
		new JSOptionalParam("id", TypeInfo.STRING, true),
		new JSOptionalParam("tag", TypeInfo.STRING, true),
		new JSOptionalParam("amount", TypeInfo.INT, true)
	));

	private final RecipeComponentType<?> type;
	private final Mode mode;
	private final Codec<T> codec;
	private final TypeInfo typeInfo;

	private FluidIngredientComponent(RecipeComponentType<?> type, Mode mode, Codec<T> codec, TypeInfo typeInfo) {
		this.type = type;
		this.mode = mode;
		this.codec = codec;
		this.typeInfo = typeInfo;
	}

	public static FluidIngredientComponent<SizedFluidIngredient> sizedFlat(RecipeComponentType<?> type) {
		return new FluidIngredientComponent<>(type, Mode.SIZED_FLAT, SizedFluidIngredient.FLAT_CODEC, FluidWrapper.SIZED_INGREDIENT_TYPE_INFO);
	}

	public static FluidIngredientComponent<FluidIngredient> unsized(RecipeComponentType<?> type) {
		return new FluidIngredientComponent<>(type, Mode.UNSIZED, FluidIngredient.CODEC_NON_EMPTY, FluidWrapper.TYPE_INFO);
	}

	public static FluidIngredientComponent<FluidContainerValue> container(RecipeComponentType<?> type) {
		return new FluidIngredientComponent<>(type, Mode.CONTAINER, FluidContainerValue.CODEC, CONTAINER_TYPE_INFO);
	}

	/** 给别的组件（thermo_inputs 等）内联调用用的无状态包装器。 */
	public static SizedFluidIngredient wrapSized(RecipeScriptContext cx, Object from) {
		return FluidIngredientComponent.<SizedFluidIngredient>loose(Mode.SIZED_FLAT).wrap(cx, from);
	}

	public static boolean matchesSized(RecipeMatchContext cx, SizedFluidIngredient value, ReplacementMatchInfo match) {
		return FluidIngredientComponent.<SizedFluidIngredient>loose(Mode.SIZED_FLAT).matches(cx, value, match);
	}

	public static SizedFluidIngredient replaceSized(RecipeScriptContext cx, SizedFluidIngredient original, ReplacementMatchInfo match, Object with) {
		return FluidIngredientComponent.<SizedFluidIngredient>loose(Mode.SIZED_FLAT).replace(cx, original, match, with);
	}

	public static FluidContainerValue wrapContainer(RecipeScriptContext cx, Object from) {
		return FluidIngredientComponent.<FluidContainerValue>loose(Mode.CONTAINER).wrap(cx, from);
	}

	public static boolean matchesContainer(RecipeMatchContext cx, FluidContainerValue value, ReplacementMatchInfo match) {
		return FluidIngredientComponent.<FluidContainerValue>loose(Mode.CONTAINER).matches(cx, value, match);
	}

	public static FluidContainerValue replaceContainer(RecipeScriptContext cx, FluidContainerValue original, ReplacementMatchInfo match, Object with) {
		return FluidIngredientComponent.<FluidContainerValue>loose(Mode.CONTAINER).replace(cx, original, match, with);
	}

	/** {@code type == null} 的临时实例：只做取值/校验，不注册（避开类初始化顺序问题）。 */
	@SuppressWarnings("unchecked")
	private static <X> FluidIngredientComponent<X> loose(Mode mode) {
		Object instance = switch (mode) {
			case SIZED_FLAT -> new FluidIngredientComponent<SizedFluidIngredient>(
				null, Mode.SIZED_FLAT, SizedFluidIngredient.FLAT_CODEC, FluidWrapper.SIZED_INGREDIENT_TYPE_INFO);
			case UNSIZED -> new FluidIngredientComponent<FluidIngredient>(
				null, Mode.UNSIZED, FluidIngredient.CODEC_NON_EMPTY, FluidWrapper.TYPE_INFO);
			case CONTAINER -> new FluidIngredientComponent<FluidContainerValue>(
				null, Mode.CONTAINER, FluidContainerValue.CODEC, CONTAINER_TYPE_INFO);
		};
		return (FluidIngredientComponent<X>) instance;
	}

	private String where() {
		return type == null ? "pneumaticcraft:fluid_ingredient" : type.toString();
	}

	@Override
	public RecipeComponentType<?> type() {
		return type;
	}

	@Override
	public Codec<T> codec() {
		return codec;
	}

	@Override
	public TypeInfo typeInfo() {
		return typeInfo;
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, Object from) {
		if (from instanceof FluidStack || from instanceof Fluid || from instanceof FluidIngredient || from instanceof SizedFluidIngredient) {
			return true;
		}
		if (from instanceof CharSequence) {
			return true;
		}
		if (from instanceof Map<?, ?> raw) {
			boolean itemish = raw.containsKey("item") || raw.containsKey("count");
			boolean fluidish = raw.containsKey("fluid") || raw.containsKey("tag") || raw.containsKey("amount")
				|| (mode == Mode.CONTAINER && raw.containsKey("id"));
			return fluidish && !itemish;
		}
		return false;
	}

	@Override
	public T wrap(RecipeScriptContext cx, Object from) {
		Context c = cx.cx();
		String where = where();

		if (from == null) {
			throw new IllegalArgumentException(where + ": a fluid value is required");
		}
		if (mode == Mode.SIZED_FLAT && from instanceof SizedFluidIngredient v) {
			return cast(v);
		}
		if (mode == Mode.UNSIZED && from instanceof FluidIngredient v) {
			return cast(v);
		}
		if (mode == Mode.CONTAINER && from instanceof FluidContainerValue v) {
			return cast(v);
		}
		if (from instanceof FluidStack stack) {
			return fromStack(stack);
		}
		if (from instanceof Fluid fluid) {
			return fromFluid(fluid, PncUtil.BUCKET);
		}
		if (from instanceof CharSequence) {
			boolean tag = PncUtil.isTagString(c, from);
			ResourceLocation rl = PncUtil.parseId(c, from, where);
			return tag ? fromTag(PncUtil.fluidTag(rl)) : fromId(rl, PncUtil.BUCKET);
		}

		Map<String, Object> map = PncUtil.stringMap(c, from);
		if (map != null) {
			return fromMap(c, map, where);
		}

		List<Object> list = c.optionalListOf(from, TypeInfo.NONE);
		if (list != null && list.size() == 1) {
			return wrap(cx, list.getFirst());
		}

		throw new IllegalArgumentException(
			where + ": expected a fluid id string ('minecraft:water' or '#c:crude_oil') or an object, got " + PncUtil.describe(c, from)
		);
	}

	private T fromMap(Context c, Map<String, Object> map, String where) {
		List<String> allowed = switch (mode) {
			case SIZED_FLAT -> List.of("fluid", "id", "tag", "amount");
			case UNSIZED -> List.of("fluid", "id", "tag");
			case CONTAINER -> List.of("id", "tag", "amount");
		};
		if (mode == Mode.UNSIZED && map.containsKey("amount")) {
			throw new IllegalArgumentException(
				where + ": this PnC field takes an UNSIZED fluid ingredient — PnC ignores 'amount' here, so it is rejected. "
					+ "Remove it, or use pneumaticcraft:fluid_ingredient for sized fields."
			);
		}
		if (mode == Mode.CONTAINER && map.containsKey("fluid")) {
			throw new IllegalArgumentException(
				where + ": PnC's heat_frame_cooling fluid input is either(FluidStack, tag+amount) — the single-fluid key is "
					+ "'id', not 'fluid' (this is the old trap). Valid keys: " + allowed
			);
		}
		PncUtil.rejectUnknownKeys(map, allowed, where);

		boolean hasTag = map.get("tag") != null;
		boolean hasFluid = map.get("fluid") != null || map.get("id") != null;
		if (hasTag && hasFluid) {
			throw new IllegalArgumentException(where + ": 'tag' and '" + (mode == Mode.CONTAINER ? "id" : "fluid") + "' are mutually exclusive");
		}
		if (!hasTag && !hasFluid) {
			throw new IllegalArgumentException(where + ": one of " + allowed + " is required, got none");
		}

		if (hasTag) {
			ResourceLocation rl = PncUtil.parseId(c, map.get("tag"), where + ".tag");
			if (mode == Mode.CONTAINER) {
				int amount = requirePositive(c, map, where);
				return cast(FluidContainerValue.of(new FluidContainerValue.TagAmount(PncUtil.fluidTag(rl), amount)));
			}
			return fromTag(PncUtil.fluidTag(rl), amount(c, map, where));
		}

		ResourceLocation rl = PncUtil.parseId(c, mode == Mode.CONTAINER ? map.get("id") : (map.get("fluid") != null ? map.get("fluid") : map.get("id")), where);
		if (mode == Mode.CONTAINER) {
			return fromId(rl, requirePositive(c, map, where));
		}
		return fromId(rl, amount(c, map, where));
	}

	private int amount(Context c, Map<String, Object> map, String where) {
		if (!map.containsKey("amount") || map.get("amount") == null) {
			return PncUtil.BUCKET;
		}
		int v = PncUtil.asInt(c, map.get("amount"), where + ".amount");
		if (v <= 0) {
			throw new IllegalArgumentException(where + ".amount: must be > 0, got " + v);
		}
		return v;
	}

	private int requirePositive(Context c, Map<String, Object> map, String where) {
		if (!map.containsKey("amount") || map.get("amount") == null) {
			throw new IllegalArgumentException(where + ".amount: required (> 0) for this PnC field");
		}
		return amount(c, map, where);
	}

	private T fromStack(FluidStack stack) {
		return switch (mode) {
			case SIZED_FLAT -> cast(new SizedFluidIngredient(FluidIngredient.single(stack.getFluid()), stack.getAmount()));
			case UNSIZED -> cast(FluidIngredient.single(stack.getFluid()));
			case CONTAINER -> cast(FluidContainerValue.of(stack));
		};
	}

	private T fromId(ResourceLocation rl, int amount) {
		Fluid fluid = PncUtil.fluid(rl, where());
		return fromFluid(fluid, amount);
	}

	private T fromFluid(Fluid fluid, int amount) {
		return switch (mode) {
			case SIZED_FLAT -> cast(new SizedFluidIngredient(FluidIngredient.single(fluid), amount));
			case UNSIZED -> cast(FluidIngredient.single(fluid));
			case CONTAINER -> cast(FluidContainerValue.of(new FluidStack(fluid, amount)));
		};
	}

	private T fromTag(net.minecraft.tags.TagKey<Fluid> tag) {
		return fromTag(tag, PncUtil.BUCKET);
	}

	private T fromTag(net.minecraft.tags.TagKey<Fluid> tag, int amount) {
		return switch (mode) {
			case SIZED_FLAT -> cast(new SizedFluidIngredient(FluidIngredient.tag(tag), amount));
			case UNSIZED -> cast(FluidIngredient.tag(tag));
			case CONTAINER -> cast(FluidContainerValue.of(new FluidContainerValue.TagAmount(tag, amount)));
		};
	}

	@Override
	public boolean matches(RecipeMatchContext cx, T value, ReplacementMatchInfo match) {
		if (!(match.match() instanceof FluidMatch m)) {
			return false;
		}
		return switch (mode) {
			case SIZED_FLAT -> m.matches(cx, ((SizedFluidIngredient) value).ingredient(), match.exact());
			case UNSIZED -> m.matches(cx, (FluidIngredient) value, match.exact());
			case CONTAINER -> {
				FluidContainerValue v = (FluidContainerValue) value;
				if (v.stack().isPresent()) {
					yield m.matches(cx, v.stack().get(), match.exact());
				}
				yield m.matches(cx, FluidIngredient.tag(v.tagAmount().get().tag()), match.exact());
			}
		};
	}

	@Override
	public T replace(RecipeScriptContext cx, T original, ReplacementMatchInfo match, Object with) {
		if (!matches(cx, original, match)) {
			return original;
		}
		T replacement = wrap(cx, with);
		// 数量/额度以原值为准：{output}/{input} 过滤只换"是什么流体"，不换"多少"
		return switch (mode) {
			case SIZED_FLAT -> cast(new SizedFluidIngredient(((SizedFluidIngredient) replacement).ingredient(), ((SizedFluidIngredient) original).amount()));
			case UNSIZED -> replacement;
			case CONTAINER -> cast(new FluidContainerValue(((FluidContainerValue) replacement).either()));
		};
	}

	@Override
	public void buildUniqueId(UniqueIdBuilder builder, T value) {
		switch (mode) {
			case SIZED_FLAT -> {
				FluidStack[] stacks = ((SizedFluidIngredient) value).ingredient().getStacks();
				if (stacks.length > 0) {
					builder.append(BuiltInRegistries.FLUID.getKey(stacks[0].getFluid()));
				}
			}
			case UNSIZED -> {
				FluidStack[] stacks = ((FluidIngredient) value).getStacks();
				if (stacks.length > 0) {
					builder.append(BuiltInRegistries.FLUID.getKey(stacks[0].getFluid()));
				}
			}
			case CONTAINER -> {
				FluidContainerValue v = (FluidContainerValue) value;
				if (v.stack().isPresent()) {
					builder.append(BuiltInRegistries.FLUID.getKey(v.stack().get().getFluid()));
				} else {
					builder.append(v.tagAmount().get().tag().location());
				}
			}
		}
	}

	@Override
	public String toString(OpsContainer ops, T value) {
		return String.valueOf(value);
	}

	@Override
	public String toString() {
		return where();
	}

	@SuppressWarnings("unchecked")
	private T cast(Object value) {
		return (T) value;
	}
}
