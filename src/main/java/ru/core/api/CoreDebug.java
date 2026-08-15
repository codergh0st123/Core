package ru.core.api;

import ru.core.debug.DebugManager;

import java.util.Locale;

public final class CoreDebug {

    private static volatile DebugManager manager;

    private CoreDebug() {
    }

    public static void register(DebugManager debugManager) {
        manager = debugManager;
    }

    public static void unregister(DebugManager debugManager) {
        if (manager == debugManager) {
            manager = null;
        }
    }

    public static void log(String source, String message) {
        DebugManager debugManager = manager;
        if (debugManager == null || source == null || message == null) {
            return;
        }
        debugManager.log(source.toUpperCase(Locale.ROOT), message);
    }
}
