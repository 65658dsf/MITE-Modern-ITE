package io.github.ningmeng123.modernite.content;

import net.minecraft.world.phys.Vec3;

/** The same logical phase drives player physics, input and render interpolation. @author NingMeng123 */
public interface PlayerTimeDilationState {
    int modernite$timePhase();
    Vec3 modernite$previousTimePosition();
    int modernite$timeVisualAge();
}
