package com.vbgone.model;

public record UploadProjectRequest(
        String filename,
        String content
) {}
