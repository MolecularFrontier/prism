package tech.molecules.structurized.prismlite.swing.workspace.views;

import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.engine.ScatterPlotViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

public final class ScatterPlotViewRenderer implements PrismSwingViewRenderer {
    private static final Color[] CATEGORY_COLORS = {
            new Color(51, 102, 204),
            new Color(220, 87, 43),
            new Color(80, 150, 83),
            new Color(145, 91, 165),
            new Color(44, 154, 183),
            new Color(204, 156, 45),
            new Color(122, 122, 122),
            new Color(197, 70, 124)
    };
    private static final int NUMERIC_BUCKETS = 5;
    private static final int HIT_RADIUS_PIXELS = 10;
    private static final int DRAG_THRESHOLD_PIXELS = 4;

    @Override
    public String viewType() {
        return ScatterPlotViewSpec.VIEW_TYPE;
    }

    @Override
    public JComponent createComponent(
            PrismViewRecord view,
            PrismLiteWorkspaceModel model,
            PrismLiteWorkspaceController controller,
            Runnable refresh
    ) {
        if (!(view.specification() instanceof ScatterPlotViewSpec spec)) {
            return message("Unsupported scatter-plot specification.");
        }
        PrismSession session = model.session();
        PrismColumn xColumn = session.table().column(spec.xColumnId());
        PrismColumn yColumn = session.table().column(spec.yColumnId());
        if (!isNumeric(xColumn) || !isNumeric(yColumn)) {
            return message("Scatter plot axes must be numeric.");
        }
        PrismColumn colorColumn = spec.colorColumnId() == null ? null : session.table().column(spec.colorColumnId());
        List<PointRow> points = points(session, spec, xColumn, yColumn, colorColumn);
        if (points.isEmpty()) {
            return message("No points to display.");
        }

        XYChart chart = new XYChartBuilder()
                .width(900)
                .height(640)
                .title(spec.title())
                .xAxisTitle(xColumn.schema().displayName())
                .yAxisTitle(yColumn.schema().displayName())
                .build();
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        chart.getStyler().setMarkerSize(7);
        chart.getStyler().setPlotGridLinesVisible(true);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(Color.WHITE);
        chart.getStyler().setXAxisMin(spec.xMin());
        chart.getStyler().setXAxisMax(spec.xMax());
        chart.getStyler().setYAxisMin(spec.yMin());
        chart.getStyler().setYAxisMax(spec.yMax());

        addSeries(chart, points, colorColumn);
        JComponent component = new InteractiveScatterPlotPanel(
                chart,
                points,
                session,
                model,
                xColumn.schema().displayName(),
                yColumn.schema().displayName(),
                colorColumn == null ? null : colorColumn.schema().displayName(),
                refresh
        );
        component.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return component;
    }

    @Override
    public JComponent createConfigurationComponent(
            PrismViewRecord view,
            PrismLiteWorkspaceModel model,
            PrismLiteWorkspaceController controller,
            Runnable refresh
    ) {
        if (!(view.specification() instanceof ScatterPlotViewSpec spec)) {
            return null;
        }
        PrismSession session = model.session();
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        JTextField title = new JTextField(spec.title(), 18);
        JComboBox<String> rowSet = new JComboBox<>();
        rowSet.addItem("");
        for (PrismRowSet existing : session.rowSets()) {
            rowSet.addItem(existing.id());
        }
        rowSet.setSelectedItem(spec.rowSetId() == null ? "" : spec.rowSetId());
        JComboBox<String> xColumn = numericColumnSelector(session, spec.xColumnId());
        JComboBox<String> yColumn = numericColumnSelector(session, spec.yColumnId());
        JComboBox<String> colorColumn = anyColumnSelector(session, spec.colorColumnId());
        JTextField xMin = new JTextField(text(spec.xMin()), 8);
        JTextField xMax = new JTextField(text(spec.xMax()), 8);
        JTextField yMin = new JTextField(text(spec.yMin()), 8);
        JTextField yMax = new JTextField(text(spec.yMax()), 8);
        JButton apply = new JButton("Apply");
        apply.addActionListener(event -> {
            ScatterPlotViewSpec updatedSpec = new ScatterPlotViewSpec(
                    spec.viewId(),
                    title.getText(),
                    stringValue(rowSet.getSelectedItem()),
                    Objects.toString(xColumn.getSelectedItem(), ""),
                    Objects.toString(yColumn.getSelectedItem(), ""),
                    stringValue(colorColumn.getSelectedItem()),
                    doubleValue(xMin.getText()),
                    doubleValue(xMax.getText()),
                    doubleValue(yMin.getText()),
                    doubleValue(yMax.getText())
            );
            Map<String, Object> provenance = new LinkedHashMap<>(view.provenance());
            provenance.put("updatedAt", Instant.now().toString());
            session.updateView(new PrismViewRecord(
                    updatedSpec.viewId(),
                    updatedSpec.viewType(),
                    updatedSpec.title(),
                    updatedSpec,
                    view.createdAt(),
                    provenance
            ));
            refresh.run();
        });

        addConfigRow(panel, 0, "Title", title);
        addConfigRow(panel, 1, "Row set", rowSet);
        addConfigRow(panel, 2, "X", xColumn);
        addConfigRow(panel, 3, "Y", yColumn);
        addConfigRow(panel, 4, "Color", colorColumn);
        addConfigRow(panel, 5, "X min", xMin);
        addConfigRow(panel, 6, "X max", xMax);
        addConfigRow(panel, 7, "Y min", yMin);
        addConfigRow(panel, 8, "Y max", yMax);
        addConfigRow(panel, 9, "", apply);
        return panel;
    }

    private static void addSeries(XYChart chart, List<PointRow> points, PrismColumn colorColumn) {
        if (colorColumn == null) {
            addSeries(chart, "All", points, CATEGORY_COLORS[0]);
            return;
        }
        if (isNumeric(colorColumn)) {
            addNumericBucketSeries(chart, points);
            return;
        }
        LinkedHashMap<String, List<PointRow>> groups = new LinkedHashMap<>();
        for (PointRow point : points) {
            groups.computeIfAbsent(point.colorLabel(), ignored -> new ArrayList<>()).add(point);
        }
        int colorIndex = 0;
        for (Map.Entry<String, List<PointRow>> entry : groups.entrySet()) {
            addSeries(chart, entry.getKey(), entry.getValue(), CATEGORY_COLORS[colorIndex++ % CATEGORY_COLORS.length]);
        }
    }

    private static void addNumericBucketSeries(XYChart chart, List<PointRow> points) {
        double min = points.stream().map(PointRow::colorValue).filter(Objects::nonNull).mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
        double max = points.stream().map(PointRow::colorValue).filter(Objects::nonNull).mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        if (Double.isNaN(min) || Double.isNaN(max) || min == max) {
            addSeries(chart, "Color", points, CATEGORY_COLORS[0]);
            return;
        }
        ArrayList<List<PointRow>> buckets = new ArrayList<>();
        for (int index = 0; index < NUMERIC_BUCKETS; index++) {
            buckets.add(new ArrayList<>());
        }
        for (PointRow point : points) {
            Double value = point.colorValue();
            int bucket = value == null ? 0 : Math.min(NUMERIC_BUCKETS - 1, (int) Math.floor(((value - min) / (max - min)) * NUMERIC_BUCKETS));
            buckets.get(bucket).add(point);
        }
        for (int index = 0; index < buckets.size(); index++) {
            List<PointRow> bucket = buckets.get(index);
            if (bucket.isEmpty()) {
                continue;
            }
            double low = min + (max - min) * index / NUMERIC_BUCKETS;
            double high = min + (max - min) * (index + 1) / NUMERIC_BUCKETS;
            addSeries(chart, String.format("%.3g - %.3g", low, high), bucket, CATEGORY_COLORS[index % CATEGORY_COLORS.length]);
        }
    }

    private static void addSeries(XYChart chart, String name, List<PointRow> points, Color color) {
        double[] x = points.stream().mapToDouble(PointRow::x).toArray();
        double[] y = points.stream().mapToDouble(PointRow::y).toArray();
        XYSeries series = chart.addSeries(name, x, y);
        series.setMarker(SeriesMarkers.CIRCLE);
        series.setMarkerColor(color);
        series.setLineStyle(org.knowm.xchart.style.lines.SeriesLines.NONE);
    }

    private static List<PointRow> points(PrismSession session, ScatterPlotViewSpec spec, PrismColumn xColumn, PrismColumn yColumn, PrismColumn colorColumn) {
        ArrayList<PointRow> points = new ArrayList<>();
        for (int physicalRow : resolvedRows(session, spec)) {
            if (xColumn.isMissing(physicalRow) || yColumn.isMissing(physicalRow)) {
                continue;
            }
            Double colorValue = colorColumn != null && isNumeric(colorColumn) && !colorColumn.isMissing(physicalRow)
                    ? colorColumn.doubleValueAt(physicalRow)
                    : null;
            String colorLabel = colorColumn == null || colorColumn.isMissing(physicalRow)
                    ? "Missing"
                    : colorColumn.formattedValueAt(physicalRow);
            points.add(new PointRow(
                    physicalRow,
                    session.rowIdForPhysicalRow(physicalRow),
                    xColumn.doubleValueAt(physicalRow),
                    yColumn.doubleValueAt(physicalRow),
                    xColumn.formattedValueAt(physicalRow),
                    yColumn.formattedValueAt(physicalRow),
                    colorValue,
                    colorLabel
            ));
        }
        return points;
    }

    private static List<Integer> resolvedRows(PrismSession session, ScatterPlotViewSpec spec) {
        ArrayList<Integer> rows = new ArrayList<>();
        int[] visiblePhysicalRows = session.visiblePhysicalRows();
        if (spec.rowSetId() == null) {
            for (int physicalRow : visiblePhysicalRows) {
                rows.add(physicalRow);
            }
            return rows;
        }
        BitSet visibleRows = new BitSet(session.totalRowCount());
        for (int physicalRow : visiblePhysicalRows) {
            visibleRows.set(physicalRow);
        }
        PrismRowSet rowSet = session.rowSet(spec.rowSetId());
        for (String rowId : rowSet.rowIds()) {
            OptionalInt physicalRow = session.physicalRowForRowId(rowId);
            if (physicalRow.isPresent() && visibleRows.get(physicalRow.getAsInt())) {
                rows.add(physicalRow.getAsInt());
            }
        }
        return rows;
    }

    private static JComboBox<String> numericColumnSelector(PrismSession session, String selected) {
        JComboBox<String> columns = new JComboBox<>();
        for (PrismColumn column : session.table().columns()) {
            if (isNumeric(column)) {
                columns.addItem(column.id());
            }
        }
        columns.setSelectedItem(selected);
        return columns;
    }

    private static JComboBox<String> anyColumnSelector(PrismSession session, String selected) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("");
        for (PrismColumn column : session.table().columns()) {
            if (column.type() != PrismColumnType.MOLECULE) {
                model.addElement(column.id());
            }
        }
        JComboBox<String> columns = new JComboBox<>(model);
        columns.setSelectedItem(selected == null ? "" : selected);
        return columns;
    }

    private static boolean isNumeric(PrismColumn column) {
        return column.type() == PrismColumnType.NUMERIC || column.type() == PrismColumnType.INTEGER;
    }

    private static void addConfigRow(JPanel panel, int row, String label, JComponent component) {
        GridBagConstraints labelConstraints = configConstraints(0, row);
        panel.add(new JLabel(label), labelConstraints);
        GridBagConstraints componentConstraints = configConstraints(1, row);
        componentConstraints.fill = GridBagConstraints.HORIZONTAL;
        componentConstraints.weightx = 1.0;
        panel.add(component, componentConstraints);
    }

    private static GridBagConstraints configConstraints(int x, int y) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.insets = new Insets(2, 2, 2, 2);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private static String stringValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static Double doubleValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.valueOf(value.trim());
    }

    private static String text(Double value) {
        return value == null ? "" : value.toString();
    }

    private static JComponent message(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(text), BorderLayout.CENTER);
        return panel;
    }

    private static final class InteractiveScatterPlotPanel extends XChartPanel<XYChart> {
        private final List<PointRow> points;
        private final PrismSession session;
        private final PrismLiteWorkspaceModel model;
        private final String xLabel;
        private final String yLabel;
        private final String colorLabel;
        private final Runnable refresh;
        private final ArrayList<Point> lasso = new ArrayList<>();
        private Point dragStart;
        private boolean dragging;

        private InteractiveScatterPlotPanel(
                XYChart chart,
                List<PointRow> points,
                PrismSession session,
                PrismLiteWorkspaceModel model,
                String xLabel,
                String yLabel,
                String colorLabel,
                Runnable refresh
        ) {
            super(chart);
            this.points = List.copyOf(points);
            this.session = session;
            this.model = model;
            this.xLabel = xLabel;
            this.yLabel = yLabel;
            this.colorLabel = colorLabel;
            this.refresh = refresh == null ? () -> { } : refresh;
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            setToolTipText("");
            ScatterMouseHandler handler = new ScatterMouseHandler();
            addMouseListener(handler);
            addMouseMotionListener(handler);
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            PointRow point = nearestPoint(event.getPoint());
            if (point == null) {
                return null;
            }
            StringBuilder text = new StringBuilder("<html><b>")
                    .append(escape(point.rowId()))
                    .append("</b><br>")
                    .append(escape(xLabel)).append(": ").append(escape(point.xText()))
                    .append("<br>")
                    .append(escape(yLabel)).append(": ").append(escape(point.yText()));
            if (colorLabel != null) {
                text.append("<br>").append(escape(colorLabel)).append(": ").append(escape(point.colorLabel()));
            }
            return text.append("</html>").toString();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (lasso.size() < 2) {
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                Polygon polygon = polygon(lasso);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
                g2.setColor(new Color(51, 102, 204));
                g2.fillPolygon(polygon);
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(new Color(51, 102, 204));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawPolyline(polygon.xpoints, polygon.ypoints, polygon.npoints);
                if (lasso.size() > 2) {
                    Point first = lasso.getFirst();
                    Point last = lasso.getLast();
                    g2.drawLine(last.x, last.y, first.x, first.y);
                }
            } finally {
                g2.dispose();
            }
        }

        private PointRow nearestPoint(Point mouse) {
            PointRow best = null;
            double bestDistance = HIT_RADIUS_PIXELS * HIT_RADIUS_PIXELS;
            for (PointRow point : points) {
                Point screen = screenPoint(point);
                double distance = screen.distanceSq(mouse);
                if (distance <= bestDistance) {
                    best = point;
                    bestDistance = distance;
                }
            }
            return best;
        }

        private Point screenPoint(PointRow point) {
            XYChart chart = getChart();
            return new Point(
                    (int) Math.round(chart.getScreenXFromChart(point.x())),
                    (int) Math.round(chart.getScreenYFromChart(point.y()))
            );
        }

        private void selectPoint(PointRow point, int modifiersEx) {
            if (point == null) {
                return;
            }
            boolean additive = additiveSelection(modifiersEx);
            BitSet selectedRows = additive
                    ? session.viewState().selectionModel().selectedRows()
                    : new BitSet(session.totalRowCount());
            selectedRows.set(point.physicalRow());
            session.viewState().selectionModel().replace(selectedRows);
            model.setFocusedPhysicalRow(point.physicalRow());
            refresh.run();
        }

        private void selectLasso(int modifiersEx) {
            if (lasso.size() < 3) {
                return;
            }
            Polygon polygon = polygon(lasso);
            ArrayList<PointRow> selected = new ArrayList<>();
            for (PointRow point : points) {
                if (polygon.contains(screenPoint(point))) {
                    selected.add(point);
                }
            }
            if (selected.isEmpty()) {
                return;
            }
            boolean additive = additiveSelection(modifiersEx);
            BitSet selectedRows = additive
                    ? session.viewState().selectionModel().selectedRows()
                    : new BitSet(session.totalRowCount());
            for (PointRow point : selected) {
                selectedRows.set(point.physicalRow());
            }
            session.viewState().selectionModel().replace(selectedRows);
            model.setFocusedPhysicalRow(selected.getFirst().physicalRow());
            refresh.run();
        }

        private static boolean additiveSelection(int modifiersEx) {
            return (modifiersEx & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
        }

        private static Polygon polygon(List<Point> points) {
            Polygon polygon = new Polygon();
            for (Point point : points) {
                polygon.addPoint(point.x, point.y);
            }
            return polygon;
        }

        private static String escape(String text) {
            if (text == null) {
                return "";
            }
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
        }

        private final class ScatterMouseHandler extends MouseAdapter {
            @Override
            public void mousePressed(MouseEvent event) {
                if (event.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                dragStart = event.getPoint();
                dragging = false;
                lasso.clear();
                lasso.add(event.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragStart == null) {
                    return;
                }
                if (!dragging && dragStart.distance(event.getPoint()) >= DRAG_THRESHOLD_PIXELS) {
                    dragging = true;
                }
                if (dragging) {
                    lasso.add(event.getPoint());
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (dragStart == null || event.getButton() != MouseEvent.BUTTON1) {
                    clearDrag();
                    return;
                }
                if (dragging) {
                    lasso.add(event.getPoint());
                    selectLasso(event.getModifiersEx());
                } else {
                    selectPoint(nearestPoint(event.getPoint()), event.getModifiersEx());
                }
                clearDrag();
            }

            private void clearDrag() {
                dragStart = null;
                dragging = false;
                lasso.clear();
                repaint();
            }
        }
    }

    private record PointRow(
            int physicalRow,
            String rowId,
            double x,
            double y,
            String xText,
            String yText,
            Double colorValue,
            String colorLabel
    ) {
    }
}
