package com.vbgone.service;

import com.vbgone.model.Bucket;
import com.vbgone.model.ClassReadiness;
import com.vbgone.model.ReadinessReport;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectAssessmentServiceTest {

    private ProtectAssessmentService service;
    private SessionStore sessionStore;

    @BeforeEach
    void setUp() {
        sessionStore = new SessionStore();
        service = new ProtectAssessmentService(sessionStore);
    }

    private ClassReadiness classNamed(ReadinessReport r, String name) {
        return r.classes().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
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
}
