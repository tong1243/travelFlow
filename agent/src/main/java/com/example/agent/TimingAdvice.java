package com.example.agent;

import net.bytebuddy.asm.Advice;

public class TimingAdvice {

    @Advice.OnMethodEnter
    public static long onEnter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
            @Advice.Origin("#t.#m") String method,
            @Advice.Enter long startNanos,
            @Advice.Thrown Throwable throwable
    ) {
        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        if (throwable == null) {
            System.out.printf("[Agent] %s took %.3f ms%n", method, elapsedMs);
        } else {
            System.out.printf("[Agent] %s failed in %.3f ms (%s)%n",
                    method,
                    elapsedMs,
                    throwable.getClass().getSimpleName());
        }
    }
}
