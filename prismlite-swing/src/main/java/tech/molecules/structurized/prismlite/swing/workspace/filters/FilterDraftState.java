package tech.molecules.structurized.prismlite.swing.workspace.filters;

import tech.molecules.structurized.prism.engine.PrismFilter;

import java.util.Objects;

public final class FilterDraftState {
    private final PrismFilter filter;
    private final boolean enabled;
    private final boolean inverted;

    public FilterDraftState(PrismFilter filter, boolean enabled, boolean inverted) {
        this.filter = filter;
        this.enabled = enabled;
        this.inverted = inverted;
    }

    public PrismFilter filter() {
        return filter;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean inverted() {
        return inverted;
    }

    public FilterDraftState withFilter(PrismFilter nextFilter) {
        return new FilterDraftState(nextFilter, enabled, inverted);
    }

    public FilterDraftState withEnabled(boolean nextEnabled) {
        return new FilterDraftState(filter, nextEnabled, inverted);
    }

    public FilterDraftState withInverted(boolean nextInverted) {
        return new FilterDraftState(filter, enabled, nextInverted);
    }

    public boolean hasFilter() {
        return filter != null;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof FilterDraftState other)) {
            return false;
        }
        return enabled == other.enabled
                && inverted == other.inverted
                && Objects.equals(filter, other.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter, enabled, inverted);
    }
}
