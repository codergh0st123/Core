package ru.core.packet.protocollib;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import ru.core.Core;
import ru.core.text.Colors;

import java.util.List;

public final class VanillaFeedbackPackets {

    private final Core plugin;
    private ProtocolManager manager;
    private PacketListener listener;

    public VanillaFeedbackPackets(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.configs().config().getBoolean("VANILLA-FEEDBACK.ENABLED", true)) {
            return;
        }
        manager = ProtocolLibrary.getProtocolManager();
        listener = new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.SYSTEM_CHAT, PacketType.Play.Server.DISGUISED_CHAT, PacketType.Play.Server.CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                format(event);
            }
        };
        manager.addPacketListener(listener);
    }

    public void stop() {
        if (manager != null && listener != null) {
            manager.removePacketListener(listener);
        }
        listener = null;
        manager = null;
    }

    private void format(PacketEvent event) {
        if (event.isPlayerTemporary() || event.getPacket().getChatComponents().size() == 0) {
            return;
        }
        WrappedChatComponent original = event.getPacket().getChatComponents().read(0);
        if (original == null) {
            return;
        }
        String json = original.getJson();
        Component feedback = GsonComponentSerializer.gson().deserialize(json);
        if (json.contains("\"translate\":\"chat.type.announcement\"")) {
            feedback = announcement(feedback);
        } else if (!json.contains("\"translate\":\"commands.")) {
            return;
        }
        String prefix = plugin.configs().config().getString("VANILLA-FEEDBACK.PREFIX", "&8[&cServer&8] &f");
        Component formatted = LegacyComponentSerializer.legacySection().deserialize(Colors.apply(prefix))
                .append(Component.space())
                .append(feedback);
        event.getPacket().getChatComponents().write(0,
                WrappedChatComponent.fromJson(GsonComponentSerializer.gson().serialize(formatted)));
    }

    private Component announcement(Component component) {
        if (!(component instanceof TranslatableComponent)) {
            return component;
        }
        TranslatableComponent announcement = (TranslatableComponent) component;
        List<TranslationArgument> arguments = announcement.arguments();
        if (!announcement.key().equals("chat.type.announcement") || arguments.size() < 2) {
            return component;
        }
        Component sender = component(arguments.get(0));
        if (!PlainTextComponentSerializer.plainText().serialize(sender).equalsIgnoreCase("Server")) {
            return component;
        }
        return component(arguments.get(1));
    }

    private Component component(TranslationArgument argument) {
        Object value = argument.value();
        return value instanceof Component ? (Component) value : Component.text(String.valueOf(value));
    }
}
