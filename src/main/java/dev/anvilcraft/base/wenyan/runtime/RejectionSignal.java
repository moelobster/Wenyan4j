package dev.anvilcraft.base.wenyan.runtime;

import java.util.Objects;

public class RejectionSignal extends RuntimeException {
    private final WenyanValue reason;

    public RejectionSignal(WenyanValue reason) {
        super(Objects.toString(reason));
        this.reason = reason;
    }

    public WenyanValue reason() {
        return this.reason;
    }
}
