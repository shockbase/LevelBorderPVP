package de.shockbase.levelborderpvp.border;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class BorderSpatialIndexTest {
    @Test void matchesBruteForceIncludingNegativeCoordinatesAndLongEdges() {
        Random random = new Random(42);
        List<BorderSpatialIndex.Entry<Integer>> edges = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            double x = random.nextDouble() * 10000 - 5000;
            double z = random.nextDouble() * 10000 - 5000;
            edges.add(new BorderSpatialIndex.Entry<>(x, z, x, z + random.nextDouble() * 10000, i));
        }
        BorderSpatialIndex<Integer> index = new BorderSpatialIndex<>(edges);
        for (int n = 0; n < 100; n++) {
            double x = random.nextDouble() * 10000 - 5000;
            double z = random.nextDouble() * 10000 - 5000;
            var expected = new HashSet<Integer>();
            for (var edge : edges) {
                if (edge.maxX() >= x - 64 && edge.minX() <= x + 64
                        && edge.maxZ() >= z - 64 && edge.minZ() <= z + 64) expected.add(edge.value());
            }
            assertEquals(expected, new HashSet<>(index.query(x, z, 64)));
        }
    }

    @Test void includesBoundaryAndDeduplicatesCorners() {
        var index = new BorderSpatialIndex<>(List.of(
                new BorderSpatialIndex.Entry<>(64, 0, 64, 10000, "a"),
                new BorderSpatialIndex.Entry<>(64, 0, 10000, 0, "a")));
        assertEquals(List.of("a"), index.query(0, 0, 64));
        assertTrue(index.query(0, 0, 63).isEmpty());
    }
}
