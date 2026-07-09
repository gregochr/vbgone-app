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
 * Assure's front-gate classifier. A purely <b>static</b> pass — no AI, no sidecar, nothing
 * leaves the tenant — that buckets every business-logic method of the uploaded VB.NET into
 * {@link Bucket#NET_READY} / {@link Bucket#WINDOWS_GATED} / {@link Bucket#REFACTOR_FIRST}.
 *
 * <p>It's the per-method evolution of the frontend's {@code looksUiCoupled} heuristic:
 * <ul>
 *   <li>touches controls / pops dialogs / is a UI event handler → <b>refactor-first</b>;
 *   <li>reads or writes ASP.NET intrinsics ({@code Session}/{@code Application}/{@code HttpContext}) —
 *       ambient web-host state that can't run headless → <b>refactor-first</b>;
 *   <li>pure, but its class is WinForms-bound → <b>windows-gated</b>;
 *   <li>pure and UI-free → <b>net-ready</b>.
 * </ul>
 * Heuristic by design — the report is presented as an estimate.
 */
@Service
public class AssureAssessmentService {

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

    // Genuinely UI-specific properties. The ADO.NET/collection-ambiguous ones (Rows, Cells,
    // Items, DataSource) are deliberately NOT here — they fire on DataTable/DataSet/DataRow far
    // more often than on a grid, so matching them receiver-blind flags ordinary data code as UI.
    // A grid touched through a *declared* control field is still caught by the controlFields path.
    // The `System` receiver is excluded so fully-qualified BCL usages (System.Text.StringBuilder,
    // System.Text.Encoding.UTF8) aren't read as a control's `.Text` — same data-code-as-UI trap.
    private static final Pattern CONTROL_PROP = Pattern.compile(
            "\\b(?!System\\b)\\w+\\.(Text|Enabled|Visible|Checked|SelectedIndex|SelectedItem|"
                    + "SelectedValue|Focus|Show|ShowDialog)\\b");

    // ASP.NET intrinsics used as ambient state — the request-pipeline coupling that stops a
    // method running headless. Case-sensitive on the conventional PascalCase so a lowercase
    // local (e.g. a `response As String` parameter) isn't mistaken for the intrinsic object.
    private static final Pattern WEB_CONTEXT = Pattern.compile(
            "\\b(?:Session|Application)\\s*[(.]|\\bHttpContext\\b");

    private static final Pattern INHERITS = Pattern.compile("(?im)^[ \\t]*Inherits\\s+([\\w.]+)");

    // A VB line-continuation: whitespace + a trailing underscore ending a line, which splices the
    // next physical line onto this one. Attribute lines love this — {@code <WebService(…)> _} then
    // {@code Public Class …}, or {@code <WebMethod()> _} then {@code Public Function …}, is one
    // logical statement. The class/attribute matchers here are all line-anchored: they expect the
    // attribute to end right after {@code >} + newline, and {@code Public Class}/{@code Public
    // Function} to start a line. So we drop the {@code _} continuation but KEEP the newline — the
    // attribute stays on its own line and the declaration stays anchored (see
    // {@link #normalizeContinuations}). We deliberately do NOT fold the two lines into one: that
    // would push the attribute onto the declaration line and defeat those anchors.
    private static final Pattern LINE_CONTINUATION = Pattern.compile("[ \\t]+_[ \\t]*(\\r?\\n)");

    // .NET Framework-only dependencies that can't compile or run on the headless cross-platform SDK
    // the CLR sidecar uses: LINQ-to-SQL (System.Data.Linq — DataContext / Table / EntitySet /
    // EntityRef / DBML mapping attributes) and classic ASMX or WCF-Web services (System.Web.Services,
    // <WebMethod>, <WebService>, <WebGet>, ScriptMethod). A class carrying any of these is
    // Windows/Framework-bound, so it must never enter the net-ready subset — even when its own
    // methods look pure — or the shared assurable project fails to build (BC30002: type not defined).
    private static final Pattern FRAMEWORK_BOUND = Pattern.compile(
            "(?i)\\bSystem\\.Data\\.Linq\\b|\\bEntity(?:Set|Ref)\\b"
                    + "|<\\s*(?:Table|Column|Association|Database)\\s*\\("
                    + "|\\bSystem\\.Web\\.Services\\b|<\\s*WebMethod\\b|<\\s*WebService\\b"
                    + "|\\bScriptMethod\\b|<\\s*WebGet\\b|<\\s*WebInvoke\\b"
                    + "|<\\s*(?:Service|Operation)Contract\\b");

    // ── REST API surface extraction (a separate, future Assure target) ──

    /** A public action method, capturing any attribute lines that immediately precede it. */
    private static final Pattern ACTION_METHOD = Pattern.compile(
            "(?ms)^[ \\t]*((?:<[^\\r\\n]*>[ \\t]*\\r?\\n?[ \\t]*)*)(?:Public|Friend)\\s+(?:Async\\s+)?"
                    + "(Function|Sub)\\s+(\\w+)\\s*\\(([^)]*)\\)([^\\r\\n]*)");

    /** {@code <Route("…")>} or {@code <RoutePrefix("…")>} attribute, capturing the template. */
    private static final Pattern ROUTE_ATTR =
            Pattern.compile("(?i)<Route(?:Prefix)?\\s*\\(\\s*\"([^\"]*)\"");

    /** {@code {token}} placeholders in a route template. */
    private static final Pattern ROUTE_TOKEN = Pattern.compile("\\{(\\w+)");

    /** One VB parameter: optional attributes, then {@code name As Type}. */
    private static final Pattern PARAM = Pattern.compile(
            "(?i)^\\s*((?:<[^>]*>\\s*)*)(?:ByVal\\s+|ByRef\\s+|Optional\\s+|ParamArray\\s+)*"
                    + "(\\w+)\\s+As\\s+([\\w.]+(?:\\(Of[^)]*\\))?)");

    /** Scalar VB/.NET types — anything else in a body position is treated as a DTO. */
    private static final Set<String> SCALAR_TYPES = Set.of(
            "integer", "int", "int16", "int32", "int64", "uinteger", "long", "ulong", "short",
            "decimal", "double", "single", "float", "boolean", "bool", "string", "char", "byte",
            "sbyte", "date", "datetime", "datetimeoffset", "timespan", "guid", "object");

    /** Designer/boilerplate members that aren't business logic. */
    private static final Set<String> SKIP_METHODS =
            Set.of("InitializeComponent", "Dispose", "New", "Finalize");

    private final SessionStore sessionStore;

    public AssureAssessmentService(SessionStore sessionStore) {
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
        List<String> netReadyBlocks = new ArrayList<>();
        List<RestApiEndpoint> restApis = new ArrayList<>();
        classifyInto(content, file, session, classes, netReadyBlocks, restApis);
        session.setAssurableSource(String.join("\n\n", netReadyBlocks));

        return new ReadinessReport(session.getSessionId(), tally(classes), "static", classes, restApis);
    }

    /**
     * Classify an uploaded estate (a zip's worth of .vb files). The session was created by
     * {@code ZipExtractorService.extract}. Each class's {@code file} is its relative path;
     * per-class sources are stored for the drill-in, and the net-ready subset is pinned as the
     * headless-compilable {@code assurableSource}.
     */
    public ReadinessReport assessProject(ZipManifest manifest) {
        MigrationSession session = sessionStore.get(manifest.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + manifest.sessionId()));

        List<ClassReadiness> classes = new ArrayList<>();
        List<String> netReadyBlocks = new ArrayList<>();
        List<RestApiEndpoint> restApis = new ArrayList<>();
        for (VbSourceFile f : manifest.files()) {
            classifyInto(f.content(), f.relativePath(), session, classes, netReadyBlocks, restApis);
        }
        session.setAssurableSource(String.join("\n\n", netReadyBlocks));

        return new ReadinessReport(session.getSessionId(), tally(classes), "static", classes, restApis);
    }

    /** Parses classes out of one source string, classifies each, and collects net-ready blocks. */
    private void classifyInto(String content, String file, MigrationSession session,
                              List<ClassReadiness> out, List<String> netReadyBlocks,
                              List<RestApiEndpoint> restApis) {
        String src = content == null ? "" : content;
        // Line-anchored matching (class attrs, method attrs) runs over a continuation-folded copy so
        // VB `_`-split attributes parse; the raw `block` is what we store/compile, left untouched.
        String detectSrc = normalizeContinuations(src);
        Matcher cm = CLASS_BLOCK.matcher(src);
        while (cm.find()) {
            String className = cm.group(1);
            String block = cm.group().trim();
            String body = normalizeContinuations(cm.group(2));

            // Web API controllers / ASMX services are reported as endpoints, not as readiness
            // classes — they're a separate, future Assure target and aren't in the class buckets.
            List<RestApiEndpoint> endpoints =
                    extractEndpoints(className, file, precedingAttrs(detectSrc, className), body);
            if (!endpoints.isEmpty()) {
                restApis.addAll(endpoints);
                continue;
            }

            session.putClassSource(className, block);
            ClassReadiness cr = classifyClass(className, file, body);
            out.add(cr);
            // Only net-ready (UI-free) classes go into the headless-compilable subset.
            if (cr.bucket() == Bucket.NET_READY) netReadyBlocks.add(block);
        }
    }

    private ClassReadiness classifyClass(String className, String file, String body) {
        boolean uiCoupled = isClassUiCoupled(body);
        boolean frameworkBound = isFrameworkBound(body);
        Set<String> controlFields = controlFields(body);

        List<MethodReadiness> methods = new ArrayList<>();
        Matcher mm = METHOD_BLOCK.matcher(body);
        while (mm.find()) {
            String name = mm.group(3);
            if (SKIP_METHODS.contains(name)) continue;
            String visibility = mm.group(1) != null ? mm.group(1).toLowerCase() : "public";
            boolean handler = mm.group(4) != null && mm.group(4).contains("Handles ");
            String methodBody = mm.group(5) == null ? "" : mm.group(5);
            methods.add(classifyMethod(name, visibility, handler, methodBody, uiCoupled, frameworkBound, controlFields));
        }

        Bucket bucket = methods.stream()
                .map(MethodReadiness::bucket)
                .max((a, b) -> Integer.compare(a.severity(), b.severity()))
                .orElse(uiCoupled || frameworkBound ? Bucket.WINDOWS_GATED : Bucket.NET_READY);

        // A class that references .NET Framework-only APIs (LINQ-to-SQL, ASMX/WCF) can't compile on
        // the headless SDK even when its own methods look pure — keep it out of the net-ready subset.
        if (frameworkBound && bucket == Bucket.NET_READY) bucket = Bucket.WINDOWS_GATED;

        return new ClassReadiness(className, file, bucket,
                classReason(bucket, uiCoupled, frameworkBound, methods), methods);
    }

    private MethodReadiness classifyMethod(String name, String visibility, boolean handler,
                                           String methodBody, boolean uiCoupled, boolean frameworkBound,
                                           Set<String> controlFields) {
        boolean touchesControls = touchesControls(methodBody, controlFields);
        boolean touchesWebContext = touchesWebContext(methodBody);

        if (touchesControls || touchesWebContext || (handler && uiCoupled)) {
            String reason = methodBody.matches("(?s).*\\b(MsgBox|MessageBox)\\b.*")
                    ? "pops a dialog / mutates controls directly"
                    : handler ? "UI event handler — reads and writes controls"
                    : touchesControls ? "reads or writes control state directly"
                    : "reads or writes ASP.NET Session/Application state — needs the web host";
            return new MethodReadiness(name, visibility, Bucket.REFACTOR_FIRST, reason);
        }
        if (uiCoupled) {
            String reason = "private".equals(visibility)
                    ? "pure, but the class is WinForms-bound — needs reflection"
                    : "pure, but trapped in a WinForms-referencing class";
            return new MethodReadiness(name, visibility, Bucket.WINDOWS_GATED, reason);
        }
        if (frameworkBound) {
            return new MethodReadiness(name, visibility, Bucket.WINDOWS_GATED,
                    "pure, but the class needs .NET Framework (LINQ-to-SQL / ASMX / WCF) — Windows-only");
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

    /** True when the class references .NET Framework-only APIs (LINQ-to-SQL, ASMX/WCF web services). */
    private boolean isFrameworkBound(String body) {
        return FRAMEWORK_BOUND.matcher(body).find();
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

    /** True when the method reads or writes ASP.NET ambient state — it can't run outside the web host. */
    private boolean touchesWebContext(String methodBody) {
        return WEB_CONTEXT.matcher(methodBody).find();
    }

    private String classReason(Bucket bucket, boolean uiCoupled, boolean frameworkBound,
                               List<MethodReadiness> methods) {
        return switch (bucket) {
            case NET_READY -> "public, no WinForms references";
            case WINDOWS_GATED -> uiCoupled
                    ? "pure logic trapped in a WinForms-referencing class"
                    : "references .NET Framework-only APIs (LINQ-to-SQL / ASMX / WCF) — needs a Windows runner";
            case REFACTOR_FIRST -> uiCoupled
                    ? "reads/writes controls in event handlers"
                    : "logic entangled with the UI";
        };
    }

    // ── REST API surface extraction ──

    /** A parsed VB parameter (name + declared type, and whether it's a {@code <FromBody>} DTO). */
    private record Param(String name, String type, boolean fromBody) {}

    /**
     * Folds VB line-continuations ({@code … _} at end of line) out of {@code src} for the
     * line-anchored classifier/endpoint matchers: the trailing {@code _} is dropped but the newline
     * is kept, so a {@code <WebMethod()> _} attribute and its {@code Public Function} stay on
     * separate, individually-anchored lines. Never applied to source we store or compile — dropping
     * a mid-expression continuation ({@code Return a > 0 And _}) would leave invalid VB.
     */
    private static String normalizeContinuations(String src) {
        return src == null ? "" : LINE_CONTINUATION.matcher(src).replaceAll("$1");
    }

    /** The attribute lines (if any) immediately preceding {@code Class className} in the source. */
    private String precedingAttrs(String content, String className) {
        Matcher m = Pattern.compile(
                "(?ms)((?:^[ \\t]*<[^\\r\\n]*>[ \\t]*\\r?\\n)+)[ \\t]*"
                        + "(?:Public|Friend|Partial|MustInherit|NotInheritable|\\s)*Class\\s+"
                        + Pattern.quote(className) + "\\b").matcher(content);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Extracts the web API endpoints exposed by {@code className} — its ASP.NET Web API actions or
     * ASMX web methods. Returns an empty list for an ordinary business-logic class.
     */
    private List<RestApiEndpoint> extractEndpoints(String className, String file,
                                                   String classAttrs, String body) {
        List<RestApiEndpoint> endpoints = new ArrayList<>();
        boolean asmx = isAsmxService(file, classAttrs, body);
        boolean webApi = !asmx && isWebApiController(className, body);
        if (!asmx && !webApi) return endpoints;

        String prefix = asmx ? null : routePrefix(classAttrs);
        Matcher m = ACTION_METHOD.matcher(body);
        while (m.find()) {
            String attrs = m.group(1) == null ? "" : m.group(1);
            boolean isFunction = "Function".equalsIgnoreCase(m.group(2));
            String name = m.group(3);
            String rawParams = m.group(4) == null ? "" : m.group(4).trim();
            String tail = m.group(5) == null ? "" : m.group(5);
            if (SKIP_METHODS.contains(name)) continue;

            if (asmx) {
                // Only <WebMethod>-decorated methods are actually exposed over the ASMX endpoint.
                if (!attrs.toLowerCase().contains("<webmethod")) continue;
                endpoints.add(asmxEndpoint(className, name, file, rawParams, isFunction, tail));
            } else {
                endpoints.add(webApiEndpoint(className, name, file, attrs, prefix, rawParams, isFunction, tail));
            }
        }
        return endpoints;
    }

    private boolean isWebApiController(String className, String body) {
        if (className.endsWith("Controller")) return true;
        Matcher inh = INHERITS.matcher(body);
        while (inh.find()) {
            String base = inh.group(1);
            if (base.endsWith("ApiController") || base.endsWith("ControllerBase")) return true;
        }
        return false;
    }

    private boolean isAsmxService(String file, String classAttrs, String body) {
        String f = file == null ? "" : file.toLowerCase();
        if (f.endsWith(".asmx.vb") || f.endsWith(".asmx")) return true;
        String attrs = (classAttrs + body).toLowerCase();
        return attrs.contains("<webservice") || attrs.contains("<webmethod");
    }

    private String routePrefix(String classAttrs) {
        Matcher m = ROUTE_ATTR.matcher(classAttrs == null ? "" : classAttrs);
        return m.find() ? m.group(1) : null;
    }

    private RestApiEndpoint webApiEndpoint(String className, String name, String file, String attrs,
                                           String prefix, String rawParams, boolean isFunction, String tail) {
        String verb = webApiVerb(attrs, name);
        List<Param> params = parseParams(rawParams);

        // An explicit <Route> on the method wins; otherwise synthesise from the prefix + name.
        Matcher rm = ROUTE_ATTR.matcher(attrs);
        String methodRoute = rm.find() ? rm.group(1) : null;
        boolean hasId = params.stream().anyMatch(p -> p.name().equalsIgnoreCase("id"));
        String route = buildRoute(className, prefix, methodRoute, hasId);
        Set<String> tokens = routeTokens(route);

        // A POST/PUT/PATCH's first non-route DTO (or <FromBody> param) is the request body.
        boolean bodyVerb = verb.equals("POST") || verb.equals("PUT") || verb.equals("PATCH");
        Param body = null;
        if (bodyVerb) {
            for (Param p : params) {
                if (tokens.contains(p.name().toLowerCase())) continue;
                if (p.fromBody() || !isScalar(p.type())) { body = p; break; }
            }
        }

        List<RestApiParam> apiParams = new ArrayList<>();
        for (Param p : params) {
            if (p == body) continue;
            boolean path = tokens.contains(p.name().toLowerCase());
            apiParams.add(new RestApiParam(p.name(), path ? "path" : "query", p.type(),
                    path ? "part of the URL" : "query-string value"));
        }

        String reqType = body != null ? body.type() : "—";
        String resType = isFunction ? returnType(tail) : "—";
        String resStatus = switch (verb) {
            case "POST" -> "201 Created";
            case "DELETE" -> "204 No Content";
            default -> isFunction ? "200 OK" : "204 No Content";
        };
        return new RestApiEndpoint(verb, route, className + "." + name, file, "Web API",
                apiParams, reqType, null, resType, resStatus, "");
    }

    private RestApiEndpoint asmxEndpoint(String className, String name, String file,
                                         String rawParams, boolean isFunction, String tail) {
        List<RestApiParam> apiParams = new ArrayList<>();
        for (Param p : parseParams(rawParams)) {
            apiParams.add(new RestApiParam(p.name(), "query", p.type(), "SOAP parameter"));
        }
        String route = "/" + asmxBase(file) + "/" + name;
        String resType = isFunction ? returnType(tail) : "—";
        return new RestApiEndpoint("GET", route, className + "." + name, file, "ASMX",
                apiParams, "SOAP request", null, resType, "200 OK", "");
    }

    private String webApiVerb(String attrs, String name) {
        String a = attrs.toLowerCase();
        if (a.contains("<httpget")) return "GET";
        if (a.contains("<httppost")) return "POST";
        if (a.contains("<httpput")) return "PUT";
        if (a.contains("<httppatch")) return "PATCH";
        if (a.contains("<httpdelete")) return "DELETE";
        String n = name.toLowerCase();
        if (n.startsWith("post") || n.startsWith("create") || n.startsWith("add")) return "POST";
        if (n.startsWith("put") || n.startsWith("update") || n.startsWith("edit")) return "PUT";
        if (n.startsWith("patch")) return "PATCH";
        if (n.startsWith("delete") || n.startsWith("remove")) return "DELETE";
        return "GET";
    }

    private String buildRoute(String className, String prefix, String methodRoute, boolean hasId) {
        // An absolute method route (starting with / or ~/) ignores the controller prefix.
        if (methodRoute != null && (methodRoute.startsWith("/") || methodRoute.startsWith("~/"))) {
            return "/" + trimSlashes(methodRoute.replaceFirst("^~", ""));
        }
        String base = prefix != null && !prefix.isBlank()
                ? trimSlashes(prefix)
                : "api/" + controllerBase(className);
        String suffix = methodRoute != null && !methodRoute.isBlank()
                ? trimSlashes(methodRoute)
                : (hasId ? "{id}" : "");
        return "/" + (suffix.isBlank() ? base : base + "/" + suffix);
    }

    private String controllerBase(String className) {
        String n = className;
        if (n.endsWith("ApiController")) n = n.substring(0, n.length() - "ApiController".length());
        else if (n.endsWith("Controller")) n = n.substring(0, n.length() - "Controller".length());
        return n.toLowerCase();
    }

    private String asmxBase(String file) {
        String base = file == null || file.isBlank() ? "Service.asmx" : file;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        if (base.toLowerCase().endsWith(".vb")) base = base.substring(0, base.length() - 3);
        return base;
    }

    private String trimSlashes(String s) {
        return s.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private Set<String> routeTokens(String route) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = ROUTE_TOKEN.matcher(route);
        while (m.find()) tokens.add(m.group(1).toLowerCase());
        return tokens;
    }

    private String returnType(String tail) {
        Matcher m = Pattern.compile("(?i)\\bAs\\s+([\\w.]+(?:\\(Of[^)]*\\))?)").matcher(tail);
        if (!m.find()) return "—";
        String type = m.group(1);
        // Unwrap the common ASP.NET result wrappers to the payload type where we can.
        Matcher g = Pattern.compile(
                "(?i)(?:ActionResult|Task|IEnumerable|IHttpActionResult)\\(Of\\s+([\\w.]+(?:\\(Of[^)]*\\))?)\\)")
                .matcher(type);
        if (g.find()) return g.group(1);
        if (type.matches("(?i)IHttpActionResult|IActionResult|ActionResult")) return "—";
        return type;
    }

    private boolean isScalar(String type) {
        String t = type.toLowerCase().replace("?", "");
        int dot = t.lastIndexOf('.');
        if (dot >= 0) t = t.substring(dot + 1);
        return SCALAR_TYPES.contains(t);
    }

    private List<Param> parseParams(String rawParams) {
        List<Param> params = new ArrayList<>();
        if (rawParams == null || rawParams.isBlank()) return params;
        for (String part : splitTopLevel(rawParams)) {
            Matcher m = PARAM.matcher(part.trim());
            if (!m.find()) continue;
            String attrs = m.group(1) == null ? "" : m.group(1).toLowerCase();
            params.add(new Param(m.group(2), m.group(3), attrs.contains("<frombody")));
        }
        return params;
    }

    /** Splits a parameter list on commas that aren't nested inside {@code (Of …)} generics. */
    private List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
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
