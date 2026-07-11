package tech.molecules.structurized.prismlite.swing.workspace.filters;

import tech.molecules.structurized.prism.engine.PrismFilter;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;

public final class FilterShelfPanel extends JPanel {
    private final PrismLiteWorkspaceModel model;
    private final PrismLiteWorkspaceController controller;
    private final Runnable refresh;
    private final PrismFilterLabelProvider labelProvider = new DefaultPrismFilterLabelProvider();

    public FilterShelfPanel(PrismLiteWorkspaceModel model,
                            PrismLiteWorkspaceController controller,
                            Runnable refresh) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 2));
        this.model = Objects.requireNonNull(model, "model");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.refresh = refresh == null ? () -> { } : refresh;
        refresh();
    }

    public void refresh() {
        removeAll();
        add(new JLabel("Filters"));
        List<PrismFilter> filters = model.session().viewState().activeFilters();
        if (filters.isEmpty()) {
            add(new JLabel("none"));
        }
        for (PrismFilter filter : filters) {
            JButton chip = new JButton(labelProvider.label(filter, model.table()) + " x");
            chip.addActionListener(event -> {
                if (filter.referencedColumnIds().size() == 1) {
                    controller.focusColumn(filter.referencedColumnIds().iterator().next());
                }
                model.removeAppliedFilter(filter);
                refresh.run();
            });
            add(chip);
        }
        revalidate();
        repaint();
    }
}
