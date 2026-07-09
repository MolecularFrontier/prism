package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ComputedValueDefinition<T> {
    private final String id;
    private final String displayName;
    private final Class<T> valueType;
    private final PrismColumnType columnType;
    private final List<String> dependencyColumnIds;
    private final List<String> dependencyComputedValueIds;
    private final CachePolicy cachePolicy;
    private final String configurationFingerprint;
    private final String implementationVersion;
    private final ComputedValueProvider<T> provider;

    private ComputedValueDefinition(Builder<T> builder) {
        this.id = requireText(builder.id, "id");
        this.displayName = builder.displayName == null || builder.displayName.isBlank() ? this.id : builder.displayName.trim();
        this.valueType = Objects.requireNonNull(builder.valueType, "valueType");
        this.columnType = builder.columnType == null ? PrismColumnType.TEXT : builder.columnType;
        this.dependencyColumnIds = List.copyOf(builder.dependencyColumnIds);
        this.dependencyComputedValueIds = List.copyOf(builder.dependencyComputedValueIds);
        this.cachePolicy = builder.cachePolicy == null ? CachePolicy.LAZY : builder.cachePolicy;
        this.configurationFingerprint = builder.configurationFingerprint == null ? "default" : builder.configurationFingerprint;
        this.implementationVersion = builder.implementationVersion == null ? "1" : builder.implementationVersion;
        this.provider = Objects.requireNonNull(builder.provider, "provider");
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Class<T> valueType() {
        return valueType;
    }

    public PrismColumnType columnType() {
        return columnType;
    }

    public List<String> dependencyColumnIds() {
        return dependencyColumnIds;
    }

    public List<String> dependencyComputedValueIds() {
        return dependencyComputedValueIds;
    }

    public CachePolicy cachePolicy() {
        return cachePolicy;
    }

    public String configurationFingerprint() {
        return configurationFingerprint;
    }

    public String implementationVersion() {
        return implementationVersion;
    }

    public ComputedValueProvider<T> provider() {
        return provider;
    }

    public String definitionFingerprint() {
        return id + ":" + implementationVersion + ":" + configurationFingerprint;
    }

    public PrismColumnSchema columnSchema() {
        return new PrismColumnSchema(
                id,
                columnType,
                displayName,
                "computed_value",
                "computed",
                null,
                null,
                null,
                null,
                Map.of(
                        "computedValueId", id,
                        "implementationVersion", implementationVersion,
                        "configurationFingerprint", configurationFingerprint
                )
        );
    }

    public static <T> Builder<T> builder(String id, Class<T> valueType) {
        return new Builder<>(id, valueType);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public static final class Builder<T> {
        private final String id;
        private final Class<T> valueType;
        private String displayName;
        private PrismColumnType columnType;
        private List<String> dependencyColumnIds = List.of();
        private List<String> dependencyComputedValueIds = List.of();
        private CachePolicy cachePolicy = CachePolicy.LAZY;
        private String configurationFingerprint;
        private String implementationVersion;
        private ComputedValueProvider<T> provider;

        private Builder(String id, Class<T> valueType) {
            this.id = id;
            this.valueType = valueType;
        }

        public Builder<T> displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder<T> columnType(PrismColumnType columnType) {
            this.columnType = columnType;
            return this;
        }

        public Builder<T> dependencyColumnIds(List<String> dependencyColumnIds) {
            this.dependencyColumnIds = dependencyColumnIds == null ? List.of() : new ArrayList<>(dependencyColumnIds);
            return this;
        }

        public Builder<T> dependencyComputedValueIds(List<String> dependencyComputedValueIds) {
            this.dependencyComputedValueIds = dependencyComputedValueIds == null ? List.of() : new ArrayList<>(dependencyComputedValueIds);
            return this;
        }

        public Builder<T> cachePolicy(CachePolicy cachePolicy) {
            this.cachePolicy = cachePolicy;
            return this;
        }

        public Builder<T> configurationFingerprint(String configurationFingerprint) {
            this.configurationFingerprint = configurationFingerprint;
            return this;
        }

        public Builder<T> implementationVersion(String implementationVersion) {
            this.implementationVersion = implementationVersion;
            return this;
        }

        public Builder<T> provider(ComputedValueProvider<T> provider) {
            this.provider = provider;
            return this;
        }

        public ComputedValueDefinition<T> build() {
            return new ComputedValueDefinition<>(this);
        }
    }
}
