package dev.taao.kubejspnc.value;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * {@code pneumaticcraft:heat_frame_cooling} 的流体容器输入（{@code input.fluid}）镜像。
 *
 * <p>PnC 侧的类型是 {@code FluidContainerIngredient}，其 codec（源码实读）为
 * {@code {"fluid": either(FluidStack.CODEC, TagWithAmount)}}，即：
 * <ul>
 *   <li>{@code {"amount":1000,"id":"minecraft:water"}}（FluidStack 分支，amount 缺省 1000）</li>
 *   <li>{@code {"amount":1000,"tag":"minecraft:water"}}（TagWithAmount 分支，amount <b>必填</b>且为正）</li>
 * </ul>
 * <b>这里不是</b> {@code SizedFluidIngredient.FLAT_CODEC}：写 {@code fluid:} 会两边都不匹配，
 * 这正是 Tier A 时期实测踩到的"either 树"报错。本组件把这条规则搬到 KubeJS 层。
 */
public record FluidContainerValue(Either<FluidStack, TagAmount> either) {
	public static final Codec<FluidContainerValue> CODEC = Codec.either(FluidStack.CODEC, TagAmount.CODEC)
		.xmap(FluidContainerValue::new, FluidContainerValue::either);

	public static FluidContainerValue of(FluidStack stack) {
		return new FluidContainerValue(Either.left(stack));
	}

	public static FluidContainerValue of(TagAmount tagAmount) {
		return new FluidContainerValue(Either.right(tagAmount));
	}

	public boolean isTag() {
		return either.right().isPresent();
	}

	public int amount() {
		return either.map(FluidStack::getAmount, TagAmount::amount);
	}

	public Optional<FluidStack> stack() {
		return either.left();
	}

	public Optional<TagAmount> tagAmount() {
		return either.right();
	}

	@Override
	public String toString() {
		return either.map(FluidStack::toString, TagAmount::toString);
	}

	/** PnC {@code FluidContainerIngredient.TagWithAmount} 的镜像。 */
	public record TagAmount(TagKey<Fluid> tag, int amount) {
		public static final Codec<TagAmount> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					TagKey.codec(Registries.FLUID).fieldOf("tag").forGetter(TagAmount::tag),
					ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(TagAmount::amount)
				)
				.apply(instance, TagAmount::new)
		);

		@Override
		public String toString() {
			return amount + "mB #" + tag.location();
		}
	}
}
