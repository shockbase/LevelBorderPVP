package de.shockbase.levelborderpvp.data;

import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerBorderRepositoryTest {
    @TempDir Path directory;

    @Test void closeWritesLatestChangeAfterAnOlderBackgroundSnapshot() {
        PlayerBorderRepository repository = new PlayerBorderRepository(directory.toFile(), Logger.getAnonymousLogger(), mock(Messages.class));
        UUID id = UUID.randomUUID();
        PlayerBorderData first = new PlayerBorderData(id, UUID.randomUUID(), "world", 0.5, 64, 0.5, 1, 0, 11, null);
        repository.load();
        try {
            repository.save(first);
            assertFalse(Files.exists(directory.resolve("players.yml")), "A data mutation must not write synchronously");
            repository.save();
            repository.save(first.withMaxReachedLevel(9));
        } finally {
            repository.close();
        }
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(directory.resolve("players.yml").toFile());
        assertEquals(9, saved.getInt("players." + id + ".max-reached-level"));
    }

    @Test void atomicReplacementPreservesOtherFilesWhenItFails() throws Exception {
        Path target = Files.createDirectory(directory.resolve("players.yml"));
        Path blocker = Files.writeString(target.resolve("existing"), "original");
        assertThrows(java.io.IOException.class, () -> AtomicFileStore.write(target, "replacement"));
        assertEquals("original", Files.readString(blocker));
        try (var paths = Files.list(directory)) {
            assertEquals(1, paths.count(), "Temporary files must be cleaned up");
        }
    }
}
