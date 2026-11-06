package com.endel.demobox.model.dto.catalog.transfer;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** What an import did (or, when only reviewing, would do), row by row. */
@Data
public class CatalogImportResultDto {

    public enum Action { CREATE, UPDATE, UNCHANGED, ERROR }

    /** True only when the import was confirmed and had no errors, so everything was saved. */
    private boolean saved;
    private boolean dryRun;
    private Summary products = new Summary();
    private Summary kits = new Summary();
    /** Problems with the file as a whole (not tied to one row). */
    private List<String> fileErrors = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();

    public boolean hasErrors() {
        return !fileErrors.isEmpty() || rows.stream().anyMatch(row -> row.getAction() == Action.ERROR);
    }

    public int getErrorCount() {
        return fileErrors.size() + (int) rows.stream().filter(row -> row.getAction() == Action.ERROR).count();
    }

    @Data
    public static class Summary {
        private int created;
        private int updated;
        private int unchanged;
        private int errors;

        void count(Action action) {
            switch (action) {
                case CREATE -> created++;
                case UPDATE -> updated++;
                case UNCHANGED -> unchanged++;
                case ERROR -> errors++;
            }
        }
    }

    @Data
    public static class Row {
        /** "product" or "kit". */
        private String section;
        /** 1-based position inside its list in the file. */
        private int position;
        /** SKU of a product, name of a kit. */
        private String key;
        private String name;
        /** Product type label in Spanish, e.g. "Batería"; "Kit" for kits. */
        private String typeLabel;
        private Action action;
        private List<String> errors = new ArrayList<>();
    }

    public void add(Row row) {
        rows.add(row);
        ("kit".equals(row.getSection()) ? kits : products).count(row.getAction());
    }
}
