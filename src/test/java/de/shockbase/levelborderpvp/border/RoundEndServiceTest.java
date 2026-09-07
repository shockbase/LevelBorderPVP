package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.RoundEndCondition;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;

class RoundEndServiceTest {
    @Test void reschedulingUsesTheRemainingRoundTime() {
        LevelBorderSettings settings = mock(LevelBorderSettings.class);
        RoundSession session = mock(RoundSession.class);
        when(settings.endCondition()).thenReturn(RoundEndCondition.TIMED_SCORE);
        when(settings.roundDurationMinutes()).thenReturn(60);
        when(session.elapsedTicks()).thenReturn(40L * 60 * 20);
        RoundEndService service = new RoundEndService(mock(Plugin.class), settings, session,
                new RoundPlayerTracker(), mock(RoundScoreTracker.class), mock(RoundResultService.class));
        service.scheduleRoundEnd();
        verify(session).later(any(Runnable.class), eq(20L * 60 * 20));
    }

    @Test void reconnectingOfflineParticipantPreventsPrematureWinner() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        Player online = player(), offline = player();
        when(server.getOnlinePlayers()).thenAnswer(ignored -> List.of(online));
        RoundPlayerTracker players = new RoundPlayerTracker();
        players.activate(online);
        players.activate(offline);
        RoundSession session = mock(RoundSession.class);
        when(session.state()).thenReturn(RoundState.ACTIVE);
        LevelBorderSettings settings = mock(LevelBorderSettings.class);
        when(settings.endCondition()).thenReturn(RoundEndCondition.ELIMINATION);
        RoundResultService results = mock(RoundResultService.class);
        RoundEndService service = new RoundEndService(plugin, settings, session, players, mock(RoundScoreTracker.class), results);
        service.checkEliminationWinner();
        verifyNoInteractions(results);
        players.markSpectator(offline);
        service.checkRoundEndAfterActivePlayerRemoval("empty");
        verify(results).finishRoundWithWinner(online, "service.end-reason-elimination");
    }

    @Test void inactiveRoundIgnoresTargetEvents() {
        LevelBorderSettings settings = mock(LevelBorderSettings.class);
        RoundSession session = mock(RoundSession.class);
        when(session.state()).thenReturn(RoundState.IDLE);
        RoundResultService results = mock(RoundResultService.class);
        RoundEndService service = new RoundEndService(mock(Plugin.class), settings, session,
                new RoundPlayerTracker(), mock(RoundScoreTracker.class), results);
        service.checkTargetLevel(player(), 100);
        service.checkTargetBorder(player(), 1000);
        service.checkEliminationWinner();
        verifyNoInteractions(results);
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
