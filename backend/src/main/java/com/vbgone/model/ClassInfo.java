package com.vbgone.model;

import java.util.List;

public record ClassInfo(
        String name,
        List<String> methods,
        List<String> dependencies,
        Complexity complexity
) {}
