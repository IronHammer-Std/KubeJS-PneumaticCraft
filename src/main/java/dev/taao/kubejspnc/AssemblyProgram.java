package dev.taao.kubejspnc;

import net.minecraft.util.StringRepresentable;

/**
 * PnC {@code AssemblyRecipe.AssemblyProgramType} 的镜像（只保留可撰写的两个值）。
 *
 * <p>PnC 侧的枚举有三个值：{@code drill} / {@code laser} / {@code drill_laser}，但
 * {@code drill_laser} 在 codec 里被显式拒绝（源码实读：{@code checkNotDrillAndLaser}
 * → "'drill_laser' may not be used as a recipe type!"，它只由运行时合成链生成）。
 * 所以组件只放行 {@code drill} / {@code laser}，写 {@code drill_laser} 会在 KubeJS 层被拒。
 */
public enum AssemblyProgram implements StringRepresentable {
	DRILL("drill"),
	LASER("laser");

	private final String name;

	AssemblyProgram(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
