package com.endel.demobox.controller.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The kit editor is a page shell (like the admin panel itself): the data comes from the admin-only
 * API, which its script calls with the admin's token. The pages and their scripts must be reachable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminKitEditorPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void newKitPageIsServedWithoutAKitId() throws Exception {
        // No id: Thymeleaf leaves the empty attribute out, and the script reads a missing one as "new kit".
        mockMvc.perform(get("/admin/kits/nuevo"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        not(containsString("data-kit-id=\"1")),
                        containsString("/js/admin-common.js"),
                        containsString("/js/kit-editor.js"))));
    }

    @Test
    void editPageCarriesTheKitId() throws Exception {
        mockMvc.perform(get("/admin/kits/7/editar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-kit-id=\"7\"")));
    }

    @Test
    void theAdminPanelLoadsTheSharedScriptFirst() throws Exception {
        String html = mockMvc.perform(get("/admin")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html.indexOf("/js/admin-common.js")).isPositive()
                .isLessThan(html.indexOf("/js/admin.js"));
    }

    @Test
    void scriptsAreServed() throws Exception {
        mockMvc.perform(get("/js/admin-common.js")).andExpect(status().isOk());
        mockMvc.perform(get("/js/kit-editor.js")).andExpect(status().isOk());
    }
}
