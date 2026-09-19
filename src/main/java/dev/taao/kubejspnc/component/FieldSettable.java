package dev.taao.kubejspnc.component;

import java.util.Set;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;

/**
 * 让"设对象子字段"的函数糖（{@code .minTemp(373)} 之类）能作用在自定义组件上。
 *
 * <p>背景：schema JSON 的 {@code functions} 只支持内置的 {@code set}（整键赋值）与
 * {@code add_to_list}（列表追加），做不到"把某个键的对象的某个子字段改掉"。
 * 于是注册一个自定义函数类型 {@code pnc_set_field}（见 {@link dev.taao.kubejspnc.function.SetFieldFunction}），
 * 由组件自己声明有哪些可写字段、怎么合并。
 */
public interface FieldSettable<T> {
	/** 当前还没设过值时用的起点（例：temperature 的起点是 any）。 */
	T defaultValue();

	/** 返回把 current 的 field 子字段设成 value 之后的新值。 */
	T withField(RecipeScriptContext cx, T current, String field, Object value);

	/** 可写字段名（用于 schema 校验与报错文案）。 */
	Set<String> fieldNames();
}
