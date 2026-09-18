package com.endel.demobox.service.catalog;

import com.endel.demobox.exception.ResourceNotFoundException;
import com.endel.demobox.model.catalog.Kit;
import com.endel.demobox.model.catalog.KitItem;
import com.endel.demobox.model.catalog.Product;
import com.endel.demobox.model.dto.catalog.KitDto;
import com.endel.demobox.model.dto.catalog.KitItemDto;
import com.endel.demobox.repository.catalog.KitRepository;
import com.endel.demobox.repository.catalog.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class KitService {
    private final KitRepository kitRepository;
    private final ProductRepository productRepository;

    public KitService(KitRepository kitRepository, ProductRepository productRepository) {
        this.kitRepository = kitRepository;
        this.productRepository = productRepository;
    }

    public List<KitDto> findAll() {
        return kitRepository.findAll().stream().map(this::toDto).toList();
    }

    public KitDto getById(Long id) {
        return toDto(findEntity(id));
    }

    public KitDto create(KitDto dto) {
        Kit kit = new Kit();
        applyDto(kit, dto);
        return toDto(kitRepository.save(kit));
    }

    public KitDto update(Long id, KitDto dto) {
        Kit kit = findEntity(id);
        applyDto(kit, dto);
        return toDto(kitRepository.save(kit));
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

    private KitDto toDto(Kit kit) {
        KitDto dto = new KitDto();
        dto.setId(kit.getId());
        dto.setName(kit.getName());
        dto.setDescription(kit.getDescription());
        dto.setActive(kit.isActive());
        dto.setPriceOverride(kit.getPriceOverride());

        BigDecimal computed = kit.getItems().stream()
                .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setComputedPrice(computed);

        dto.setItems(kit.getItems().stream().map(item -> {
            KitItemDto itemDto = new KitItemDto();
            itemDto.setId(item.getId());
            itemDto.setProductId(item.getProduct().getId());
            itemDto.setProductSku(item.getProduct().getSku());
            itemDto.setProductName(item.getProduct().getName());
            itemDto.setUnitPrice(item.getProduct().getPrice());
            itemDto.setQuantity(item.getQuantity());
            return itemDto;
        }).toList());

        return dto;
    }
}
