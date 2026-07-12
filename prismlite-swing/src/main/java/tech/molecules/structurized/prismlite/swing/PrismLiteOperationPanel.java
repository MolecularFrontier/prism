package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismOperationDescriptor;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prismlite.swing.operations.PrismOperationForm;
import tech.molecules.structurized.prismlite.swing.operations.PrismOperationFormRegistry;
import tech.molecules.structurized.prismlite.swing.operations.PrismOperationLaunchContext;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class PrismLiteOperationPanel extends JPanel {
    private final PrismSession session;
    private final Runnable refresh;
    private final PrismLiteRowSetPanel rowSetPanel;
    private final PrismOperationFormRegistry formRegistry;
    private final PrismOperationLaunchContext launchContext;
    private final JComboBox<PrismOperationDescriptor> operationSelector;
    private final JPanel parameterPanel = new JPanel(new BorderLayout());
    private final JTextArea status = new JTextArea(3, 24);
    private PrismOperationForm currentForm;

    public PrismLiteOperationPanel(PrismSession session, PrismLiteRowSetPanel rowSetPanel, Runnable refresh) {
        this(session, rowSetPanel, refresh, Set::of);
    }

    public PrismLiteOperationPanel(
            PrismSession session,
            PrismLiteRowSetPanel rowSetPanel,
            Runnable refresh,
            Supplier<Set<Integer>> selectedPhysicalRowsSupplier
    ) {
        super(new BorderLayout(4, 4));
        this.session = Objects.requireNonNull(session, "session");
        this.rowSetPanel = Objects.requireNonNull(rowSetPanel, "rowSetPanel");
        this.refresh = refresh == null ? () -> { } : refresh;
        this.formRegistry = PrismOperationFormRegistry.defaults();
        this.launchContext = new PrismOperationLaunchContext(session, selectedPhysicalRowsSupplier);
        this.operationSelector = new JComboBox<>(session.operationRegistry().descriptors().toArray(PrismOperationDescriptor[]::new));
        operationSelector.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : value.name());
            if (isSelected) {
                label.setOpaque(true);
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });
        operationSelector.addActionListener(event -> rebuildParameters());
        JButton run = new JButton("Run");
        run.addActionListener(event -> runSelectedOperation());

        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(operationSelector, BorderLayout.CENTER);
        top.add(run, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(parameterPanel), BorderLayout.CENTER);
        status.setEditable(false);
        add(status, BorderLayout.SOUTH);
        rebuildParameters();
    }

    public void reloadOperations() {
        operationSelector.removeAllItems();
        for (PrismOperationDescriptor descriptor : session.operationRegistry().descriptors()) {
            operationSelector.addItem(descriptor);
        }
        rebuildParameters();
    }

    private void rebuildParameters() {
        parameterPanel.removeAll();
        PrismOperationDescriptor descriptor = (PrismOperationDescriptor) operationSelector.getSelectedItem();
        if (descriptor == null) {
            currentForm = null;
            parameterPanel.revalidate();
            parameterPanel.repaint();
            return;
        }
        currentForm = formRegistry.createForm(session, descriptor, launchContext);
        parameterPanel.add(currentForm.component(), BorderLayout.NORTH);
        parameterPanel.revalidate();
        parameterPanel.repaint();
    }

    private void runSelectedOperation() {
        PrismOperationDescriptor descriptor = (PrismOperationDescriptor) operationSelector.getSelectedItem();
        if (descriptor == null || currentForm == null) {
            return;
        }
        try {
            Map<String, Object> parameters = currentForm.collectParameters();
            var result = session.runOperation(descriptor.id(), parameters);
            rowSetPanel.refreshRowSets();
            refresh.run();
            status.setText("Added " + result.addedRowSets().size() + " row set(s), "
                    + result.addedColumns().size() + " column(s), "
                    + result.addedViews().size() + " view(s), updated "
                    + result.updatedViews().size() + " view(s).");
        } catch (RuntimeException exception) {
            status.setText(exception.getMessage());
        }
    }
}
