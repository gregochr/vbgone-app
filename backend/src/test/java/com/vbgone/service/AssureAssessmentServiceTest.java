package com.vbgone.service;

import com.vbgone.model.Bucket;
import com.vbgone.model.ClassReadiness;
import com.vbgone.model.MethodReadiness;
import com.vbgone.model.MigrationSession;
import com.vbgone.model.ReadinessReport;
import com.vbgone.model.RestApiEndpoint;
import com.vbgone.model.VbSourceFile;
import com.vbgone.model.ZipManifest;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssureAssessmentServiceTest {

    private AssureAssessmentService service;
    private SessionStore sessionStore;

    @BeforeEach
    void setUp() {
        sessionStore = new SessionStore();
        service = new AssureAssessmentService(sessionStore);
    }

    private ClassReadiness classNamed(ReadinessReport r, String name) {
        return r.classes().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private MethodReadiness method(ClassReadiness c, String name) {
        return c.methods().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void cleanBusinessLogic_isNetReady() {
        String vb = """
                Public Class OrderProcessor
                    Public Function CalculateTotal(qty As Integer, price As Decimal) As Decimal
                        Return qty * price
                    End Function
                    Public Function ApplyDiscount(subtotal As Decimal, code As String) As Decimal
                        If code = "SAVE10" Then Return subtotal * 0.9D
                        Return subtotal
                    End Function
                End Class
                """;

        ReadinessReport r = service.assess("OrderProcessor.vb", vb);

        ClassReadiness c = classNamed(r, "OrderProcessor");
        assertThat(c.bucket()).isEqualTo(Bucket.NET_READY);
        assertThat(c.methods()).hasSize(2);
        assertThat(c.methods()).allMatch(m -> m.bucket() == Bucket.NET_READY);
        assertThat(r.confidence()).isEqualTo("static");
        assertThat(r.sessionId()).isNotBlank();
    }

    @Test
    void winFormsEventHandlerTouchingControls_isRefactorFirst() {
        String vb = """
                Public Class Form1
                    Inherits Form
                    Private WithEvents Button1 As Button
                    Private TextBox1 As TextBox
                    Private Sub Button1_Click(sender As Object, e As EventArgs) Handles Button1.Click
                        TextBox1.Text = "result"
                    End Sub
                End Class
                """;

        ClassReadiness c = classNamed(service.assess("Form1.vb", vb), "Form1");
        assertThat(c.bucket()).isEqualTo(Bucket.REFACTOR_FIRST);
        assertThat(c.methods().get(0).bucket()).isEqualTo(Bucket.REFACTOR_FIRST);
    }

    @Test
    void pureMethodInsideAForm_isWindowsGated() {
        String vb = """
                Public Class FeeForm
                    Inherits Form
                    Private Function ComputeFee(amount As Decimal) As Decimal
                        Return amount * 0.05D
                    End Function
                End Class
                """;

        ClassReadiness c = classNamed(service.assess("FeeForm.vb", vb), "FeeForm");
        assertThat(c.bucket()).isEqualTo(Bucket.WINDOWS_GATED);
        assertThat(c.methods().get(0).bucket()).isEqualTo(Bucket.WINDOWS_GATED);
        assertThat(c.methods().get(0).reason()).contains("reflection");
    }

    @Test
    void msgBox_isRefactorFirst() {
        String vb = """
                Public Class PaymentDialog
                    Inherits Form
                    Private Sub Charge(amount As Decimal)
                        MsgBox("charged " & amount)
                    End Sub
                End Class
                """;

        ClassReadiness c = classNamed(service.assess("PaymentDialog.vb", vb), "PaymentDialog");
        assertThat(c.methods().get(0).bucket()).isEqualTo(Bucket.REFACTOR_FIRST);
    }

    @Test
    void dataTableRows_isNotMistakenForControls_staysNetReady() {
        // Regression: `.Rows` on an ADO.NET DataTable must not be read as a DataGridView.
        String vb = """
                Public Class OrderGateway
                    Public Function HasQueue() As Boolean
                        Dim q As New OrdersBLL
                        Return q.GetQBQueue().Rows.Count > 0
                    End Function
                    Public Function FirstId(dt As DataTable) As Integer
                        Return CInt(dt.Rows(0).Item("O_ID"))
                    End Function
                End Class
                """;

        ClassReadiness c = classNamed(service.assess("OrderGateway.vb", vb), "OrderGateway");
        assertThat(c.bucket()).isEqualTo(Bucket.NET_READY);
        assertThat(c.methods()).allMatch(m -> m.bucket() == Bucket.NET_READY);
    }

    @Test
    void qualifiedSystemText_isNotMistakenForControls_staysNetReady() {
        // Regression: `System.Text.StringBuilder`/`System.Text.Encoding` are BCL usages, not a
        // control's `.Text` — the receiver `System` must not trip CONTROL_PROP. Pure back-end code.
        String vb = """
                Public Class Serialiser
                    Public Function Build(name As String) As String
                        Dim str As New System.Text.StringBuilder
                        str.Append(name)
                        Return str.ToString()
                    End Function
                    Public Function Encode(payload As String) As Byte()
                        Return System.Text.Encoding.UTF8.GetBytes(payload)
                    End Function
                End Class
                """;

        ClassReadiness c = classNamed(service.assess("Serialiser.vb", vb), "Serialiser");
        assertThat(c.bucket()).isEqualTo(Bucket.NET_READY);
        assertThat(c.methods()).allMatch(m -> m.bucket() == Bucket.NET_READY);
    }

    @Test
    void aspNetSessionState_isRefactorFirst_asWebHostCoupling() {
        // Session/Application are the request-pipeline coupling that stops a method running headless.
        String vb = """
                Public Class QBService
                    Public Function ClientVersion(v As String) As String
                        Return v
                    End Function
                    Public Function GetLastError(ticket As String) As String
                        Return CStr(Session("QBLastError"))
                    End Function
                    Public Function CacheItem(id As String) As Boolean
                        Application("KartrisQBItemListID") = id
                        Return True
                    End Function
                End Class
                """;

        ClassReadiness c = classNamed(service.assess("QBService.vb", vb), "QBService");
        assertThat(c.bucket()).isEqualTo(Bucket.REFACTOR_FIRST);

        MethodReadiness clientVersion = method(c, "ClientVersion");
        assertThat(clientVersion.bucket()).isEqualTo(Bucket.NET_READY);

        MethodReadiness lastError = method(c, "GetLastError");
        assertThat(lastError.bucket()).isEqualTo(Bucket.REFACTOR_FIRST);
        assertThat(lastError.reason()).contains("Session/Application");

        assertThat(method(c, "CacheItem").bucket()).isEqualTo(Bucket.REFACTOR_FIRST);
    }

    @Test
    void lowercaseResponseLocal_isNotMistakenForTheIntrinsic() {
        // A `response` parameter (lowercase) must not trip the PascalCase Session/Response intrinsic check.
        String vb = """
                Public Class Parser
                    Public Function Length(response As String) As Integer
                        Return response.Length
                    End Function
                End Class
                """;

        ClassReadiness c = classNamed(service.assess("Parser.vb", vb), "Parser");
        assertThat(c.methods().get(0).bucket()).isEqualTo(Bucket.NET_READY);
    }

    @Test
    void gridTouchedViaDeclaredControlField_stillCountsAsUi() {
        // Removing Rows/Cells from CONTROL_PROP must not lose grid detection when it's a declared field.
        String vb = """
                Public Class GridForm
                    Inherits Form
                    Private WithEvents grid As DataGridView
                    Private Sub ClearAll()
                        grid.Rows.Clear()
                    End Sub
                End Class
                """;

        ClassReadiness c = classNamed(service.assess("GridForm.vb", vb), "GridForm");
        assertThat(method(c, "ClearAll").bucket()).isEqualTo(Bucket.REFACTOR_FIRST);
    }

    @Test
    void mixedEstate_talliesClassesAndMethods() {
        String vb = """
                Public Class OrderProcessor
                    Public Function CalculateTotal(qty As Integer, price As Decimal) As Decimal
                        Return qty * price
                    End Function
                    Public Function ApplyDiscount(subtotal As Decimal) As Decimal
                        Return subtotal
                    End Function
                End Class

                Public Class FeeForm
                    Inherits Form
                    Private Function ComputeFee(amount As Decimal) As Decimal
                        Return amount * 0.05D
                    End Function
                End Class

                Public Class Form1
                    Inherits Form
                    Private TextBox1 As TextBox
                    Private Sub Button1_Click(sender As Object, e As EventArgs) Handles Button1.Click
                        TextBox1.Text = "x"
                    End Sub
                End Class
                """;

        ReadinessReport r = service.assess("Estate.vb", vb);

        assertThat(r.totals().classes()).isEqualTo(3);
        assertThat(r.totals().netReady()).isEqualTo(1);
        assertThat(r.totals().windowsGated()).isEqualTo(1);
        assertThat(r.totals().refactorFirst()).isEqualTo(1);
        assertThat(r.totals().methods()).isEqualTo(4);
        assertThat(r.totals().methodNetReady()).isEqualTo(2);
        assertThat(r.totals().methodWindowsGated()).isEqualTo(1);
        assertThat(r.totals().methodRefactorFirst()).isEqualTo(1);
    }

    @Test
    void skipsDesignerBoilerplate() {
        String vb = """
                Public Class Form1
                    Inherits Form
                    Private Sub InitializeComponent()
                        Me.Button1 = New Button()
                    End Sub
                    Public Sub New()
                        InitializeComponent()
                    End Sub
                    Public Function NetRevenue(gross As Decimal) As Decimal
                        Return gross * 0.8D
                    End Function
                End Class
                """;

        ClassReadiness c = classNamed(service.assess("Form1.vb", vb), "Form1");
        // Only the real business method is counted; New/InitializeComponent are skipped.
        assertThat(c.methods()).extracting(m -> m.name()).containsExactly("NetRevenue");
        assertThat(c.methods().get(0).bucket()).isEqualTo(Bucket.WINDOWS_GATED);
    }

    // ── Portfolio demo blobs (must stay in sync with frontend DEMO_ESTATE_* content) ──

    private static final String DEMO_MIXED = """
            Public Class OrderService
                Public Function PlaceOrder(customerId As Integer, total As Decimal) As Integer
                    Return customerId + CInt(total)
                End Function
                Public Function CalculateTotal(qty As Integer, price As Decimal) As Decimal
                    Return qty * price
                End Function
                Public Function ApplyDiscount(subtotal As Decimal, code As String) As Decimal
                    If code = "SAVE10" Then Return subtotal * 0.9D
                    Return subtotal
                End Function
            End Class

            Public Class PricingEngine
                Public Function Quote(basePrice As Decimal, margin As Decimal) As Decimal
                    Return basePrice * (1D + margin)
                End Function
                Public Function RoundToTier(amount As Decimal) As Decimal
                    Return Math.Ceiling(amount)
                End Function
            End Class

            Public Class TaxCalculator
                Public Function VatFor(net As Decimal) As Decimal
                    Return net * 0.2D
                End Function
                Public Function NetOf(gross As Decimal) As Decimal
                    Return gross / 1.2D
                End Function
            End Class

            Public Class LedgerView
                Inherits Form
                Private Function Post(amount As Decimal) As Decimal
                    Return amount * -1D
                End Function
                Private Function Reconcile(a As Decimal, b As Decimal) As Decimal
                    Return a - b
                End Function
            End Class

            Public Class ReportRenderer
                Inherits Form
                Private Function BuildSummary(count As Integer, total As Decimal) As String
                    Return count.ToString() & " orders"
                End Function
            End Class

            Public Class CustomerEntryForm
                Inherits Form
                Private WithEvents btnSave As Button
                Private txtName As TextBox
                Private Sub btnSave_Click(sender As Object, e As EventArgs) Handles btnSave.Click
                    txtName.Text = txtName.Text.Trim()
                End Sub
            End Class

            Public Class MainForm
                Inherits Form
                Private WithEvents btnRun As Button
                Private Sub btnRun_Click(sender As Object, e As EventArgs) Handles btnRun.Click
                    MsgBox("Running")
                End Sub
            End Class""";

    private static final String DEMO_BLOCKED = """
            Public Class CalculatorForm
                Inherits Form
                Private WithEvents btnEquals As Button
                Private txtDisplay As TextBox
                Private Sub btnEquals_Click(sender As Object, e As EventArgs) Handles btnEquals.Click
                    txtDisplay.Text = "0"
                End Sub
            End Class

            Public Class SettingsForm
                Inherits Form
                Private chkAuto As CheckBox
                Private Sub Save()
                    Dim auto As Boolean = chkAuto.Checked
                End Sub
            End Class

            Public Class PrintHelper
                Inherits Form
                Private Function Paginate(lines As Integer, perPage As Integer) As Integer
                    Return lines \\ perPage
                End Function
            End Class

            Public Class GridView
                Inherits Form
                Private Function SortKey(a As Integer, b As Integer) As Integer
                    Return a - b
                End Function
            End Class""";

    @Test
    void demoMixedEstate_producesAllThreeBuckets() {
        ReadinessReport r = service.assess("LegacyEstate.zip", DEMO_MIXED);
        // Plain classes → net-ready; pure methods in a Form → windows-gated; handlers/controls/MsgBox → refactor-first.
        assertThat(r.totals().classes()).isEqualTo(7);
        assertThat(r.totals().netReady()).isEqualTo(3);
        assertThat(r.totals().windowsGated()).isEqualTo(2);
        assertThat(r.totals().refactorFirst()).isEqualTo(2);
        assertThat(classNamed(r, "OrderService").bucket()).isEqualTo(Bucket.NET_READY);
        assertThat(classNamed(r, "LedgerView").bucket()).isEqualTo(Bucket.WINDOWS_GATED);
        assertThat(classNamed(r, "MainForm").bucket()).isEqualTo(Bucket.REFACTOR_FIRST);
    }

    @Test
    void demoBlockedEstate_hasNoReadyClasses() {
        ReadinessReport r = service.assess("WinFormsApp.zip", DEMO_BLOCKED);
        assertThat(r.totals().classes()).isEqualTo(4);
        assertThat(r.totals().netReady()).isZero();
        assertThat(r.totals().windowsGated()).isGreaterThan(0);
        assertThat(r.totals().refactorFirst()).isGreaterThan(0);
    }

    // ── assurable subset (the compile-scope fix) + estate scan ──

    @Test
    void assess_pinsOnlyNetReadyClassesAsTheAssurableSubset() {
        ReadinessReport r = service.assess("LegacyEstate.zip", DEMO_MIXED);
        MigrationSession s = sessionStore.get(r.sessionId()).orElseThrow();
        String subset = s.getAssurableSource();
        // The subset that the CLR run compiles must contain the clean classes...
        assertThat(subset).contains("Public Class OrderService").contains("Public Class PricingEngine");
        // ...and NONE of the WinForms classes that would break a headless build.
        assertThat(subset).doesNotContain("Inherits Form").doesNotContain("MainForm");
    }

    @Test
    void assessProject_classifiesAcrossFilesWithRelativePaths() {
        MigrationSession session = sessionStore.create();
        ZipManifest manifest = new ZipManifest(session.getSessionId(), List.of(
                new VbSourceFile("Domain/OrderService.vb", "OrderService.vb", """
                        Public Class OrderService
                            Public Function Total(qty As Integer, price As Decimal) As Decimal
                                Return qty * price
                            End Function
                        End Class"""),
                new VbSourceFile("Forms/MainForm.vb", "MainForm.vb", """
                        Public Class MainForm
                            Inherits Form
                            Private WithEvents btn As Button
                            Private Sub btn_Click(sender As Object, e As EventArgs) Handles btn.Click
                                btn.Text = "x"
                            End Sub
                        End Class""")),
                2);

        ReadinessReport r = service.assessProject(manifest);

        assertThat(r.totals().classes()).isEqualTo(2);
        assertThat(r.totals().netReady()).isEqualTo(1);
        assertThat(r.totals().refactorFirst()).isEqualTo(1);
        assertThat(classNamed(r, "OrderService").file()).isEqualTo("Domain/OrderService.vb");
        assertThat(classNamed(r, "MainForm").file()).isEqualTo("Forms/MainForm.vb");
        // The drill-in compiles only the net-ready class, from across the estate.
        MigrationSession s = sessionStore.get(r.sessionId()).orElseThrow();
        assertThat(s.getAssurableSource()).contains("OrderService").doesNotContain("Inherits Form");
        assertThat(s.getVbContentForClass("OrderService")).contains("Public Class OrderService");
    }

    // ── REST API surface extraction ──

    private RestApiEndpoint endpoint(ReadinessReport r, String verb, String route) {
        return r.restApis().stream()
                .filter(e -> e.verb().equals(verb) && e.route().equals(route))
                .findFirst().orElseThrow(() -> new AssertionError("no " + verb + " " + route));
    }

    @Test
    void webApiController_extractsEndpointsAndIsNotAReadinessClass() {
        String vb = """
                Public Class OrderService
                    Public Function PlaceOrder(id As Integer) As Integer
                        Return id
                    End Function
                End Class

                <RoutePrefix("api/orders")>
                Public Class OrderApiController
                    Inherits ApiController

                    <HttpGet>
                    Public Function Get() As IEnumerable(Of OrderDto)
                        Return Nothing
                    End Function

                    <HttpGet>
                    <Route("{id}")>
                    Public Function GetById(id As Integer) As OrderDto
                        Return Nothing
                    End Function

                    <HttpPost>
                    Public Function Create(<FromBody> request As CreateOrderRequest) As OrderDto
                        Return Nothing
                    End Function

                    <HttpDelete>
                    <Route("{id}")>
                    Public Function Delete(id As Integer) As Boolean
                        Return True
                    End Function
                End Class
                """;

        ReadinessReport r = service.assess("Api.vb", vb);

        // The controller is reported as endpoints only — it isn't in the readiness class buckets.
        assertThat(r.classes()).extracting(ClassReadiness::name).containsExactly("OrderService");
        assertThat(r.restApis()).hasSize(4);

        RestApiEndpoint list = endpoint(r, "GET", "/api/orders");
        assertThat(list.handler()).isEqualTo("OrderApiController.Get");
        assertThat(list.kind()).isEqualTo("Web API");
        assertThat(list.resType()).isEqualTo("OrderDto"); // unwrapped from IEnumerable(Of OrderDto)
        assertThat(list.resStatus()).isEqualTo("200 OK");
        assertThat(list.req()).isNull();

        RestApiEndpoint byId = endpoint(r, "GET", "/api/orders/{id}");
        assertThat(byId.params()).singleElement()
                .satisfies(p -> {
                    assertThat(p.name()).isEqualTo("id");
                    assertThat(p.in()).isEqualTo("path");
                    assertThat(p.type()).isEqualTo("Integer");
                });

        RestApiEndpoint create = endpoint(r, "POST", "/api/orders");
        assertThat(create.reqType()).isEqualTo("CreateOrderRequest");
        assertThat(create.resStatus()).isEqualTo("201 Created");
        assertThat(create.params()).isEmpty(); // the body param is not listed as a path/query input

        assertThat(endpoint(r, "DELETE", "/api/orders/{id}").resStatus()).isEqualTo("204 No Content");
    }

    @Test
    void asmxService_extractsWebMethodAsAsmxEndpoint() {
        String vb = """
                <WebService(Namespace:="http://vbgone/tax")>
                Public Class TaxService
                    Inherits System.Web.Services.WebService

                    <WebMethod>
                    Public Function GetVatRate(countryCode As String) As Decimal
                        Return 0.2D
                    End Function
                End Class
                """;

        ReadinessReport r = service.assess("Services/TaxService.asmx.vb", vb);

        assertThat(r.classes()).isEmpty();
        assertThat(r.restApis()).singleElement().satisfies(e -> {
            assertThat(e.verb()).isEqualTo("GET");
            assertThat(e.kind()).isEqualTo("ASMX");
            assertThat(e.route()).isEqualTo("/TaxService.asmx/GetVatRate");
            assertThat(e.handler()).isEqualTo("TaxService.GetVatRate");
            assertThat(e.resType()).isEqualTo("Decimal");
            assertThat(e.params()).singleElement().satisfies(p -> {
                assertThat(p.name()).isEqualTo("countryCode");
                assertThat(p.in()).isEqualTo("query");
            });
        });
    }

    @Test
    void ordinaryEstate_hasNoRestApis() {
        assertThat(service.assess("LegacyEstate.zip", DEMO_MIXED).restApis()).isEmpty();
    }
}
