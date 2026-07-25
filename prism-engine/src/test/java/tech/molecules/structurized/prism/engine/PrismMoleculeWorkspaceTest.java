package tech.molecules.structurized.prism.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrismMoleculeWorkspaceTest {
    @Test
    void managesOrderedListsAndDocuments() {
        PrismMoleculeWorkspace workspace = new PrismMoleculeWorkspace();
        PrismMoleculeList ideas = workspace.createList("ideas", "Ideas");
        PrismMoleculeDocument first = workspace.addDocument(ideas.id(), null, "First",
                PrismMoleculeDocumentMode.MOLECULE, "idcode-a", "coords-a");
        PrismMoleculeDocument second = workspace.addDocument(ideas.id(), null, "Query",
                PrismMoleculeDocumentMode.FRAGMENT, "idcode-b", "coords-b");

        workspace.reorderDocument(second.id(), 0);

        assertEquals("Scratchpad", workspace.lists().getFirst().title());
        assertEquals(java.util.List.of(second.id(), first.id()), workspace.findList("ideas").orElseThrow()
                .documents().stream().map(PrismMoleculeDocument::id).toList());
        assertEquals(PrismMoleculeDocumentMode.FRAGMENT, workspace.findDocument(second.id()).orElseThrow().mode());
    }

    @Test
    void updatesRevisionAndPublishesChanges() {
        PrismMoleculeWorkspace workspace = new PrismMoleculeWorkspace();
        ArrayList<PrismMoleculeWorkspaceChange> changes = new ArrayList<>();
        workspace.subscribe(changes::add);
        PrismMoleculeDocument created = workspace.addDocument(PrismMoleculeWorkspace.SCRATCHPAD_ID, null,
                "Example", PrismMoleculeDocumentMode.MOLECULE, "idcode-a", "");
        PrismMoleculeDocument updated = workspace.updateDocument(created.id(), "Edited",
                PrismMoleculeDocumentMode.FRAGMENT, "idcode-b", "coords");

        assertEquals(2, updated.revision());
        assertEquals(2, changes.size());
        assertEquals(workspace.revision(), changes.getLast().revision());
        assertEquals(created.id(), changes.getLast().documentId());
    }

    @Test
    void batchOperationsPublishOnceAndPreserveOrder() {
        PrismMoleculeWorkspace workspace = new PrismMoleculeWorkspace();
        workspace.createList("ideas", "Ideas");
        PrismMoleculeDocument first = workspace.addDocument("ideas", null, "First",
                PrismMoleculeDocumentMode.MOLECULE, "a", "");
        PrismMoleculeDocument second = workspace.addDocument("ideas", null, "Second",
                PrismMoleculeDocumentMode.MOLECULE, "b", "");
        PrismMoleculeDocument third = workspace.addDocument("ideas", null, "Third",
                PrismMoleculeDocumentMode.MOLECULE, "c", "");
        ArrayList<PrismMoleculeWorkspaceChange> changes = new ArrayList<>();
        workspace.subscribe(changes::add);

        workspace.reorderDocuments(java.util.List.of(second.id(), third.id()), 0);

        assertEquals(1, changes.size());
        assertEquals(java.util.List.of(second.id(), third.id(), first.id()), workspace.findList("ideas").orElseThrow()
                .documents().stream().map(PrismMoleculeDocument::id).toList());

        java.util.List<PrismMoleculeDocument> copies =
                workspace.duplicateDocuments(java.util.List.of(second.id(), third.id()), PrismMoleculeWorkspace.SCRATCHPAD_ID);
        assertEquals(2, changes.size());
        assertEquals(java.util.List.of("Second copy", "Third copy"),
                copies.stream().map(PrismMoleculeDocument::title).toList());

        workspace.deleteDocuments(copies.stream().map(PrismMoleculeDocument::id).toList());
        assertEquals(3, changes.size());
        assertEquals(0, workspace.findList(PrismMoleculeWorkspace.SCRATCHPAD_ID).orElseThrow().documents().size());
    }

    @Test
    void protectsScratchpadAndGlobalDocumentIdentity() {
        PrismMoleculeWorkspace workspace = new PrismMoleculeWorkspace();
        workspace.addDocument(PrismMoleculeWorkspace.SCRATCHPAD_ID, "same", "One",
                PrismMoleculeDocumentMode.MOLECULE, "idcode", "");
        workspace.createList("other", "Other");

        assertThrows(IllegalArgumentException.class, () -> workspace.deleteList(PrismMoleculeWorkspace.SCRATCHPAD_ID));
        assertThrows(IllegalArgumentException.class, () -> workspace.addDocument("other", "same", "Two",
                PrismMoleculeDocumentMode.MOLECULE, "idcode", ""));
    }
}
