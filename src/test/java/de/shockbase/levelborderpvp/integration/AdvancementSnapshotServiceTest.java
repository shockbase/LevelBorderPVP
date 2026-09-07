package de.shockbase.levelborderpvp.integration;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdvancementSnapshotServiceTest {
    @TempDir Path directory;

    private record Fixture(AdvancementSnapshotService service, Player player, AdvancementProgress progress) {}

    private Fixture fixture() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        LevelBorderSettings settings = mock(LevelBorderSettings.class);
        Messages messages = mock(Messages.class);
        Player player = mock(Player.class);
        Advancement advancement = mock(Advancement.class);
        AdvancementProgress progress = mock(AdvancementProgress.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(settings.advancementBonusEnabled()).thenReturn(true);
        when(settings.advancementExcludedPrefixes()).thenReturn(List.of());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Test");
        when(advancement.getKey()).thenReturn(NamespacedKey.minecraft("story/root"));
        when(server.advancementIterator()).thenAnswer(ignored -> List.of(advancement).iterator());
        when(server.getOnlinePlayers()).thenAnswer(ignored -> List.of(player));
        when(server.getAdvancement(any())).thenReturn(advancement);
        when(player.getAdvancementProgress(advancement)).thenReturn(progress);
        when(progress.getAwardedCriteria()).thenReturn(List.of("criterion"));
        when(progress.getRemainingCriteria()).thenReturn(List.of("criterion"));
        return new Fixture(new AdvancementSnapshotService(plugin, settings, messages), player, progress);
    }

    @Test void backupExistsBeforeFirstRevocationAndRejoinDoesNotRestore() throws Exception {
        Fixture f = fixture();
        doAnswer(ignored -> {
            assertTrue(Files.readString(directory.resolve("advancements.yml")).contains("criterion"));
            return true;
        }).when(f.progress()).revokeCriteria("criterion");
        assertTrue(f.service().beginRound(List.of(f.player())));
        clearInvocations(f.progress());
        f.service().restoreIfPending(f.player());
        verifyNoInteractions(f.progress());
        f.service().restoreOnlinePlayers();
        verify(f.progress()).awardCriteria("criterion");
    }

    @Test void failedBackupDoesNotRevokeAnything() throws Exception {
        Fixture f = fixture();
        Files.createDirectory(directory.resolve("advancements.yml"));
        Files.writeString(directory.resolve("advancements.yml/blocker"), "occupied");
        assertFalse(f.service().beginRound(List.of(f.player())));
        verify(f.progress(), never()).revokeCriteria(anyString());
    }
}
