# 对齐 SilentAura AI 旋转模式与 LiquidBounceNG 输出语义

## Context

Vape 的 SilentAura AI 模式当前将 MLP 模型输出 `output[0] * yawMultiplier` 当作**绝对目标角度**通过 `setTargetRotation()` 设给 `AdaptiveRotationController`，后续由 PID（`updateYaw()`/`updatePitch()` + `getSpeed()` 对数正态步进）多 tick 逼近。这与 LiquidBounceNG 的语义不一致——LBNG 中 `AiAngleSmooth.process()` 把模型输出当作**直接 delta**，加到 `currentRotation` 上得到 `modelOutput` 后直接应用（经 `correctionMode` 二次平滑，本次不引入）。

用户要求：让 Vape 的 AI multiplier 输出方式与 LBNG 对齐——**output * multiplier 直接转成 pendingDelta 一步应用，绕过 PID 步进**。`getSpeed()` 的 lognormal+aimSpeed 在 Normal 模式保留不变，AI 模式自然不走该路径。不引入 LBNG 的 correctionMode。

## 关键文件

- `src/main/java/gg/vape/rotation/AdaptiveRotationController.java` — 新增 flag + 两个方法 + updateYaw/updatePitch/update 守卫
- `src/main/java/gg/vape/module/combat/SilentAura.java` — 修改 updateAimAi 输出 + Normal 分支/resetTargeting/onDisable 调用清理

## 实现步骤

### 1. AdaptiveRotationController 新增 `aiRotationActive` 字段

在 L46 附近的私有字段区声明：
```java
private boolean aiRotationActive;
```

### 2. updateYaw / updatePitch 插入 bypass 守卫

- `updateYaw()` (L347-L368)：在 L350 的 `UNSET_ROTATION` 检查之后立即插入：
  ```java
  if (this.aiRotationActive) return true;
  ```
- `updatePitch()` (L60-L83)：在 L63 的 `UNSET_ROTATION` 检查之后立即插入同样一行。

返回 `true` 与 UNSET 早返回语义一致（"本 tick 无更多工作"），且**完全跳过** `addPendingStep`，AI 注入的 pendingDelta 原封不动保留给 `applyPendingMovement` 消费。

**不要**修改 `FixedRotationController.updateYaw/updatePitch` (L69/L118)——其他消费者（Freecam、固定旋转）依赖 PID。

### 3. update() 的 rotationComplete 守卫

`update()` (L303-L319) 在 AI 模式下：updateYaw/updatePitch 都返回 true，若 pendingDelta 绝对值 < 1.0 会触发 `setRelativeMode(true)` (L314-L315)，这会干扰 AI 模式的 controller 状态。

在 L314 的 `if (!this.relativeMode && rotationComplete && ...)` 之前加守卫：
```java
if (this.aiRotationActive) {
    this.setComplete(false);
    return;
}
```
即 AI 模式下跳过 relativeMode 切换，保持 controller active 等 `applyPendingMovement` 消费 pendingDelta。

### 4. 新增 `applyAiRotationDelta(float, float)` 方法

在 `setTargetRotation(float, float)` (L425-L429) 之后添加：
```java
public void applyAiRotationDelta(float yawDelta, float pitchDelta) {
    this.aiRotationActive = true;
    this.target = null;
    float conversion = 1.0f / (this.getMouseScale() * 0.15f);
    this.pendingYawDelta = yawDelta * conversion;
    this.pendingPitchDelta = pitchDelta * conversion;
    this.setComplete(false);
}
```

**符号约定**：pitch 无需符号反转。已验证 `updatePitch()` L68 的 `currentPitch - (float)(int)(-pendingPitchDelta) * mouseScale * 0.15f` 展开后等价于 `currentPitch + pendingPitchDelta * mouseScale * 0.15f`，即正 pendingPitchDelta → pitch 增大（向下看）。与现有 `setTargetRotation` 路径下 `aiPitch = managedPitch + output[1]*mult`（正 output → pitch 增大）方向一致。

**精度**：`pendingYawDelta` 是 float，但 `applyPendingMovement` 以 `(int)` 截断应用（`MouseRotationController` L185-L186）。小 delta（< mouseScale*0.15 度）会被截断为 0。这与 LBNG 的离散 mouse delta 模型一致，可接受。剩余小数会累积到下一 tick（L211-L212）。

### 5. 新增 `clearAiRotationMode()` 方法

紧跟 `applyAiRotationDelta` 之后：
```java
public void clearAiRotationMode() {
    this.aiRotationActive = false;
    this.pendingYawDelta = 0.0f;
    this.pendingPitchDelta = 0.0f;
}
```
清零 pendingDelta 防止 AI 残留值污染下一 tick 的 PID `predictedYaw/predictedPitch` 计算。

### 6. 修改 SilentAura.updateAimAi()

文件：`src/main/java/gg/vape/module/combat/SilentAura.java`，方法在 L793-L839。

**L830 fallback 分支前**（model 未加载或推理失败时）插入：
```java
this.rotationController.clearAiRotationMode();
this.rotationController.setTargetRotation(managedYaw, managedPitch);
return;
```
确保 fallback 走 Normal-style 绝对目标，且 flag 已清。

**L834-L838 替换**：
```java
// 删除：
// float aiYaw = managedYaw + output[0] * yawMultiplier;
// float aiPitch = managedPitch + output[1] * pitchMultiplier;
// this.rotationController.setTargetRotation(aiYaw, aiPitch);

// 替换为：
float yawMultiplier = ((Number) this.aiYawMultiplier.getValue()).floatValue();
float pitchMultiplier = ((Number) this.aiPitchMultiplier.getValue()).floatValue();
this.rotationController.applyAiRotationDelta(
        output[0] * yawMultiplier,
        output[1] * pitchMultiplier);
```

注意：传入的是 **delta**（output * multiplier），不是绝对角度（managedYaw + output * multiplier）。

### 7. SilentAura Normal 分支调用清理

L729 的 `else` 块开头（PID 逻辑之前）插入：
```java
this.rotationController.clearAiRotationMode();
```
确保从 AI 切回 Normal 时 flag 和 pendingDelta 被清零，PID 从干净状态开始。

### 8. resetTargeting() 调用清理

`resetTargeting()` (L884-L909) 在 L896 的 `if (this.rotationController != null && this.isControllingRotation())` 块内、`releaseController` 之前插入：
```java
this.rotationController.clearAiRotationMode();
```
防止 target 丢失时 AI flag 卡住。

### 9. onDisable() 调用清理

`onDisable()` (L927-L938) 开头（在清理 controller 之前）插入同样的 null-guarded 调用：
```java
if (this.rotationController != null) {
    this.rotationController.clearAiRotationMode();
}
```

## 验证

1. **编译**：`f:\openvape\newscore\gradlew.bat compileJava` 必须 BUILD SUCCESSFUL。新增的是 AdaptiveRotationController 上的 public 方法，无 API 破坏。
2. **AI 模式语义验证**（静态推理）：
   - AI 模式每 tick：`applyAiRotationDelta` 设 pendingDelta + flag=true → `update()` 调用 updateYaw/updatePitch 早返回 → `applyPendingMovement` 消费 pendingDelta → 实际旋转 = currentYaw + (int)pendingDelta * mouseScale * 0.15
   - 等价于：actualYawDelta ≈ output[0] * yawMultiplier（受 (int) 截断）
   - 与 LBNG `currentRotation + output * multiplier` 语义对齐
3. **Normal 模式不受影响**：flag 默认 false，updateYaw/updatePitch 走原 PID 路径，getSpeed() 的 lognormal+aimSpeed 完全保留。
4. **模式切换安全**：AI→Normal 时 `clearAiRotationMode()` 清零 flag + pendingDelta；Normal→AI 时 `applyAiRotationDelta` 设 flag。
5. **target 丢失安全**：resetTargeting() 调用 clearAiRotationMode。

## 风险

- **Pitch 符号**：已验证与现有 setTargetRotation 路径方向一致。若模型 output 方向本来就错，两条路径错得一样。
- **(int) 截断**：小 delta 被截断为 0。LBNG 同样是离散 mouse delta 模型，可接受。剩余小数累积。
- **toggle-off 中途**：clearAiRotationMode 在 resetTargeting/onDisable 调用，pendingDelta 被清零，PID 不会用到陈旧 AI 值。
- **其他 controller 消费者**：flag 默认 false，Freecam 等不受影响。
