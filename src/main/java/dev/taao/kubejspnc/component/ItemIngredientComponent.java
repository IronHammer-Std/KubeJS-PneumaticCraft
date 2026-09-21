package dev.taao.kubejspnc.component;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.plugin.builtin.wrapper.IngredientWrapper;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.UniqueIdBuilder;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.kubejs.recipe.match.ItemMatch;
import dev.latvian.mods.kubejs.recipe.match.ReplacementMatchInfo;
import dev.latvian.mods.kubejs.util.OpsContainer;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.JSOptionalParam;
import dev.latvian.mods.rhino.type.JSObjectTypeInfo;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.taao.kubejspnc.PncUtil;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * {@code pneumaticcraft:item_ingredient} —— PnC 的"<b>带数量</b>的物品输入"
 * （NeoForge {@code SizedIngredient}，PnC 侧一律用 {@code SizedIngredient.FLAT_CODEC} 读它：
 * {@code pressure_chamber.inputs} / {@code explosion_crafting.input} /
 * {@code assembly_drill.input} / {@code assembly_laser.input}）。
 *
 * <p><b>为什么不直接用 KubeJS 内置的 {@code flat_sized_ingredient}</b>：
 * KubeJS 2101.7.x 的 {@code SizedIngredientWrapper.wrapResult()} 收到 JS 对象（Map）时，
 * 走的是 {@code IngredientWrapper.wrapResult(...).map(IngredientKJS::kjs$asStack)}，
 * 而 {@code kjs$asStack()} 把数量<b>硬编码成 1</b>
 * ⇒ 脚本里写的 {@code {count: 4, tag: '...'}} 会被<b>静默</b>压成 1 个。
 * 只有对象形态（{@code type({...})}，那条路走 codec）才是对的，
 * 于是同一个包里两种写法写出来的配方消耗量还不一样 —— 这是实测踩到的坑（2026-09-20）。
 * 本组件自己实现 {@link #wrap} 把 {@code count} 读出来，并沿用本联动件
 * "键写错就在 reload 时报错"的做法。
 *
 * <p>合法 JS 写法（{@code count} 缺省 1，必须 ≥ 1）：
 * <pre>
 * { count: 4, tag: 'c:ingots/compressed_iron' }   // tag / item 二选一
 * { count: 4, item: 'minecraft:iron_ingot' }
 * '4x #c:ingots/compressed_iron'                  // 字符串简写（# 前缀 = 标签）
 * Item.of('minecraft:iron_ingot', 4)              // 直接给 ItemStack（数量取 stack 的）
 * Ingredient.of('#c:ingots/compressed_iron', 4)   // 直接给 SizedIngredient
 * </pre>
 */
public final class ItemIngredientComponent implements RecipeComponent<SizedIngredient> {
	private static final TypeInfo TYPE_INFO = new JSObjectTypeInfo(List.of(
		new JSOptionalParam("count", TypeInfo.INT, true),
		new JSOptionalParam("item", TypeInfo.STRING, true),
		new JSOptionalParam("tag", TypeInfo.STRING, true)
	));

	private static final List<String> ALLOWED = List.of("count", "item", "id", "tag");

	/** 字符串简写里的数量前缀：{@code "4x <ingredient>"} / {@code "4 x <ingredient>"}。 */
	private static final Pattern COUNTED = Pattern.compile("^(\\d+)\\s*x\\s*(.+)$", Pattern.CASE_INSENSITIVE);

	private final RecipeComponentType<?> type;

	public ItemIngredientComponent(RecipeComponentType<?> type) {
		this.type = type;
	}

	@Override
	public RecipeComponentType<?> type() {
		return type;
	}

	@Override
	public Codec<SizedIngredient> codec() {
		return SizedIngredient.FLAT_CODEC;
	}

	@Override
	public TypeInfo typeInfo() {
		return TYPE_INFO;
	}

	@Override
	public boolean hasPriority(RecipeMatchContext cx, Object from) {
		if (from instanceof SizedIngredient || from instanceof ItemStack || from instanceof Ingredient) {
			return true;
		}
		if (from instanceof CharSequence) {
			return true;
		}
		if (from instanceof Map<?, ?> map) {
			// 不认 fluid 键，避免和 pneumaticcraft:fluid_ingredient 抢
			return map.containsKey("item") || map.containsKey("id") || map.containsKey("tag") || map.containsKey("count");
		}
		return false;
	}

	@Override
	public SizedIngredient wrap(RecipeScriptContext cx, Object from) {
		Context c = cx.cx();
		String where = where();

		if (from == null) {
			throw new IllegalArgumentException(where + ": an item is required");
		}
		if (from instanceof SizedIngredient v) {
			return v;
		}
		if (from instanceof ItemStack stack) {
			if (stack.isEmpty()) {
				throw new IllegalArgumentException(where + ": empty item stack is not a valid ingredient");
			}
			return new SizedIngredient(Ingredient.of(stack.copyWithCount(1)), stack.getCount());
		}
		if (from instanceof Ingredient ingredient) {
			if (ingredient.isEmpty()) {
				throw new IllegalArgumentException(where + ": empty ingredient is not allowed here");
			}
			return new SizedIngredient(ingredient, 1);
		}
		if (from instanceof CharSequence) {
			return fromString(c, from.toString().trim(), where);
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
			where + ": expected {count?, item|tag}, '4x #tag', an ItemStack or a SizedIngredient, got "
				+ PncUtil.describe(c, from)
		);
	}

	/** {@code '4x #c:ingots/iron'} / {@code '#c:ingots/iron'} / {@code '4x minecraft:iron_ingot'}。 */
	private SizedIngredient fromString(Context c, String raw, String where) {
		int count = 1;
		String s = raw;
		Matcher m = COUNTED.matcher(raw);
		if (m.matches()) {
			count = countOf(c, m.group(1), where);
			s = m.group(2).trim();
		}
		if (s.isEmpty()) {
			throw new IllegalArgumentException(where + ": '" + raw + "' has no item after the count");
		}
		boolean tag = PncUtil.isTagString(c, s);
		ResourceLocation rl = PncUtil.parseId(c, s, where);
		return tag ? fromTag(rl, count) : fromItem(rl, count, where);
	}

	private SizedIngredient fromMap(Context c, Map<String, Object> map, String where) {
		PncUtil.rejectUnknownKeys(map, ALLOWED, where);

		boolean hasTag = map.get("tag") != null;
		boolean hasItem = map.get("item") != null || map.get("id") != null;
		if (hasTag && hasItem) {
			throw new IllegalArgumentException(where + ": 'tag' and 'item' are mutually exclusive");
		}
		if (!hasTag && !hasItem) {
			throw new IllegalArgumentException(where + ": one of " + ALLOWED + " is required, got keys " + map.keySet());
		}

		int count = map.get("count") == null ? 1 : PncUtil.asInt(c, map.get("count"), where + ".count");
		if (count < 1) {
			throw new IllegalArgumentException(where + ".count: must be >= 1, got " + count);
		}

		if (hasTag) {
			return fromTag(PncUtil.parseId(c, map.get("tag"), where + ".tag"), count);
		}
		Object item = map.get("item") != null ? map.get("item") : map.get("id");
		return fromItem(PncUtil.parseId(c, item, where), count, where);
	}

	private int countOf(Context c, String digits, String where) {
		try {
			int count = Integer.parseInt(digits);
			if (count < 1) {
				throw new IllegalArgumentException(where + ": count must be >= 1, got " + count);
			}
			return count;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(where + ": '" + digits + "' is not a valid count");
		}
	}

	private SizedIngredient fromTag(ResourceLocation rl, int count) {
		// 标签不在注册表里，无法即时校验（与 KubeJS 一致：写错标签只会匹配不到东西）
		return new SizedIngredient(Ingredient.of(TagKey.create(Registries.ITEM, rl)), count);
	}

	private SizedIngredient fromItem(ResourceLocation rl, int count, String where) {
		Item item = PncUtil.item(rl, where);
		return new SizedIngredient(Ingredient.of(item), count);
	}

	@Override
	public boolean matches(RecipeMatchContext cx, SizedIngredient value, ReplacementMatchInfo match) {
		return match.match() instanceof ItemMatch m && !value.ingredient().isEmpty() && m.matches(cx, value.ingredient(), match.exact());
	}

	@Override
	public SizedIngredient replace(RecipeScriptContext cx, SizedIngredient original, ReplacementMatchInfo match, Object with) {
		if (!matches(cx, original, match)) {
			return original;
		}
		// 数量以原值为准：{output}/{input} 过滤只换"是什么物品"，不换"多少个"
		return new SizedIngredient(wrap(cx, with).ingredient(), original.count());
	}

	@Override
	public boolean isEmpty(SizedIngredient value) {
		return value.count() <= 0 || value.ingredient().isEmpty();
	}

	@Override
	public void buildUniqueId(UniqueIdBuilder builder, SizedIngredient value) {
		TagKey<Item> tag = IngredientWrapper.tagKeyOf(value.ingredient());
		if (tag != null) {
			builder.append(tag.location());
			return;
		}
		ItemStack[] stacks = value.ingredient().getItems();
		if (stacks.length > 0 && !stacks[0].isEmpty()) {
			builder.append(BuiltInRegistries.ITEM.getKey(stacks[0].getItem()));
		}
	}

	@Override
	public String toString(OpsContainer ops, SizedIngredient value) {
		return value.count() + "x " + value.ingredient();
	}

	@Override
	public String toString() {
		return where();
	}

	private String where() {
		return type == null ? "pneumaticcraft:item_ingredient" : type.toString();
	}
}
