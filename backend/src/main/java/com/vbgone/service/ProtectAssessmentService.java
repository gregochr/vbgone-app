package com.vbgone.service;

import com.vbgone.model.*;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protect's front-gate classifier. A purely <b>static</b> pass — no AI, no sidecar, nothing
 * leaves the tenant — that buckets every business-logic method of the uploaded VB.NET into
 * {@link Bucket#NET_READY} / {@link Bucket#WINDOWS_GATED} / {@link Bucket#REFACTOR_FIRST}.
 *
 * <p>It's the per-method evolution of the frontend's {@code looksUiCoupled} heuristic:
 * <ul>
 *   <li>touches controls / pops dialogs / is a UI event handler → <b>refactor-first</b>;
 *   <li>pure, but its class is WinForms-bound → <b>windows-gated</b>;
 *   <li>pure and UI-free → <b>net-ready</b>.
 * </ul>
 * Heuristic by design — the report is presented as an estimate.
 */
@Service
public class ProtectAssessmentService {

    private static final Pattern CLASS_BLOCK = Pattern.compile(
            "(?ms)^[ \\t]*(?:Public|Friend|Private|Partial|\\s)*Class\\s+(\\w+)\\b(.*?)^[ \\t]*End\\s+Class");

    private static final Pattern METHOD_BLOCK = Pattern.compile(
            "(?ms)^[ \\t]*(Public|Private|Friend|Protected)?\\s*(?:Shared\\s+|Overrides\\s+|Overridable\\s+)*"
                    + "(Sub|Function)\\s+(\\w+)\\s*\\([^)]*\\)([^\\r\\n]*)(.*?)^[ \\t]*End\\s+\\2");

    private static final String CONTROL_TYPES =
            "TextBox|Button|Label|ComboBox|CheckBox|ListBox|DataGridView|RadioButton|GroupBox|"
                    + "Panel|Form|Control|MenuStrip|ToolStrip|PictureBox|RichTextBox|MaskedTextBox|"
                    + "NumericUpDown|DateTimePicker|TabControl|TreeView|ListView";

    private static final Pattern CONTROL_FIELD = Pattern.compile(
            "(?im)^[ \\t]*(?:Friend|Private|Public|Protected|Dim)?\\s*(?:WithEvents\\s+)?(\\w+)\\s+As\\s+"
                    + "(?:System\\.Windows\\.Forms\\.)?(?:" + CONTROL_TYPES + ")\\b");

    private static final Pattern CONTROL_PROP = Pattern.compile(
            "\\b\\w+\\.(Text|Enabled|Visible|Checked|SelectedIndex|SelectedItem|SelectedValue|Items|"
                    + "DataSource|Rows|Cells|Focus|Show|ShowDialog)\\b");

    private static final Pattern INHERITS = Pattern.compile("(?im)^[ \\t]*Inherits\\s+([\\w.]+)");

    /** Designer/boilerplate members that aren't business logic. */
    private static final Set<String> SKIP_METHODS =
            Set.of("InitializeComponent", "Dispose", "New", "Finalize");

    private final SessionStore sessionStore;

    public ProtectAssessmentService(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    /**
     * Classifies {@code content} and stores it in a fresh session (so the per-class Baseline
     * flow can run afterwards against the same source).
     */
    public ReadinessReport assess(String filename, String content) {
        MigrationSession session = sessionStore.create();
        session.setFilename(filename);
        session.setVbContent(content);

        String file = filename != null && !filename.isBlank() ? filename : "source.vb";
        List<ClassReadiness> classes = new ArrayList<>();

        Matcher cm = CLASS_BLOCK.matcher(content == null ? "" : content);
        while (cm.find()) {
            String className = cm.group(1);
            String body = cm.group(2);
            session.putClassSource(className, cm.group().trim());
            classes.add(classifyClass(className, file, body));
        }

        return new ReadinessReport(session.getSessionId(), tally(classes), "static", classes);
    }

    private ClassReadiness classifyClass(String className, String file, String body) {
        boolean uiCoupled = isClassUiCoupled(body);
        Set<String> controlFields = controlFields(body);

        List<MethodReadiness> methods = new ArrayList<>();
        Matcher mm = METHOD_BLOCK.matcher(body);
        while (mm.find()) {
            String name = mm.group(3);
            if (SKIP_METHODS.contains(name)) continue;
            String visibility = mm.group(1) != null ? mm.group(1).toLowerCase() : "public";
            boolean handler = mm.group(4) != null && mm.group(4).contains("Handles ");
            String methodBody = mm.group(5) == null ? "" : mm.group(5);
            methods.add(classifyMethod(name, visibility, handler, methodBody, uiCoupled, controlFields));
        }

        Bucket bucket = methods.stream()
                .map(MethodReadiness::bucket)
                .max((a, b) -> Integer.compare(a.severity(), b.severity()))
                .orElse(uiCoupled ? Bucket.WINDOWS_GATED : Bucket.NET_READY);

        return new ClassReadiness(className, file, bucket, classReason(bucket, uiCoupled, methods), methods);
    }

    private MethodReadiness classifyMethod(String name, String visibility, boolean handler,
                                           String methodBody, boolean uiCoupled, Set<String> controlFields) {
        boolean touchesControls = touchesControls(methodBody, controlFields);

        if (touchesControls || (handler && (touchesControls || uiCoupled))) {
            String reason = methodBody.matches("(?s).*\\b(MsgBox|MessageBox)\\b.*")
                    ? "pops a dialog / mutates controls directly"
                    : handler ? "UI event handler — reads and writes controls"
                    : "reads or writes control state directly";
            return new MethodReadiness(name, visibility, Bucket.REFACTOR_FIRST, reason);
        }
        if (uiCoupled) {
            String reason = "private".equals(visibility)
                    ? "pure, but the class is WinForms-bound — needs reflection"
                    : "pure, but trapped in a WinForms-referencing class";
            return new MethodReadiness(name, visibility, Bucket.WINDOWS_GATED, reason);
        }
        return new MethodReadiness(name, visibility, Bucket.NET_READY, "params in, value out; no control access");
    }

    private boolean isClassUiCoupled(String body) {
        Matcher inh = INHERITS.matcher(body);
        if (inh.find()) {
            String base = inh.group(1);
            if (base.endsWith("Form") || base.endsWith("UserControl") || base.endsWith("Control")
                    || base.endsWith("ContainerControl")) {
                return true;
            }
        }
        return CONTROL_FIELD.matcher(body).find()
                || Pattern.compile("(?im)Imports\\s+System\\.Windows\\.Forms").matcher(body).find()
                || Pattern.compile("Handles\\s+\\w+\\.\\w+").matcher(body).find();
    }

    private Set<String> controlFields(String body) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = CONTROL_FIELD.matcher(body);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    private boolean touchesControls(String methodBody, Set<String> controlFields) {
        if (Pattern.compile("\\b(MsgBox|MessageBox)\\b").matcher(methodBody).find()) return true;
        if (CONTROL_PROP.matcher(methodBody).find()) return true;
        for (String field : controlFields) {
            if (Pattern.compile("\\b" + Pattern.quote(field) + "\\b").matcher(methodBody).find()) return true;
        }
        return false;
    }

    private String classReason(Bucket bucket, boolean uiCoupled, List<MethodReadiness> methods) {
        return switch (bucket) {
            case NET_READY -> "public, no WinForms references";
            case WINDOWS_GATED -> "pure logic trapped in a WinForms-referencing class";
            case REFACTOR_FIRST -> uiCoupled
                    ? "reads/writes controls in event handlers"
                    : "logic entangled with the UI";
        };
    }

    private ReadinessReport.ReadinessTotals tally(List<ClassReadiness> classes) {
        int cNet = 0, cWin = 0, cRef = 0, mTotal = 0, mNet = 0, mWin = 0, mRef = 0;
        for (ClassReadiness c : classes) {
            switch (c.bucket()) {
                case NET_READY -> cNet++;
                case WINDOWS_GATED -> cWin++;
                case REFACTOR_FIRST -> cRef++;
            }
            for (MethodReadiness mth : c.methods()) {
                mTotal++;
                switch (mth.bucket()) {
                    case NET_READY -> mNet++;
                    case WINDOWS_GATED -> mWin++;
                    case REFACTOR_FIRST -> mRef++;
                }
            }
        }
        return new ReadinessReport.ReadinessTotals(
                classes.size(), mTotal, cNet, cWin, cRef, mNet, mWin, mRef);
    }
}
