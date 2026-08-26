package gg.vape.mapping.mappings;

import gg.vape.mapping.MappedClasses;
import gg.vape.mapping.Mapping;
import gg.vape.mapping.MappingField;
import gg.vape.mapping.MappingMethod;
import gg.vape.mapping.mappings.MPacketIdFactory;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.wrapper.impl.ForgeVersion;

public class MCPacketEntityAction
extends Mapping {
    private MappingMethod actionIdConstructor;
    private MappingMethod actionConstructor;
    // C0BPacketEntityAction.action: 1.8.9 是 Action 枚举，1.16+ 是 CPacketEntityAction.Action 枚举。
    // 用于读取已发出包的 action 字段，例如桥模块需要拦截 START_SNEAKING。
    private MappingField actionField;

    public MCPacketEntityAction() {
        this(MPacketIdFactory.getPacketMappingControlFlowState());
    }

    private MCPacketEntityAction(GuiComponent[] controlFlowState) {
        super(MappedClasses.Dj);
        if (controlFlowState != null) {
            this.actionField = this.J("action", true, MappedClasses.Do);
            if (ForgeVersion.MC_1_7_10.Y()) {
                this.actionConstructor = this.Y("<init>", false, Void.TYPE, new Class[]{MappedClasses.zc, MappedClasses.Do});
            } else {
                this.actionIdConstructor = this.Y("<init>", false, Void.TYPE, new Class[]{MappedClasses.zc, Integer.TYPE});
            }
            return;
        }
        this.actionIdConstructor = this.Y("<init>", false, Void.TYPE, new Class[]{MappedClasses.zc, Integer.TYPE});
    }

    public Object getAction(Object packet) {
        return this.actionField == null ? null : this.actionField.getObject(packet);
    }

    public Object createPacket(Object entity, int actionId) {
        return this.actionIdConstructor.newInstance(entity, actionId);
    }

    public Object createPacket(Object entity, Object action) {
        return this.actionConstructor.newInstance(entity, action);
    }
}
