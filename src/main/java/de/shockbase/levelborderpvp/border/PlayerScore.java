package de.shockbase.levelborderpvp.border;

import org.bukkit.entity.Player;

record PlayerScore(Player player, double borderSize, int highestLevel, int kills, int deaths) {
}
