package com.springda.devnest.flowchart;

import com.springda.devnest.common.BadRequestException;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** Strict portable graph schema: engine attributes never enter persistence or shared views. */
@Component
public class FlowchartValidator {
    private final ObjectMapper json;

    public FlowchartValidator(ObjectMapper json) {
        this.json = json;
    }

    public record Validated(String serialized, String searchText, int nodeCount, int edgeCount) {}

    public Validated validate(JsonNode graph) {
        fields(graph, "schemaVersion", "nodes", "edges");
        integer(graph.get("schemaVersion"), 1, 1);
        JsonNode nodes = graph.get("nodes"), edges = graph.get("edges");
        array(nodes, 1000);
        array(edges, 2000);
        Set<String> ids = new HashSet<>(), nodeIds = new HashSet<>();
        StringBuilder labels = new StringBuilder();
        for (JsonNode n : nodes) {
            fields(n, "id", "kind", "label", "x", "y", "width", "height", "zIndex", "style");
            String id = id(n.get("id"));
            if (!ids.add(id)) fail("Cell IDs must be unique");
            nodeIds.add(id);
            choice(
                    n.get("kind"),
                    "terminal",
                    "rectangle",
                    "rounded",
                    "diamond",
                    "io",
                    "database",
                    "text");
            label(n.get("label"), labels);
            coordinate(n.get("x"));
            coordinate(n.get("y"));
            number(n.get("width"), 10, 10000);
            number(n.get("height"), 10, 10000);
            integer(n.get("zIndex"), -10000, 10000);
            JsonNode s = n.get("style");
            fields(
                    s,
                    "fill",
                    "stroke",
                    "strokeWidth",
                    "dash",
                    "textColor",
                    "fontSize",
                    "textAlign");
            color(s.get("fill"), true);
            style(s);
            choice(s.get("textAlign"), "left", "center", "right");
        }
        for (JsonNode e : edges) {
            fields(
                    e,
                    "id",
                    "kind",
                    "source",
                    "target",
                    "label",
                    "vertices",
                    "zIndex",
                    "sourceArrow",
                    "targetArrow",
                    "style");
            if (!ids.add(id(e.get("id")))) fail("Cell IDs must be unique");
            choice(e.get("kind"), "straight", "orthogonal");
            label(e.get("label"), labels);
            endpoint(e.get("source"), nodeIds);
            endpoint(e.get("target"), nodeIds);
            integer(e.get("zIndex"), -10000, 10000);
            bool(e.get("sourceArrow"));
            bool(e.get("targetArrow"));
            JsonNode vertices = e.get("vertices");
            array(vertices, 100);
            for (JsonNode v : vertices) {
                fields(v, "x", "y");
                coordinate(v.get("x"));
                coordinate(v.get("y"));
            }
            JsonNode s = e.get("style");
            fields(s, "stroke", "strokeWidth", "dash", "textColor", "fontSize");
            style(s);
        }
        String serialized = json.writeValueAsString(canonical(graph));
        if (serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 5 * 1024 * 1024)
            fail("Diagram exceeds 5 MiB");
        return new Validated(serialized, labels.toString(), nodes.size(), edges.size());
    }

    private JsonNode canonical(JsonNode n) {
        if (n.isObject()) {
            var result = json.createObjectNode();
            n.propertyNames().stream().sorted().forEach(k -> result.set(k, canonical(n.get(k))));
            return result;
        }
        if (n.isArray()) {
            var result = json.createArrayNode();
            n.forEach(v -> result.add(canonical(v)));
            return result;
        }
        return n;
    }

    private void style(JsonNode s) {
        color(s.get("stroke"), false);
        color(s.get("textColor"), false);
        number(s.get("strokeWidth"), 0, 12);
        number(s.get("fontSize"), 8, 96);
        bool(s.get("dash"));
    }

    private void endpoint(JsonNode n, Set<String> ids) {
        fields(n, "nodeId", "port");
        if (!ids.contains(id(n.get("nodeId")))) fail("Endpoint node does not exist");
        choice(n.get("port"), "top", "right", "bottom", "left");
    }

    private void color(JsonNode n, boolean fill) {
        String v = text(n);
        if (!(fill && v.equals("transparent")) && !v.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
            fail("Invalid color");
    }

    private void label(JsonNode n, StringBuilder labels) {
        String value = text(n);
        if (value.length() > 10000) fail("Label exceeds 10000 characters");
        if (!labels.isEmpty()) labels.append('\n');
        labels.append(value);
    }

    private String id(JsonNode n) {
        String v = text(n);
        if (!v.matches("[A-Za-z0-9_-]{1,80}")) fail("Invalid cell ID");
        return v;
    }

    private String text(JsonNode n) {
        if (n == null || !n.isString()) fail("Expected text");
        return n.asString();
    }

    private void choice(JsonNode n, String... values) {
        if (!Set.of(values).contains(text(n))) fail("Unsupported value");
    }

    private void bool(JsonNode n) {
        if (n == null || !n.isBoolean()) fail("Expected boolean");
    }

    private void coordinate(JsonNode n) {
        number(n, -1000000, 1000000);
    }

    private void integer(JsonNode n, long min, long max) {
        if (n == null || !n.isIntegralNumber()) fail("Expected integer");
        number(n, min, max);
    }

    private void number(JsonNode n, double min, double max) {
        if (n == null
                || !n.isNumber()
                || !Double.isFinite(n.asDouble())
                || n.asDouble() < min
                || n.asDouble() > max) fail("Number out of bounds");
    }

    private void array(JsonNode n, int max) {
        if (n == null || !n.isArray() || n.size() > max) fail("Invalid array size");
    }

    private void fields(JsonNode n, String... names) {
        if (n == null || !n.isObject() || !n.propertyNames().equals(Set.of(names)))
            fail("Missing or unknown diagram properties");
    }

    private static void fail(String reason) {
        throw new BadRequestException(reason);
    }
}
