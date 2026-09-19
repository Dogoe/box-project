package com.endel.demobox.quote;

import com.endel.demobox.exception.InvalidApplianceTemplateException;
import com.endel.demobox.quote.dto.ApplianceLoadDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppliancePresetResolverTest {

    private AppliancePresetResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        resolver = new AppliancePresetResolver(new ApplianceTemplateCatalog(new ObjectMapper()));
    }

    @Test
    void resolve_expandsPresetUsingTemplateDefaults() {
        ApplianceLoadDto selection = new ApplianceLoadDto();
        selection.setTemplateId("fridge_medium");
        selection.setQuantity(2);

        ApplianceLoadDto resolved = resolver.resolve(selection);

        assertEquals(150, resolved.getWatts());
        assertEquals(24, resolved.getHoursPerDay());
        assertEquals(2, resolved.getQuantity());
        assertEquals(true, resolved.isMotorLoad());
        assertEquals(3.0, resolved.getSurgeMultiplier());
        assertEquals(true, resolved.isRunsSimultaneously());
    }

    @Test
    void resolve_hoursPerDayOverrideWinsOverTemplateDefault() {
        ApplianceLoadDto selection = new ApplianceLoadDto();
        selection.setTemplateId("tv_medium");
        selection.setQuantity(1);
        selection.setHoursPerDayOverride(8.0);

        ApplianceLoadDto resolved = resolver.resolve(selection);

        assertEquals(8.0, resolved.getHoursPerDay());
    }

    @Test
    void resolve_unknownTemplateIdThrows() {
        ApplianceLoadDto selection = new ApplianceLoadDto();
        selection.setTemplateId("not_a_real_template");

        assertThrows(InvalidApplianceTemplateException.class, () -> resolver.resolve(selection));
    }

    @Test
    void resolve_customEntryPassesThroughUnchanged() {
        ApplianceLoadDto custom = new ApplianceLoadDto();
        custom.setName("Custom deep freezer");
        custom.setWatts(300);
        custom.setHoursPerDay(24);

        ApplianceLoadDto resolved = resolver.resolve(custom);

        assertSame(custom, resolved);
    }
}
