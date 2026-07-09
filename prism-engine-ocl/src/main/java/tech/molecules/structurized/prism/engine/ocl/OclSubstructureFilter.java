package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.SSSearcher;
import com.actelion.research.chem.SSSearcherWithIndex;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.descriptor.DescriptorHandlerLongFFP512;
import tech.molecules.structurized.prism.engine.PrismEvaluationContext;
import tech.molecules.structurized.prism.engine.PrismFilter;
import tech.molecules.structurized.prism.engine.PrismTable;

import java.util.BitSet;
import java.util.Objects;
import java.util.Set;

public final class OclSubstructureFilter implements PrismFilter {
    private final String structureColumnId;
    private final StereoMolecule query;
    private final long[] queryFfp512;
    private final OclStereoMode stereoMode;
    private final int countMode;
    private final int matchMode;

    public OclSubstructureFilter(String structureColumnId, StereoMolecule query) {
        this(structureColumnId, query, OclStereoMode.IGNORE_STEREO);
    }

    public OclSubstructureFilter(String structureColumnId, StereoMolecule query, OclStereoMode stereoMode) {
        this(structureColumnId, query, stereoMode, SSSearcher.cCountModeFirstMatch, SSSearcher.cDefaultMatchMode);
    }

    public OclSubstructureFilter(String structureColumnId,
                                 StereoMolecule query,
                                 OclStereoMode stereoMode,
                                 int countMode,
                                 int matchMode) {
        if (structureColumnId == null || structureColumnId.isBlank()) {
            throw new IllegalArgumentException("structureColumnId must not be blank");
        }
        this.structureColumnId = structureColumnId;
        this.stereoMode = stereoMode == null ? OclStereoMode.IGNORE_STEREO : stereoMode;
        this.countMode = countMode;
        this.matchMode = matchMode;
        this.query = normalizeQuery(Objects.requireNonNull(query, "query"), this.stereoMode);
        this.queryFfp512 = DescriptorHandlerLongFFP512.getDefaultInstance().createDescriptor(this.query);
    }

    public String structureColumnId() {
        return structureColumnId;
    }

    public OclStereoMode stereoMode() {
        return stereoMode;
    }

    @Override
    public BitSet evaluate(PrismTable table, PrismEvaluationContext context) {
        if (context.computedValues() == null) {
            throw new IllegalStateException("OCL substructure filtering requires PrismEvaluationContext computed values");
        }
        BitSet result = new BitSet(table.rowCount());
        SSSearcherWithIndex searcher = new SSSearcherWithIndex();
        searcher.setFragment(query, queryFfp512);
        String moleculeValueId = OclComputedValueIds.molecule(structureColumnId);
        String ffpValueId = OclComputedValueIds.ffp512(structureColumnId);
        for (int row = 0; row < table.rowCount(); row++) {
            StereoMolecule molecule = context.computedValues().value(moleculeValueId, row, StereoMolecule.class);
            long[] ffp = context.computedValues().value(ffpValueId, row, long[].class);
            if (molecule == null || ffp == null) {
                continue;
            }
            StereoMolecule candidate = stereoMode == OclStereoMode.IGNORE_STEREO ? withoutStereo(molecule) : molecule;
            searcher.setMolecule(candidate, ffp);
            if (searcher.findFragmentInMolecule(countMode, matchMode) != 0) {
                result.set(row);
            }
        }
        return result;
    }

    @Override
    public Set<String> referencedColumnIds() {
        return Set.of(structureColumnId);
    }

    private static StereoMolecule normalizeQuery(StereoMolecule query, OclStereoMode stereoMode) {
        StereoMolecule normalized = new StereoMolecule(query);
        if (stereoMode == OclStereoMode.IGNORE_STEREO) {
            normalized.stripStereoInformation();
        }
        normalized.setFragment(true);
        normalized.ensureHelperArrays(StereoMolecule.cHelperCIP);
        return normalized;
    }

    private static StereoMolecule withoutStereo(StereoMolecule molecule) {
        StereoMolecule copy = new StereoMolecule(molecule);
        copy.stripStereoInformation();
        copy.ensureHelperArrays(StereoMolecule.cHelperCIP);
        return copy;
    }
}
