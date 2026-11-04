package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.InvalidKitCompositionException;
import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.AluminumStructure;
import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.ConnectionCable;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.KitItem;
import com.endel.demobox.model.catalog.Product;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.model.dto.catalog.KitItemDto;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import com.endel.demobox.model.dto.WiringSettingsDto;
import com.endel.demobox.model.dto.catalog.compatibility.KitCompatibilityReportDto;
import com.endel.demobox.service.ProfitMarginService;
import com.endel.demobox.service.WiringSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KitService {
    private final KitRepository kitRepository;
    private final ProductRepository productRepository;
    private final ProfitMarginService profitMarginService;
    private final KitCompatibilityService compatibilityService;
    private final WiringSettingsService wiringSettingsService;

    public KitService(KitRepository kitRepository, ProductRepository productRepository, ProfitMarginService profitMarginService,
                      KitCompatibilityService compatibilityService, WiringSettingsService wiringSettingsService) {
        this.kitRepository = kitRepository;
        this.productRepository = productRepository;
        this.profitMarginService = profitMarginService;
        this.compatibilityService = compatibilityService;
        this.wiringSettingsService = wiringSettingsService;
    }

    @Transactional
    public Map<Long, KitCompatibilityReportDto> compatibilityByKitId(List<Long> kitIds) {
        WiringSettingsDto lengths = wiringSettingsService.getCurrent();
        Map<Long, KitCompatibilityReportDto> reports = new HashMap<>();
        for (Kit kit : kitRepository.findAllById(kitIds)) {
            List<KitLine> lines = kit.getItems().stream()
                    .map(item -> new KitLine(item.getProduct(), item.getQuantity()))
                    .toList();
            reports.put(kit.getId(), compatibilityService.analyze(lines, lengths));
        }
        return reports;
    }

    @Transactional
    public KitCompatibilityReportDto validate(List<KitItemDto> items) {
        return compatibilityService.analyze(toLines(items), wiringSettingsService.getCurrent());
    }

    /** One line per product: the same product picked in several rows is merged by adding its quantities. */
    private List<KitLine> toLines(List<KitItemDto> items) {
        Map<Long, Integer> quantityByProductId = new LinkedHashMap<>();
        for (KitItemDto item : items) {
            quantityByProductId.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        return quantityByProductId.entrySet().stream()
                .map(entry -> new KitLine(findProduct(entry.getKey()), entry.getValue()))
                .toList();
    }

    public List<KitDto> findAll() {
        BigDecimal marginPercent = profitMarginService.getCurrentMargin().getMarginPercent();
        return kitRepository.findAll().stream().map(kit -> toDto(kit, marginPercent)).toList();
    }

    public List<KitDto> findAllActive() {
        BigDecimal marginPercent = profitMarginService.getCurrentMargin().getMarginPercent();
        return kitRepository.findAll().stream()
                .filter(Kit::isActive)
                .map(kit -> toDto(kit, marginPercent))
                .sorted(Comparator.comparing(KitDto::getSellPrice))
                .toList();
    }

    public KitDto getById(Long id) {
        return toDto(findEntity(id), profitMarginService.getCurrentMargin().getMarginPercent());
    }

    public KitDto create(KitDto dto) {
        Kit kit = new Kit();
        applyDto(kit, dto);
        return toDto(kitRepository.save(kit), profitMarginService.getCurrentMargin().getMarginPercent());
    }

    public KitDto update(Long id, KitDto dto) {
        Kit kit = findEntity(id);
        applyDto(kit, dto);
        return toDto(kitRepository.save(kit), profitMarginService.getCurrentMargin().getMarginPercent());
    }

    public void delete(Long id) {
        kitRepository.delete(findEntity(id));
    }

    private Kit findEntity(Long id) {
        return kitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kit not found: " + id));
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private void applyDto(Kit kit, KitDto dto) {
        List<KitLine> lines = toLines(dto.getItems());
        List<String> compositionErrors = compatibilityService.compositionErrors(lines);
        if (!compositionErrors.isEmpty()) {
            throw new InvalidKitCompositionException(String.join(" ", compositionErrors));
        }

        kit.setName(dto.getName());
        kit.setDescription(dto.getDescription());
        kit.setActive(dto.isActive());
        kit.setPriceOverride(dto.getPriceOverride());
        kit.setImageUrl(dto.getImageUrl());

        kit.getItems().clear();
        for (KitLine line : lines) {
            KitItem item = new KitItem();
            item.setKit(kit);
            item.setProduct(line.product());
            item.setQuantity(line.quantity());
            kit.getItems().add(item);
        }
    }

    private KitDto toDto(Kit kit, BigDecimal marginPercent) {
        KitDto dto = new KitDto();
        dto.setId(kit.getId());
        dto.setName(kit.getName());
        dto.setDescription(kit.getDescription());
        dto.setActive(kit.isActive());
        dto.setPriceOverride(kit.getPriceOverride());
        dto.setImageUrl(kit.getImageUrl());

        BigDecimal computedPrice = BigDecimal.ZERO;
        double totalPanelArrayKwp = 0;
        double totalBatteryCapacityKwh = 0;
        double usableBatteryCapacityKwh = 0;
        double totalInverterPowerKw = 0;
        List<KitItemDto> itemDtos = new ArrayList<>();

        for (KitItem item : kit.getItems()) {
            Product product = item.getProduct();
            int quantity = item.getQuantity();
            computedPrice = computedPrice.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));

            KitItemDto itemDto = new KitItemDto();
            itemDto.setId(item.getId());
            itemDto.setProductId(product.getId());
            itemDto.setProductSku(product.getSku());
            itemDto.setProductName(product.getName());
            itemDto.setUnitPrice(product.getPrice());
            itemDto.setQuantity(quantity);

            if (product instanceof SolarPanel panel) {
                totalPanelArrayKwp += panel.getWattage() * quantity / 1000.0;
                itemDto.setProductType("PANEL");
                itemDto.setSpecLabel(formatNumber(panel.getWattage()) + " W");
            } else if (product instanceof Battery battery) {
                totalBatteryCapacityKwh += battery.getCapacityKwh() * quantity;
                usableBatteryCapacityKwh += battery.getCapacityKwh() * battery.getDepthOfDischarge() * quantity;
                itemDto.setProductType("BATTERY");
                itemDto.setSpecLabel(formatNumber(battery.getCapacityKwh()) + " kWh");
            } else if (product instanceof Inverter inverter) {
                totalInverterPowerKw += inverter.getRatedPowerKw() * quantity;
                itemDto.setProductType("INVERTER");
                itemDto.setSpecLabel(formatNumber(inverter.getRatedPowerKw()) + " kW");
            } else if (product instanceof ConnectionCable cable) {
                itemDto.setProductType("CABLE");
                itemDto.setSpecLabel(formatNumber(cable.getLengthMeters()) + " m"
                        + (cable.getGauge() != null ? " · " + cable.getGauge().getLabel() : ""));
            } else if (product instanceof AluminumStructure) {
                itemDto.setProductType("STRUCTURE");
            } else {
                itemDto.setProductType("OTHER");
            }

            itemDtos.add(itemDto);
        }

        dto.setComputedPrice(computedPrice);
        dto.setSellPrice(dto.getPriceOverride() != null
                ? dto.getPriceOverride()
                : profitMarginService.applyMargin(computedPrice, marginPercent));
        dto.setTotalPanelArrayKwp(round(totalPanelArrayKwp));
        dto.setTotalBatteryCapacityKwh(round(totalBatteryCapacityKwh));
        dto.setUsableBatteryCapacityKwh(round(usableBatteryCapacityKwh));
        dto.setTotalInverterPowerKw(round(totalInverterPowerKw));
        dto.setItems(itemDtos);

        return dto;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String formatNumber(double value) {
        double rounded = round(value);
        return rounded == Math.rint(rounded) ? String.format("%.0f", rounded) : String.format("%.2f", rounded);
    }
}
