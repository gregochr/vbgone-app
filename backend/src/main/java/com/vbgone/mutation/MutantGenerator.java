package com.vbgone.mutation;

import java.util.List;

/**
 * Generates first-order mutants from VB source. The bootstrap implementation ({@link VbMutator})
 * is token-based; ADR-0001 targets a Roslyn-VB AST implementation for production — this interface
 * is the seam that lets one replace the other without touching the mutation-testing service.
 */
public interface MutantGenerator {

    /** All first-order mutants for the given VB source (empty if none / blank input). */
    List<Mutant> generate(String vbSource);
}
