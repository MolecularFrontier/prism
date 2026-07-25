package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.descriptor.DescriptorHandlerLongFFP512;
import tech.molecules.structurized.prism.engine.ColumnFilter;
import tech.molecules.structurized.prism.engine.PrismEvaluationContext;
import tech.molecules.structurized.prism.engine.PrismTable;

import java.util.BitSet;
import java.util.Objects;

public final class OclSimilarityFilter extends ColumnFilter {
    private final StereoMolecule query;
    private final long[] queryFfp512;
    private final double minimumSimilarity;

    public OclSimilarityFilter(String structureColumnId, StereoMolecule query, double minimumSimilarity) {
        super(structureColumnId);
        if (!Double.isFinite(minimumSimilarity) || minimumSimilarity < 0.0 || minimumSimilarity > 1.0) {
            throw new IllegalArgumentException("minimumSimilarity must be between 0 and 1");
        }
        this.query = new StereoMolecule(Objects.requireNonNull(query, "query"));
        this.query.setFragment(false);
        this.query.ensureHelperArrays(StereoMolecule.cHelperCIP);
        this.queryFfp512 = DescriptorHandlerLongFFP512.getDefaultInstance().createDescriptor(this.query);
        this.minimumSimilarity = minimumSimilarity;
    }

    public StereoMolecule query() {
        return new StereoMolecule(query);
    }

    public double minimumSimilarity() {
        return minimumSimilarity;
    }

    @Override
    public BitSet evaluate(PrismTable table, PrismEvaluationContext context) {
        if (context.computedValues() == null) {
            throw new IllegalStateException("OCL similarity filtering requires PrismEvaluationContext computed values");
        }
        BitSet result = new BitSet(table.rowCount());
        DescriptorHandlerLongFFP512 descriptor = DescriptorHandlerLongFFP512.getDefaultInstance();
        String ffpValueId = OclComputedValueIds.ffp512(columnId());
        for (int row = 0; row < table.rowCount(); row++) {
            long[] candidate = context.computedValues().value(ffpValueId, row, long[].class);
            if (candidate != null && descriptor.getSimilarity(queryFfp512, candidate) >= minimumSimilarity) {
                result.set(row);
            }
        }
        return result;
    }
}
