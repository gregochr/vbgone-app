package com.vbgone.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class DefaultProcessRunner implements ProcessRunner {

    @Override
    public ProcessOutput run(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        int exitCode = process.waitFor();
        return new ProcessOutput(exitCode, stdout, stderr);
    }
}
