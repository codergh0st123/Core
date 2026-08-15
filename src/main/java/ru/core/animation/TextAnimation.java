package ru.core.animation;

import java.util.List;

final class TextAnimation {

    private final long createdAtNanos;
    private final long changeIntervalNanos;
    private final List<String> texts;

    TextAnimation(long createdAtNanos, long changeIntervalMillis, List<String> texts) {
        this.createdAtNanos = createdAtNanos;
        this.changeIntervalNanos = Math.max(1L, changeIntervalMillis) * 1_000_000L;
        this.texts = List.copyOf(texts);
    }

    String text() {
        return textAt(System.nanoTime());
    }

    String textAt(long nowNanos) {
        long elapsed = Math.max(0L, nowNanos - createdAtNanos);
        long frame = elapsed / changeIntervalNanos;
        return texts.get((int) (frame % texts.size()));
    }
}
