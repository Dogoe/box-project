package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.BadRequestException;
import com.endel.demobox.exception.InvalidKitCompositionException;
import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.AluminumStructure;
import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.ChargeController;
import com.endel.demobox.model.catalog.ConnectionCable;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.Product;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.dto.catalog.AluminumStructureDto;
import com.endel.demobox.model.dto.catalog.BatteryDto;
import com.endel.demobox.model.dto.catalog.ChargeControllerDto;
import com.endel.demobox.model.dto.catalog.ConnectionCableDto;
import com.endel.demobox.model.dto.catalog.InverterDto;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.model.dto.catalog.KitItemDto;
import com.endel.demobox.model.dto.catalog.SolarPanelDto;
import com.endel.demobox.model.dto.catalog.transfer.CatalogImportResultDto;
import com.endel.demobox.model.dto.catalog.transfer.CatalogImportResultDto.Action;
import com.endel.demobox.model.dto.catalog.transfer.CatalogImportResultDto.Row;
import com.endel.demobox.model.dto.catalog.transfer.KitTransferDto;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.log4j.Log4j2;
import org.hibernate.Hibernate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Moves the catalog (products and kits) in and out as one JSON file, e.g. from a local database
 * to production, or products formatted by hand from a supplier's page. Products are identified by
 * SKU and kits by name, never by database id, so a file works in any database. Importing creates
 * what is new and updates what already exists; it never deletes. Store settings, users and orders
 * are not part of the file.
 */
@Service
@Log4j2
public class CatalogTransferService {

    public static final String FORMAT = "baja-solar-catalog";
    public static final int VERSION = 1;
    static final int MAX_PRODUCTS = 5000;
    static final int MAX_KITS = 1000;

    private final ObjectMapper objectMapper;
    private final ObjectReader reader;
    private final Validator validator;
    private final TransactionTemplate transactionTemplate;
    private final ProductRepository productRepository;
    private final KitRepository kitRepository;
    private final KitService kitService;
    /** By type code, in the order products are written to the file. */
    private final Map<String, ProductType<?>> productTypes = new LinkedHashMap<>();

    public CatalogTransferService(ObjectMapper objectMapper, Validator validator, PlatformTransactionManager transactionManager,
                                  ProductRepository productRepository, KitRepository kitRepository, KitService kitService,
                                  SolarPanelService solarPanelService, BatteryService batteryService,
                                  InverterService inverterService, ChargeControllerService chargeControllerService,
                                  ConnectionCableService connectionCableService,
                                  AluminumStructureService aluminumStructureService) {
        this.objectMapper = objectMapper;
        // Exact decimals for prices; the numbers are only converted when mapped onto each DTO field.
        this.reader = objectMapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.validator = validator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.productRepository = productRepository;
        this.kitRepository = kitRepository;
        this.kitService = kitService;

        register(new ProductType<>("SOLAR_PANEL", "Panel solar", SolarPanelDto.class, SolarPanel.class,
                solarPanelService::findAll, solarPanelService::getById, solarPanelService::create, solarPanelService::update,
                Set.of()));
        register(new ProductType<>("BATTERY", "Batería", BatteryDto.class, Battery.class,
                batteryService::findAll, batteryService::getById, batteryService::create, batteryService::update,
                Set.of("capacityKwh", "depthOfDischargePercent", "maxDischargeCurrentA")));
        register(new ProductType<>("INVERTER", "Inversor", InverterDto.class, Inverter.class,
                inverterService::findAll, inverterService::getById, inverterService::create, inverterService::update,
                Set.of("maxChargePowerW", "effectiveMaxPvPowerW", "maxPvPowerEstimated")));
        register(new ProductType<>("CHARGE_CONTROLLER", "Controlador MPPT", ChargeControllerDto.class, ChargeController.class,
                chargeControllerService::findAll, chargeControllerService::getById, chargeControllerService::create,
                chargeControllerService::update, Set.of()));
        register(new ProductType<>("CABLE", "Cable", ConnectionCableDto.class, ConnectionCable.class,
                connectionCableService::findAll, connectionCableService::getById, connectionCableService::create,
                connectionCableService::update, Set.of()));
        register(new ProductType<>("STRUCTURE", "Estructura", AluminumStructureDto.class, AluminumStructure.class,
                aluminumStructureService::findAll, aluminumStructureService::getById, aluminumStructureService::create,
                aluminumStructureService::update, Set.of()));
    }

    private void register(ProductType<?> type) {
        productTypes.put(type.code, type);
    }

    // ------------------------------------------------------------------ export

    public ObjectNode exportCatalog() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("exportedAt", OffsetDateTime.now().withNano(0).toString());
        ArrayNode products = root.putArray("products");
        for (ProductType<?> type : productTypes.values()) {
            type.exportAll(this).stream()
                    .sorted(Comparator.comparing(node -> node.path("sku").asText()))
                    .forEach(products::add);
        }
        ArrayNode kits = root.putArray("kits");
        kitService.findAll().stream()
                .sorted(Comparator.comparing(KitDto::getName))
                .forEach(kit -> kits.add(kitNode(kit)));
        return root;
    }

    /** A product as written in the file: its type first, no database id, no computed values, no empty fields. */
    private ObjectNode productNode(ProductType<?> type, Object dto) {
        ObjectNode fields = objectMapper.valueToTree(dto);
        fields.remove("id");
        // The photo list carries every photo; the single field only exists for older clients.
        fields.remove("imageUrl");
        type.readOnly.forEach(fields::remove);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type.code);
        node.setAll(withoutNulls(fields));
        return node;
    }

    private ObjectNode kitNode(KitDto kit) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", kit.getName());
        if (kit.getDescription() != null) {
            node.put("description", kit.getDescription());
        }
        node.put("active", kit.isActive());
        if (kit.getPriceOverride() != null) {
            node.put("priceOverride", kit.getPriceOverride());
        }
        ArrayNode photos = node.putArray("imageUrls");
        kit.getImageUrls().forEach(photos::add);
        ArrayNode items = node.putArray("items");
        kit.getItems().stream()
                .sorted(Comparator.comparing(KitItemDto::getProductSku))
                .forEach(item -> items.addObject().put("sku", item.getProductSku()).put("quantity", item.getQuantity()));
        return node;
    }

    private static ObjectNode withoutNulls(ObjectNode node) {
        List<String> nulls = new ArrayList<>();
        node.fieldNames().forEachRemaining(name -> {
            if (node.get(name).isNull()) {
                nulls.add(name);
            }
        });
        nulls.forEach(node::remove);
        return node;
    }

    // ------------------------------------------------------------------ import

    /**
     * Checks the whole file and, unless {@code dryRun}, saves it. Saving is all or nothing: with any
     * error nothing is saved, and a dry run always rolls back, so its result is exactly what a real
     * import would do.
     */
    public CatalogImportResultDto importCatalog(String json, boolean dryRun) {
        CatalogImportResultDto result = new CatalogImportResultDto();
        result.setDryRun(dryRun);

        JsonNode root = parse(json);
        List<JsonNode> productNodes = list(root, "products", MAX_PRODUCTS, result);
        List<JsonNode> kitNodes = list(root, "kits", MAX_KITS, result);
        checkHeader(root, result);
        if (!result.getFileErrors().isEmpty()) {
            return result;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                Map<String, Boolean> skuInFile = importProducts(productNodes, result);
                importKits(kitNodes, skuInFile, result);
                if (dryRun || result.hasErrors()) {
                    status.setRollbackOnly();
                }
            });
        } catch (DataAccessException | TransactionException e) {
            log.warn("Catalog import rolled back by the database", e);
            result.getFileErrors().add("La base de datos rechazó los datos y no se guardó nada: "
                    + e.getMostSpecificCause().getMessage());
        }
        result.setSaved(!dryRun && !result.hasErrors());
        return result;
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            throw new BadRequestException("El archivo está vacío.");
        }
        try {
            JsonNode root = reader.readTree(json);
            if (root == null || !root.isObject()) {
                throw new BadRequestException("El archivo debe ser un objeto JSON: { \"products\": [...], \"kits\": [...] }.");
            }
            return root;
        } catch (JsonProcessingException e) {
            String where = e.getLocation() == null ? "" : " (línea " + e.getLocation().getLineNr() + ", columna "
                    + e.getLocation().getColumnNr() + ")";
            throw new BadRequestException("El archivo no es un JSON válido" + where + ".");
        }
    }

    private void checkHeader(JsonNode root, CatalogImportResultDto result) {
        JsonNode format = root.get("format");
        if (format != null && !FORMAT.equals(format.asText())) {
            result.getFileErrors().add("\"format\" debe ser \"" + FORMAT + "\".");
        }
        JsonNode version = root.get("version");
        if (version != null && version.asInt(-1) != VERSION) {
            result.getFileErrors().add("Esta tienda lee la versión " + VERSION + " del formato; el archivo dice " + version + ".");
        }
        if (!root.has("products") && !root.has("kits")) {
            result.getFileErrors().add("El archivo no tiene \"products\" ni \"kits\".");
        }
        Set<String> unknown = new LinkedHashSet<>();
        root.fieldNames().forEachRemaining(name -> {
            if (!Set.of("format", "version", "exportedAt", "products", "kits").contains(name)) {
                unknown.add(name);
            }
        });
        if (!unknown.isEmpty()) {
            result.getFileErrors().add("Campos desconocidos en el archivo: " + quoted(unknown) + ".");
        }
    }

    private static List<JsonNode> list(JsonNode root, String field, int max, CatalogImportResultDto result) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            result.getFileErrors().add("\"" + field + "\" debe ser una lista [ ... ].");
            return List.of();
        }
        if (node.size() > max) {
            result.getFileErrors().add("\"" + field + "\" tiene " + node.size() + " elementos; el máximo por archivo es " + max + ".");
            return List.of();
        }
        List<JsonNode> items = new ArrayList<>();
        node.forEach(items::add);
        return items;
    }

    // ---- products

    /** Imports the products and returns, for each SKU in the file, whether its row was fine. */
    private Map<String, Boolean> importProducts(List<JsonNode> nodes, CatalogImportResultDto result) {
        Map<String, Integer> firstPositionBySku = new HashMap<>();
        Map<String, Boolean> okBySku = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            Row row = new Row();
            row.setSection("product");
            row.setPosition(i + 1);
            JsonNode node = nodes.get(i);
            String sku = node.isObject() ? text(node.get("sku")) : null;
            row.setKey(sku);
            row.setName(node.isObject() ? text(node.get("name")) : null);

            if (sku != null) {
                Integer first = firstPositionBySku.putIfAbsent(sku, i + 1);
                if (first != null) {
                    row.getErrors().add("El SKU «" + sku + "» está repetido en el archivo (también en el producto " + first + ").");
                }
            }
            if (row.getErrors().isEmpty()) {
                importProduct(node, row);
            }
            if (!row.getErrors().isEmpty()) {
                row.setAction(Action.ERROR);
            }
            if (sku != null) {
                okBySku.merge(sku, row.getAction() != Action.ERROR, Boolean::logicalAnd);
            }
            result.add(row);
        }
        return okBySku;
    }

    private void importProduct(JsonNode node, Row row) {
        if (!node.isObject()) {
            row.getErrors().add("Cada producto debe ser un objeto { ... }.");
            return;
        }
        String typeCode = text(node.get("type"));
        ProductType<?> type = typeCode == null ? null : productTypes.get(typeCode.toUpperCase());
        if (type == null) {
            row.getErrors().add((typeCode == null ? "Falta «type»" : "El tipo «" + typeCode + "» no existe")
                    + "; usa uno de: " + String.join(", ", productTypes.keySet()) + ".");
            return;
        }
        row.setTypeLabel(type.label);
        type.importRow(this, (ObjectNode) node, row);
    }

    <D> void importProductRow(ProductType<D> type, ObjectNode node, Row row) {
        ObjectNode fields = node.deepCopy();
        fields.remove("type");
        // Ids belong to one database; computed values are recalculated. Both may come in an exported file.
        fields.remove("id");
        type.readOnly.forEach(fields::remove);
        List<String> unknown = unknownFields(fields, type.dtoClass);
        if (!unknown.isEmpty()) {
            row.getErrors().add("Campos desconocidos para " + type.label.toLowerCase() + ": " + quoted(unknown) + ".");
            return;
        }
        String sku = text(fields.get("sku"));
        if (sku == null) {
            row.getErrors().add("Falta «sku».");
            return;
        }
        fields.put("sku", sku);

        Product existing = productRepository.findBySku(sku).map(product -> (Product) Hibernate.unproxy(product)).orElse(null);
        if (existing != null && !type.entityClass.isInstance(existing)) {
            row.getErrors().add("El SKU «" + sku + "» ya existe como " + labelOf(existing).toLowerCase()
                    + "; cambia el SKU o el tipo.");
            return;
        }
        // Updating keeps every field the file leaves out, so a file with just "sku" and "price" only changes prices.
        ObjectNode before = existing == null ? null : productNode(type, type.getById.apply(existing.getId()));
        ObjectNode merged = before == null ? objectMapper.createObjectNode() : before.deepCopy();
        merged.remove("type");
        merged.setAll(fields);
        if (!merged.hasNonNull("stockQuantity")) {
            merged.put("stockQuantity", 0);
        }

        D dto = map(merged, type.dtoClass, row);
        if (dto == null) {
            return;
        }
        row.getErrors().addAll(violations(dto));
        checkLength(merged, "sku", 255, row);
        checkLength(merged, "name", 255, row);
        checkLength(merged, "brand", 255, row);
        checkLength(merged, "description", 2000, row);
        checkLength(merged, "referenceUrl", 2000, row);
        checkPhotoLengths(merged, row);
        if (!row.getErrors().isEmpty()) {
            return;
        }
        row.setName(text(merged.get("name")));

        try {
            if (existing == null) {
                type.create.apply(dto);
                row.setAction(Action.CREATE);
            } else {
                ObjectNode after = productNode(type, type.update.apply(existing.getId(), dto));
                row.setAction(sameJson(before, after) ? Action.UNCHANGED : Action.UPDATE);
            }
        } catch (BadRequestException | ResourceNotFoundException | IllegalArgumentException e) {
            row.getErrors().add(e.getMessage());
        }
    }

    private String labelOf(Product product) {
        return productTypes.values().stream()
                .filter(type -> type.entityClass.isInstance(product))
                .map(type -> type.label)
                .findFirst()
                .orElse("otro tipo de producto");
    }

    // ---- kits

    private void importKits(List<JsonNode> nodes, Map<String, Boolean> skuInFile, CatalogImportResultDto result) {
        Map<String, Integer> firstPositionByName = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            Row row = new Row();
            row.setSection("kit");
            row.setPosition(i + 1);
            row.setTypeLabel("Kit");
            JsonNode node = nodes.get(i);
            String name = node.isObject() ? text(node.get("name")) : null;
            row.setKey(name);
            row.setName(name);
            if (name != null) {
                Integer first = firstPositionByName.putIfAbsent(name, i + 1);
                if (first != null) {
                    row.getErrors().add("El kit «" + name + "» está repetido en el archivo (también en el kit " + first + ").");
                }
            }
            if (row.getErrors().isEmpty()) {
                importKit(node, skuInFile, row);
            }
            if (!row.getErrors().isEmpty()) {
                row.setAction(Action.ERROR);
            }
            result.add(row);
        }
    }

    private void importKit(JsonNode node, Map<String, Boolean> skuInFile, Row row) {
        if (!node.isObject()) {
            row.getErrors().add("Cada kit debe ser un objeto { ... }.");
            return;
        }
        ObjectNode fields = ((ObjectNode) node).deepCopy();
        fields.remove("id");
        List<String> unknown = unknownFields(fields, KitTransferDto.class);
        if (!unknown.isEmpty()) {
            row.getErrors().add("Campos desconocidos para el kit: " + quoted(unknown) + ".");
        }
        JsonNode itemsNode = fields.get("items");
        if (itemsNode != null && itemsNode.isArray()) {
            for (int i = 0; i < itemsNode.size(); i++) {
                if (itemsNode.get(i).isObject()) {
                    List<String> unknownInItem = unknownFields((ObjectNode) itemsNode.get(i), KitTransferDto.Item.class);
                    if (!unknownInItem.isEmpty()) {
                        row.getErrors().add("Producto " + (i + 1) + " del kit: campos desconocidos " + quoted(unknownInItem)
                                + " (usa \"sku\" y \"quantity\").");
                    }
                }
            }
        }
        if (!row.getErrors().isEmpty()) {
            return;
        }
        String name = text(fields.get("name"));
        if (name == null) {
            row.getErrors().add("Falta «name».");
            return;
        }
        fields.put("name", name);
        // Like products: updating keeps what the file leaves out ("items", when present, replaces the list).
        Kit existing = kitRepository.findByName(name).orElse(null);
        ObjectNode before = existing == null ? null : kitNode(kitService.getById(existing.getId()));
        ObjectNode merged = before == null ? objectMapper.createObjectNode() : before.deepCopy();
        merged.setAll(fields);
        fields = merged;

        KitTransferDto kit = map(fields, KitTransferDto.class, row);
        if (kit == null) {
            return;
        }
        if (kit.getName() == null || kit.getName().isBlank()) {
            row.getErrors().add("Falta «name».");
        }
        checkLength(fields, "name", 255, row);
        checkLength(fields, "description", 2000, row);
        checkPhotoLengths(fields, row);
        if (kit.getPriceOverride() != null && kit.getPriceOverride().signum() < 0) {
            row.getErrors().add("«priceOverride» no puede ser negativo.");
        }

        List<KitItemDto> items = new ArrayList<>();
        if (kit.getItems() == null || kit.getItems().isEmpty()) {
            row.getErrors().add("El kit no tiene productos («items»).");
        } else {
            for (int i = 0; i < kit.getItems().size(); i++) {
                KitTransferDto.Item item = kit.getItems().get(i);
                String where = "Producto " + (i + 1) + " del kit";
                if (item == null || item.getSku() == null || item.getSku().isBlank()) {
                    row.getErrors().add(where + ": falta «sku».");
                    continue;
                }
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    row.getErrors().add(where + " («" + item.getSku() + "»): «quantity» debe ser mayor que 0.");
                    continue;
                }
                if (Boolean.FALSE.equals(skuInFile.get(item.getSku()))) {
                    row.getErrors().add(where + ": el producto «" + item.getSku() + "» tiene errores en este archivo.");
                    continue;
                }
                Product product = productRepository.findBySku(item.getSku()).orElse(null);
                if (product == null) {
                    row.getErrors().add(where + ": no existe un producto con SKU «" + item.getSku()
                            + "» (ni en el archivo ni en la tienda).");
                    continue;
                }
                KitItemDto itemDto = new KitItemDto();
                itemDto.setProductId(product.getId());
                itemDto.setQuantity(item.getQuantity());
                items.add(itemDto);
            }
        }
        if (!row.getErrors().isEmpty()) {
            return;
        }

        KitDto dto = new KitDto();
        dto.setName(kit.getName().trim());
        dto.setDescription(kit.getDescription());
        dto.setActive(kit.getActive() == null || kit.getActive());
        dto.setPriceOverride(kit.getPriceOverride());
        dto.setImageUrls(kit.getImageUrls());
        dto.setImageUrl(kit.getImageUrl());
        dto.setItems(items);
        row.setKey(dto.getName());
        row.setName(dto.getName());
        row.getErrors().addAll(violations(dto));
        if (!row.getErrors().isEmpty()) {
            return;
        }

        try {
            if (existing == null) {
                kitService.create(dto);
                row.setAction(Action.CREATE);
            } else {
                ObjectNode after = kitNode(kitService.update(existing.getId(), dto));
                row.setAction(sameJson(before, after) ? Action.UNCHANGED : Action.UPDATE);
            }
        } catch (InvalidKitCompositionException | BadRequestException | ResourceNotFoundException e) {
            row.getErrors().add(e.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Fields the target class doesn't have; listed all at once so a hand-made file is fixed in one go. */
    private List<String> unknownFields(ObjectNode node, Class<?> target) {
        Set<String> known = objectMapper.getDeserializationConfig()
                .introspect(objectMapper.constructType(target))
                .findProperties().stream()
                .map(BeanPropertyDefinition::getName)
                .collect(Collectors.toSet());
        List<String> unknown = new ArrayList<>();
        node.fieldNames().forEachRemaining(name -> {
            if (!known.contains(name)) {
                unknown.add(name);
            }
        });
        return unknown;
    }

    private <T> T map(ObjectNode node, Class<T> target, Row row) {
        try {
            return objectMapper.treeToValue(node, target);
        } catch (InvalidFormatException e) {
            String field = fieldOf(e);
            Class<?> expected = e.getTargetType();
            if (expected != null && expected.isEnum()) {
                String options = Arrays.stream(expected.getEnumConstants()).map(Object::toString).collect(Collectors.joining(", "));
                row.getErrors().add("«" + field + "»: «" + e.getValue() + "» no es válido; usa uno de: " + options + ".");
            } else if (expected != null && (Number.class.isAssignableFrom(expected) || expected.isPrimitive())) {
                row.getErrors().add("«" + field + "» debe ser un número (llegó «" + e.getValue() + "»).");
            } else {
                row.getErrors().add("«" + field + "» tiene un valor no válido: «" + e.getValue() + "».");
            }
        } catch (JsonMappingException e) {
            row.getErrors().add("«" + fieldOf(e) + "» tiene un formato incorrecto (revisa si va entre comillas, como número o como lista).");
        } catch (JsonProcessingException e) {
            row.getErrors().add("No se pudo leer este elemento.");
        }
        return null;
    }

    private static String fieldOf(JsonMappingException e) {
        return e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + (ref.getIndex() + 1) + "]")
                .collect(Collectors.joining("."))
                .replace(".[", "[");
    }

    /** Bean Validation messages in Spanish, naming the field as it is written in the file. */
    private List<String> violations(Object dto) {
        List<String> messages = new ArrayList<>();
        for (ConstraintViolation<Object> violation : validator.validate(dto)) {
            String field = violation.getPropertyPath().toString();
            String template = violation.getMessageTemplate();
            if (template.contains("NotBlank") || template.contains("NotNull") || template.contains("NotEmpty")) {
                messages.add("Falta «" + field + "».");
            } else if (template.contains("PositiveOrZero")) {
                messages.add("«" + field + "» no puede ser negativo.");
            } else if (template.contains("Positive")) {
                messages.add("«" + field + "» debe ser mayor que 0.");
            } else if (template.startsWith("{")) {
                messages.add("«" + field + "» no es válido.");
            } else {
                messages.add(violation.getMessage() + " («" + field + "»).");
            }
        }
        messages.sort(String::compareTo);
        return messages;
    }

    private static void checkLength(ObjectNode node, String field, int max, Row row) {
        JsonNode value = node.get(field);
        if (value != null && value.isTextual() && value.asText().length() > max) {
            row.getErrors().add("«" + field + "» es muy largo (máximo " + max + " caracteres).");
        }
    }

    private static void checkPhotoLengths(ObjectNode node, Row row) {
        JsonNode photos = node.get("imageUrls");
        if (photos != null && photos.isArray()) {
            for (JsonNode photo : photos) {
                if (photo.isTextual() && photo.asText().length() > 1000) {
                    row.getErrors().add("Un link de foto es muy largo (máximo 1000 caracteres).");
                    return;
                }
            }
        }
    }

    /** Equal JSON, with numbers compared by value (2450 and 2450.00 are the same price). */
    private static boolean sameJson(JsonNode a, JsonNode b) {
        Comparator<JsonNode> byValue = (x, y) -> {
            if (x.isNumber() && y.isNumber()) {
                return x.decimalValue().compareTo(y.decimalValue());
            }
            return x.equals(y) ? 0 : 1;
        };
        return a.equals(byValue, b);
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() || !node.isValueNode() || node.asText().isBlank() ? null : node.asText().trim();
    }

    private static String quoted(java.util.Collection<String> names) {
        return names.stream().map(name -> "«" + name + "»").collect(Collectors.joining(", "));
    }

    /** One product type: how to read, create and update it through its existing service. */
    static final class ProductType<D> {
        final String code;
        final String label;
        final Class<D> dtoClass;
        final Class<? extends Product> entityClass;
        final Supplier<List<D>> findAll;
        final Function<Long, D> getById;
        final Function<D, D> create;
        final BiFunction<Long, D, D> update;
        /** Values the service computes; written by older exports, ignored on import. */
        final Set<String> readOnly;

        ProductType(String code, String label, Class<D> dtoClass, Class<? extends Product> entityClass,
                    Supplier<List<D>> findAll, Function<Long, D> getById, Function<D, D> create,
                    BiFunction<Long, D, D> update, Set<String> readOnly) {
            this.code = code;
            this.label = label;
            this.dtoClass = dtoClass;
            this.entityClass = entityClass;
            this.findAll = findAll;
            this.getById = getById;
            this.create = create;
            this.update = update;
            this.readOnly = new HashSet<>(readOnly);
        }

        List<ObjectNode> exportAll(CatalogTransferService service) {
            return findAll.get().stream().map(dto -> service.productNode(this, dto)).toList();
        }

        void importRow(CatalogTransferService service, ObjectNode node, Row row) {
            service.importProductRow(this, node, row);
        }
    }
}
