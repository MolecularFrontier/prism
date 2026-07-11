package tech.molecules.structurized.prismlite.swing.chembl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ChemblPublicationClient {
    private static final URI DEFAULT_BASE_URI = URI.create("https://www.ebi.ac.uk/chembl/api/data/");
    private static final int PAGE_LIMIT = 1000;
    private static final int BATCH_SIZE = 100;

    private final URI baseUri;
    private final ObjectMapper mapper;
    private final PageFetcher fetcher;

    public ChemblPublicationClient() {
        this(DEFAULT_BASE_URI);
    }

    public ChemblPublicationClient(URI baseUri) {
        this(baseUri, new ObjectMapper(), new HttpPageFetcher(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()));
    }

    ChemblPublicationClient(URI baseUri, ObjectMapper mapper, PageFetcher fetcher) {
        this.baseUri = normalizeBaseUri(baseUri);
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
    }

    public ChemblPublicationSourceData fetchPublication(String documentChemblId) throws IOException, InterruptedException {
        String normalizedDocumentId = ChemblPublicationImportOptions.defaults(documentChemblId).documentChemblId();
        List<JsonNode> documents = fetchCollection("document", "documents", Map.of("document_chembl_id", normalizedDocumentId));
        if (documents.isEmpty()) {
            throw new IOException("ChEMBL document not found: " + normalizedDocumentId);
        }
        ChemblPublicationSourceData.DocumentInfo document = documentInfo(documents.getFirst());

        List<ChemblPublicationSourceData.ActivityInfo> activities = fetchCollection("activity", "activities", Map.of("document_chembl_id", normalizedDocumentId))
                .stream()
                .map(this::activityInfo)
                .toList();

        List<String> assayIds = activities.stream()
                .map(ChemblPublicationSourceData.ActivityInfo::assayChemblId)
                .filter(ChemblPublicationClient::hasText)
                .distinct()
                .toList();
        Map<String, ChemblPublicationSourceData.AssayInfo> assaysById = new LinkedHashMap<>();
        for (JsonNode assay : fetchBatched("assay", "assays", "assay_chembl_id__in", assayIds)) {
            ChemblPublicationSourceData.AssayInfo info = assayInfo(assay);
            if (hasText(info.assayChemblId())) {
                assaysById.put(info.assayChemblId(), info);
            }
        }

        List<String> moleculeIds = activities.stream()
                .flatMap(activity -> Stream.of(activity.parentMoleculeChemblId(), activity.moleculeChemblId()))
                .filter(ChemblPublicationClient::hasText)
                .distinct()
                .toList();
        Map<String, ChemblPublicationSourceData.MoleculeInfo> moleculesById = new LinkedHashMap<>();
        for (JsonNode molecule : fetchBatched("molecule", "molecules", "molecule_chembl_id__in", moleculeIds)) {
            ChemblPublicationSourceData.MoleculeInfo info = moleculeInfo(molecule);
            if (hasText(info.moleculeChemblId())) {
                moleculesById.put(info.moleculeChemblId(), info);
            }
        }

        return new ChemblPublicationSourceData(document, activities, assaysById, moleculesById);
    }

    private List<JsonNode> fetchBatched(String collection, String arrayName, String filterName, List<String> ids) throws IOException, InterruptedException {
        ArrayList<JsonNode> rows = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            String value = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size())).stream()
                    .collect(Collectors.joining(","));
            rows.addAll(fetchCollection(collection, arrayName, Map.of(filterName, value)));
        }
        return List.copyOf(rows);
    }

    private List<JsonNode> fetchCollection(String collection, String arrayName, Map<String, String> filters) throws IOException, InterruptedException {
        ArrayList<JsonNode> rows = new ArrayList<>();
        URI next = collectionUri(collection, filters);
        while (next != null) {
            JsonNode page = fetcher.fetch(next, mapper);
            JsonNode array = page.path(arrayName);
            if (array.isArray()) {
                array.forEach(rows::add);
            }
            String nextText = text(page.path("page_meta").path("next"));
            next = hasText(nextText) ? resolve(nextText) : null;
        }
        return List.copyOf(rows);
    }

    private URI collectionUri(String collection, Map<String, String> filters) {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>(filters);
        parameters.put("limit", Integer.toString(PAGE_LIMIT));
        String query = parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return baseUri.resolve(collection + ".json?" + query);
    }

    private URI resolve(String next) {
        if (next.startsWith("http://") || next.startsWith("https://")) {
            return URI.create(next);
        }
        if (next.startsWith("/")) {
            return URI.create(baseUri.getScheme() + "://" + baseUri.getHost()).resolve(next);
        }
        return baseUri.resolve(next);
    }

    private ChemblPublicationSourceData.DocumentInfo documentInfo(JsonNode node) {
        return new ChemblPublicationSourceData.DocumentInfo(
                text(node.path("document_chembl_id")),
                text(node.path("title")),
                text(node.path("journal")),
                text(node.path("year")),
                text(node.path("doi")),
                text(node.path("pubmed_id"))
        );
    }

    private ChemblPublicationSourceData.ActivityInfo activityInfo(JsonNode node) {
        return new ChemblPublicationSourceData.ActivityInfo(
                text(node.path("activity_id")),
                text(node.path("molecule_chembl_id")),
                firstText(node, "parent_molecule_chembl_id", "parent_molecule_chembl_id__chembl_id"),
                text(node.path("assay_chembl_id")),
                text(node.path("target_chembl_id")),
                firstText(node, "target_pref_name", "target_name"),
                text(node.path("standard_type")),
                text(node.path("standard_relation")),
                number(node.path("standard_value")),
                text(node.path("standard_units")),
                number(node.path("pchembl_value")),
                text(node.path("data_validity_comment")),
                text(node.path("potential_duplicate")),
                text(node.path("canonical_smiles")),
                text(node.path("molecule_pref_name"))
        );
    }

    private ChemblPublicationSourceData.AssayInfo assayInfo(JsonNode node) {
        return new ChemblPublicationSourceData.AssayInfo(
                text(node.path("assay_chembl_id")),
                text(node.path("description")),
                text(node.path("assay_type")),
                integer(node.path("confidence_score")),
                text(node.path("target_chembl_id")),
                firstText(node, "target_pref_name", "target_name"),
                text(node.path("target_organism"))
        );
    }

    private ChemblPublicationSourceData.MoleculeInfo moleculeInfo(JsonNode node) {
        return new ChemblPublicationSourceData.MoleculeInfo(
                text(node.path("molecule_chembl_id")),
                text(node.path("pref_name")),
                text(node.path("molecule_structures").path("canonical_smiles"))
        );
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node.path(field));
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return hasText(value) ? value.trim() : null;
    }

    private static Double number(JsonNode node) {
        String value = text(node);
        if (!hasText(value)) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer integer(JsonNode node) {
        String value = text(node);
        if (!hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static URI normalizeBaseUri(URI uri) {
        String text = Objects.requireNonNull(uri, "baseUri").toString();
        return URI.create(text.endsWith("/") ? text : text + "/");
    }

    @FunctionalInterface
    interface PageFetcher {
        JsonNode fetch(URI uri, ObjectMapper mapper) throws IOException, InterruptedException;
    }

    private record HttpPageFetcher(HttpClient client) implements PageFetcher {
        @Override
        public JsonNode fetch(URI uri, ObjectMapper mapper) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("ChEMBL API request failed with HTTP " + response.statusCode() + ": " + uri);
            }
            return mapper.readTree(response.body());
        }
    }
}
