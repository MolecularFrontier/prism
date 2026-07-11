package tech.molecules.structurized.prismlite.swing.workspace.filters;

import tech.molecules.structurized.prism.engine.PrismFilter;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.Map;
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
        boolean any = false;
        for (Map.Entry<String, FilterDraftState> entry : model.appliedColumnFilterStates().entrySet()) {
            FilterDraftState state = entry.getValue();
            if (state == null || state.filter() == null) {
                continue;
            }
            any = true;
            add(columnFilterChip(entry.getKey(), state));
        }
        for (PrismFilter filter : model.nonGuiActiveFilters()) {
            any = true;
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
        if (!any) {
            add(new JLabel("none"));
        }
        revalidate();
        repaint();
    }

    private JPanel columnFilterChip(String columnId, FilterDraftState state) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JCheckBox enabled = new JCheckBox("on", state.enabled());
        enabled.addActionListener(event -> {
            model.setAppliedFilterEnabled(columnId, enabled.isSelected());
            refresh.run();
        });
        JCheckBox inverted = new JCheckBox("not", state.inverted());
        inverted.addActionListener(event -> {
            model.setAppliedFilterInverted(columnId, inverted.isSelected());
            refresh.run();
        });
        JButton edit = new JButton(labelProvider.label(state.filter(), model.table()));
        edit.setEnabled(state.enabled());
        edit.addActionListener(event -> controller.focusColumn(columnId));
        JButton remove = new JButton("x");
        remove.addActionListener(event -> {
            model.removeColumnFilterState(columnId);
            refresh.run();
        });
        chip.add(enabled);
        chip.add(inverted);
        chip.add(edit);
        chip.add(remove);
        return chip;
    }
}
