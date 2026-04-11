package tech.molecules.structurized.prism.provider;

import java.util.List;

/**
 * Discovery API for lightweight subject groups.
 */
public interface SubjectSetProvider {
    List<String> listSubjectSetScopes();

    List<SubjectSet> listSubjectSets();

    List<SubjectSet> listSubjectSets(String subjectSetScope);

    List<String> listSubjects(String subjectSetId, int offset, int limit);

    long countSubjects(String subjectSetId);
}
