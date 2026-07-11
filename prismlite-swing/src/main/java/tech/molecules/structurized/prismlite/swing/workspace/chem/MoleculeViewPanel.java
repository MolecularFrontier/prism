package tech.molecules.structurized.prismlite.swing.workspace.chem;

import com.actelion.research.chem.Depictor2D;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.gui.generic.GenericRectangle;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class MoleculeViewPanel extends JPanel {
    private StereoMolecule molecule;

    public MoleculeViewPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(220, 160));
    }

    public void setMolecule(StereoMolecule molecule) {
        this.molecule = molecule == null ? null : new StereoMolecule(molecule);
        repaint();
    }

    public StereoMolecule molecule() {
        return molecule == null ? null : new StereoMolecule(molecule);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (molecule == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            Depictor2D depictor = new Depictor2D(molecule, Depictor2D.cDModeSuppressChiralText);
            GenericRectangle rectangle = new GenericRectangle(4, 4, Math.max(1, getWidth() - 8), Math.max(1, getHeight() - 8));
            depictor.validateView(g, rectangle, Depictor2D.cModeInflateToMaxAVBL | Depictor2D.cDModeSuppressChiralText);
            depictor.paint(g);
        } finally {
            g.dispose();
        }
    }
}
