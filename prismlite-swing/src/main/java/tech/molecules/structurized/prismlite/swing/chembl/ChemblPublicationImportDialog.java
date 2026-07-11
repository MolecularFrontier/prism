package tech.molecules.structurized.prismlite.swing.chembl;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.Optional;

public final class ChemblPublicationImportDialog {
    private ChemblPublicationImportDialog() {
    }

    public static Optional<ChemblPublicationImportOptions> prompt(Component parent) {
        JTextField documentId = new JTextField("CHEMBL5360622", 18);
        JSpinner minCompounds = new JSpinner(new SpinnerNumberModel(10, 1, 10000, 1));
        JSpinner maxEndpoints = new JSpinner(new SpinnerNumberModel(12, 1, 500, 1));

        JPanel content = new JPanel(new GridLayout(0, 2, 8, 6));
        content.add(new JLabel("Document ID"));
        content.add(documentId);
        content.add(new JLabel("Min compounds per endpoint"));
        content.add(minCompounds);
        content.add(new JLabel("Max endpoints"));
        content.add(maxEndpoints);

        int result = JOptionPane.showConfirmDialog(
                parent,
                content,
                "Import ChEMBL Publication",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ChemblPublicationImportOptions(
                    documentId.getText(),
                    ((Number) minCompounds.getValue()).intValue(),
                    ((Number) maxEndpoints.getValue()).intValue()
            ));
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(
                    parent,
                    exception.getMessage(),
                    "Import ChEMBL Publication",
                    JOptionPane.ERROR_MESSAGE
            );
            return Optional.empty();
        }
    }
}
