package dev.anvilcraft.base.wenyan.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WenyanPromiseTest {
    @Test
    void resolveRunsThenHandlerAndChainsReturnValue() {
        WenyanPromise promise = new WenyanPromise();
        AtomicReference<WenyanValue> seen = new AtomicReference<>();

        WenyanPromise next = promise.then(args -> {
            seen.set(args.getFirst());
            return WenyanValue.number(2);
        });

        promise.resolve(WenyanValue.number(1));

        assertTrue(promise.isFulfilled());
        assertEquals(WenyanValue.number(1), promise.valueOrNull());
        assertEquals(WenyanValue.number(1), seen.get());
        assertTrue(next.isFulfilled());
        assertEquals(WenyanValue.number(2), next.valueOrNull());
    }

    @Test
    void rejectRunsCrashHandlerAndRecoversPromise() {
        WenyanPromise promise = new WenyanPromise();
        AtomicReference<WenyanValue> seen = new AtomicReference<>();

        WenyanPromise next = promise.crash(args -> {
            seen.set(args.getFirst());
            return WenyanValue.text("已復原");
        });

        promise.reject(WenyanValue.text("失敗"));

        assertTrue(promise.isRejected());
        assertEquals(WenyanValue.text("失敗"), promise.reasonOrNull());
        assertEquals(WenyanValue.text("失敗"), seen.get());
        assertTrue(next.isFulfilled());
        assertEquals(WenyanValue.text("已復原"), next.valueOrNull());
    }

    @Test
    void handlersRegisteredAfterSettlementRunImmediately() {
        WenyanPromise promise = WenyanPromise.resolved(WenyanValue.number(3));
        AtomicReference<WenyanValue> seen = new AtomicReference<>();

        WenyanPromise next = promise.then(args -> {
            seen.set(args.getFirst());
            return args.getFirst();
        });

        assertEquals(WenyanValue.number(3), seen.get());
        assertTrue(next.isFulfilled());
        assertEquals(WenyanValue.number(3), next.valueOrNull());
    }

    @Test
    void promiseReturnValueIsFlattenedInThenChain() {
        WenyanPromise source = WenyanPromise.resolved(WenyanValue.number(1));
        WenyanPromise nested = new WenyanPromise();

        WenyanPromise chained = source.then(args -> WenyanValue.promise(nested));
        assertTrue(chained.isPending());

        nested.resolve(WenyanValue.number(9));

        assertTrue(chained.isFulfilled());
        assertEquals(WenyanValue.number(9), chained.valueOrNull());
    }

    @Test
    void resolvingWithPromiseAdoptsItsFinalState() {
        WenyanPromise outer = new WenyanPromise();
        WenyanPromise inner = new WenyanPromise();

        outer.resolve(WenyanValue.promise(inner));
        assertTrue(outer.isPending());

        inner.resolve(WenyanValue.text("成"));

        assertTrue(outer.isFulfilled());
        assertEquals(WenyanValue.text("成"), outer.valueOrNull());
    }

    @Test
    void awaitBlocksUntilPromiseSettles() {
        WenyanPromise promise = new WenyanPromise();
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            promise.resolve(WenyanValue.number(5));
        });
        thread.start();

        assertEquals(WenyanValue.number(5), promise.await());
        assertTrue(promise.isFulfilled());
    }

    @Test
    void secondSettlementIsIgnored() {
        WenyanPromise promise = new WenyanPromise();

        promise.resolve(WenyanValue.number(7));
        promise.reject(WenyanValue.text("錯"));

        assertTrue(promise.isFulfilled());
        assertEquals(WenyanValue.number(7), promise.valueOrNull());
    }

    @Test
    void wenyanValueDisplaysPromiseState() {
        WenyanPromise promise = new WenyanPromise();
        WenyanValue value = WenyanValue.promise(promise);

        assertEquals("<待...>", value.toDisplayString());

        promise.resolve(WenyanValue.number(11));

        assertEquals("十一", value.toDisplayString());
        assertEquals(promise, value.asPromise());
    }
}

