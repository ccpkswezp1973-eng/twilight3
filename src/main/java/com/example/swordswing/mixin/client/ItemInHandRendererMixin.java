package com.example.swordswing.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 검을 들고 공격할 때마다 3가지 베기 동작이 순서대로 반복되는 콤보 모션.
 *   0) 내려찍기  1) 쓸어베기  2) 올려베기
 *
 * 스윙 "속도"(전체 시간)는 바닐라 그대로입니다. 대신 시간 배분으로 묵직함을 만듭니다.
 *   0.00~0.30  젖히기   : 천천히 힘을 모음 (무거운 검을 드는 느낌)
 *   0.30~0.50  베기     : 가속해서 순간적으로 내려침 (속도 변화가 클수록 묵직함)
 *   0.50~0.62  여운     : 살짝 지나쳤다가 돌아옴 (관성/오버슈트)
 *   0.62~0.70  정지     : 베기 끝에서 잠깐 멈춤 (타격감)
 *   0.70~1.00  복귀     : 천천히 원위치로 (무게 때문에 느리게 돌아옴)
 * 그리고 베기 직후 손이 아래로 푹 꺼지며 잔떨림이 생깁니다.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    // ===== 묵직함 조절 =====
    private static final float POWER        = 1.15F;  // 전체 동작 크기 배율 (크게 휘두를수록 묵직해 보임)
    private static final float OVERSHOOT    = 0.08F;  // 베기 끝에서 지나쳤다 돌아오는 정도
    private static final float IMPACT_DIP   = 0.07F;  // 베기 직후 손이 아래로 꺼지는 정도
    private static final float IMPACT_PUSH  = 0.05F;  // 베기 직후 앞으로 밀리는 정도
    private static final float IMPACT_SHAKE = 3.0F;   // 베기 직후 잔떨림 각도 (도)
    // ======================

    // 각 행: { yaw, roll, pitch } (단위: 도)
    // 방향이 반대로 느껴지면 해당 숫자의 부호를 뒤집으세요.
    private static final float[][] WINDUP = {
        {   0F,  10F,  70F },  // 0 내려찍기: 검을 위로 번쩍 듦
        {  55F,  15F,   5F },  // 1 쓸어베기: 몸쪽으로 크게 감음
        {  10F, -30F, -40F },  // 2 올려베기: 아래로 낮춤
    };
    private static final float[][] SLASH = {
        {   0F, -10F, -100F }, // 0 내려찍기: 크게 내려침
        { -80F, -20F,  -15F }, // 1 쓸어베기: 반대편까지 쓸어냄
        { -45F,  65F,   60F }, // 2 올려베기: 위로 올려 벰
    };

    private static int combo = 0;
    private static float lastProgress = 0F;

    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void swordswing$customSwing(PoseStack poseStack, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || arm != player.getMainArm() || !player.getMainHandItem().is(ItemTags.SWORDS)) {
            return; // 검이 아니면 바닐라 모션 그대로
        }

        // 새 스윙이 시작되면 다음 동작으로 넘어감
        boolean newSwing = (lastProgress <= 0.001F && swingProgress > 0.001F)
                || swingProgress + 0.05F < lastProgress;
        if (newSwing) {
            combo = (combo + 1) % WINDUP.length;
        }
        lastProgress = swingProgress;

        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float c = curve(swingProgress); // -1(젖힘) ~ +1(베기 끝), 여운 구간에서는 1을 살짝 넘음
        float[] from = WINDUP[combo];
        float[] to = SLASH[combo];

        float yaw   = (c < 0 ? from[0] * -c : to[0] * c) * POWER;
        float roll  = (c < 0 ? from[1] * -c : to[1] * c) * POWER;
        float pitch = (c < 0 ? from[2] * -c : to[2] * c) * POWER;

        // 베기 직후 충격: 0 → 1 → 0 으로 부드럽게 올라갔다 가라앉는 값
        float u = Mth.clamp((swingProgress - 0.50F) / 0.25F, 0.0F, 1.0F);
        float impact = Mth.sin(u * (float) Math.PI) * (1.0F - u);
        float shake = Mth.sin(u * (float) Math.PI * 5.0F) * (1.0F - u) * IMPACT_SHAKE;

        poseStack.translate(0.0F, -IMPACT_DIP * impact, -IMPACT_PUSH * impact);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * (roll + shake)));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        ci.cancel();
    }

    /** 스윙 진행도(0~1) -> 동작 위치(-1 젖힘 ~ +1 베기 끝). 전체 시간은 바뀌지 않음. */
    private static float curve(float p) {
        if (p < 0.30F) {
            return -smooth(p / 0.30F);                                   // 천천히 힘 모으기
        }
        if (p < 0.50F) {
            float t = (p - 0.30F) / 0.20F;
            return Mth.lerp(t * t * t, -1.0F, 1.0F);                     // 가속해서 내려침
        }
        if (p < 0.62F) {
            float t = (p - 0.50F) / 0.12F;
            return 1.0F + OVERSHOOT * Mth.sin(t * (float) Math.PI);      // 지나쳤다 돌아옴
        }
        if (p < 0.70F) {
            return 1.0F;                                                 // 타격 순간 정지
        }
        return 1.0F - smooth((p - 0.70F) / 0.30F);                       // 천천히 복귀
    }

    private static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }
}
