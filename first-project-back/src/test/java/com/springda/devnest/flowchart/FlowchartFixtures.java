package com.springda.devnest.flowchart;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class FlowchartFixtures {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private FlowchartFixtures() {}

    public static JsonNode graph(String label) {
        return JSON.readTree(
                """
                {"schemaVersion":1,"nodes":[{"id":"n1","kind":"rectangle","label":%s,"x":0,"y":0,"width":160,"height":80,"zIndex":1,"style":{"fill":"#FFFFFF","stroke":"#123456","strokeWidth":1,"dash":false,"textColor":"#123456","fontSize":14,"textAlign":"center"}}],"edges":[{"id":"e1","kind":"orthogonal","source":{"nodeId":"n1","port":"right"},"target":{"nodeId":"n1","port":"left"},"label":"edge label","vertices":[],"zIndex":0,"sourceArrow":false,"targetArrow":true,"style":{"stroke":"#123456","strokeWidth":1,"dash":false,"textColor":"#123456","fontSize":14}}]}
                """
                        .formatted(JSON.writeValueAsString(label)));
    }
}
