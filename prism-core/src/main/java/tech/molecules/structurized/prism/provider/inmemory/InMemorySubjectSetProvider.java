package tech.molecules.structurized.prism.provider.inmemory;

import tech.molecules.structurized.prism.provider.SubjectSet;
import tech.molecules.structurized.prism.provider.SubjectSetProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * In-memory {@link SubjectSetProvider} backed by an {@link InMemoryPrismDataset}.
 */
public final class InMemorySubjectSetProvider implements SubjectSetProvider {
    private final InMemoryPrismDataset dataset;

    public InMemorySubjectSetProvider(InMemoryPrismDataset dataset) {
        this.dataset = Objects.requireNonNull(dataset, "dataset must not be null");
    }

    @Override
    public List<String> listSubjectSetScopes() {
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        for (SubjectSet subjectSet : dataset.getSubjectSets()) {
            if (subjectSet.getSubjectSetScope() != null) {
                scopes.add(subjectSet.getSubjectSetScope());
            }
        }
        return List.copyOf(scopes);
    }

    @Override
    public List<SubjectSet> listSubjectSets() {
        return dataset.getSubjectSets();
    }

    @Override
    public List<SubjectSet> listSubjectSets(String subjectSetScope) {
        if (subjectSetScope == null || subjectSetScope.trim().isEmpty()) {
            return List.of();
        }
        ArrayList<SubjectSet> sets = new ArrayList<>();
        for (SubjectSet subjectSet : dataset.getSubjectSets()) {
            if (subjectSetScope.equals(subjectSet.getSubjectSetScope())) {
                sets.add(subjectSet);
            }
        }
        return List.copyOf(sets);
    }

    @Override
    public List<String> listSubjects(String subjectSetId, int offset, int limit) {
        validatePaging(offset, limit);
        List<String> subjects = dataset.getSubjectsForSet(subjectSetId);
        if (subjects.isEmpty() || limit == 0 || offset >= subjects.size()) {
            return List.of();
        }
        int toIndex = Math.min(subjects.size(), offset + limit);
        return List.copyOf(subjects.subList(offset, toIndex));
    }

    @Override
    public long countSubjects(String subjectSetId) {
        return dataset.getSubjectsForSet(subjectSetId).size();
    }

    private static void validatePaging(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0");
        }
    }
}
