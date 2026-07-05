package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.i18n.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class BorderNotifier {

    private static final double NUMBER_EPSILON = 0.000001D;
    private static final Title.Times TITLE_TIMES = Title.Times.times(
            Duration.ofMillis(500L),
            Duration.ofSeconds(3L),
            Duration.ofMillis(700L)
    );
    private static final Title.Times COUNTDOWN_TITLE_TIMES = Title.Times.times(
            Duration.ZERO,
            Duration.ofMillis(900L),
            Duration.ofMillis(100L)
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

    public void showSpreadOut(Player player, int countdownSeconds) {
        Component title = Component.text(messages.text("title.spread-out"), NamedTextColor.GOLD);
        Component subtitle = Component.text(
                messages.text("subtitle.spread-out", Messages.placeholder("seconds", countdownSeconds)),
                NamedTextColor.WHITE
        );

        player.showTitle(Title.title(title, subtitle, TITLE_TIMES));
    }

    public void showCountdown(Player player, int remainingSeconds) {
        Component title = Component.text(
                messages.text("title.countdown", Messages.placeholder("seconds", remainingSeconds)),
                NamedTextColor.RED
        );
        Component subtitle = Component.text(messages.text("subtitle.countdown"), NamedTextColor.WHITE);

        player.showTitle(Title.title(title, subtitle, COUNTDOWN_TITLE_TIMES));
    }

    public void showEliminated(Player player, String killerName) {
        Component title = Component.text(messages.text("title.eliminated"), NamedTextColor.RED);
        Component subtitle = Component.text(
                messages.text("subtitle.eliminated-by", Messages.placeholder("killer", killerName)),
                NamedTextColor.WHITE
        );

        player.showTitle(Title.title(title, subtitle, TITLE_TIMES));
    }

    public void showPlayerKill(Player player, String victimName, double radiusGainedBlocks) {
        Component title = Component.text(messages.text("title.player-kill"), NamedTextColor.GREEN);
        String subtitleKey = radiusGainedBlocks > NUMBER_EPSILON
                ? "subtitle.player-kill"
                : "subtitle.player-kill-no-bonus";
        Component subtitle = Component.text(
                messages.text(
                        subtitleKey,
                        Messages.placeholder("victim", victimName),
                        Messages.placeholder("radius", formatBlockAmount(radiusGainedBlocks))
                ),
                NamedTextColor.WHITE
        );

        player.showTitle(Title.title(title, subtitle, TITLE_TIMES));
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

    public void showRoundPlacement(
            Player player,
            int place,
            int kills,
            int deaths,
            int highestLevel,
            double borderSize
    ) {
        Component title = Component.text(
                messages.text("title.round-placement", Messages.placeholder("place", place)),
                NamedTextColor.GOLD
        );
        Component subtitle = Component.text(
                messages.text(
                        "subtitle.round-stats",
                        Messages.placeholder("kills", kills),
                        Messages.placeholder("deaths", deaths),
                        Messages.placeholder("level", highestLevel),
                        Messages.placeholder("border", sizeFormatter.format(borderSize))
                ),
                NamedTextColor.WHITE
        );

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

    private String formatBlockAmount(double blocks) {
        return formatNumber(blocks) + " " + messages.text("unit.blocks");
    }

    private String formatNumber(double value) {
        long wholeValue = Math.round(value);
        if (Math.abs(value - wholeValue) < NUMBER_EPSILON) {
            return Long.toString(wholeValue);
        }

        String valueText = String.format(java.util.Locale.ROOT, "%.2f", value);
        while (valueText.endsWith("0")) {
            valueText = valueText.substring(0, valueText.length() - 1);
        }
        if (valueText.endsWith(".")) {
            valueText = valueText.substring(0, valueText.length() - 1);
        }
        return valueText;
    }
}
