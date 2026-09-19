package dev.taao.kubejspnc.function;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.function.RecipeSchemaFunction;
import dev.latvian.mods.kubejs.recipe.schema.function.RecipeSchemaFunctionType;
import dev.latvian.mods.kubejs.recipe.schema.function.ResolvedRecipeSchemaFunction;
import dev.taao.kubejspnc.component.FieldSettable;
import java.util.List;

/**
 * 自定义 schema 函数类型 {@code pnc_set_field} —— 给"设对象子字段"提供真正的函数糖。
 *
 * <p>为什么需要它：schema JSON 的内置函数只有 {@code set}（把整个键设成常量）与
 * {@code add_to_list}（往列表键追加），而 {@code .minTemp(373)} 这种是"把
 * {@code temperature} 这个对象键的 {@code min} 子字段设成实参"。
 *
 * <p>用法（schema JSON，函数名就是 JS 方法名）：
 * <pre>
 * "functions": {
 *   "minTemp": { "type": "pnc_set_field", "key": "temperature", "field": "min" },
 *   "maxTemp": { "type": "pnc_set_field", "key": "temperature", "field": "max" }
 * }
 * </pre>
 * 于是 JS 里可以写 {@code ....temperature({min:373}).maxTemp(393)}，也可以只写
 * {@code ....minTemp(373)}（未设过的键从组件的 {@link FieldSettable#defaultValue()} 起步）。
 */
public record SetFieldFunction(String key, String field) implements RecipeSchemaFunction {
	public static final RecipeSchemaFunctionType<SetFieldFunction> TYPE = new RecipeSchemaFunctionType<>(
		"pnc_set_field",
		RecordCodecBuilder.mapCodec(instance -> instance.group(
				Codec.STRING.fieldOf("key").forGetter(SetFieldFunction::key),
				Codec.STRING.fieldOf("field").forGetter(SetFieldFunction::field)
			).apply(instance, SetFieldFunction::new))
	);

	@Override
	public RecipeSchemaFunctionType<?> type() {
		return TYPE;
	}

	@Override
	public DataResult<ResolvedRecipeSchemaFunction> resolve(DynamicOps<JsonElement> jsonOps, RecipeSchema schema) {
		RecipeKey<?> target = schema.getOptionalKey(key);
		if (target == null) {
			return DataResult.error(() -> "Key '" + key + "' not found");
		}
		if (!(target.component instanceof FieldSettable<?> settable)) {
			return DataResult.error(() -> "Component '" + target.component + "' of key '" + key + "' does not support field setting");
		}
		if (!settable.fieldNames().contains(field)) {
			return DataResult.error(() -> "Key '" + key + "' has no field '" + field + "'; valid fields: " + settable.fieldNames());
		}
		@SuppressWarnings({"unchecked", "rawtypes"})
		ResolvedRecipeSchemaFunction resolved = new Resolved(target, (FieldSettable) settable, field);
		return DataResult.success(resolved);
	}

	public record Resolved<T>(RecipeKey<T> key, FieldSettable<T> component, String field) implements ResolvedRecipeSchemaFunction {
		@Override
		public List<RecipeComponent<?>> arguments() {
			return List.<RecipeComponent<?>>of(NumberComponent.INT);
		}

		@Override
		public void execute(RecipeScriptContext cx, List<Object> args) {
			if (args.isEmpty()) {
				throw new IllegalArgumentException("pnc_set_field: one argument expected for key '" + key.name + "'");
			}
			KubeRecipe recipe = cx.recipe();
			T current = recipe.getValue(key);
			if (current == null) {
				current = component.defaultValue();
			}
			recipe.setValue(key, component.withField(cx, current, field, args.getFirst()));
		}
	}
}
