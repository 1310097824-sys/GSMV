package com.gsmv.versioning.dto;

public record VersionFieldChangeView(
        String fieldKey,
        String fieldLabel,
        String oldValue,
        String newValue
) {
}
