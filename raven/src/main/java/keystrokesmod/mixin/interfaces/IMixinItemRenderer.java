package keystrokesmod.mixin.interfaces;

public interface IMixinItemRenderer {
    void setCancelUpdate(boolean cancel);

    void setCancelReset(boolean reset);

    boolean isRenderItemInUse();

    void setRenderItemInUse(boolean renderItemInUse);
}
