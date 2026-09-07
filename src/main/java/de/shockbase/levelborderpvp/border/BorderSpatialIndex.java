package de.shockbase.levelborderpvp.border;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Bounding volume tree of border edges, including borders whose centers are far away. */
final class BorderSpatialIndex<T> {
    record Entry<T>(double minX, double minZ, double maxX, double maxZ, T value) {}
    private final Node<T> root;

    BorderSpatialIndex(List<Entry<T>> entries) {
        root = entries.isEmpty() ? null : build(new ArrayList<>(entries));
    }

    List<T> query(double x, double z, double radius) {
        Set<T> result = new LinkedHashSet<>();
        collect(root, x - radius, z - radius, x + radius, z + radius, result);
        return new ArrayList<>(result);
    }

    private Node<T> build(List<Entry<T>> entries) {
        double minX = Double.POSITIVE_INFINITY, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = maxX;
        for (Entry<T> entry : entries) {
            minX = Math.min(minX, entry.minX()); minZ = Math.min(minZ, entry.minZ());
            maxX = Math.max(maxX, entry.maxX()); maxZ = Math.max(maxZ, entry.maxZ());
        }
        if (entries.size() == 1) return new Node<>(minX, minZ, maxX, maxZ, entries.getFirst().value(), null, null);
        boolean splitX = maxX - minX >= maxZ - minZ;
        entries.sort(Comparator.comparingDouble(e -> splitX ? e.minX() + e.maxX() : e.minZ() + e.maxZ()));
        int middle = entries.size() / 2;
        return new Node<>(minX, minZ, maxX, maxZ, null,
                build(new ArrayList<>(entries.subList(0, middle))),
                build(new ArrayList<>(entries.subList(middle, entries.size()))));
    }

    private void collect(Node<T> node, double minX, double minZ, double maxX, double maxZ, Set<T> result) {
        if (node == null || node.maxX < minX || node.minX > maxX || node.maxZ < minZ || node.minZ > maxZ) return;
        if (node.left == null) { result.add(node.value); return; }
        collect(node.left, minX, minZ, maxX, maxZ, result);
        collect(node.right, minX, minZ, maxX, maxZ, result);
    }

    private record Node<T>(double minX, double minZ, double maxX, double maxZ, T value, Node<T> left, Node<T> right) {}
}
