package com.vbgone.service;

import com.vbgone.model.ImplementMode;
import com.vbgone.model.ImplementResult;
import com.vbgone.model.InterfaceResult;
import com.vbgone.model.StubResult;
import com.vbgone.model.TestsResult;
import org.springframework.stereotype.Service;

@Service
public class GenerationService {

    public InterfaceResult generateInterface(String sessionId, String className) {
        throw new UnsupportedOperationException("Not yet implemented — Phase 2");
    }

    public TestsResult generateTests(String sessionId, String className) {
        throw new UnsupportedOperationException("Not yet implemented — Phase 2");
    }

    public StubResult generateStub(String sessionId, String className) {
        throw new UnsupportedOperationException("Not yet implemented — Phase 2");
    }

    public ImplementResult implement(String sessionId, String className, ImplementMode mode) {
        throw new UnsupportedOperationException("Not yet implemented — Phase 2");
    }
}
