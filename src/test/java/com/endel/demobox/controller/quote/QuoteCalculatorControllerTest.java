package com.endel.demobox.controller.quote;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class QuoteCalculatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void calculate_mixesPresetAndCustomAppliances() throws Exception {
        // fridge_medium preset: 150W * 24h = 3.6 kWh. Custom deep freezer: 300W * 24h = 7.2 kWh. Total 10.8 kWh.
        String body = """
                {
                  "systemType": "OFF_GRID",
                  "autonomyDays": 2,
                  "peakSunHours": 5.0,
                  "appliances": [
                    { "templateId": "fridge_medium", "quantity": 1 },
                    { "name": "Custom deep freezer", "watts": 300, "hoursPerDay": 24, "quantity": 1, "motorLoad": true, "surgeMultiplier": 3.0 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/quotes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyConsumptionKwh").value(10.8))
                .andExpect(jsonPath("$.recommendedBatteryCapacityKwh").exists());
    }

    @Test
    void calculate_rejectsApplianceWithNeitherTemplateNorCustomSpecs() throws Exception {
        String body = """
                {
                  "systemType": "ON_GRID",
                  "peakSunHours": 5.0,
                  "appliances": [ { "quantity": 1 } ]
                }
                """;

        mockMvc.perform(post("/api/quotes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calculate_rejectsUnknownTemplateId() throws Exception {
        String body = """
                {
                  "systemType": "ON_GRID",
                  "peakSunHours": 5.0,
                  "appliances": [ { "templateId": "not_a_real_template", "quantity": 1 } ]
                }
                """;

        mockMvc.perform(post("/api/quotes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAppliancePresets_listsKnownCategories() throws Exception {
        mockMvc.perform(get("/api/quotes/appliance-presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.REFRIGERATOR").isArray());
    }
}
