package dev.anvilcraft.base.wenyan.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * 文言运行时中的“期约”。
 *
 * <p>该实现采用单次决议模型：一个期约只能从 {@link State#PENDING} 进入
 * {@link State#FULFILLED} 或 {@link State#REJECTED} 其中之一，后续重复 settle
 * 会被忽略。回调在当前线程同步触发，但状态迁移本身是线程安全的。</p>
 */
public final class WenyanPromise {
    public enum State {
        PENDING,
        FULFILLED,
        REJECTED
    }

    private final List<Continuation> continuations = new ArrayList<>();
    private State state = State.PENDING;
    private @Nullable WenyanValue value;

    // 繼以
    public WenyanPromise then(WenyanCallable callable) {
        Objects.requireNonNull(callable, "callable");
        WenyanPromise next = new WenyanPromise();
        WenyanValue settledValue;
        synchronized (this) {
            if (this.state == State.PENDING) {
                this.continuations.add(new Continuation(callable, null, next));
                return next;
            }
            settledValue = this.valueOrNullUnsafe();
        }

        if (this.state == State.FULFILLED) {
            this.invokeContinuation(new Continuation(callable, null, next), settledValue);
        } else {
            next.reject(settledValue);
        }
        return next;
    }

    // 攝錯
    public WenyanPromise crash(WenyanCallable callable) {
        Objects.requireNonNull(callable, "callable");
        WenyanPromise next = new WenyanPromise();
        WenyanValue settledValue;
        synchronized (this) {
            if (this.state == State.PENDING) {
                this.continuations.add(new Continuation(null, callable, next));
                return next;
            }
            settledValue = this.valueOrNullUnsafe();
        }

        if (this.state == State.REJECTED) {
            this.invokeContinuation(new Continuation(null, callable, next), settledValue);
        } else {
            next.resolve(settledValue);
        }
        return next;
    }

    // 立成
    public void resolve(WenyanValue data) {
        WenyanValue normalized = normalize(data);
        if (normalized.type() == WenyanValue.Type.PROMISE) {
            WenyanPromise promise = normalized.asPromise();
            if (promise == this) {
                this.reject(WenyanValue.text("Promise cannot resolve itself"));
                return;
            }
            promise.then(value -> {
                this.resolve(singleArgument(value));
                return WenyanValue.NULL;
            });
            promise.crash(reason -> {
                this.reject(singleArgument(reason));
                return WenyanValue.NULL;
            });
            return;
        }
        this.settle(State.FULFILLED, normalized);
    }

    // 即拒
    public void reject(WenyanValue data) {
        this.settle(State.REJECTED, normalize(data));
    }

    public synchronized State state() {
        return this.state;
    }

    public synchronized boolean isPending() {
        return this.state == State.PENDING;
    }

    public synchronized boolean isFulfilled() {
        return this.state == State.FULFILLED;
    }

    public synchronized boolean isRejected() {
        return this.state == State.REJECTED;
    }

    public synchronized @Nullable WenyanValue valueOrNull() {
        return this.state == State.FULFILLED ? this.value : null;
    }

    public synchronized @Nullable WenyanValue reasonOrNull() {
        return this.state == State.REJECTED ? this.value : null;
    }

    public WenyanValue await() {
        synchronized (this) {
            while (this.state == State.PENDING) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Promise await interrupted", e);
                }
            }
            return this.valueOrNullUnsafe();
        }
    }

    public static WenyanPromise resolved(WenyanValue value) {
        WenyanPromise promise = new WenyanPromise();
        promise.resolve(value);
        return promise;
    }

    public static WenyanPromise rejected(WenyanValue reason) {
        WenyanPromise promise = new WenyanPromise();
        promise.reject(reason);
        return promise;
    }

    @Override
    public String toString() {
        return switch (this.state()) {
            case PENDING -> "<待...>";
            case FULFILLED, REJECTED -> {
                WenyanValue settled = this.valueOrNullUnsafe();
                yield settled.toString();
            }
        };
    }

    private void settle(State target, WenyanValue data) {
        List<Continuation> toRun;
        synchronized (this) {
            if (this.state != State.PENDING) {
                return;
            }
            this.state = target;
            this.value = data;
            toRun = List.copyOf(this.continuations);
            this.continuations.clear();
            this.notifyAll();
        }

        for (Continuation continuation : toRun) {
            if (target == State.FULFILLED) {
                if (continuation.onFulfilled() == null) {
                    continuation.next().resolve(data);
                } else {
                    this.invokeContinuation(continuation, data);
                }
            } else if (continuation.onRejected() == null) {
                continuation.next().reject(data);
            } else {
                this.invokeContinuation(continuation, data);
            }
        }
    }

    private void invokeContinuation(Continuation continuation, WenyanValue data) {
        WenyanCallable callable = this.state == State.FULFILLED ? continuation.onFulfilled() : continuation.onRejected();
        if (callable == null) {
            if (this.state == State.FULFILLED) {
                continuation.next().resolve(data);
            } else {
                continuation.next().reject(data);
            }
            return;
        }

        try {
            WenyanValue returned = normalize(callable.call(List.of(data)));
            if (returned.type() == WenyanValue.Type.PROMISE) {
                WenyanPromise promise = returned.asPromise();
                promise.then(value -> {
                    continuation.next().resolve(singleArgument(value));
                    return WenyanValue.NULL;
                });
                promise.crash(reason -> {
                    continuation.next().reject(singleArgument(reason));
                    return WenyanValue.NULL;
                });
                return;
            }
            continuation.next().resolve(returned);
        } catch (Throwable throwable) {
            continuation.next().reject(errorToValue(throwable));
        }
    }

    private synchronized WenyanValue valueOrNullUnsafe() {
        return this.value == null ? WenyanValue.NULL : this.value;
    }

    private static WenyanValue normalize(@Nullable WenyanValue value) {
        return value == null ? WenyanValue.NULL : value;
    }

    private static WenyanValue errorToValue(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return WenyanValue.text(message);
    }

    private static WenyanValue singleArgument(List<WenyanValue> args) {
        return args.isEmpty() ? WenyanValue.NULL : normalize(args.getFirst());
    }

    private record Continuation(@Nullable WenyanCallable onFulfilled,
                                @Nullable WenyanCallable onRejected,
                                WenyanPromise next) {
    }
}
