package de.oehme.personallevelborder.border;

import java.util.Locale;

public final class BorderSizeFormatter {

    private static final double BORDER_SIZE_EPSILON = 0.000001D;

    public String format(double borderSize) {
        long wholeSize = Math.round(borderSize);
        String sizeText;

        if (Math.abs(borderSize - wholeSize) < BORDER_SIZE_EPSILON) {
            sizeText = Long.toString(wholeSize);
        } else {
            sizeText = String.format(Locale.ROOT, "%.2f", borderSize);
            while (sizeText.endsWith("0")) {
                sizeText = sizeText.substring(0, sizeText.length() - 1);
            }
            if (sizeText.endsWith(".")) {
                sizeText = sizeText.substring(0, sizeText.length() - 1);
            }
        }

        return sizeText + "x" + sizeText;
    }
}
