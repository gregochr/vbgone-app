' OrderProcessor.vb — handles everything for order processing
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
        Dim sw As New StreamWriter("C:\OrderLog\orders.txt", True)
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
                        Dim sw As New StreamWriter("C:\OrderLog\refunds.txt", True)
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
        Dim sw As New StreamWriter("C:\OrderLog\report_" & DateTime.Now.ToString("yyyyMMdd") & ".txt")
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
End Class