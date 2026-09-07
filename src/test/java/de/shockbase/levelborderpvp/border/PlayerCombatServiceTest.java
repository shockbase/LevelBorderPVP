package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.RoundEndCondition;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerCombatServiceTest {
    @Test void deathCancelsBreakoutBeforeSpectatorTransitionAndWinnerCheck() {
        RoundSession session = mock(RoundSession.class);
        when(session.state()).thenReturn(RoundState.ACTIVE);
        LevelBorderSettings settings = mock(LevelBorderSettings.class);
        when(settings.endCondition()).thenReturn(RoundEndCondition.ELIMINATION);
        RoundPlayerTracker players = new RoundPlayerTracker();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        players.activate(player);
        BreakoutService breakouts = mock(BreakoutService.class);
        PlayerStateService states = mock(PlayerStateService.class);
        RoundEndService rules = mock(RoundEndService.class);
        PlayerCombatService service = new PlayerCombatService(settings, session, players,
                mock(BorderNotifier.class), mock(KillBonusService.class), states, rules, breakouts);
        service.handlePlayerDeath(player, null);
        assertEquals(1, players.deaths(player));
        var order = inOrder(breakouts, states, rules);
        order.verify(breakouts).cancelBreakoutTask(player);
        order.verify(states).enterSpectator(player, BorderNotification.SPECTATOR);
        order.verify(rules).checkRoundEndAfterActivePlayerRemoval("service.end-reason-no-active-players");
    }
}
