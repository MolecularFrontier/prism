package tech.molecules.structurized.prismlite.swing.workspace.profile;

import java.awt.Color;
import java.util.Locale;

public final class ScoreDisplayService {
    private static final Color LOW = new Color(197, 67, 63);
    private static final Color MID = new Color(222, 178, 55);
    private static final Color HIGH = new Color(54, 145, 91);

    private ScoreDisplayService() {
    }

    public static Color scoreColor(double score) {
        double value = Math.max(0.0, Math.min(1.0, score));
        return value <= 0.5 ? blend(LOW, MID, value * 2.0) : blend(MID, HIGH, (value - 0.5) * 2.0);
    }

    public static Color softScoreColor(double score) {
        return blend(Color.WHITE, scoreColor(score), 0.28);
    }

    public static String format(Double score) {
        return score == null || !Double.isFinite(score) ? "missing" : String.format(Locale.US, "%.3f", score);
    }

    private static Color blend(Color from, Color to, double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        return new Color(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * f),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * f),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * f));
    }
}
