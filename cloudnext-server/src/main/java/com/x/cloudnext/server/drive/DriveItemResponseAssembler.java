package com.x.cloudnext.server.drive;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;

@Component
public class DriveItemResponseAssembler {

    private final DriveItemRepository driveItemRepository;

    public DriveItemResponseAssembler(DriveItemRepository driveItemRepository) {
        this.driveItemRepository = driveItemRepository;
    }

    public DriveItemResponse toResponse(DriveItem item) {
        return toResponse(item, null);
    }

    public DriveItemResponse toResponseWithParentName(DriveItem item) {
        if (item == null) {
            return null;
        }
        Map<String, String> parentNames = resolveParentNames(Collections.singletonList(item));
        return toResponse(item, parentNames.get(item.getParentId()));
    }

    public List<DriveItemResponse> toResponses(List<DriveItem> items) {
        Map<String, String> parentNames = resolveParentNames(items);
        return items.stream()
                .map(item -> toResponse(item, parentNames.get(item.getParentId())))
                .collect(Collectors.toList());
    }

    public DrivePageResponse toPageResponse(DriveItemQueryResult queryResult) {
        DrivePageResponse response = new DrivePageResponse();
        response.setPage(queryResult.getPage());
        response.setSize(queryResult.getSize());
        response.setTotal(queryResult.getTotal());
        response.setTotalPages(queryResult.getTotalPages());
        response.setHasNext(queryResult.isHasNext());
        response.setItems(toResponses(queryResult.getItems()));
        return response;
    }

    private Map<String, String> resolveParentNames(Collection<DriveItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> parentIds = items.stream()
                .map(DriveItem::getParentId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> parentNames = new LinkedHashMap<>();
        driveItemRepository.findAllById(parentIds)
                .forEach(parent -> parentNames.put(parent.getId(), parent.getName()));
        return parentNames;
    }

    private DriveItemResponse toResponse(DriveItem item, String parentName) {
        DriveItemResponse response = new DriveItemResponse();
        response.setId(item.getId());
        response.setParentId(item.getParentId());
        response.setParentName(parentName);
        response.setOwnerPersonOaId(item.getOwnerPersonOaId());
        response.setOwnerName(item.getOwnerName());
        response.setName(item.getName());
        response.setItemType(item.getItemType().name().toLowerCase(Locale.ROOT));
        response.setFileType(item.getFileType());
        response.setContentType(item.getContentType());
        response.setSizeBytes(item.getSizeBytes());
        response.setDeleted(item.isDeleted());
        response.setDeletedAt(item.getDeletedAt());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());
        response.setLastAccessedAt(item.getLastAccessedAt());
        return response;
    }
}
