package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;

public final class BorderSizeCalculator {

    private final LevelBorderSettings settings;

    public BorderSizeCalculator(LevelBorderSettings settings) {
        this.settings = settings;
    }

    public double calculate(int level) {
        double size = settings.initialSizeBlocks() + (Math.max(0, level) * settings.growthPerLevelBlocks());

        if (settings.maxSizeBlocks() > 0.0D) {
            size = Math.min(size, settings.maxSizeBlocks());
        }

        return size;
    }
}
