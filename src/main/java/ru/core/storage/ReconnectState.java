package ru.core.storage;

import java.util.UUID;

public record ReconnectState(UUID uuid, String name, String target, long expires) {
}
