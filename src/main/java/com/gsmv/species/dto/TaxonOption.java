package com.gsmv.species.dto;

public record TaxonOption(
        Long id,
        Long parentId,
        String rank,
        String scientificName,
        String chineseName
) {
}
