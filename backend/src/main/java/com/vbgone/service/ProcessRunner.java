package com.vbgone.service;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface ProcessRunner {
    ProcessOutput run(List<String> command) throws IOException, InterruptedException;
}
