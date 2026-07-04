package de.shockbase.levelborderpvp.border;

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

    public BorderNotifier(BorderSizeFormatter sizeFormatter) {
        this.sizeFormatter = sizeFormatter;
    }

    public void showLevelUp(Player player, double borderSize) {
        Component title = Component.text("LEVEL UP!", NamedTextColor.GREEN);
        Component subtitle = Component.text(
                "Deine Border ist jetzt " + sizeFormatter.format(borderSize) + " gro\u00df.",
                NamedTextColor.WHITE
        );

        player.showTitle(Title.title(title, subtitle, TITLE_TIMES));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
    }

    public void showBorderChanged(Player player, double borderSize) {
        showStatus(player, "BORDER AKTUALISIERT", "Deine Border ist jetzt " + sizeFormatter.format(borderSize) + " gro\u00df.");
    }

    public void showKillBonus(Player player, double borderSize) {
        showStatus(player, "BORDER EROBERT", "Deine Border ist jetzt " + sizeFormatter.format(borderSize) + " gro\u00df.");
    }

    public void showJoined(Player player, double borderSize) {
        showStatus(player, "BORDER AKTIV", "Deine Border ist " + sizeFormatter.format(borderSize) + " gro\u00df.");
    }

    public void showRespawned(Player player, double borderSize) {
        showStatus(player, "BORDER AKTIV", "Deine Border wurde auf " + sizeFormatter.format(borderSize) + " gesetzt.");
    }

    private void showStatus(Player player, String titleText, String subtitleText) {
        Component title = Component.text(titleText, NamedTextColor.GREEN);
        Component subtitle = Component.text(subtitleText, NamedTextColor.WHITE);

        player.showTitle(Title.title(title, subtitle, TITLE_TIMES));
    }
}
