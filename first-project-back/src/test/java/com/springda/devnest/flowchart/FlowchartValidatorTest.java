package com.springda.devnest.flowchart;

import static org.assertj.core.api.Assertions.*;

import com.springda.devnest.common.BadRequestException;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.*;

class FlowchartValidatorTest {
    final FlowchartValidator validator = new FlowchartValidator(JsonMapper.builder().build());

    ObjectNode graph() {
        return (ObjectNode) FlowchartFixtures.graph("<b>plain literal text</b>");
    }

    @Test
    void acceptsSelfLoopsPlainTextAndCanonicalizesObjectOrder() {
        var g = graph();
        var result = validator.validate(g);
        assertThat(result.nodeCount()).isEqualTo(1);
        assertThat(result.edgeCount()).isEqualTo(1);
        assertThat(result.searchText()).contains("<b>plain literal text</b>", "edge label");
        var reordered = JsonMapper.builder().build().createObjectNode();
        reordered.set("edges", g.get("edges"));
        reordered.set("nodes", g.get("nodes"));
        reordered.put("schemaVersion", 1);
        assertThat(validator.validate(reordered).serialized()).isEqualTo(result.serialized());
    }

    @Test
    void rejectsUnknownAndMissingFieldsAtEveryNestedBoundary() {
        for (String pointer :
                new String[] {
                    "",
                    "/nodes/0",
                    "/nodes/0/style",
                    "/edges/0",
                    "/edges/0/style",
                    "/edges/0/source"
                }) {
            var g = graph();
            ((ObjectNode) g.at(pointer)).put("html", "https://remote.test/x");
            assertThatThrownBy(() -> validator.validate(g)).isInstanceOf(BadRequestException.class);
        }
        var g = graph();
        ((ObjectNode) g.at("/nodes/0")).remove("width");
        assertThatThrownBy(() -> validator.validate(g)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsDuplicateDanglingIdsAndExternalColors() {
        var duplicate = graph();
        ((ObjectNode) duplicate.at("/edges/0")).put("id", "n1");
        assertThatThrownBy(() -> validator.validate(duplicate))
                .isInstanceOf(BadRequestException.class);
        var missing = graph();
        ((ObjectNode) missing.at("/edges/0/source")).put("nodeId", "other");
        assertThatThrownBy(() -> validator.validate(missing))
                .isInstanceOf(BadRequestException.class);
        var remote = graph();
        ((ObjectNode) remote.at("/nodes/0/style")).put("fill", "url(https://remote.test/x)");
        assertThatThrownBy(() -> validator.validate(remote))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsNonFiniteNumbersAndOutOfRangeGeometry() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 1000001}) {
            var g = graph();
            ((ObjectNode) g.at("/nodes/0")).put("x", value);
            assertThatThrownBy(() -> validator.validate(g)).isInstanceOf(BadRequestException.class);
        }
        var g = graph();
        ((ObjectNode) g.at("/nodes/0")).put("width", 9);
        assertThatThrownBy(() -> validator.validate(g)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void enforcesUtf8ByteCellAndVertexLimits() {
        var g = graph();
        var n = (ArrayNode) g.get("nodes");
        for (int i = 1; i <= 1000; i++)
            n.add(((ObjectNode) n.get(0)).deepCopy().put("id", "n" + (i + 1)));
        assertThatThrownBy(() -> validator.validate(g)).isInstanceOf(BadRequestException.class);
        var vertices = graph();
        var v = (ArrayNode) vertices.at("/edges/0/vertices");
        for (int i = 0; i < 101; i++) v.addObject().put("x", i).put("y", i);
        assertThatThrownBy(() -> validator.validate(vertices))
                .isInstanceOf(BadRequestException.class);
        var big = (ObjectNode) FlowchartFixtures.graph("中".repeat(10000));
        var nodes = (ArrayNode) big.get("nodes");
        for (int i = 1; i < 200; i++)
            nodes.add(((ObjectNode) nodes.get(0)).deepCopy().put("id", "node" + i));
        assertThatThrownBy(() -> validator.validate(big))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("5 MiB");
    }
}
