package tech.molecules.structurized.prismlite.swing.workspace.molecule;

import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.gui.editor.EditorEvent;
import com.actelion.research.gui.editor.SwingEditorPanel;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismMoleculeDocument;
import tech.molecules.structurized.prism.engine.PrismMoleculeDocumentMode;
import tech.molecules.structurized.prism.engine.PrismMoleculeList;
import tech.molecules.structurized.prism.engine.PrismMoleculeWorkspace;
import tech.molecules.structurized.prism.engine.PrismMoleculeWorkspaceSubscription;
import tech.molecules.structurized.prism.engine.ocl.OclMoleculeDocumentCodec;
import tech.molecules.structurized.prism.engine.ocl.OclSimilarityFilter;
import tech.molecules.structurized.prism.engine.ocl.OclSubstructureFilter;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderUtil;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeViewPanel;
import tech.molecules.structurized.prismlite.swing.workspace.chem.StructureCoordinateResolver;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class PrismMoleculeWorkspacePanel extends JPanel implements AutoCloseable {
    private final PrismMoleculeWorkspace workspace;
    private final PrismLiteWorkspaceModel tableWorkspace;
    private final OclMoleculeDocumentCodec codec = new OclMoleculeDocumentCodec();
    private final DefaultListModel<MoleculeListItem> listModel = new DefaultListModel<>();
    private final DefaultListModel<MoleculeDocumentItem> documentModel = new DefaultListModel<>();
    private final JList<MoleculeListItem> lists = new JList<>(listModel);
    private final JList<MoleculeDocumentItem> documents = new JList<>(documentModel);
    private final JTabbedPane documentTabs = new JTabbedPane();
    private final SwingEditorPanel editor = new SwingEditorPanel(new StereoMolecule());
    private final JCheckBox fragmentMode = new JCheckBox("Fragment");
    private final javax.swing.JLabel status = new javax.swing.JLabel(" ");
    private final Timer commitTimer;
    private final PrismMoleculeWorkspaceSubscription subscription;
    private final Consumer<String> filterTargetFocused;
    private boolean suppressUiEvents;
    private String activeDocumentId;
    private long loadedDocumentRevision;

    public PrismMoleculeWorkspacePanel(PrismMoleculeWorkspace workspace,
                                       PrismLiteWorkspaceModel tableWorkspace) {
        this(workspace, tableWorkspace, columnId -> { });
    }

    public PrismMoleculeWorkspacePanel(PrismMoleculeWorkspace workspace,
                                       PrismLiteWorkspaceModel tableWorkspace,
                                       Consumer<String> filterTargetFocused) {
        super(new BorderLayout());
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.tableWorkspace = Objects.requireNonNull(tableWorkspace, "tableWorkspace");
        this.filterTargetFocused = filterTargetFocused == null ? columnId -> { } : filterTargetFocused;
        this.commitTimer = new Timer(250, event -> commitEditor());
        this.commitTimer.setRepeats(false);
        this.subscription = workspace.subscribe(change -> SwingUtilities.invokeLater(this::refreshFromWorkspace));

        lists.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        documents.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        documents.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        documents.setVisibleRowCount(-1);
        documents.setFixedCellWidth(190);
        documents.setFixedCellHeight(160);
        documents.setCellRenderer(new MoleculeDocumentTileRenderer(codec));
        lists.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !suppressUiEvents) {
                commitPending();
                refreshDocuments(Set.of(), null);
            }
        });
        documents.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !suppressUiEvents) {
                commitPending();
                MoleculeDocumentItem selected = documents.getSelectedValue();
                activeDocumentId = selected == null ? null : selected.document().id();
                loadActiveDocument();
            }
        });
        documents.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && documents.locationToIndex(event.getPoint()) >= 0) {
                    documentTabs.setSelectedIndex(1);
                }
            }
        });
        editor.getDrawArea().addDrawAreaListener(event -> {
            if (!suppressUiEvents
                    && event.getWhat() == EditorEvent.WHAT_MOLECULE_CHANGED
                    && event.isUserChange()
                    && activeDocumentId != null) {
                commitTimer.restart();
            }
        });
        fragmentMode.setToolTipText("Treat this document as an OpenChemLib query fragment");
        fragmentMode.addActionListener(event -> {
            if (suppressUiEvents || activeDocumentId == null) return;
            editor.getDrawArea().setAllowQueryFeatures(fragmentMode.isSelected());
            editor.getDrawArea().getMolecule().setFragment(fragmentMode.isSelected());
            commitTimer.restart();
        });

        add(documentToolbar(), BorderLayout.NORTH);
        add(content(), BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(status, BorderLayout.SOUTH);
        refreshFromWorkspace();
    }

    private JToolBar documentToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton create = button("New", this::createDocument);
        JButton openRow = button("Open focused row", this::openFocusedRow);
        JButton duplicate = button("Duplicate", this::duplicateDocument);
        JButton rename = button("Rename", this::renameDocument);
        JButton move = button("Move", this::moveDocument);
        JButton remove = button("Delete", this::deleteDocument);
        JButton useAsFilter = button("Use as filter...", this::useAsFilter);
        toolbar.add(create);
        toolbar.add(openRow);
        toolbar.addSeparator();
        toolbar.add(duplicate);
        toolbar.add(rename);
        toolbar.add(move);
        toolbar.add(remove);
        toolbar.addSeparator();
        toolbar.add(useAsFilter);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(fragmentMode);
        return toolbar;
    }

    private JSplitPane content() {
        JPanel navigation = new JPanel(new BorderLayout(0, 4));
        navigation.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        navigation.setPreferredSize(new Dimension(210, 400));
        navigation.add(sectionHeader("Lists", listActions()), BorderLayout.NORTH);
        navigation.add(new JScrollPane(lists), BorderLayout.CENTER);

        JPanel overview = new JPanel(new BorderLayout());
        overview.add(sectionHeader("Molecules", documentOrderActions()), BorderLayout.NORTH);
        JScrollPane documentScroll = new JScrollPane(documents);
        documentScroll.getVerticalScrollBar().setUnitIncrement(16);
        overview.add(documentScroll, BorderLayout.CENTER);

        documentTabs.addTab("Overview", overview);
        documentTabs.addTab("Editor", editor);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigation, documentTabs);
        split.setResizeWeight(0.15);
        split.setDividerLocation(210);
        return split;
    }

    private JPanel sectionHeader(String title, JPanel actions) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setAlignmentX(LEFT_ALIGNMENT);
        javax.swing.JLabel label = new javax.swing.JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 4));
        panel.add(label, BorderLayout.WEST);
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    private JPanel listActions() {
        JPanel actions = new JPanel();
        actions.add(button("+", this::createList));
        actions.add(button("Rename", this::renameList));
        actions.add(button("-", this::deleteList));
        return actions;
    }

    private JPanel documentOrderActions() {
        JPanel actions = new JPanel();
        actions.add(button("Up", () -> moveDocumentBy(-1)));
        actions.add(button("Down", () -> moveDocumentBy(1)));
        return actions;
    }

    private JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void createList() {
        String title = prompt("New molecule list", "List name:", "Candidate molecules");
        if (title == null) return;
        PrismMoleculeList created = workspace.createList(null, title);
        refreshFromWorkspace(created.id(), null);
    }

    private void renameList() {
        MoleculeListItem selected = lists.getSelectedValue();
        if (selected == null) return;
        String title = prompt("Rename molecule list", "List name:", selected.list().title());
        if (title == null) return;
        workspace.renameList(selected.list().id(), title);
    }

    private void deleteList() {
        MoleculeListItem selected = lists.getSelectedValue();
        if (selected == null || PrismMoleculeWorkspace.SCRATCHPAD_ID.equals(selected.list().id())) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete list '" + selected.list().title() + "' and its documents?",
                "Delete molecule list",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) workspace.deleteList(selected.list().id());
    }

    private void createDocument() {
        MoleculeListItem selectedList = selectedOrScratchpad();
        PrismMoleculeDocument created = workspace.addDocument(
                selectedList.list().id(), null, "Untitled molecule",
                PrismMoleculeDocumentMode.MOLECULE, "", ""
        );
        refreshFromWorkspace(selectedList.list().id(), created.id());
        documentTabs.setSelectedIndex(1);
    }

    private void openFocusedRow() {
        Integer physicalRow = tableWorkspace.focusedPhysicalRow();
        if (physicalRow == null) {
            showMessage("Select a table row first.");
            return;
        }
        PrismColumn structureColumn = focusedStructureColumn();
        if (structureColumn == null || structureColumn.isMissing(physicalRow)) {
            showMessage("The focused row does not contain a structure.");
            return;
        }
        String coordinates = StructureCoordinateResolver.coordinateValue(
                tableWorkspace.session().table(), structureColumn, physicalRow
        );
        StereoMolecule molecule = MoleculeRenderUtil.parse(
                structureColumn, structureColumn.valueAt(physicalRow), coordinates
        );
        if (molecule == null) {
            showMessage("The focused row structure could not be parsed.");
            return;
        }
        OclMoleculeDocumentCodec.EncodedMolecule encoded =
                codec.encode(molecule, PrismMoleculeDocumentMode.MOLECULE);
        MoleculeListItem target = selectedOrScratchpad();
        String rowId = tableWorkspace.focusedRowId();
        PrismMoleculeDocument created = workspace.addDocument(
                target.list().id(), null,
                rowId == null ? "Table molecule" : rowId,
                PrismMoleculeDocumentMode.MOLECULE,
                encoded.idcode(), encoded.coordinates()
        );
        refreshFromWorkspace(target.list().id(), created.id());
        documentTabs.setSelectedIndex(1);
    }

    private PrismColumn focusedStructureColumn() {
        String focusedColumnId = tableWorkspace.focusedColumnId();
        if (focusedColumnId != null) {
            PrismColumn focused = tableWorkspace.session().table().findColumn(focusedColumnId).orElse(null);
            if (focused != null && focused.type() == PrismColumnType.MOLECULE) return focused;
        }
        return tableWorkspace.session().table().columns().stream()
                .filter(column -> column.type() == PrismColumnType.MOLECULE)
                .findFirst()
                .orElse(null);
    }

    private void duplicateDocument() {
        List<String> selectedIds = selectedDocumentIds();
        MoleculeListItem target = lists.getSelectedValue();
        if (selectedIds.isEmpty() || target == null) return;
        List<PrismMoleculeDocument> duplicates = workspace.duplicateDocuments(selectedIds, target.list().id());
        refreshFromWorkspace(target.list().id(),
                duplicates.stream().map(PrismMoleculeDocument::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)),
                duplicates.isEmpty() ? null : duplicates.getFirst().id());
    }

    private void renameDocument() {
        MoleculeDocumentItem selected = documents.getSelectedValue();
        if (selected == null) return;
        String title = prompt("Rename molecule", "Molecule name:", selected.document().title());
        if (title == null) return;
        PrismMoleculeDocument document = selected.document();
        workspace.updateDocument(document.id(), title, document.mode(), document.idcode(), document.coordinates());
    }

    private void moveDocument() {
        List<String> selectedIds = selectedDocumentIds();
        if (selectedIds.isEmpty() || workspace.lists().size() < 2) return;
        MoleculeListItem[] choices = workspace.lists().stream().map(MoleculeListItem::new).toArray(MoleculeListItem[]::new);
        MoleculeListItem target = (MoleculeListItem) JOptionPane.showInputDialog(
                this, "Move to list:", "Move molecules",
                JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]
        );
        if (target == null) return;
        workspace.moveDocuments(selectedIds, target.list().id(), target.list().documents().size());
        refreshFromWorkspace(target.list().id(), new LinkedHashSet<>(selectedIds), selectedIds.getFirst());
    }

    private void moveDocumentBy(int offset) {
        List<String> selectedIds = selectedDocumentIds();
        int firstIndex = documents.getMinSelectionIndex();
        if (selectedIds.isEmpty() || firstIndex < 0) return;
        int target = Math.max(0, Math.min(documentModel.size() - selectedIds.size(), firstIndex + offset));
        if (target == firstIndex) return;
        workspace.reorderDocuments(selectedIds, target);
        refreshFromWorkspace(selectedListId(), new LinkedHashSet<>(selectedIds), activeDocumentId);
    }

    private void deleteDocument() {
        List<String> selectedIds = selectedDocumentIds();
        if (selectedIds.isEmpty()) return;
        String message = selectedIds.size() == 1
                ? "Delete '" + documents.getSelectedValue().document().title() + "'?"
                : "Delete " + selectedIds.size() + " selected molecules?";
        int choice = JOptionPane.showConfirmDialog(this, message, "Delete molecules",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) {
            commitTimer.stop();
            activeDocumentId = null;
            workspace.deleteDocuments(selectedIds);
        }
    }

    private void useAsFilter() {
        commitPending();
        PrismMoleculeDocument document = activeDocumentId == null
                ? null
                : workspace.findDocument(activeDocumentId).orElse(null);
        if (document == null || document.idcode().isBlank()) {
            showMessage("Select a non-empty molecule first.");
            return;
        }
        PrismColumn target = chooseStructureColumn();
        if (target == null) return;
        StereoMolecule query;
        try {
            query = codec.decode(document);
        } catch (RuntimeException exception) {
            showMessage("The selected molecule could not be parsed.");
            return;
        }
        if (query.getAllAtoms() == 0) {
            showMessage("Select a non-empty molecule first.");
            return;
        }
        if (document.mode() == PrismMoleculeDocumentMode.FRAGMENT) {
            tableWorkspace.setDraftFilter(target.id(), new OclSubstructureFilter(target.id(), query));
        } else {
            tableWorkspace.setDraftFilter(target.id(), new OclSimilarityFilter(target.id(), query, 0.70));
        }
        tableWorkspace.setFocusedColumn(target.id());
        filterTargetFocused.accept(target.id());
        status.setText("Filter draft created for " + target.schema().displayName());
    }

    private PrismColumn chooseStructureColumn() {
        List<PrismColumn> columns = tableWorkspace.table().columns().stream()
                .filter(column -> column.type() == PrismColumnType.MOLECULE)
                .toList();
        if (columns.isEmpty()) {
            showMessage("This dataset has no molecule column.");
            return null;
        }
        if (columns.size() == 1) return columns.getFirst();
        ColumnChoice[] choices = columns.stream().map(ColumnChoice::new).toArray(ColumnChoice[]::new);
        ColumnChoice initial = java.util.Arrays.stream(choices)
                .filter(choice -> Objects.equals(choice.column().id(), tableWorkspace.focusedColumnId()))
                .findFirst()
                .orElse(choices[0]);
        ColumnChoice selected = (ColumnChoice) JOptionPane.showInputDialog(
                this, "Target structure column:", "Use as filter",
                JOptionPane.PLAIN_MESSAGE, null, choices, initial
        );
        return selected == null ? null : selected.column();
    }

    private List<String> selectedDocumentIds() {
        return documents.getSelectedValuesList().stream()
                .map(item -> item.document().id())
                .toList();
    }

    private void refreshFromWorkspace() {
        refreshFromWorkspace(selectedListId(), new LinkedHashSet<>(selectedDocumentIds()), activeDocumentId);
    }

    private void refreshFromWorkspace(String preferredListId, String preferredDocumentId) {
        Set<String> selectedIds = preferredDocumentId == null ? Set.of() : Set.of(preferredDocumentId);
        refreshFromWorkspace(preferredListId, selectedIds, preferredDocumentId);
    }

    private void refreshFromWorkspace(String preferredListId,
                                      Set<String> preferredDocumentIds,
                                      String preferredActiveDocumentId) {
        suppressUiEvents = true;
        try {
            listModel.clear();
            for (PrismMoleculeList list : workspace.lists()) listModel.addElement(new MoleculeListItem(list));
            int listIndex = indexOfList(preferredListId);
            if (listIndex < 0) listIndex = indexOfList(PrismMoleculeWorkspace.SCRATCHPAD_ID);
            if (listIndex >= 0) lists.setSelectedIndex(listIndex);
            refreshDocumentsWhileSuppressed(preferredDocumentIds, preferredActiveDocumentId);
        } finally {
            suppressUiEvents = false;
        }
        PrismMoleculeDocument current = activeDocumentId == null
                ? null
                : workspace.findDocument(activeDocumentId).orElse(null);
        if (current != null && current.revision() != loadedDocumentRevision && !commitTimer.isRunning()) {
            loadActiveDocument();
        } else if (current == null && documentModel.isEmpty()) {
            clearEditor();
        }
    }

    private void refreshDocuments(Set<String> preferredDocumentIds, String preferredActiveDocumentId) {
        suppressUiEvents = true;
        try {
            refreshDocumentsWhileSuppressed(preferredDocumentIds, preferredActiveDocumentId);
        } finally {
            suppressUiEvents = false;
        }
        loadActiveDocument();
    }

    private void refreshDocumentsWhileSuppressed(Set<String> preferredDocumentIds,
                                                 String preferredActiveDocumentId) {
        PrismMoleculeList selectedList = selectedList();
        documentModel.clear();
        if (selectedList != null) {
            for (PrismMoleculeDocument document : selectedList.documents()) {
                documentModel.addElement(new MoleculeDocumentItem(document));
            }
        }
        documents.clearSelection();
        for (String documentId : preferredDocumentIds == null ? Set.<String>of() : preferredDocumentIds) {
            int index = indexOfDocument(documentId);
            if (index >= 0) documents.addSelectionInterval(index, index);
        }
        int activeIndex = indexOfDocument(preferredActiveDocumentId);
        if (documents.isSelectionEmpty() && !documentModel.isEmpty()) {
            activeIndex = activeIndex < 0 ? 0 : activeIndex;
            documents.setSelectedIndex(activeIndex);
        }
        if (activeIndex < 0 && !documents.isSelectionEmpty()) activeIndex = documents.getLeadSelectionIndex();
        activeDocumentId = activeIndex < 0 ? null : documentModel.get(activeIndex).document().id();
    }

    private void loadActiveDocument() {
        PrismMoleculeDocument document = activeDocumentId == null
                ? null
                : workspace.findDocument(activeDocumentId).orElse(null);
        if (document == null) {
            clearEditor();
            return;
        }
        suppressUiEvents = true;
        try {
            fragmentMode.setSelected(document.mode() == PrismMoleculeDocumentMode.FRAGMENT);
            editor.getDrawArea().setAllowQueryFeatures(fragmentMode.isSelected());
            editor.getDrawArea().setMolecule(codec.decode(document));
            loadedDocumentRevision = document.revision();
            updateStatus(document);
        } finally {
            suppressUiEvents = false;
        }
    }

    private void clearEditor() {
        suppressUiEvents = true;
        try {
            editor.getDrawArea().setMolecule(new StereoMolecule());
            fragmentMode.setSelected(false);
            status.setText("No molecule selected");
            loadedDocumentRevision = 0;
        } finally {
            suppressUiEvents = false;
        }
    }

    private void commitPending() {
        if (commitTimer.isRunning()) {
            commitTimer.stop();
            commitEditor();
        }
    }

    private void commitEditor() {
        if (suppressUiEvents || activeDocumentId == null) return;
        PrismMoleculeDocument current = workspace.findDocument(activeDocumentId).orElse(null);
        if (current == null) return;
        PrismMoleculeDocumentMode mode = fragmentMode.isSelected()
                ? PrismMoleculeDocumentMode.FRAGMENT
                : PrismMoleculeDocumentMode.MOLECULE;
        OclMoleculeDocumentCodec.EncodedMolecule encoded = codec.encode(editor.getDrawArea().getMolecule(), mode);
        PrismMoleculeDocument updated = workspace.updateDocument(
                current.id(), current.title(), mode, encoded.idcode(), encoded.coordinates()
        );
        loadedDocumentRevision = updated.revision();
        updateStatus(updated);
    }

    private void updateStatus(PrismMoleculeDocument document) {
        String interchange;
        try {
            interchange = codec.interchange(document);
        } catch (RuntimeException exception) {
            interchange = "";
        }
        String prefix = document.mode() == PrismMoleculeDocumentMode.FRAGMENT ? "SMARTS" : "SMILES";
        status.setText(interchange.isBlank() ? prefix + ": empty" : prefix + ": " + interchange);
    }

    private MoleculeListItem selectedOrScratchpad() {
        MoleculeListItem selected = lists.getSelectedValue();
        if (selected != null) return selected;
        PrismMoleculeList scratchpad = workspace.findList(PrismMoleculeWorkspace.SCRATCHPAD_ID).orElseThrow();
        return new MoleculeListItem(scratchpad);
    }

    private PrismMoleculeList selectedList() {
        MoleculeListItem selected = lists.getSelectedValue();
        return selected == null ? null : workspace.findList(selected.list().id()).orElse(null);
    }

    private String selectedListId() {
        MoleculeListItem selected = lists.getSelectedValue();
        return selected == null ? PrismMoleculeWorkspace.SCRATCHPAD_ID : selected.list().id();
    }

    private int indexOfList(String id) {
        if (id == null) return -1;
        for (int index = 0; index < listModel.size(); index++) {
            if (id.equals(listModel.get(index).list().id())) return index;
        }
        return -1;
    }

    private int indexOfDocument(String id) {
        if (id == null) return -1;
        for (int index = 0; index < documentModel.size(); index++) {
            if (id.equals(documentModel.get(index).document().id())) return index;
        }
        return -1;
    }

    private String prompt(String title, String message, String initial) {
        Object result = JOptionPane.showInputDialog(this, message, title,
                JOptionPane.PLAIN_MESSAGE, null, null, initial);
        if (result == null) return null;
        String text = result.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "Molecule workspace", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void close() {
        commitPending();
        subscription.close();
    }

    private record ColumnChoice(PrismColumn column) {
        @Override
        public String toString() {
            return column.schema().displayName();
        }
    }

    private static final class MoleculeDocumentTileRenderer extends JPanel
            implements ListCellRenderer<MoleculeDocumentItem> {
        private final OclMoleculeDocumentCodec codec;
        private final MoleculeViewPanel moleculeView = new MoleculeViewPanel();
        private final javax.swing.JLabel title = new javax.swing.JLabel();
        private final javax.swing.JLabel mode = new javax.swing.JLabel();

        private MoleculeDocumentTileRenderer(OclMoleculeDocumentCodec codec) {
            super(new BorderLayout(4, 4));
            this.codec = codec;
            setOpaque(true);
            moleculeView.setPreferredSize(new Dimension(176, 112));
            title.setHorizontalAlignment(SwingConstants.CENTER);
            mode.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel labels = new JPanel(new BorderLayout());
            labels.setOpaque(false);
            labels.add(title, BorderLayout.CENTER);
            labels.add(mode, BorderLayout.SOUTH);
            add(moleculeView, BorderLayout.CENTER);
            add(labels, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MoleculeDocumentItem> list,
                                                      MoleculeDocumentItem value,
                                                      int index,
                                                      boolean selected,
                                                      boolean focused) {
            PrismMoleculeDocument document = value.document();
            Color background = selected
                    ? UIManager.getColor("List.selectionBackground")
                    : UIManager.getColor("List.background");
            Color foreground = selected
                    ? UIManager.getColor("List.selectionForeground")
                    : UIManager.getColor("List.foreground");
            setBackground(background);
            moleculeView.setBackground(background);
            title.setForeground(foreground);
            mode.setForeground(foreground);
            title.setText(abbreviate(document.title(), 28));
            title.setToolTipText(document.title());
            mode.setText(document.mode() == PrismMoleculeDocumentMode.FRAGMENT ? "Fragment" : "Molecule");
            try {
                StereoMolecule molecule = codec.decode(document);
                moleculeView.setMolecule(molecule.getAllAtoms() == 0 ? null : molecule);
            } catch (RuntimeException exception) {
                moleculeView.setMolecule(null);
                mode.setText("Invalid structure");
            }
            Color borderColor = selected
                    ? UIManager.getColor("List.selectionForeground")
                    : UIManager.getColor("Component.borderColor");
            if (borderColor == null) borderColor = Color.GRAY;
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));
            return this;
        }

        private static String abbreviate(String value, int maximumLength) {
            if (value == null || value.length() <= maximumLength) return value;
            return value.substring(0, maximumLength - 3) + "...";
        }
    }

    private record MoleculeListItem(PrismMoleculeList list) {
        @Override
        public String toString() {
            return list.title() + " (" + list.documents().size() + ")";
        }
    }

    private record MoleculeDocumentItem(PrismMoleculeDocument document) {
        @Override
        public String toString() {
            return document.title() + (document.mode() == PrismMoleculeDocumentMode.FRAGMENT ? " [fragment]" : "");
        }
    }
}
