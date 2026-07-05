package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;

public final class BorderSizeCalculator {

    private final LevelBorderSettings settings;

    public BorderSizeCalculator(LevelBorderSettings settings) {
        this.settings = settings;
    }

    public double calculate(int level) {
        double size = wholeBlockSize(settings.initialSizeBlocks() + (Math.max(0, level) * settings.growthPerLevelBlocks()));

        if (settings.maxSizeBlocks() > 0.0D) {
            size = Math.min(size, wholeBlockSize(settings.maxSizeBlocks()));
        }

        return size;
    }

    private double wholeBlockSize(double size) {
        long roundedSize = (long) Math.ceil(Math.max(1.0D, size));
        if (settings.centerAtBlockCenter() && roundedSize % 2L == 0L) {
            roundedSize++;
        }
        return roundedSize;
    }
}
