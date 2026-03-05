package com.vbgone.service;

public record ProcessOutput(int exitCode, String stdout, String stderr) {}
