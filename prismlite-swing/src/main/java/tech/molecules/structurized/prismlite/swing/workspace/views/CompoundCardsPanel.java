package tech.molecules.structurized.prismlite.swing.workspace.views;

import com.actelion.research.chem.StereoMolecule;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.RowSelectionSubscription;
import tech.molecules.structurized.prism.engine.ocl.CompoundCardProjectionBuilder;
import tech.molecules.structurized.prism.engine.ocl.CompoundCardProjectionModels.CompoundCard;
import tech.molecules.structurized.prism.engine.ocl.CompoundCardProjectionModels.CompoundCardsModel;
import tech.molecules.structurized.prism.engine.ocl.CompoundCardProjectionModels.CompoundCardValue;
import tech.molecules.structurized.prism.engine.ocl.CompoundCardsViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderCache;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeViewPanel;
import tech.molecules.structurized.prismlite.swing.workspace.profile.ScoreDisplayService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompoundCardsPanel extends JPanel {
    private static final Color SELECTED = new Color(51, 102, 204);
    private static final Color GRID = new Color(210, 214, 220);

    private final CompoundCardsViewSpec specification;
    private final PrismLiteWorkspaceModel workspace;
    private final CompoundCardsModel projection;
    private final Map<Integer, JPanel> cards = new LinkedHashMap<>();
    private RowSelectionSubscription selectionSubscription;

    public CompoundCardsPanel(CompoundCardsViewSpec specification, PrismLiteWorkspaceModel workspace) {
        super(new BorderLayout(0, 4));
        this.specification = specification;
        this.workspace = workspace;
        PrismSession session = workspace.session();
        this.projection = CompoundCardProjectionBuilder.build(session.snapshot(), specification);
        if (projection.cards().isEmpty()) {
            add(new JLabel("No compounds to compare."), BorderLayout.CENTER);
            return;
        }

        PrismColumn structureColumn = session.table().column(specification.structureColumnId());
        MoleculeRenderCache cache = new MoleculeRenderCache(session.table());
        int columns = Math.min(3, projection.cards().size());
        JPanel grid = new JPanel(new GridLayout(0, columns, 10, 10));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (CompoundCard item : projection.cards()) {
            int physicalRow = session.physicalRowForRowId(item.rowId()).orElseThrow();
            JPanel card = card(session, structureColumn, cache, physicalRow, item);
            cards.put(physicalRow, card);
            grid.add(card);
        }
        JScrollPane scroll = new JScrollPane(grid);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        int rows = (projection.cards().size() + columns - 1) / columns;
        scroll.setPreferredSize(new Dimension(760, Math.min(680, Math.max(270, rows * cardHeight() + 20))));
        add(scroll, BorderLayout.CENTER);

        String countText = "Showing " + projection.cards().size() + " of "
                + projection.totalCompoundCount() + " compounds";
        if (projection.referenceRowId() != null) countText += " · deltas vs " + projection.referenceRowId();
        JLabel count = new JLabel(countText, SwingConstants.RIGHT);
        count.setForeground(new Color(90, 94, 102));
        count.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        add(count, BorderLayout.SOUTH);
        subscribeToSelection();
        selectionChanged(session.viewState().selectionModel().selectedRows());
    }

    public CompoundCardsModel projection() {
        return projection;
    }

    public List<String> displayedRowIds() {
        return projection.cards().stream().map(CompoundCard::rowId).toList();
    }

    JComponent cardForPhysicalRow(int physicalRow) {
        return cards.get(physicalRow);
    }

    private JPanel card(PrismSession session, PrismColumn structureColumn, MoleculeRenderCache cache,
                        int physicalRow, CompoundCard item) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(Color.WHITE);
        card.putClientProperty("prism.rowId", item.rowId());
        updateSelectionBorder(card, session.viewState().selectionModel().isSelected(physicalRow));

        JPanel heading = new JPanel(new BorderLayout(4, 0));
        heading.setOpaque(false);
        JLabel title = new JLabel(item.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        heading.add(title, BorderLayout.CENTER);
        if (item.reference()) {
            JLabel reference = new JLabel("REFERENCE");
            reference.setForeground(new Color(55, 80, 130));
            reference.setFont(reference.getFont().deriveFont(Font.BOLD, reference.getFont().getSize2D() - 1));
            heading.add(reference, BorderLayout.EAST);
        }
        card.add(heading, BorderLayout.NORTH);

        MoleculeViewPanel moleculeView = new MoleculeViewPanel();
        moleculeView.setPreferredSize(new Dimension(240, 150));
        StereoMolecule molecule = cache.molecule(structureColumn, physicalRow);
        moleculeView.setMolecule(molecule);
        card.add(moleculeView, BorderLayout.CENTER);

        JPanel properties = new JPanel();
        properties.setOpaque(false);
        properties.setLayout(new BoxLayout(properties, BoxLayout.Y_AXIS));
        for (CompoundCardValue value : item.values()) properties.add(propertyRow(session, value));
        card.add(properties, BorderLayout.SOUTH);

        MouseAdapter selectionHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!specification.linkSelection()) return;
                boolean additive = (event.getModifiersEx()
                        & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
                        | InputEvent.META_DOWN_MASK)) != 0;
                BitSet selected = additive ? session.viewState().selectionModel().selectedRows()
                        : new BitSet(session.totalRowCount());
                selected.set(physicalRow, !additive || !selected.get(physicalRow));
                session.viewState().selectionModel().replace(selected);
                workspace.setFocusedPhysicalRow(physicalRow);
            }
        };
        installSelectionHandler(card, selectionHandler);
        return card;
    }

    private static JComponent propertyRow(PrismSession session, CompoundCardValue value) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        String labelText = value.specification().label() == null
                ? session.table().column(value.specification().columnId()).schema().displayName()
                : value.specification().label();
        JLabel label = new JLabel(labelText);
        label.setForeground(new Color(75, 78, 84));
        row.add(label, BorderLayout.WEST);

        JPanel displayed = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        displayed.setOpaque(false);
        JLabel raw = new JLabel(value.formattedValue());
        raw.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        if (value.score() != null) {
            raw.setOpaque(true);
            raw.setBackground(ScoreDisplayService.softScoreColor(value.score()));
            raw.setToolTipText("Score: " + ScoreDisplayService.format(value.score()));
        }
        displayed.add(raw);
        if (value.formattedDelta() != null) {
            JLabel delta = new JLabel(value.formattedDelta());
            delta.setForeground(new Color(80, 86, 98));
            delta.setToolTipText("Difference from reference compound");
            displayed.add(delta);
        }
        row.add(displayed, BorderLayout.EAST);
        return row;
    }

    private int cardHeight() {
        return 210 + specification.properties().size() * 27;
    }

    private static void installSelectionHandler(Component component, MouseAdapter handler) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.addMouseListener(handler);
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) installSelectionHandler(child, handler);
        }
    }

    private static void updateSelectionBorder(JPanel card, boolean selected) {
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selected ? SELECTED : GRID, selected ? 2 : 1),
                BorderFactory.createEmptyBorder(7, 7, 7, 7)));
    }

    private void subscribeToSelection() {
        if (selectionSubscription == null && specification.linkSelection()) {
            selectionSubscription = workspace.session().viewState().selectionModel().subscribe(this::selectionChanged);
        }
    }

    private void selectionChanged(BitSet selected) {
        if (!specification.linkSelection()) return;
        Runnable update = () -> cards.forEach((row, card) -> updateSelectionBorder(card, selected.get(row)));
        if (SwingUtilities.isEventDispatchThread()) update.run(); else SwingUtilities.invokeLater(update);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        subscribeToSelection();
        selectionChanged(workspace.session().viewState().selectionModel().selectedRows());
    }

    @Override
    public void removeNotify() {
        if (selectionSubscription != null) {
            selectionSubscription.close();
            selectionSubscription = null;
        }
        super.removeNotify();
    }
}
