package com.vbgone.build;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Assembles the compilation unit for a Windows characterisation: the target class plus the transitive
 * closure of the estate <em>sibling</em> classes it references, concatenated so it compiles as one
 * assembly.
 *
 * <p>Per-class extraction hands the runner a single class. That's fine for self-contained logic, but a
 * class that names a sibling type — most commonly a LINQ-to-SQL entity with an
 * {@code EntitySet(Of OtherEntity)} association — fails to compile in isolation (BC30002). Here we pull
 * every referenced sibling (and its siblings, recursively) into the same source so those references
 * resolve.
 *
 * <p>A "reference" is a whole-word mention of another estate class name — deliberately imperfect but
 * safe in this direction: an over-included class that compiles is harmless (it just isn't exercised by
 * the tests). The caller is expected to pass only <em>compilable</em> candidates (i.e. exclude the
 * tangled {@code REFACTOR_FIRST} bucket) so an over-match can't drag an un-compilable class in.
 */
public final class ClusterAssembler {

    private ClusterAssembler() {}

    /** Guard against a reference chain dragging in the whole estate. */
    static final int MAX_CLUSTER = 25;

    /**
     * @param targetClassName the class being characterised (seeded first, so it leads the file)
     * @param targetSource    its source
     * @param candidateSources estate {@code className → source} eligible to be pulled in (typically all
     *                         classes except the tangled/uncompilable ones)
     * @return the target source, followed by each referenced sibling's source, concatenated
     */
    public static String assemble(String targetClassName, String targetSource,
                                  Map<String, String> candidateSources) {
        LinkedHashMap<String, String> cluster = new LinkedHashMap<>();
        cluster.put(targetClassName, targetSource == null ? "" : targetSource);

        Deque<String> queue = new ArrayDeque<>();
        queue.add(targetClassName);
        while (!queue.isEmpty() && cluster.size() < MAX_CLUSTER) {
            String current = cluster.get(queue.poll());
            if (current == null || current.isBlank()) {
                continue;
            }
            for (Map.Entry<String, String> candidate : candidateSources.entrySet()) {
                if (cluster.size() >= MAX_CLUSTER) {
                    break;
                }
                String name = candidate.getKey();
                String source = candidate.getValue();
                if (cluster.containsKey(name) || source == null || source.isBlank()) {
                    continue;
                }
                if (referencesType(current, name)) {
                    cluster.put(name, source);
                    queue.add(name);
                }
            }
        }
        return String.join("\n\n", cluster.values());
    }

    /** True when {@code source} mentions {@code typeName} as a whole word (identifier boundary). */
    private static boolean referencesType(String source, String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return false;
        }
        return Pattern.compile("\\b" + Pattern.quote(typeName) + "\\b").matcher(source).find();
    }
}
