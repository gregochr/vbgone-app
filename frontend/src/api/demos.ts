/* Static demo VB.NET sources used by the wizard's "try a demo" flows. */

const DEMO_VB_CONTENT = `Public Class Form1
    'Code for SUM
    Private Sub Button1_Click(sender As Object, e As EventArgs) Handles Button1.Click
        Label3.Text = "Sum of " + TextBox1.Text + " and " + TextBox2.Text
        TextBox3.Text = Int(TextBox1.Text) + Int(TextBox2.Text)
    End Sub
    'Code for Difference
    Private Sub Button2_Click(sender As Object, e As EventArgs) Handles Button2.Click
        Label3.Text = "Difference of " + TextBox1.Text + " and " + TextBox2.Text
        TextBox3.Text = Int(TextBox1.Text) - Int(TextBox2.Text)
    End Sub
    'Code for Product
    Private Sub Button3_Click(sender As Object, e As EventArgs) Handles Button3.Click
        Label3.Text = "Product of " + TextBox1.Text + " and " + TextBox2.Text
        TextBox3.Text = Int(TextBox1.Text) * Int(TextBox2.Text)
    End Sub
    'Code for Quotient
    Private Sub Button4_Click(sender As Object, e As EventArgs) Handles Button4.Click
        Label3.Text = "Quotient of " + TextBox1.Text + " and " + TextBox2.Text
        TextBox3.Text = Int(TextBox1.Text) / Int(TextBox2.Text)
    End Sub
    'Code for Clear
    Private Sub Button5_Click(sender As Object, e As EventArgs) Handles Button5.Click
        TextBox1.Text = ""
        TextBox2.Text = ""
        TextBox3.Text = ""
        Label3.Text = "Answer"
    End Sub
    'Code for Exit
    Private Sub Button6_Click(sender As Object, e As EventArgs) Handles Button6.Click
        End
    End Sub
End Class`

const DEMO_FILENAME = 'Form1.vb'

export const DEMO_PROJECT_FILES: { path: string; content: string }[] = [
  {
    path: 'ValidationHelper.vb',
    content: `Public Class ValidationHelper
    Public Function IsNullOrEmpty(value As String) As Boolean
        Return String.IsNullOrEmpty(value)
    End Function

    Public Function IsValidEmail(email As String) As Boolean
        If IsNullOrEmpty(email) Then Return False
        Return email.Contains("@") AndAlso email.Contains(".")
    End Function

    Public Function IsInRange(value As Integer, min As Integer, max As Integer) As Boolean
        Return value >= min AndAlso value <= max
    End Function
End Class`,
  },
  {
    path: 'StringHelper.vb',
    content: `Public Class StringHelper
    Public Function Capitalize(input As String) As String
        If String.IsNullOrEmpty(input) Then Return input
        Return input.Substring(0, 1).ToUpper() & input.Substring(1)
    End Function

    Public Function TruncateWithEllipsis(input As String, maxLength As Integer) As String
        If input.Length <= maxLength Then Return input
        Return input.Substring(0, maxLength) & "..."
    End Function

    Public Function RemoveWhitespace(input As String) As String
        Return input.Replace(" ", "").Replace(vbTab, "")
    End Function

    Public Function CountWords(input As String) As Integer
        If String.IsNullOrEmpty(input) Then Return 0
        Return input.Split(" "c).Length
    End Function
End Class`,
  },
  {
    path: 'DateHelper.vb',
    content: `Public Class DateHelper
    Private validator As New ValidationHelper()

    Public Function IsWeekday(d As Date) As Boolean
        Return d.DayOfWeek <> DayOfWeek.Saturday AndAlso d.DayOfWeek <> DayOfWeek.Sunday
    End Function

    Public Function GetBusinessDaysBetween(startDate As Date, endDate As Date) As Integer
        Dim count As Integer = 0
        Dim current As Date = startDate
        While current <= endDate
            If IsWeekday(current) Then count += 1
            current = current.AddDays(1)
        End While
        Return count
    End Function

    Public Function FormatFriendly(d As Date) As String
        Return d.ToString("dddd, dd MMMM yyyy")
    End Function
End Class`,
  },
  {
    path: 'Calculator.vb',
    content: `Public Class Calculator
    Private stringHelper As New StringHelper()
    Private dateHelper As New DateHelper()

    Public Function Add(a As Integer, b As Integer) As Integer
        Return a + b
    End Function

    Public Function Subtract(a As Integer, b As Integer) As Integer
        Return a - b
    End Function

    Public Function Multiply(a As Integer, b As Integer) As Integer
        Return a * b
    End Function

    Public Function Divide(a As Integer, b As Integer) As Double
        If b = 0 Then Throw New DivideByZeroException("Cannot divide by zero.")
        Return CDbl(a) / CDbl(b)
    End Function

    Public Function Power(base As Integer, exponent As Integer) As Long
        Return CLng(Math.Pow(base, exponent))
    End Function

    Public Function CalculateCompound(principal As Double, rate As Double, years As Integer) As Double
        Return principal * Math.Pow(1 + rate, years)
    End Function
End Class`,
  },
]

const DEMO_COMPLEX_CONTENT = `' OrderProcessor.vb — handles everything for order processing
' Written by Dave, 2009. Updated by Steve, 2012. Fixed by nobody since.
Imports System.Data.SqlClient
Imports System.Windows.Forms
Imports System.IO

Public Class OrderProcessor
    Inherits Form

    ' Database connection
    Dim cn As SqlConnection
    Dim cmd As SqlCommand
    Dim dr As SqlDataReader
    Dim da As SqlDataAdapter
    Dim ds As DataSet

    ' Form controls
    Dim txtN As TextBox
    Dim txtA As TextBox
    Dim txtQ As TextBox
    Dim lblT As Label
    Dim dgv As DataGridView
    Dim btnS As Button
    Dim btnP As Button

    ' Constants? What constants?
    Dim t As Double = 0.0825
    Dim d1 As Double = 0.1
    Dim d2 As Double = 0.15
    Dim d3 As Double = 0.2
    Dim s1 As Double = 5.99
    Dim s2 As Double = 9.99
    Dim s3 As Double = 14.99
    Dim maxR As Integer = 3

    Private Sub btnS_Click(sender As Object, e As EventArgs) Handles btnS.Click
        On Error Resume Next
        Dim n As String = txtN.Text
        Dim a As Double = CDbl(txtA.Text)
        Dim q As Integer = CInt(txtQ.Text)
        Dim tot As Double = 0
        Dim disc As Double = 0
        Dim ship As Double = 0
        Dim tx As Double = 0
        Dim msg As String = ""

        ' Calculate discount
        If a > 0 Then
            If q > 0 Then
                If a * q > 100 Then
                    If a * q > 500 Then
                        If a * q > 1000 Then
                            disc = d3
                        Else
                            disc = d2
                        End If
                    Else
                        disc = d1
                    End If
                Else
                    disc = 0
                End If
            End If
        End If

        ' Calculate shipping
        If q > 0 Then
            If q <= 5 Then
                ship = s1
            Else
                If q <= 20 Then
                    ship = s2
                Else
                    ship = s3
                End If
            End If
        End If

        tot = (a * q) - ((a * q) * disc) + ship
        tx = tot * t
        tot = tot + tx

        ' Save to database
        cn = New SqlConnection("Server=PROD-DB-01;Database=Orders;Trusted_Connection=True;")
        cn.Open()
        cmd = New SqlCommand("INSERT INTO Orders (CustomerName, Amount, Quantity, Discount, Shipping, Tax, Total, OrderDate) VALUES ('" & n & "', " & a & ", " & q & ", " & disc & ", " & ship & ", " & tx & ", " & tot & ", '" & DateTime.Now.ToString() & "')", cn)
        cmd.ExecuteNonQuery()
        cn.Close()

        ' Also log to file
        Dim sw As New StreamWriter("C:\\OrderLog\\orders.txt", True)
        sw.WriteLine(DateTime.Now.ToString() & "|" & n & "|" & tot)
        sw.Close()

        ' Update the grid
        da = New SqlDataAdapter("SELECT * FROM Orders WHERE CustomerName = '" & n & "' ORDER BY OrderDate DESC", cn)
        ds = New DataSet()
        da.Fill(ds)
        dgv.DataSource = ds.Tables(0)

        ' Format the total
        lblT.Text = "Total: $" & tot.ToString("0.00") & " (Tax: $" & tx.ToString("0.00") & ", Disc: " & (disc * 100).ToString() & "%, Ship: $" & ship.ToString("0.00") & ")"

        ' Show confirmation
        MsgBox("Order saved for " & n & ". Total: $" & tot.ToString("0.00"))
    End Sub

    Public Function CalculateTotal(a As Double, q As Integer) As Double
        Dim tot As Double = 0
        Dim disc As Double = 0
        Dim ship As Double = 0
        Dim tx As Double = 0

        ' Calculate discount — copy pasted from above
        If a > 0 Then
            If q > 0 Then
                If a * q > 100 Then
                    If a * q > 500 Then
                        If a * q > 1000 Then
                            disc = d3
                        Else
                            disc = d2
                        End If
                    Else
                        disc = d1
                    End If
                Else
                    disc = 0
                End If
            End If
        End If

        ' Calculate shipping — copy pasted from above
        If q > 0 Then
            If q <= 5 Then
                ship = s1
            Else
                If q <= 20 Then
                    ship = s2
                Else
                    ship = s3
                End If
            End If
        End If

        tot = (a * q) - ((a * q) * disc) + ship
        tx = tot * t
        tot = tot + tx
        Return tot
    End Function

    Public Function ProcessRefund(orderId As Integer, reason As String) As Boolean
        On Error Resume Next
        Dim r As Boolean = False
        cn = New SqlConnection("Server=PROD-DB-01;Database=Orders;Trusted_Connection=True;")
        cn.Open()

        cmd = New SqlCommand("SELECT * FROM Orders WHERE OrderId = " & orderId, cn)
        dr = cmd.ExecuteReader()

        If dr.Read() Then
            Dim tot As Double = CDbl(dr("Total"))
            Dim cnt As Integer = 0

            ' Check how many refunds already
            dr.Close()
            cmd = New SqlCommand("SELECT COUNT(*) FROM Refunds WHERE OrderId = " & orderId, cn)
            cnt = CInt(cmd.ExecuteScalar())

            If cnt < maxR Then
                If tot > 0 Then
                    If reason <> "" Then
                        cmd = New SqlCommand("INSERT INTO Refunds (OrderId, Amount, Reason, RefundDate) VALUES (" & orderId & ", " & tot & ", '" & reason & "', '" & DateTime.Now.ToString() & "')", cn)
                        cmd.ExecuteNonQuery()

                        cmd = New SqlCommand("UPDATE Orders SET Total = 0, Refunded = 1 WHERE OrderId = " & orderId, cn)
                        cmd.ExecuteNonQuery()

                        ' Send email — hardcoded SMTP
                        Dim smtp As New System.Net.Mail.SmtpClient("mail.company.local")
                        Dim mail As New System.Net.Mail.MailMessage()
                        mail.From = New System.Net.Mail.MailAddress("orders@company.local")
                        mail.To.Add("refunds@company.local")
                        mail.Subject = "Refund Processed #" & orderId
                        mail.Body = "Refund of $" & tot.ToString("0.00") & " processed for order " & orderId & ". Reason: " & reason
                        smtp.Send(mail)

                        ' Also log to file
                        Dim sw As New StreamWriter("C:\\OrderLog\\refunds.txt", True)
                        sw.WriteLine(DateTime.Now.ToString() & "|" & orderId & "|" & tot & "|" & reason)
                        sw.Close()

                        MsgBox("Refund processed.")
                        r = True
                    End If
                End If
            Else
                MsgBox("Maximum refunds reached for this order.")
            End If
        End If

        cn.Close()
        Return r
    End Function

    Private Sub btnP_Click(sender As Object, e As EventArgs) Handles btnP.Click
        On Error Resume Next
        ' Print report — also does way too much
        cn = New SqlConnection("Server=PROD-DB-01;Database=Orders;Trusted_Connection=True;")
        cn.Open()
        cmd = New SqlCommand("SELECT * FROM Orders WHERE OrderDate >= '" & DateTime.Today.AddDays(-30).ToString() & "' ORDER BY Total DESC", cn)
        dr = cmd.ExecuteReader()
        Dim sw As New StreamWriter("C:\\OrderLog\\report_" & DateTime.Now.ToString("yyyyMMdd") & ".txt")
        Dim gt As Double = 0
        Dim gc As Integer = 0
        While dr.Read()
            Dim n As String = dr("CustomerName").ToString()
            Dim tot As Double = CDbl(dr("Total"))
            sw.WriteLine(n & " | $" & tot.ToString("0.00"))
            gt = gt + tot
            gc = gc + 1
        End While
        sw.WriteLine("---")
        sw.WriteLine("Total Orders: " & gc)
        sw.WriteLine("Grand Total: $" & gt.ToString("0.00"))
        sw.WriteLine("Average: $" & (gt / gc).ToString("0.00"))
        sw.Close()
        dr.Close()
        cn.Close()
        MsgBox("Report generated: " & gc & " orders, $" & gt.ToString("0.00") & " total.")
    End Sub

    Public Function ValidateOrder(n As String, a As String, q As String) As String
        Dim err As String = ""
        If n = "" Then
            err = err & "Name required. "
        End If
        If Not IsNumeric(a) Then
            err = err & "Amount must be numeric. "
        Else
            If CDbl(a) <= 0 Then
                err = err & "Amount must be positive. "
            End If
        End If
        If Not IsNumeric(q) Then
            err = err & "Quantity must be numeric. "
        Else
            If CInt(q) <= 0 Then
                err = err & "Quantity must be positive. "
            End If
        End If
        Return err
    End Function

    Public Function GetDiscountTier(subtotal As Double) As String
        ' GoTo for flow control — a classic
        If subtotal <= 0 Then GoTo NoDiscount
        If subtotal <= 100 Then GoTo NoDiscount
        If subtotal <= 500 Then GoTo Tier1
        If subtotal <= 1000 Then GoTo Tier2
        GoTo Tier3

NoDiscount:
        Return "NONE"
Tier1:
        Return "BRONZE"
Tier2:
        Return "SILVER"
Tier3:
        Return "GOLD"
    End Function
End Class`

const DEMO_COMPLEX_FILENAME = 'OrderProcessor.vb'

// Assure demo. Unlike the Migrate complex demo (a WinForms God class that inherits Form
// and can't run headless), this is the SAME OrderProcessor business logic with the UI
// severed — pure, self-contained, and compilable standalone on the Linux CLR sidecar, so
// the real characterisation run reaches GREEN instead of the WinForms ERROR path. It keeps
// the supporting types (LineItem, Order) the suite needs, and preserves the observed faults
// (silent unknown-code discount, divide-by-zero, non-numeric cast).
const DEMO_ASSURE_CONTENT = `' OrderProcessor.vb — order-processing business logic (no UI; runs headless on the CLR)
Imports System
Imports System.Collections.Generic

Public Class LineItem
    Public Property UnitPrice As Decimal
    Public Property Quantity As Integer
    Public Sub New(unitPrice As Decimal, quantity As Integer)
        Me.UnitPrice = unitPrice
        Me.Quantity = quantity
    End Sub
End Class

Public Class Order
    Public Property Items As List(Of LineItem) = New List(Of LineItem)()
    Public Property Total As Decimal
End Class

Public Class OrderProcessor
    ' Sum of unitPrice * quantity. A null line item throws NullReferenceException.
    Public Function CalculateTotal(items As IReadOnlyList(Of LineItem)) As Decimal
        Dim total As Decimal = 0D
        For Each item In items
            total += item.UnitPrice * item.Quantity
        Next
        Return total
    End Function

    ' Known codes discount; an unknown code silently returns the subtotal unchanged.
    Public Function ApplyDiscount(subtotal As Decimal, code As String) As Decimal
        Select Case code
            Case "SAVE10"
                Return subtotal * 0.9D
            Case "HALF"
                Return subtotal * 0.5D
            Case Else
                Return subtotal
        End Select
    End Function

    ' Integer division — a headcount of 0 throws DivideByZeroException.
    Public Function SplitPerHead(total As Decimal, headcount As Integer) As Decimal
        Return CDec(CLng(total) \\ headcount)
    End Function

    ' Quantities come straight off a text field; a non-numeric value throws InvalidCastException.
    Public Function ParseQty(text As String) As Integer
        Return CInt(text)
    End Function

    Public Function ValidateOrder(order As Order) As Boolean
        Return order.Items.Count > 0 AndAlso order.Total >= 0D
    End Function
End Class`

// Portfolio demos — REAL multi-class VB.NET so the live /assess classifier (which parses
// the source, not a zip) produces a genuine mixed report. Shapes are chosen to hit each
// bucket: plain classes → net-ready; pure methods inside a Form → windows-gated; handlers /
// control access / MsgBox → refactor-first.
const DEMO_ESTATE_MIXED = `' LegacyEstate — a mixed VB.NET estate (business logic + WinForms)

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
End Class`

const DEMO_ESTATE_BLOCKED = `' WinFormsApp — a WinForms app with no headless surface

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
End Class`

export {
  DEMO_VB_CONTENT,
  DEMO_FILENAME,
  DEMO_COMPLEX_CONTENT,
  DEMO_COMPLEX_FILENAME,
  DEMO_ASSURE_CONTENT,
  DEMO_ESTATE_MIXED,
  DEMO_ESTATE_BLOCKED,
}
