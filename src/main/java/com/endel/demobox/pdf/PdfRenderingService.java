package com.endel.demobox.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Renders a Thymeleaf template straight to PDF bytes via openhtmltopdf, which only
 * understands well-formed XHTML (no browser-grade HTML5 tolerance), so every
 * templates/pdf/* template must keep tags properly closed and avoid named HTML
 * entities. These are dedicated print layouts, not the live Bootstrap pages, since
 * openhtmltopdf doesn't support flexbox/grid.
 */
@Service
public class PdfRenderingService {

    private final SpringTemplateEngine templateEngine;

    public PdfRenderingService(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] render(String templateName, Context context) {
        String html = templateEngine.process(templateName, context);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render PDF for template " + templateName, e);
        }
    }
}
