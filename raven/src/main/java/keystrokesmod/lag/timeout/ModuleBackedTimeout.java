package keystrokesmod.lag.timeout;

import keystrokesmod.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ModuleBackedTimeout extends AbstractTimeout {

    private final @NotNull Module module;
    private final @Nullable AbstractTimeout secondaryTimeout;
    private boolean hasModuleDisabled = false;

    public ModuleBackedTimeout(final @NotNull Module module, final @Nullable AbstractTimeout secondaryTimeout) {
        this.module = module;
        this.secondaryTimeout = secondaryTimeout;

        if (!module.isEnabled()) {
            hasModuleDisabled = true;
        }
    }

    public ModuleBackedTimeout(final @NotNull Module module) {
        this(module, null);
    }

    @Override
    protected boolean shouldHaveTimedOut() {
        if (!module.isEnabled()) {
            hasModuleDisabled = true;
        }

        if (hasModuleDisabled) {
            return true;
        }

        return secondaryTimeout != null && secondaryTimeout.isTimedOut();
    }
}