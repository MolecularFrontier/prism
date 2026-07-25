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
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;
import tech.molecules.structurized.prismlite.swing.workspace.chem.MoleculeRenderUtil;
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
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Objects;

public final class PrismMoleculeWorkspacePanel extends JPanel implements AutoCloseable {
    private final PrismMoleculeWorkspace workspace;
    private final PrismLiteWorkspaceModel tableWorkspace;
    private final OclMoleculeDocumentCodec codec = new OclMoleculeDocumentCodec();
    private final DefaultListModel<MoleculeListItem> listModel = new DefaultListModel<>();
    private final DefaultListModel<MoleculeDocumentItem> documentModel = new DefaultListModel<>();
    private final JList<MoleculeListItem> lists = new JList<>(listModel);
    private final JList<MoleculeDocumentItem> documents = new JList<>(documentModel);
    private final SwingEditorPanel editor = new SwingEditorPanel(new StereoMolecule());
    private final JCheckBox fragmentMode = new JCheckBox("Fragment");
    private final javax.swing.JLabel status = new javax.swing.JLabel(" ");
    private final Timer commitTimer;
    private final PrismMoleculeWorkspaceSubscription subscription;
    private boolean suppressUiEvents;
    private String activeDocumentId;
    private long loadedDocumentRevision;

    public PrismMoleculeWorkspacePanel(PrismMoleculeWorkspace workspace,
                                       PrismLiteWorkspaceModel tableWorkspace) {
        super(new BorderLayout());
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.tableWorkspace = Objects.requireNonNull(tableWorkspace, "tableWorkspace");
        this.commitTimer = new Timer(250, event -> commitEditor());
        this.commitTimer.setRepeats(false);
        this.subscription = workspace.subscribe(change -> SwingUtilities.invokeLater(this::refreshFromWorkspace));

        lists.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        documents.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lists.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !suppressUiEvents) {
                commitPending();
                refreshDocuments(null);
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
        toolbar.add(create);
        toolbar.add(openRow);
        toolbar.addSeparator();
        toolbar.add(duplicate);
        toolbar.add(rename);
        toolbar.add(move);
        toolbar.add(remove);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(fragmentMode);
        return toolbar;
    }

    private JSplitPane content() {
        JPanel navigation = new JPanel();
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        navigation.setPreferredSize(new Dimension(260, 400));
        navigation.add(sectionHeader("Lists", listActions()));
        JScrollPane listScroll = new JScrollPane(lists);
        listScroll.setAlignmentX(LEFT_ALIGNMENT);
        navigation.add(listScroll);
        navigation.add(Box.createVerticalStrut(8));
        navigation.add(sectionHeader("Molecules", documentOrderActions()));
        JScrollPane documentScroll = new JScrollPane(documents);
        documentScroll.setAlignmentX(LEFT_ALIGNMENT);
        navigation.add(documentScroll);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navigation, editor);
        split.setResizeWeight(0.2);
        split.setDividerLocation(260);
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
        MoleculeDocumentItem selected = documents.getSelectedValue();
        MoleculeListItem target = lists.getSelectedValue();
        if (selected == null || target == null) return;
        PrismMoleculeDocument duplicate = workspace.duplicateDocument(selected.document().id(), target.list().id());
        refreshFromWorkspace(target.list().id(), duplicate.id());
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
        MoleculeDocumentItem selected = documents.getSelectedValue();
        if (selected == null || workspace.lists().size() < 2) return;
        MoleculeListItem[] choices = workspace.lists().stream().map(MoleculeListItem::new).toArray(MoleculeListItem[]::new);
        MoleculeListItem target = (MoleculeListItem) JOptionPane.showInputDialog(
                this, "Move to list:", "Move molecule",
                JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]
        );
        if (target == null) return;
        workspace.moveDocument(selected.document().id(), target.list().id(), target.list().documents().size());
        refreshFromWorkspace(target.list().id(), selected.document().id());
    }

    private void moveDocumentBy(int offset) {
        MoleculeDocumentItem selected = documents.getSelectedValue();
        int index = documents.getSelectedIndex();
        if (selected == null || index < 0) return;
        int target = Math.max(0, Math.min(documentModel.size() - 1, index + offset));
        if (target == index) return;
        workspace.reorderDocument(selected.document().id(), target);
        refreshFromWorkspace(selectedListId(), selected.document().id());
    }

    private void deleteDocument() {
        MoleculeDocumentItem selected = documents.getSelectedValue();
        if (selected == null) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete '" + selected.document().title() + "'?",
                "Delete molecule",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) {
            commitTimer.stop();
            activeDocumentId = null;
            workspace.deleteDocument(selected.document().id());
        }
    }

    private void refreshFromWorkspace() {
        refreshFromWorkspace(selectedListId(), activeDocumentId);
    }

    private void refreshFromWorkspace(String preferredListId, String preferredDocumentId) {
        suppressUiEvents = true;
        try {
            String listId = preferredListId;
            listModel.clear();
            for (PrismMoleculeList list : workspace.lists()) listModel.addElement(new MoleculeListItem(list));
            int listIndex = indexOfList(listId);
            if (listIndex < 0) listIndex = indexOfList(PrismMoleculeWorkspace.SCRATCHPAD_ID);
            if (listIndex >= 0) lists.setSelectedIndex(listIndex);
            refreshDocumentsWhileSuppressed(preferredDocumentId);
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

    private void refreshDocuments(String preferredDocumentId) {
        suppressUiEvents = true;
        try {
            refreshDocumentsWhileSuppressed(preferredDocumentId);
        } finally {
            suppressUiEvents = false;
        }
        loadActiveDocument();
    }

    private void refreshDocumentsWhileSuppressed(String preferredDocumentId) {
        PrismMoleculeList selectedList = selectedList();
        documentModel.clear();
        if (selectedList != null) {
            for (PrismMoleculeDocument document : selectedList.documents()) {
                documentModel.addElement(new MoleculeDocumentItem(document));
            }
        }
        int documentIndex = indexOfDocument(preferredDocumentId);
        if (documentIndex < 0 && !documentModel.isEmpty()) documentIndex = 0;
        if (documentIndex >= 0) {
            documents.setSelectedIndex(documentIndex);
            activeDocumentId = documentModel.get(documentIndex).document().id();
        } else {
            activeDocumentId = null;
        }
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
