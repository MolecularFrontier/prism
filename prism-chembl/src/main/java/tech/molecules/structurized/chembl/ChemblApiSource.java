package tech.molecules.structurized.chembl;

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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class ChemblApiSource implements ChemblSource {
    private static final URI DEFAULT_BASE_URI = URI.create("https://www.ebi.ac.uk/chembl/api/data/");
    private final URI baseUri;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final int maxRetries;
    private final String release;
    private final Deque<ChemblRecord> buffer = new ArrayDeque<>();
    private URI next;
    private boolean exhausted;

    public ChemblApiSource(String release, int pageSize, int maxRetries) {
        this(DEFAULT_BASE_URI, release, pageSize, maxRetries);
    }

    ChemblApiSource(URI baseUri, String release, int pageSize, int maxRetries) {
        this.baseUri = normalizeBaseUri(baseUri);
        if (pageSize < 1) throw new IllegalArgumentException("pageSize must be positive");
        this.maxRetries = Math.max(0, maxRetries);
        this.release = release;
        this.mapper = new ObjectMapper();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).version(HttpClient.Version.HTTP_1_1).build();
        String query = "limit=" + pageSize + (release == null || release.isBlank() ? "" : "&chembl_release=" + encode(release));
        this.next = this.baseUri.resolve("molecule.json?" + query);
    }

    @Override
    public boolean hasNext() throws IOException { fill(); return !buffer.isEmpty(); }

    @Override
    public ChemblRecord next() throws IOException {
        fill();
        if (buffer.isEmpty()) throw new IllegalStateException("no more ChEMBL molecules");
        return buffer.removeFirst();
    }

    private void fill() throws IOException { while (buffer.isEmpty() && !exhausted) fetchPage(); }

    private void fetchPage() throws IOException {
        if (next == null) { exhausted = true; return; }
        JsonNode page = fetch(next);
        JsonNode molecules = page.path("molecules");
        if (molecules.isArray()) molecules.forEach(node -> buffer.addLast(record(node)));
        String nextText = text(page.path("page_meta").path("next"));
        next = nextText == null ? null : resolve(nextText);
        if (buffer.isEmpty() && next == null) exhausted = true;
    }

    private JsonNode fetch(URI uri) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("Accept", "application/json").GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 200 && response.statusCode() < 300) return mapper.readTree(response.body());
                last = new IOException("ChEMBL API request failed with HTTP " + response.statusCode() + ": " + uri);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching ChEMBL API", exception);
            } catch (IOException exception) { last = exception; }
            if (attempt < maxRetries) sleep(attempt);
        }
        throw Objects.requireNonNull(last);
    }

    private static void sleep(int attempt) throws IOException {
        try { Thread.sleep(Math.min(10_000L, 250L << Math.min(attempt, 5))); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IOException("Interrupted during API backoff", exception); }
    }

    private ChemblRecord record(JsonNode node) {
        JsonNode structures = node.path("molecule_structures");
        JsonNode hierarchy = node.path("molecule_hierarchy");
        return new ChemblRecord(text(node.path("molecule_chembl_id")), text(structures.path("canonical_smiles")), text(structures.path("standard_inchi_key")),
                text(hierarchy.path("parent_chembl_id")), text(node.path("molecule_type")), text(node.path("structure_type")),
                flag(node.path("polymer_flag")), flag(node.path("inorganic_flag")), release == null ? text(node.path("chembl_release")) : release, 0, 0, "", "");
    }

    private URI resolve(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) return URI.create(value);
        if (value.startsWith("/")) return URI.create(baseUri.getScheme() + "://" + baseUri.getHost()).resolve(value);
        return baseUri.resolve(value);
    }

    private static boolean flag(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return false;
        if (node.isBoolean()) return node.booleanValue();
        if (node.isNumber()) return node.intValue() == 1;
        String value = node.asText("").trim();
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "y".equalsIgnoreCase(value);
    }
    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static URI normalizeBaseUri(URI uri) { String value = Objects.requireNonNull(uri).toString(); return URI.create(value.endsWith("/") ? value : value + "/"); }
}
