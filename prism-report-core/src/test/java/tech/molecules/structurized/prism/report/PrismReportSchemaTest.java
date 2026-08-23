package tech.molecules.structurized.prism.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PrismReportSchemaTest {
    @Test
    void describesEveryRegisteredBuiltInBlockWithParseableExamples() throws Exception {
        PrismReportSchema schema = PrismReportSchema.current();
        assertEquals(1, schema.prismReportVersion());
        assertEquals(".prism.md", schema.fileExtension());
        assertEquals(Set.copyOf(PrismReportBlockRegistry.defaults().blockTypes()),
                schema.blockTypes().stream().map(PrismReportBlockSchema::type).collect(Collectors.toSet()));

        ObjectMapper mapper = new ObjectMapper();
        for (PrismReportBlockSchema block : schema.blockTypes()) {
            String source = """
                    ---
                    prismReportVersion: 1
                    dataset: current
                    title: Schema example
                    ---

                    ~~~prism
                    %s
                    ~~~
                    """.formatted(mapper.writeValueAsString(block.example()));
            PrismReportDocument parsed = new PrismReportParser().parse(source);
            assertFalse(parsed.hasErrors(), block.type());
        }
    }
}
