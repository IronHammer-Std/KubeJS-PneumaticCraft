package dev.taao.kubejspnc;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

/**
 * 共享工具：JS 值读取、ResourceLocation / 流体 / 标签解析、报错文案。
 *
 * <p>设计约束（见 TierC 设计稿 §2）：本联动件<b>不引用任何 PnC 类</b>，只与 KubeJS /
 * Minecraft / NeoForge 的类型打交道，因此许可可保持 MIT，且不随 PnC 内部实现变动而崩。
 */
public final class PncUtil {
	public static final String PNC = "pneumaticcraft";
	/** NeoForge {@code FluidType.BUCKET_VOLUME}：SizedFluidIngredient 缺省 amount。 */
	public static final int BUCKET = 1000;

	private PncUtil() {
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(PNC, path);
	}

	public static String describe(Context cx, Object value) {
		if (value == null) {
			return "null";
		}
		try {
			return cx.toString(value);
		} catch (Throwable t) {
			return String.valueOf(value);
		}
	}

	/** 只有当值真的是"对象/Map"时才返回它，否则返回 null（由调用方决定怎么报价）。 */
	public static Map<String, Object> stringMap(Context cx, Object from) {
		if (from == null || !cx.isMapLike(from)) {
			return null;
		}
		Map<String, Object> map = cx.optionalMapOf(from, TypeInfo.STRING, TypeInfo.NONE);
		return map == null ? Map.of() : map;
	}

	public static int asInt(Context cx, Object value, String where) {
		if (value instanceof Number n) {
			return n.intValue();
		}
		try {
			Object converted = cx.jsToJava(value, TypeInfo.INT);
			if (converted instanceof Number n) {
				return n.intValue();
			}
		} catch (Throwable ignored) {
			// 落到下面统一报价
		}
		throw new IllegalArgumentException(where + ": expected an integer, got " + describe(cx, value));
	}

	public static float asFloat(Context cx, Object value, String where) {
		if (value instanceof Number n) {
			return n.floatValue();
		}
		try {
			Object converted = cx.jsToJava(value, TypeInfo.FLOAT);
			if (converted instanceof Number n) {
				return n.floatValue();
			}
		} catch (Throwable ignored) {
			// 落到下面统一报价
		}
		throw new IllegalArgumentException(where + ": expected a number, got " + describe(cx, value));
	}

	public static boolean asBoolean(Context cx, Object value, String where) {
		if (value instanceof Boolean b) {
			return b;
		}
		try {
			Object converted = cx.jsToJava(value, TypeInfo.BOOLEAN);
			if (converted instanceof Boolean b) {
				return b;
			}
		} catch (Throwable ignored) {
			// 落到下面统一报价
		}
		throw new IllegalArgumentException(where + ": expected a boolean, got " + describe(cx, value));
	}

	public static String asString(Context cx, Object value, String where) {
		if (value instanceof CharSequence cs) {
			return cs.toString();
		}
		Object converted = cx.jsToJava(value, TypeInfo.STRING);
		if (converted instanceof CharSequence cs) {
			return cs.toString();
		}
		throw new IllegalArgumentException(where + ": expected a string, got " + describe(cx, value));
	}

	/**
	 * 解析 {@code ns:path}（也接受 KubeJS 常见的 {@code #ns:path} 写法，此时返回值与是否带 # 无关，
	 * 由调用方通过 {@link #isTagString} 判断）。
	 */
	public static ResourceLocation parseId(Context cx, Object value, String where) {
		String s = asString(cx, value, where).trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		ResourceLocation rl = ResourceLocation.tryParse(s);
		if (rl == null) {
			throw new IllegalArgumentException(where + ": '" + s + "' is not a valid resource location (ns:path)");
		}
		return rl;
	}

	public static boolean isTagString(Context cx, Object value) {
		return value instanceof CharSequence cs && cs.toString().trim().startsWith("#");
	}

	public static TagKey<Fluid> fluidTag(ResourceLocation rl) {
		return TagKey.create(Registries.FLUID, rl);
	}

	public static Fluid fluid(ResourceLocation rl, String where) {
		if (!BuiltInRegistries.FLUID.containsKey(rl)) {
			throw new IllegalArgumentException(where + ": unknown fluid '" + rl + "'");
		}
		return BuiltInRegistries.FLUID.get(rl);
	}

	public static net.minecraft.world.item.Item item(ResourceLocation rl, String where) {
		if (!BuiltInRegistries.ITEM.containsKey(rl)) {
			throw new IllegalArgumentException(where + ": unknown item '" + rl + "'");
		}
		return BuiltInRegistries.ITEM.get(rl);
	}

	/** 逐键白名单检查：多写/写错键时在 KubeJS 层就报错，而不是等 PnC 静默忽略。 */
	public static void rejectUnknownKeys(Map<String, Object> map, Collection<String> allowed, String where) {
		List<String> bad = new ArrayList<>();
		for (String k : map.keySet()) {
			if (!allowed.contains(k)) {
				bad.add(k);
			}
		}
		if (!bad.isEmpty()) {
			throw new IllegalArgumentException(where + ": unknown key(s) " + bad + "; valid keys: " + allowed);
		}
	}
}
