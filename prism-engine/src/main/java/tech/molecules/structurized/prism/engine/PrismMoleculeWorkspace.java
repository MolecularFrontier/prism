package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class PrismMoleculeWorkspace {
    public static final String SCRATCHPAD_ID = "scratchpad";

    private final LinkedHashMap<String, MutableList> lists = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Consumer<PrismMoleculeWorkspaceChange>> listeners = new CopyOnWriteArrayList<>();
    private long revision = 1;
    private long nextListId = 1;
    private long nextDocumentId = 1;

    public PrismMoleculeWorkspace() {
        lists.put(SCRATCHPAD_ID, new MutableList(SCRATCHPAD_ID, "Scratchpad"));
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized List<PrismMoleculeList> lists() {
        return lists.values().stream().map(MutableList::snapshot).toList();
    }

    public synchronized Optional<PrismMoleculeList> findList(String listId) {
        MutableList list = lists.get(listId);
        return list == null ? Optional.empty() : Optional.of(list.snapshot());
    }

    public synchronized Optional<PrismMoleculeDocument> findDocument(String documentId) {
        for (MutableList list : lists.values()) {
            PrismMoleculeDocument document = list.documents.get(documentId);
            if (document != null) return Optional.of(document);
        }
        return Optional.empty();
    }

    public synchronized PrismMoleculeList createList(String requestedId, String title) {
        String id = requestedId == null || requestedId.isBlank() ? nextListId() : requestedId.trim();
        if (lists.containsKey(id)) {
            throw new IllegalArgumentException("molecule list '" + id + "' already exists");
        }
        MutableList list = new MutableList(id, title == null || title.isBlank() ? id : title.trim());
        lists.put(id, list);
        publish(id, null);
        return list.snapshot();
    }

    public synchronized PrismMoleculeList renameList(String listId, String title) {
        MutableList list = requireList(listId);
        list.title = requireText(title, "molecule list title");
        publish(list.id, null);
        return list.snapshot();
    }

    public synchronized void deleteList(String listId) {
        String id = requireText(listId, "molecule list id");
        if (SCRATCHPAD_ID.equals(id)) {
            throw new IllegalArgumentException("the Scratchpad molecule list cannot be deleted");
        }
        if (lists.remove(id) == null) {
            throw new IllegalArgumentException("unknown molecule list '" + id + "'");
        }
        publish(id, null);
    }

    public synchronized PrismMoleculeDocument addDocument(String listId,
                                                           String requestedId,
                                                           String title,
                                                           PrismMoleculeDocumentMode mode,
                                                           String idcode,
                                                           String coordinates) {
        MutableList list = requireList(listId);
        String id = requestedId == null || requestedId.isBlank() ? nextDocumentId() : requestedId.trim();
        if (findDocument(id).isPresent()) {
            throw new IllegalArgumentException("molecule document '" + id + "' already exists");
        }
        PrismMoleculeDocument document = new PrismMoleculeDocument(id, title, mode, idcode, coordinates, 1);
        list.documents.put(id, document);
        publish(list.id, id);
        return document;
    }

    public synchronized PrismMoleculeDocument updateDocument(String documentId,
                                                              String title,
                                                              PrismMoleculeDocumentMode mode,
                                                              String idcode,
                                                              String coordinates) {
        return updateDocument(documentId, null, title, mode, idcode, coordinates);
    }

    public synchronized PrismMoleculeDocument updateDocument(String documentId,
                                                              long expectedRevision,
                                                              String title,
                                                              PrismMoleculeDocumentMode mode,
                                                              String idcode,
                                                              String coordinates) {
        return updateDocument(documentId, Long.valueOf(expectedRevision), title, mode, idcode, coordinates);
    }

    private PrismMoleculeDocument updateDocument(String documentId,
                                                 Long expectedRevision,
                                                 String title,
                                                 PrismMoleculeDocumentMode mode,
                                                 String idcode,
                                                 String coordinates) {
        LocatedDocument located = requireDocument(documentId);
        PrismMoleculeDocument current = located.document();
        if (expectedRevision != null && current.revision() != expectedRevision) {
            throw new PrismMoleculeDocumentConflictException(current.id(), expectedRevision, current.revision());
        }
        PrismMoleculeDocument updated = new PrismMoleculeDocument(
                current.id(), title, mode, idcode, coordinates, current.revision() + 1
        );
        located.list().documents.put(current.id(), updated);
        publish(located.list().id, current.id());
        return updated;
    }

    public synchronized PrismMoleculeDocument duplicateDocument(String documentId, String targetListId) {
        PrismMoleculeDocument source = requireDocument(documentId).document();
        return addDocument(
                targetListId,
                null,
                source.title() + " copy",
                source.mode(),
                source.idcode(),
                source.coordinates()
        );
    }

    public synchronized List<PrismMoleculeDocument> duplicateDocuments(Collection<String> documentIds,
                                                                        String targetListId) {
        List<LocatedDocument> sources = requireDocuments(documentIds);
        MutableList target = requireList(targetListId);
        ArrayList<PrismMoleculeDocument> duplicates = new ArrayList<>(sources.size());
        for (LocatedDocument located : sources) {
            PrismMoleculeDocument source = located.document();
            String id = nextDocumentId();
            PrismMoleculeDocument duplicate = new PrismMoleculeDocument(
                    id, source.title() + " copy", source.mode(), source.idcode(), source.coordinates(), 1
            );
            target.documents.put(id, duplicate);
            duplicates.add(duplicate);
        }
        if (!duplicates.isEmpty()) publish(target.id, null);
        return List.copyOf(duplicates);
    }

    public synchronized void moveDocument(String documentId, String targetListId, int targetIndex) {
        LocatedDocument located = requireDocument(documentId);
        MutableList target = requireList(targetListId);
        located.list().documents.remove(documentId);
        insert(target.documents, documentId, located.document(), targetIndex);
        publish(target.id, documentId);
    }

    public synchronized void moveDocuments(Collection<String> documentIds, String targetListId, int targetIndex) {
        List<LocatedDocument> locatedDocuments = requireDocuments(documentIds);
        MutableList target = requireList(targetListId);
        ArrayList<PrismMoleculeDocument> moving = new ArrayList<>(locatedDocuments.size());
        for (LocatedDocument located : locatedDocuments) {
            located.list().documents.remove(located.document().id());
            moving.add(located.document());
        }
        int index = Math.max(0, Math.min(targetIndex, target.documents.size()));
        for (PrismMoleculeDocument document : moving) {
            insert(target.documents, document.id(), document, index++);
        }
        if (!moving.isEmpty()) publish(target.id, null);
    }

    public synchronized void reorderDocument(String documentId, int targetIndex) {
        LocatedDocument located = requireDocument(documentId);
        located.list().documents.remove(documentId);
        insert(located.list().documents, documentId, located.document(), targetIndex);
        publish(located.list().id, documentId);
    }

    public synchronized void reorderDocuments(Collection<String> documentIds, int targetIndex) {
        List<LocatedDocument> locatedDocuments = requireDocuments(documentIds);
        if (locatedDocuments.isEmpty()) return;
        MutableList list = locatedDocuments.getFirst().list();
        for (LocatedDocument located : locatedDocuments) {
            if (located.list() != list) {
                throw new IllegalArgumentException("documents must belong to the same molecule list");
            }
        }
        ArrayList<PrismMoleculeDocument> moving = new ArrayList<>(locatedDocuments.size());
        for (LocatedDocument located : locatedDocuments) {
            list.documents.remove(located.document().id());
            moving.add(located.document());
        }
        int index = Math.max(0, Math.min(targetIndex, list.documents.size()));
        for (PrismMoleculeDocument document : moving) {
            insert(list.documents, document.id(), document, index++);
        }
        publish(list.id, null);
    }

    public synchronized void deleteDocument(String documentId) {
        LocatedDocument located = requireDocument(documentId);
        located.list().documents.remove(documentId);
        publish(located.list().id, documentId);
    }

    public synchronized void deleteDocuments(Collection<String> documentIds) {
        List<LocatedDocument> locatedDocuments = requireDocuments(documentIds);
        for (LocatedDocument located : locatedDocuments) {
            located.list().documents.remove(located.document().id());
        }
        if (!locatedDocuments.isEmpty()) publish(null, null);
    }

    public PrismMoleculeWorkspaceSubscription subscribe(Consumer<PrismMoleculeWorkspaceChange> listener) {
        Consumer<PrismMoleculeWorkspaceChange> registered = Objects.requireNonNull(listener, "listener");
        listeners.add(registered);
        return () -> listeners.remove(registered);
    }

    private void publish(String listId, String documentId) {
        PrismMoleculeWorkspaceChange change = new PrismMoleculeWorkspaceChange(++revision, listId, documentId);
        for (Consumer<PrismMoleculeWorkspaceChange> listener : listeners) {
            try {
                listener.accept(change);
            } catch (RuntimeException ignored) {
                // Observers cannot roll back a completed workspace mutation.
            }
        }
    }

    private MutableList requireList(String listId) {
        String id = requireText(listId, "molecule list id");
        MutableList list = lists.get(id);
        if (list == null) throw new IllegalArgumentException("unknown molecule list '" + id + "'");
        return list;
    }

    private LocatedDocument requireDocument(String documentId) {
        String id = requireText(documentId, "molecule document id");
        for (MutableList list : lists.values()) {
            PrismMoleculeDocument document = list.documents.get(id);
            if (document != null) return new LocatedDocument(list, document);
        }
        throw new IllegalArgumentException("unknown molecule document '" + id + "'");
    }

    private String nextListId() {
        String candidate;
        do candidate = "molecule-list-" + nextListId++; while (lists.containsKey(candidate));
        return candidate;
    }

    private String nextDocumentId() {
        String candidate;
        do candidate = "molecule-" + nextDocumentId++; while (findDocument(candidate).isPresent());
        return candidate;
    }

    private static <K, V> void insert(LinkedHashMap<K, V> values, K key, V value, int targetIndex) {
        int index = Math.max(0, Math.min(targetIndex, values.size()));
        ArrayList<Map.Entry<K, V>> entries = new ArrayList<>(values.entrySet());
        entries.add(index, Map.entry(key, value));
        values.clear();
        for (Map.Entry<K, V> entry : entries) values.put(entry.getKey(), entry.getValue());
    }

    private List<LocatedDocument> requireDocuments(Collection<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return List.of();
        ArrayList<LocatedDocument> result = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (String documentId : documentIds) {
            String id = requireText(documentId, "molecule document id");
            if (seen.add(id)) result.add(requireDocument(id));
        }
        return List.copyOf(result);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static final class MutableList {
        private final String id;
        private String title;
        private final LinkedHashMap<String, PrismMoleculeDocument> documents = new LinkedHashMap<>();

        private MutableList(String id, String title) {
            this.id = id;
            this.title = title;
        }

        private PrismMoleculeList snapshot() {
            return new PrismMoleculeList(id, title, List.copyOf(documents.values()));
        }
    }

    private record LocatedDocument(MutableList list, PrismMoleculeDocument document) {
    }
}
