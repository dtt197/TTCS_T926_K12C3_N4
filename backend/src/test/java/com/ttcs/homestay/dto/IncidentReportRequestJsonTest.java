package com.ttcs.homestay.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

@JsonTest
class IncidentReportRequestJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deserializesAndSerializesDescriptionWithoutConflictingProperties() throws Exception {
        IncidentReportRequest request = objectMapper.readValue(
                "{\"severity\":\"NHE\",\"description\":\"TEST SCRUM-137\"}",
                IncidentReportRequest.class);

        assertThat(request.severity()).isEqualTo("NHE");
        assertThat(request.description()).isEqualTo("TEST SCRUM-137");

        var serialized = objectMapper.readTree(objectMapper.writeValueAsString(request));
        assertThat(serialized.size()).isEqualTo(2);
        assertThat(serialized.path("severity").asString()).isEqualTo("NHE");
        assertThat(serialized.path("description").asString()).isEqualTo("TEST SCRUM-137");
    }
}
