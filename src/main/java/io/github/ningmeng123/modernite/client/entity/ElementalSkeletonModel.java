package io.github.ningmeng123.modernite.client.entity;

import io.github.ningmeng123.mite.client.FreshAnimationsModelDriver;
import io.github.ningmeng123.modernite.ModernIte;
import io.github.ningmeng123.modernite.entity.ExtremeKind;
import java.util.function.Supplier;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.AbstractSkeleton;

/** Open ribs and elemental bone details on the native skeleton animation rig. @author NingMeng123 */
public final class ElementalSkeletonModel extends SkeletonModel<AbstractSkeleton> {
    private final ModelPart root;
    private final ExtremeKind kind;
    private final SkeletonModel<AbstractSkeleton> animationModel;
    private boolean freshAnimationsApplied;

    public ElementalSkeletonModel(ModelPart root, ExtremeKind kind) {
        this(root, kind, () -> SkeletonModel.createBodyLayer().bakeRoot());
    }

    public ElementalSkeletonModel(ModelPart root, ExtremeKind kind, Supplier<ModelPart> animationRoot) {
        super(root);
        this.root = root;
        this.kind = kind;
        // Bake the donor after super so EMF binds its skeleton animation to the donor model.
        this.animationModel = new SkeletonModel<>(animationRoot.get());
    }

    public static boolean supports(ExtremeKind kind) {
        return kind == ExtremeKind.SKELETON_BLAZE || kind == ExtremeKind.SKELETON_LIGHTNING
                || kind == ExtremeKind.SKELETON_FLOATING;
    }

    public static ResourceLocation texture(ExtremeKind kind, boolean glow) {
        String variant = switch (kind) {
            case SKELETON_BLAZE -> "blaze";
            case SKELETON_LIGHTNING -> "lightning";
            case SKELETON_FLOATING -> "floating";
            default -> throw new IllegalArgumentException("Unsupported elemental skeleton: " + kind);
        };
        return ResourceLocation.fromNamespaceAndPath(ModernIte.MOD_ID,
                "textures/entity/skeleton/elemental_" + variant + (glow ? "_glow" : "") + ".png");
    }

    public static LayerDefinition layer(ExtremeKind kind) {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -8, -4, 8, 8, 8), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        var body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        body.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(24, 40)
                .addBox(-1, 0, .5F, 2, 10, 2), PartPose.ZERO);
        body.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(0, 20)
                .addBox(-4, 0, -1, 8, 1, 2), PartPose.ZERO);
        rib(body, "rib_upper", 2, 7);
        rib(body, "rib_middle", 4.5F, 7);
        rib(body, "rib_lower", 7, 6);
        body.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(0, 25)
                .addBox(-3.5F, 10, -1.5F, 7, 2, 3), PartPose.ZERO);

        boolean floating = kind == ExtremeKind.SKELETON_FLOATING;
        arm(root, "right_arm", -5, false, floating);
        arm(root, "left_arm", 5, true, floating);
        leg(root, "right_leg", -2, false, floating);
        leg(root, "left_leg", 2, true, floating);

        switch (kind) {
            case SKELETON_BLAZE -> {
                head.addOrReplaceChild("ember_spike_left", CubeListBuilder.create().texOffs(0, 40)
                        .addBox(-1, -3, -1, 2, 3, 2), PartPose.offsetAndRotation(-2, -7, 2.5F, -.2F, 0, -.15F));
                head.addOrReplaceChild("ember_spike_right", CubeListBuilder.create().texOffs(0, 40)
                        .addBox(-1, -2, -1, 2, 2, 2), PartPose.offsetAndRotation(2, -7, 2.5F, -.2F, 0, .15F));
            }
            case SKELETON_LIGHTNING -> {
                head.addOrReplaceChild("conductor_left", CubeListBuilder.create().texOffs(12, 40)
                        .addBox(-.5F, -1, 0, 1, 2, 5), PartPose.offsetAndRotation(-2.5F, -7, 2, .45F, -.1F, 0));
                head.addOrReplaceChild("conductor_right", CubeListBuilder.create().texOffs(12, 40)
                        .addBox(-.5F, -1, 0, 1, 2, 5), PartPose.offsetAndRotation(2.5F, -7, 2, .45F, .1F, 0));
            }
            case SKELETON_FLOATING -> {
                shard(body, "shard_left", -3.2F, 22.5F, 1.6F);
                shard(body, "shard_right", 3, 23.8F, -1);
                shard(body, "shard_center", 0, 25.5F, .3F);
            }
            default -> throw new IllegalArgumentException("Unsupported elemental skeleton: " + kind);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void rib(PartDefinition body, String name, float height, int width) {
        float left = -width / 2F;
        body.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 16)
                .addBox(left, 0, -2, width, 1, 1)
                .texOffs(0, 16).addBox(left, 0, -1, 1, 1, 3)
                .texOffs(0, 16).addBox(-left - 1, 0, -1, 1, 1, 3), PartPose.offset(0, height, 0));
    }

    private static void arm(PartDefinition root, String name, float x, boolean mirror, boolean floating) {
        var arm = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(32, 16).mirror(mirror)
                .addBox(-1, -2, -1, 2, 5, 2)
                .texOffs(48, 16).addBox(-1.25F, 3, -1.25F, 2.5F, 1.5F, 2.5F), PartPose.offset(x, 2, 0));
        arm.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(40, 16).mirror(mirror)
                .addBox(-1, 0, -1, 2, floating ? 5 : 6, 2), PartPose.offset(0, floating ? 5 : 4, 0));
    }

    private static void leg(PartDefinition root, String name, float x, boolean mirror, boolean floating) {
        var leg = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(32, 32).mirror(mirror)
                .addBox(-1, 0, -1, 2, 5, 2)
                .texOffs(48, 16).addBox(-1.25F, 5, -1.25F, 2.5F, 1.5F, 2.5F), PartPose.offset(x, 12, 0));
        var shin = leg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(40, 32).mirror(mirror)
                .addBox(-1, 0, -1, 2, floating ? 4 : 5, 2), PartPose.offset(0, floating ? 7 : 6, 0));
        shin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(48, 32).mirror(mirror)
                .addBox(-1, 0, -2, 2, 1, 3), PartPose.offset(0, floating ? 4 : 5, 0));
    }

    private static void shard(PartDefinition body, String name, float x, float y, float z) {
        body.addOrReplaceChild(name, CubeListBuilder.create().texOffs(32, 48)
                .addBox(-.5F, -.5F, -.5F, 1, 1, 1), PartPose.offset(x, y, z));
    }

    @Override
    public void setupAnim(AbstractSkeleton entity, float limb, float amount, float age, float yaw, float pitch) {
        if (freshAnimationsApplied) root.getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(entity, limb, amount, age, yaw, pitch);
        copyPropertiesTo(animationModel);
        animationModel.swimAmount = swimAmount;
        animationModel.setupAnim(entity, limb, amount, age, yaw, pitch);
        freshAnimationsApplied = FreshAnimationsModelDriver.animate(entity, animationModel);
        if (freshAnimationsApplied) animationModel.copyPropertiesTo(this);

        if (kind == ExtremeKind.SKELETON_FLOATING) {
            float sway = Mth.sin(age * .065F);
            // Preserve riding and swimming poses while softening the hovering walk cycle.
            if (!riding && swimAmount == 0) {
                rightLeg.xRot = rightLeg.xRot * .25F + .08F + sway * .04F;
                leftLeg.xRot = leftLeg.xRot * .25F - .06F - sway * .04F;
            }
            float drift = Mth.sin(age * .08F) * .3F;
            body.getChild("shard_left").y = 22.5F + drift;
            body.getChild("shard_right").y = 23.8F - drift;
            body.getChild("shard_center").y = 25.5F + drift;
        }
    }

    public ModelPart root() { return root; }

    public boolean freshAnimationsApplied() { return freshAnimationsApplied; }
}
