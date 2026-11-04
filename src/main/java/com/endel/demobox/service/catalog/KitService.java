package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.Battery;
import com.endel.demobox.model.catalog.Inverter;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.KitItem;
import com.endel.demobox.model.catalog.Product;
import com.endel.demobox.model.catalog.SolarPanel;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.model.dto.catalog.KitItemDto;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import com.endel.demobox.service.ProfitMarginService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class KitService {
    private final KitRepository kitRepository;
    private final ProductRepository productRepository;
    private final ProfitMarginService profitMarginService;

    public KitService(KitRepository kitRepository, ProductRepository productRepository, ProfitMarginService profitMarginService) {
        this.kitRepository = kitRepository;
        this.productRepository = productRepository;
        this.profitMarginService = profitMarginService;
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

    private void applyDto(Kit kit, KitDto dto) {
        kit.setName(dto.getName());
        kit.setDescription(dto.getDescription());
        kit.setActive(dto.isActive());
        kit.setPriceOverride(dto.getPriceOverride());
        kit.setImageUrl(dto.getImageUrl());

        kit.getItems().clear();
        for (KitItemDto itemDto : dto.getItems()) {
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemDto.getProductId()));
            KitItem item = new KitItem();
            item.setKit(kit);
            item.setProduct(product);
            item.setQuantity(itemDto.getQuantity());
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
                itemDto.setProductType("BATTERY");
                itemDto.setSpecLabel(formatNumber(battery.getCapacityKwh()) + " kWh");
            } else if (product instanceof Inverter inverter) {
                totalInverterPowerKw += inverter.getRatedPowerKw() * quantity;
                itemDto.setProductType("INVERTER");
                itemDto.setSpecLabel(formatNumber(inverter.getRatedPowerKw()) + " kW");
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
