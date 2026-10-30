package tech.molecules.structurized.prismlite.swing.workspace.views;

import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.engine.ocl.StructureSketchEntrySpec;
import tech.molecules.structurized.prism.engine.ocl.StructureSketchViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeViewPanel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;

/**
 * Renders a {@code structure-sketch} block: structures supplied inline in the report as SMILES.
 *
 * <p>Carries no selection wiring, because these structures are not dataset rows and therefore have
 * nothing to select. A SMILES that fails to parse is shown as a placeholder rather than omitted, so
 * the grid position still lines up with the report source.
 */
public final class StructureSketchViewRenderer implements PrismSwingViewRenderer {
    private static final int CARD_WIDTH = 220;
    private static final int CARD_HEIGHT = 150;

    @Override
    public String viewType() {
        return StructureSketchViewSpec.VIEW_TYPE;
    }

    @Override
    public JComponent createComponent(PrismViewRecord view,
                                      PrismLiteWorkspaceModel model,
                                      PrismLiteWorkspaceController controller,
                                      Runnable refresh) {
        StructureSketchViewSpec spec = (StructureSketchViewSpec) view.specification();

        JPanel grid = new JPanel(new GridLayout(0, spec.gridColumns(), 8, 8));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (StructureSketchEntrySpec entry : spec.structures()) {
            grid.add(card(entry));
        }

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        if (spec.title() != null) {
            JLabel title = new JLabel(spec.title());
            title.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
            container.add(title, BorderLayout.NORTH);
        }
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        container.add(scroll, BorderLayout.CENTER);
        return container;
    }

    private static JComponent card(StructureSketchEntrySpec entry) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createLineBorder(new java.awt.Color(0xD0D0D0)));

        if (entry.label() != null) {
            JLabel label = new JLabel(entry.label());
            label.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
            card.add(label, BorderLayout.NORTH);
        }

        StereoMolecule molecule = parse(entry.smiles());
        if (molecule == null) {
            JLabel unparseable = new JLabel("unparseable SMILES");
            unparseable.setHorizontalAlignment(JLabel.CENTER);
            unparseable.setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
            card.add(unparseable, BorderLayout.CENTER);
        } else {
            MoleculeViewPanel moleculeView = new MoleculeViewPanel();
            moleculeView.setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
            moleculeView.setMolecule(molecule);
            card.add(moleculeView, BorderLayout.CENTER);
        }

        if (entry.caption() != null) {
            JPanel south = new JPanel();
            south.setOpaque(false);
            south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
            south.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
            south.add(new JLabel(entry.caption()));
            card.add(south, BorderLayout.SOUTH);
        }
        return card;
    }

    private static StereoMolecule parse(String smiles) {
        try {
            StereoMolecule molecule = new StereoMolecule();
            new SmilesParser().parse(molecule, smiles);
            if (molecule.getAllAtoms() == 0) return null;
            molecule.ensureHelperArrays(StereoMolecule.cHelperCIP);
            return molecule;
        } catch (Exception exception) {
            return null;
        }
    }
}
