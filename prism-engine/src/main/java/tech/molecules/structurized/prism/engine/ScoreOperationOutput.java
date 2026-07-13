package tech.molecules.structurized.prism.engine;

import tech.molecules.structurized.prism.score.EndpointScoreEvaluation;
import tech.molecules.structurized.prism.score.MpoComponentEvaluation;
import tech.molecules.structurized.prism.score.MpoEvaluation;
import tech.molecules.structurized.prism.score.PropertyProfileDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

final class ScoreOperationOutput {
    private ScoreOperationOutput() {
    }

    static Map<String, Object> profile(PropertyProfileDefinition profile) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("id", profile.id());
        value.put("title", profile.title());
        value.put("description", profile.description());
        value.put("items", profile.items().stream().map(item -> Map.of(
                "endpointId", item.endpointId(), "scoreId", item.scoreId() == null ? "" : item.scoreId(),
                "label", item.displayLabel(), "group", item.group() == null ? "" : item.group(),
                "order", item.order(), "visible", item.visible())).toList());
        value.put("mpos", profile.mpos().stream().map(mpo -> Map.of(
                "id", mpo.id(), "displayName", mpo.displayName(),
                "componentCount", mpo.components().size())).toList());
        value.put("metadata", profile.metadata());
        return value;
    }

    static Map<String, Object> score(EndpointScoreEvaluation evaluation) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("scoreId", evaluation.scoreId());
        value.put("endpointId", evaluation.endpointId());
        value.put("inputValue", evaluation.inputValue());
        value.put("score", evaluation.score());
        value.put("state", evaluation.state());
        value.put("message", evaluation.message());
        return value;
    }

    static Map<String, Object> mpo(MpoEvaluation evaluation) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("mpoId", evaluation.mpoId());
        value.put("score", evaluation.score());
        value.put("coverage", evaluation.coverage());
        value.put("availableCount", evaluation.availableCount());
        value.put("missingCount", evaluation.missingCount());
        value.put("hardFailCount", evaluation.hardFailCount());
        value.put("requiredMissingCount", evaluation.requiredMissingCount());
        value.put("status", evaluation.status().name());
        value.put("components", evaluation.components().stream().map(ScoreOperationOutput::component).toList());
        return value;
    }

    private static Map<String, Object> component(MpoComponentEvaluation component) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("endpointId", component.definition().endpointId());
        value.put("scoreId", component.definition().scoreId());
        value.put("label", component.definition().label());
        value.put("weight", component.definition().weight());
        value.put("required", component.definition().required());
        value.put("hardFailBelow", component.definition().hardFailBelow());
        value.put("hardFail", component.hardFail());
        value.put("evaluation", score(component.score()));
        return value;
    }
}
