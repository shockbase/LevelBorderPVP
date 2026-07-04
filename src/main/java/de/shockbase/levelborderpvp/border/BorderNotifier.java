package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.i18n.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class BorderNotifier {

    private static final Title.Times TITLE_TIMES = Title.Times.times(
            Duration.ofMillis(500L),
            Duration.ofSeconds(3L),
            Duration.ofMillis(700L)
    );

    private final BorderSizeFormatter sizeFormatter;
    private final Messages messages;

    public BorderNotifier(BorderSizeFormatter sizeFormatter, Messages messages) {
        this.sizeFormatter = sizeFormatter;
        this.messages = messages;
    }

    public void showLevelUp(Player player, double borderSize) {
        Component title = Component.text(messages.text("title.level-up"), NamedTextColor.GREEN);
        Component subtitle = Component.text(
                messages.text("subtitle.border-now", Messages.placeholder("size", sizeFormatter.format(borderSize))),
                NamedTextColor.WHITE
        );

        player.showTitle(Title.title(title, subtitle, TITLE_TIMES));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
    }

    public void showBorderChanged(Player player, double borderSize) {
        showStatus(player, "title.border-changed", "subtitle.border-now", borderSize);
    }

    public void showKillBonus(Player player, double borderSize) {
        showStatus(player, "title.kill-bonus", "subtitle.border-now", borderSize);
    }

    public void showJoined(Player player, double borderSize) {
        showStatus(player, "title.border-active", "subtitle.border-current", borderSize);
    }

    public void showRespawned(Player player, double borderSize) {
        showStatus(player, "title.border-active", "subtitle.border-set", borderSize);
    }

    public void showSpectator(Player player) {
        Component title = Component.text(messages.text("title.spectator"), NamedTextColor.YELLOW);
        Component subtitle = Component.text(messages.text("subtitle.spectator"), NamedTextColor.WHITE);

        player.showTitle(Title.title(title, subtitle, TITLE_TIMES));
    }

    private void showStatus(Player player, String titleKey, String subtitleKey, double borderSize) {
        Component title = Component.text(messages.text(titleKey), NamedTextColor.GREEN);
        Component subtitle = Component.text(
                messages.text(subtitleKey, Messages.placeholder("size", sizeFormatter.format(borderSize))),
                NamedTextColor.WHITE
        );

        player.showTitle(Title.title(title, subtitle, TITLE_TIMES));
    }
}
