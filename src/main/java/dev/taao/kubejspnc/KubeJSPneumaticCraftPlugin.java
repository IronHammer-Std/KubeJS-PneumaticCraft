package dev.taao.kubejspnc;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.function.RecipeSchemaFunctionRegistry;
import dev.taao.kubejspnc.function.SetFieldFunction;

/**
 * KubeJS × PneumaticCraft 联动件（Tier C）插件入口。
 *
 * <p>与 Tier A（纯数据 jar）的关系是<b>取代</b>：两者都会提供
 * {@code data/pneumaticcraft/kubejs/recipe_schema/*.json}，不能同时安装。
 *
 * <p>只做两件事：
 * <ol>
 *   <li>注册 8 个 PnC 专用配方组件（{@link PncComponents}）；</li>
 *   <li>注册自定义函数类型 {@code pnc_set_field}，让 schema 里能声明 {@code .minTemp(373)} 这类糖。</li>
 * </ol>
 * schema 本身仍然是数据（{@code data/pneumaticcraft/kubejs/recipe_schema/}），只引用上面注册的组件 id。
 *
 * <p>刻意<b>不引用任何 {@code me.desht.pneumaticcraft.*} 类</b>：本件许可保持 MIT，
 * 也不随 PnC 内部实现变动而崩（规则以 PnC 源码实读为准，写死在组件里）。
 */
public class KubeJSPneumaticCraftPlugin implements KubeJSPlugin {
	public static final String MOD_ID = "kubejs_pneumaticcraft";

	@Override
	public void registerRecipeComponents(RecipeComponentTypeRegistry registry) {
		PncComponents.register(registry);
	}

	@Override
	public void registerRecipeSchemaFunctionTypes(RecipeSchemaFunctionRegistry registry) {
		registry.register(SetFieldFunction.TYPE);
	}
}
