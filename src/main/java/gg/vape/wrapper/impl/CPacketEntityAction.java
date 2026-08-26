package gg.vape.wrapper.impl;

public class CPacketEntityAction
extends Packet {
    public static CPacketEntityAction create(Entity entity, CPacketEntityActionAction action) {
        return new CPacketEntityAction(CPacketEntityAction.vapeInstance.getMappingsMapperCompat().Y.createPacket(entity.getObject(), action.getObject()));
    }

    public CPacketEntityAction(Object handle) {
        super(handle);
    }

    public static CPacketEntityAction create(Entity entity, int actionId) {
        return new CPacketEntityAction(CPacketEntityAction.vapeInstance.getMappingsMapperCompat().Y.createPacket(entity.getObject(), actionId));
    }

    /**
     * 返回包内的 action 枚举实例，可与 {@link CPacketEntityActionAction#startSneaking()} 等
     * 静态工厂返回的对象用 equals 比较以判断具体动作。
     */
    public Object getAction() {
        return CPacketEntityAction.vapeInstance.getMappingsMapperCompat().Y.getAction(this.getObject());
    }
}
