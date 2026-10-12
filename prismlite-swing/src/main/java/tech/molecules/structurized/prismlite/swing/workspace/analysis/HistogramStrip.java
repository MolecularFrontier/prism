package tech.molecules.structurized.prismlite.swing.workspace.analysis;

import tech.molecules.structurized.prism.engine.HistogramBin;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

public final class HistogramStrip extends JComponent {
    private List<HistogramBin> bins = List.of();
    private Double draftMinimum;
    private Double draftMaximum;
    private boolean loading;

    public HistogramStrip() {
        setPreferredSize(new Dimension(220, 70));
        setMinimumSize(new Dimension(160, 48));
    }

    public void setHistogram(List<HistogramBin> bins) {
        this.bins = bins == null ? List.of() : List.copyOf(bins);
        repaint();
    }

    public void setDraftRange(Double draftMinimum, Double draftMaximum) {
        this.draftMinimum = draftMinimum;
        this.draftMaximum = draftMaximum;
        repaint();
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int width = getWidth();
            int height = getHeight();
            g.setColor(new Color(248, 248, 248));
            g.fillRect(0, 0, width, height);
            if (loading) {
                g.setColor(Color.GRAY);
                g.drawString("Loading...", 8, height / 2);
                return;
            }
            if (bins.isEmpty()) {
                g.setColor(Color.GRAY);
                g.drawString("No numeric values", 8, height / 2);
                return;
            }
            long maxCount = bins.stream().mapToLong(HistogramBin::count).max().orElse(1L);
            double min = bins.getFirst().minimum();
            double max = bins.getLast().maximum();
            int plotTop = 8;
            int plotBottom = height - 18;
            int plotHeight = Math.max(1, plotBottom - plotTop);
            int binWidth = Math.max(1, width / bins.size());
            for (int i = 0; i < bins.size(); i++) {
                HistogramBin bin = bins.get(i);
                int barHeight = (int) Math.round((bin.count() / (double) maxCount) * plotHeight);
                int x = i * binWidth;
                int y = plotBottom - barHeight;
                g.setColor(new Color(150, 169, 190));
                g.fillRect(x, y, Math.max(1, binWidth - 1), barHeight);
            }
            if (draftMinimum != null || draftMaximum != null) {
                double leftValue = draftMinimum == null ? min : draftMinimum;
                double rightValue = draftMaximum == null ? max : draftMaximum;
                int left = valueToX(leftValue, min, max, width);
                int right = valueToX(rightValue, min, max, width);
                g.setColor(new Color(48, 108, 181, 80));
                g.fillRect(Math.min(left, right), plotTop, Math.abs(right - left), plotHeight);
                g.setColor(new Color(48, 108, 181));
                g.drawLine(left, plotTop, left, plotBottom);
                g.drawLine(right, plotTop, right, plotBottom);
            }
            g.setColor(Color.DARK_GRAY);
            g.drawLine(0, plotBottom, width, plotBottom);
        } finally {
            g.dispose();
        }
    }
    private static int valueToX(double value, double min, double max, int width) {
        if (Double.compare(min, max) == 0) {
            return width / 2;
        }
        double fraction = (value - min) / (max - min);
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        return (int) Math.round(fraction * width);
    }
}
