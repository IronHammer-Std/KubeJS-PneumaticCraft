package dev.taao.kubejspnc.component;

import com.mojang.datafixers.util.Either;
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
import dev.taao.kubejspnc.value.AmadronResourceValue;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * {@code pneumaticcraft:amadron_resource} —— amadron 报价的 {@code input}/{@code output}
 * （PnC {@code AmadronTradeResource}：{@code {"resource": either(ItemStack, FluidStack)}}）。
 *
 * <p>JS 侧可以写完整的 {@code {resource: {...}}}，也可以直接写里面的东西（省一层）：
 * <pre>
 * input:  { resource: { id: 'minecraft:emerald', count: 8 } }   // 物品：count
 * input:  { resource: { id: 'pneumaticcraft:diesel', amount: 4000 } }  // 流体：amount
 * input:  'minecraft:emerald'                                   // 简写：自动判物品/流体
 * </pre>
 * 物品分支必须是具体物品 id（PnC 的 ItemStack codec 不支持物品标签），标签只对流体有效。
 */
public class AmadronResourceComponent implements RecipeComponent<AmadronResourceValue> {
	private static final TypeInfo TYPE_INFO = new JSObjectTypeInfo(List.of(
		new JSOptionalParam("resource", new JSObjectTypeInfo(List.of(
			new JSOptionalParam("id", TypeInfo.STRING, true),
			new JSOptionalParam("count", TypeInfo.INT, true),
			new JSOptionalParam("amount", TypeInfo.INT, true)
		)), true)
	));

	private final RecipeComponentType<?> type;

	public AmadronResourceComponent(RecipeComponentType<?> type) {
		this.type = type;
	}

	@Override
	public RecipeComponentType<?> type() {
		return type;
	}

	@Override
	public Codec<AmadronResourceValue> codec() {
		return AmadronResourceValue.CODEC;
	}

	@Override
	public TypeInfo typeInfo() {
		return TYPE_INFO;
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, Object from) {
		return from instanceof AmadronResourceValue || from instanceof ItemStack || from instanceof FluidStack || from instanceof CharSequence;
	}

	@Override
	public AmadronResourceValue wrap(RecipeScriptContext cx, Object from) {
		Context c = cx.cx();
		String where = type.toString();
		if (from == null) {
			throw new IllegalArgumentException(where + ": required (nothing to trade)");
		}
		if (from instanceof AmadronResourceValue v) {
			return v;
		}
		if (from instanceof ItemStack stack) {
			return AmadronResourceValue.of(stack);
		}
		if (from instanceof FluidStack stack) {
			return AmadronResourceValue.of(stack);
		}
		if (from instanceof CharSequence) {
			return byName(PncUtil.parseId(c, from, where));
		}

		Map<String, Object> map = PncUtil.stringMap(c, from);
		if (map == null) {
			throw new IllegalArgumentException(
				where + ": expected {resource: {id, count|amount}} or an item/fluid id string, got " + PncUtil.describe(c, from)
			);
		}
		if (map.get("resource") != null) {
			if (map.size() != 1) {
				throw new IllegalArgumentException(where + ": when 'resource' is used it must be the only key, got " + map.keySet());
			}
			return wrap(cx, map.get("resource"));
		}
		return spec(c, map, where);
	}

	private AmadronResourceValue byName(ResourceLocation rl) {
		if (BuiltInRegistries.ITEM.containsKey(rl)) {
			return AmadronResourceValue.of(new ItemStack(BuiltInRegistries.ITEM.get(rl)));
		}
		if (BuiltInRegistries.FLUID.containsKey(rl)) {
			return AmadronResourceValue.of(new FluidStack(BuiltInRegistries.FLUID.get(rl), PncUtil.BUCKET));
		}
		throw new IllegalArgumentException("amadron resource: '" + rl + "' is neither a known item nor a known fluid");
	}

	private AmadronResourceValue spec(Context c, Map<String, Object> map, String where) {
		boolean hasAmount = map.get("amount") != null;
		if (hasAmount) {
			PncUtil.rejectUnknownKeys(map, List.of("fluid", "id", "tag", "amount"), where);
			ResourceLocation rl = PncUtil.parseId(c, map.get("fluid") != null ? map.get("fluid") : map.get("id"), where);
			int amount = PncUtil.asInt(c, map.get("amount"), where + ".amount");
			if (map.get("tag") != null) {
				// 流体报价也可以是标签：PnC 侧 FluidStack 不支持标签，这里明确拒绝而不是静默写坏
				throw new IllegalArgumentException(
					where + ": amadron fluid resources must be a concrete fluid id ('id'/'fluid'), not a tag. "
						+ "PnC's AmadronTradeResource uses FluidStack, which cannot express tags."
				);
			}
			if (amount <= 0) {
				throw new IllegalArgumentException(where + ".amount: must be > 0, got " + amount);
			}
			return AmadronResourceValue.of(new FluidStack(PncUtil.fluid(rl, where), amount));
		}

		PncUtil.rejectUnknownKeys(map, List.of("item", "id", "count"), where);
		if (map.get("tag") != null) {
			throw new IllegalArgumentException(
				where + ": amadron item resources must be a concrete item id ('id'/'item'), not a tag "
					+ "(PnC's AmadronTradeResource uses ItemStack)."
			);
		}
		ResourceLocation rl = PncUtil.parseId(c, map.get("item") != null ? map.get("item") : map.get("id"), where);
		int count = map.get("count") == null ? 1 : PncUtil.asInt(c, map.get("count"), where + ".count");
		if (count <= 0) {
			throw new IllegalArgumentException(where + ".count: must be > 0, got " + count);
		}
		return AmadronResourceValue.of(new ItemStack(PncUtil.item(rl, where), count));
	}

	@Override
	public boolean matches(RecipeMatchContext cx, AmadronResourceValue value, ReplacementMatchInfo match) {
		return value.resource().map(
			stack -> ItemStackComponent.ITEM_STACK.instance().matches(cx, stack, match),
			stack -> FluidStackComponent.FLUID_STACK.instance().matches(cx, stack, match)
		);
	}

	@Override
	public AmadronResourceValue replace(RecipeScriptContext cx, AmadronResourceValue original, ReplacementMatchInfo match, Object with) {
		if (!matches(cx, original, match)) {
			return original;
		}
		Either<ItemStack, FluidStack> replaced = original.resource().map(
			stack -> Either.<ItemStack, FluidStack>left(ItemStackComponent.ITEM_STACK.instance().replace(cx, stack, match, with)),
			stack -> Either.<ItemStack, FluidStack>right(FluidStackComponent.FLUID_STACK.instance().replace(cx, stack, match, with))
		);
		return new AmadronResourceValue(replaced);
	}

	@Override
	public String toString(OpsContainer ops, AmadronResourceValue value) {
		return value.describe();
	}

	@Override
	public String toString() {
		return type.toString();
	}
}
