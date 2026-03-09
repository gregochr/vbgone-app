import axios from 'axios'

const api = axios.create({ baseURL: '/api/migrate' })

/* ── Types ── */

export interface ClassInfo {
  name: string
  methods: string[]
  dependencies: string[]
  complexity: 'LOW' | 'MEDIUM' | 'HIGH'
  codeQuality?: 'POOR' | 'FAIR' | 'GOOD'
  codeSmells?: string[]
  refactoringSuggestions?: string[]
  vbAntiPatterns?: string[]
}

export interface AnalysisResult {
  sessionId: string
  classes: ClassInfo[]
  suggestedMigrationOrder: string[]
  summary: string
}

export interface InterfaceResult {
  sessionId: string
  className: string
  interfaceName: string
  code: string
}

export interface TestsResult {
  sessionId: string
  className: string
  testClassName: string
  code: string
  testCount: number
}

export interface StubResult {
  sessionId: string
  className: string
  code: string
}

export interface BuildResult {
  sessionId: string
  buildStatus: 'RED' | 'GREEN' | 'ERROR'
  total: number
  passed: number
  failed: number
  errors: string[]
  failedTests: string[]
}

export interface ImplementResult {
  sessionId: string
  className: string
  code: string
  mode: 'STUB' | 'CLAUDE'
}

export interface PullRequestResult {
  sessionId: string
  prUrl: string
  branchName: string
  filesCommitted: string[]
}

export interface VbSourceFile {
  relativePath: string
  filename: string
  content: string
}

export interface ZipManifest {
  sessionId: string
  files: VbSourceFile[]
  totalFiles: number
}

export interface ProjectAnalysis {
  sessionId: string
  classes: ClassInfo[]
  suggestedMigrationOrder: string[]
  dependencyGraph: Record<string, string[]>
  summary: string
}

export interface TokenUsage {
  step: string
  model: string
  inputTokens: number
  outputTokens: number
  cost: number
}

export interface CostResult {
  sessionId: string
  steps: TokenUsage[]
  totalCost: number
}

/* ── Mock toggle ── */

const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true'

/* ── Mock data ── */

const MOCK_SESSION_ID = 'mock-uuid-1234'

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

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms))

/* ── Mock API calls ── */

// Track state across mock calls so build/PR can return consistent data
let lastMockTestCount = 30
const mockMigratedClasses: string[] = []

const mockApi = {
  async analyse(filename: string, content: string): Promise<AnalysisResult> {
    void content
    await delay(1200)

    // Complex demo — return multi-class God class decomposition
    if (filename === DEMO_COMPLEX_FILENAME) {
      return {
        sessionId: MOCK_SESSION_ID,
        classes: [
          {
            name: 'OrderCalculationService',
            methods: ['CalculateDiscount', 'CalculateShipping', 'CalculateTotal'],
            dependencies: [],
            complexity: 'MEDIUM',
            codeQuality: 'FAIR' as const,
            codeSmells: [
              'Magic numbers — hardcoded tax rates, discount thresholds, shipping costs',
            ],
            refactoringSuggestions: [
              'Extract discount thresholds and shipping tiers into configuration constants',
            ],
            vbAntiPatterns: ['Deep nesting — 5 levels of nested If statements'],
          },
          {
            name: 'OrderValidator',
            methods: ['ValidateOrder', 'GetDiscountTier'],
            dependencies: [],
            complexity: 'LOW',
            codeQuality: 'FAIR' as const,
            codeSmells: ['GoTo statements used for flow control'],
            refactoringSuggestions: ['Replace GoTo with early returns or switch expression'],
            vbAntiPatterns: ['GoTo statements'],
          },
          {
            name: 'RefundService',
            methods: ['ProcessRefund'],
            dependencies: ['OrderCalculationService'],
            complexity: 'HIGH',
            codeQuality: 'POOR' as const,
            codeSmells: [
              'Mixed concerns — business logic, database access, email sending, file I/O',
              'SQL injection — string concatenation for SQL queries',
              'On Error Resume Next — silently swallows all exceptions',
            ],
            refactoringSuggestions: [
              'Accept IOrderRepository and INotificationService via constructor injection',
              'Remove MsgBox calls — return result object instead',
            ],
            vbAntiPatterns: ['On Error Resume Next', 'SQL injection via string concatenation'],
          },
          {
            name: 'OrderProcessor',
            methods: ['SubmitOrder'],
            dependencies: ['OrderCalculationService', 'OrderValidator', 'RefundService'],
            complexity: 'HIGH',
            codeQuality: 'POOR' as const,
            codeSmells: [
              'God class — too many responsibilities in a single file',
              'Copy-paste duplication — discount and shipping logic duplicated',
              'Hardcoded connection strings and file paths',
            ],
            refactoringSuggestions: [
              'Orchestration only — delegate to extracted services',
              'Accept dependencies via constructor injection',
            ],
            vbAntiPatterns: [
              'On Error Resume Next',
              'SQL injection via string concatenation',
              'MsgBox for user feedback in business logic',
            ],
          },
        ],
        suggestedMigrationOrder: [
          'OrderCalculationService',
          'OrderValidator',
          'RefundService',
          'OrderProcessor',
        ],
        summary:
          'God class decomposed into 4 classes. OrderCalculationService and OrderValidator are leaf nodes with no dependencies. RefundService depends on OrderCalculationService. OrderProcessor orchestrates all three. Recommended migration order starts with the two independent services.',
      }
    }

    return {
      sessionId: MOCK_SESSION_ID,
      classes: [
        {
          name: 'Form1',
          methods: [
            'Button1_Click',
            'Button2_Click',
            'Button3_Click',
            'Button4_Click',
            'Button5_Click',
            'Button6_Click',
          ],
          dependencies: [],
          complexity: 'LOW',
          codeQuality: 'FAIR' as const,
          codeSmells: ['Mixed concerns — UI logic mixed with business logic'],
          refactoringSuggestions: ['Extract arithmetic operations into a separate service class'],
          vbAntiPatterns: ['Implicit type conversions via Int()'],
        },
      ],
      suggestedMigrationOrder: ['Form1'],
      summary:
        'One WinForms class found with 6 event handlers — sum, difference, product, quotient, clear, exit. No dependencies. Good candidate for migration.',
    }
  },

  async generateInterface(sessionId: string, className: string): Promise<InterfaceResult> {
    void sessionId
    await delay(800)
    if (!mockMigratedClasses.includes(className)) {
      mockMigratedClasses.push(className)
    }

    const interfaceCode: Record<string, string> = {
      OrderCalculationService: `namespace VBGone.Generated;

public interface IOrderCalculationService
{
    double CalculateDiscount(double unitPrice, int quantity);
    double CalculateShipping(int quantity);
    double CalculateTotal(double unitPrice, int quantity);
}`,
      OrderValidator: `namespace VBGone.Generated;

public interface IOrderValidator
{
    string ValidateOrder(string name, string amount, string quantity);
    string GetDiscountTier(double subtotal);
}`,
      RefundService: `namespace VBGone.Generated;

public interface IRefundService
{
    bool ProcessRefund(int orderId, string reason);
}`,
      OrderProcessor: `namespace VBGone.Generated;

public interface IOrderProcessor
{
    void SubmitOrder(string customerName, double unitPrice, int quantity);
}`,
    }

    return {
      sessionId: MOCK_SESSION_ID,
      className,
      interfaceName: `I${className}`,
      code:
        interfaceCode[className] ??
        `namespace VBGone.Generated;\n\npublic interface I${className}\n{\n    int Add(int a, int b);\n    int Subtract(int a, int b);\n    int Multiply(int a, int b);\n    double Divide(int a, int b);\n    int Modulus(int a, int b);\n}`,
    }
  },

  async generateTests(sessionId: string, className: string): Promise<TestsResult> {
    void sessionId
    await delay(1000)

    const testCode: Record<string, { code: string; count: number }> = {
      OrderCalculationService: {
        count: 18,
        code: `using NUnit.Framework;

namespace VBGone.Generated.Tests;

[TestFixture]
public class OrderCalculationServiceTests
{
    private IOrderCalculationService _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderCalculationService();
    }

    // ── CalculateDiscount ──

    [TestCase(10.0, 5, ExpectedResult = 0.0)]
    [TestCase(20.0, 6, ExpectedResult = 0.10)]
    [TestCase(50.0, 11, ExpectedResult = 0.15)]
    [TestCase(100.0, 11, ExpectedResult = 0.20)]
    public double CalculateDiscount_ReturnsCorrectTier(double unitPrice, int quantity)
    {
        return _sut.CalculateDiscount(unitPrice, quantity);
    }

    [Test]
    public void CalculateDiscount_BoundaryAt100_ReturnsZero()
    {
        Assert.That(_sut.CalculateDiscount(10.0, 10), Is.EqualTo(0.0));
    }

    [Test]
    public void CalculateDiscount_JustOver100_ReturnsTier1()
    {
        Assert.That(_sut.CalculateDiscount(10.1, 10), Is.EqualTo(0.10));
    }

    [Test]
    public void CalculateDiscount_ZeroQuantity_ReturnsZero()
    {
        Assert.That(_sut.CalculateDiscount(50.0, 0), Is.EqualTo(0.0));
    }

    [Test]
    public void CalculateDiscount_NegativePrice_ReturnsZero()
    {
        Assert.That(_sut.CalculateDiscount(-10.0, 5), Is.EqualTo(0.0));
    }

    // ── CalculateShipping ──

    [TestCase(1, ExpectedResult = 5.99)]
    [TestCase(5, ExpectedResult = 5.99)]
    [TestCase(6, ExpectedResult = 9.99)]
    [TestCase(20, ExpectedResult = 9.99)]
    [TestCase(21, ExpectedResult = 14.99)]
    [TestCase(100, ExpectedResult = 14.99)]
    public double CalculateShipping_ReturnsCorrectTier(int quantity)
    {
        return _sut.CalculateShipping(quantity);
    }

    [Test]
    public void CalculateShipping_ZeroQuantity_ReturnsZero()
    {
        Assert.That(_sut.CalculateShipping(0), Is.EqualTo(0.0));
    }

    // ── CalculateTotal ──

    [Test]
    public void CalculateTotal_SmallOrder_IncludesTaxAndShipping()
    {
        var total = _sut.CalculateTotal(10.0, 2);
        Assert.That(total, Is.GreaterThan(20.0));
    }

    [Test]
    public void CalculateTotal_LargeOrder_AppliesDiscount()
    {
        var noDiscount = _sut.CalculateTotal(10.0, 5);
        var withDiscount = _sut.CalculateTotal(20.0, 6);
        Assert.That(withDiscount, Is.LessThan(20.0 * 6 * 1.1));
    }

    [Test]
    public void CalculateTotal_ZeroQuantity_ReturnsZero()
    {
        Assert.That(_sut.CalculateTotal(10.0, 0), Is.EqualTo(0.0));
    }
}`,
      },
      OrderValidator: {
        count: 12,
        code: `using NUnit.Framework;

namespace VBGone.Generated.Tests;

[TestFixture]
public class OrderValidatorTests
{
    private IOrderValidator _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderValidator();
    }

    // ── ValidateOrder ──

    [Test]
    public void ValidateOrder_AllValid_ReturnsEmptyString()
    {
        Assert.That(_sut.ValidateOrder("Alice", "19.99", "3"), Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_EmptyName_ReturnsNameRequired()
    {
        Assert.That(_sut.ValidateOrder("", "10", "1"), Does.Contain("Name required"));
    }

    [Test]
    public void ValidateOrder_NonNumericAmount_ReturnsAmountError()
    {
        Assert.That(_sut.ValidateOrder("Alice", "abc", "1"), Does.Contain("Amount must be numeric"));
    }

    [Test]
    public void ValidateOrder_NegativeAmount_ReturnsPositiveError()
    {
        Assert.That(_sut.ValidateOrder("Alice", "-5", "1"), Does.Contain("Amount must be positive"));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_ReturnsPositiveError()
    {
        Assert.That(_sut.ValidateOrder("Alice", "0", "1"), Does.Contain("Amount must be positive"));
    }

    [Test]
    public void ValidateOrder_NonNumericQuantity_ReturnsQuantityError()
    {
        Assert.That(_sut.ValidateOrder("Alice", "10", "xyz"), Does.Contain("Quantity must be numeric"));
    }

    [Test]
    public void ValidateOrder_ZeroQuantity_ReturnsPositiveError()
    {
        Assert.That(_sut.ValidateOrder("Alice", "10", "0"), Does.Contain("Quantity must be positive"));
    }

    [Test]
    public void ValidateOrder_AllInvalid_ReturnsMultipleErrors()
    {
        var result = _sut.ValidateOrder("", "abc", "xyz");
        Assert.That(result, Does.Contain("Name required"));
        Assert.That(result, Does.Contain("Amount must be numeric"));
        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    // ── GetDiscountTier ──

    [TestCase(0, ExpectedResult = "NONE")]
    [TestCase(100, ExpectedResult = "NONE")]
    [TestCase(100.01, ExpectedResult = "BRONZE")]
    [TestCase(500, ExpectedResult = "BRONZE")]
    [TestCase(500.01, ExpectedResult = "SILVER")]
    [TestCase(1000, ExpectedResult = "SILVER")]
    [TestCase(1000.01, ExpectedResult = "GOLD")]
    [TestCase(5000, ExpectedResult = "GOLD")]
    public string GetDiscountTier_ReturnsCorrectTier(double subtotal)
    {
        return _sut.GetDiscountTier(subtotal);
    }

    [Test]
    public void GetDiscountTier_NegativeSubtotal_ReturnsNone()
    {
        Assert.That(_sut.GetDiscountTier(-50), Is.EqualTo("NONE"));
    }
}`,
      },
      RefundService: {
        count: 8,
        code: `using NUnit.Framework;

namespace VBGone.Generated.Tests;

[TestFixture]
public class RefundServiceTests
{
    private IRefundService _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new RefundService();
    }

    [Test]
    public void ProcessRefund_ValidOrderAndReason_ReturnsTrue()
    {
        Assert.That(_sut.ProcessRefund(1001, "Damaged goods"), Is.True);
    }

    [Test]
    public void ProcessRefund_ZeroOrderId_ReturnsFalse()
    {
        Assert.That(_sut.ProcessRefund(0, "Damaged goods"), Is.False);
    }

    [Test]
    public void ProcessRefund_NegativeOrderId_ReturnsFalse()
    {
        Assert.That(_sut.ProcessRefund(-1, "Damaged goods"), Is.False);
    }

    [Test]
    public void ProcessRefund_EmptyReason_ReturnsFalse()
    {
        Assert.That(_sut.ProcessRefund(1001, ""), Is.False);
    }

    [Test]
    public void ProcessRefund_NullReason_ReturnsFalse()
    {
        Assert.That(_sut.ProcessRefund(1001, null!), Is.False);
    }

    [Test]
    public void ProcessRefund_LargeOrderId_ReturnsTrue()
    {
        Assert.That(_sut.ProcessRefund(999999, "Customer request"), Is.True);
    }

    [Test]
    public void ProcessRefund_WhitespaceReason_ReturnsFalse()
    {
        Assert.That(_sut.ProcessRefund(1001, "   "), Is.False);
    }

    [Test]
    public void ProcessRefund_SpecialCharactersInReason_ReturnsTrue()
    {
        Assert.That(_sut.ProcessRefund(1001, "Reason with 'quotes' & <symbols>"), Is.True);
    }
}`,
      },
      OrderProcessor: {
        count: 6,
        code: `using NUnit.Framework;

namespace VBGone.Generated.Tests;

[TestFixture]
public class OrderProcessorTests
{
    private IOrderProcessor _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderProcessor(
            new OrderCalculationService(),
            new OrderValidator()
        );
    }

    [Test]
    public void SubmitOrder_ValidInput_DoesNotThrow()
    {
        Assert.DoesNotThrow(() => _sut.SubmitOrder("Alice", 19.99, 3));
    }

    [Test]
    public void SubmitOrder_EmptyName_ThrowsArgumentException()
    {
        Assert.Throws<ArgumentException>(() => _sut.SubmitOrder("", 19.99, 3));
    }

    [Test]
    public void SubmitOrder_NegativePrice_ThrowsArgumentException()
    {
        Assert.Throws<ArgumentException>(() => _sut.SubmitOrder("Alice", -10, 3));
    }

    [Test]
    public void SubmitOrder_ZeroQuantity_ThrowsArgumentException()
    {
        Assert.Throws<ArgumentException>(() => _sut.SubmitOrder("Alice", 19.99, 0));
    }

    [Test]
    public void SubmitOrder_LargeOrder_DoesNotThrow()
    {
        Assert.DoesNotThrow(() => _sut.SubmitOrder("Bob", 500.0, 50));
    }

    [Test]
    public void SubmitOrder_MinimumValidInput_DoesNotThrow()
    {
        Assert.DoesNotThrow(() => _sut.SubmitOrder("X", 0.01, 1));
    }
}`,
      },
    }

    const defaultCode = `using NUnit.Framework;

namespace VBGone.Generated.Tests;

[TestFixture]
public class ${className}Tests
{
    private I${className} _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new ${className}();
    }

    [TestCase(2, 3, ExpectedResult = 5)]
    [TestCase(-1, 1, ExpectedResult = 0)]
    [TestCase(0, 0, ExpectedResult = 0)]
    public int Add_ReturnsCorrectSum(int a, int b)
    {
        return _sut.Add(a, b);
    }

    [TestCase(5, 3, ExpectedResult = 2)]
    [TestCase(0, 5, ExpectedResult = -5)]
    public int Subtract_ReturnsCorrectDifference(int a, int b)
    {
        return _sut.Subtract(a, b);
    }

    [TestCase(3, 4, ExpectedResult = 12)]
    [TestCase(0, 5, ExpectedResult = 0)]
    public int Multiply_ReturnsCorrectProduct(int a, int b)
    {
        return _sut.Multiply(a, b);
    }

    [TestCase(10, 2, ExpectedResult = 5.0)]
    [TestCase(7, 2, ExpectedResult = 3.5)]
    public double Divide_ReturnsCorrectQuotient(int a, int b)
    {
        return _sut.Divide(a, b);
    }

    [Test]
    public void Divide_ByZero_ThrowsDivideByZeroException()
    {
        Assert.Throws<DivideByZeroException>(() => _sut.Divide(1, 0));
    }

    [TestCase(10, 3, ExpectedResult = 1)]
    [TestCase(9, 3, ExpectedResult = 0)]
    public int Modulus_ReturnsCorrectRemainder(int a, int b)
    {
        return _sut.Modulus(a, b);
    }
}`

    const match = testCode[className]
    const count = match?.count ?? 30
    lastMockTestCount = count

    return {
      sessionId: MOCK_SESSION_ID,
      className,
      testClassName: `${className}Tests`,
      code: match?.code ?? defaultCode,
      testCount: count,
    }
  },

  async generateStub(sessionId: string, className: string): Promise<StubResult> {
    void sessionId
    await delay(600)

    const stubCode: Record<string, string> = {
      OrderCalculationService: `namespace VBGone.Generated;

public class OrderCalculationService : IOrderCalculationService
{
    public double CalculateDiscount(double unitPrice, int quantity) => throw new NotImplementedException();
    public double CalculateShipping(int quantity) => throw new NotImplementedException();
    public double CalculateTotal(double unitPrice, int quantity) => throw new NotImplementedException();
}`,
      OrderValidator: `namespace VBGone.Generated;

public class OrderValidator : IOrderValidator
{
    public string ValidateOrder(string name, string amount, string quantity) => throw new NotImplementedException();
    public string GetDiscountTier(double subtotal) => throw new NotImplementedException();
}`,
      RefundService: `namespace VBGone.Generated;

public class RefundService : IRefundService
{
    public bool ProcessRefund(int orderId, string reason) => throw new NotImplementedException();
}`,
      OrderProcessor: `namespace VBGone.Generated;

public class OrderProcessor : IOrderProcessor
{
    public void SubmitOrder(string customerName, double unitPrice, int quantity) => throw new NotImplementedException();
}`,
    }

    return {
      sessionId: MOCK_SESSION_ID,
      className,
      code:
        stubCode[className] ??
        `namespace VBGone.Generated;\n\npublic class ${className} : I${className}\n{\n    public int Add(int a, int b) => throw new NotImplementedException();\n    public int Subtract(int a, int b) => throw new NotImplementedException();\n    public int Multiply(int a, int b) => throw new NotImplementedException();\n    public double Divide(int a, int b) => throw new NotImplementedException();\n    public int Modulus(int a, int b) => throw new NotImplementedException();\n}`,
    }
  },

  async build(sessionId: string): Promise<BuildResult> {
    void sessionId
    await delay(1500)
    const total = lastMockTestCount
    return {
      sessionId: MOCK_SESSION_ID,
      buildStatus: 'RED',
      total,
      passed: 0,
      failed: total,
      errors: [],
      failedTests: Array.from({ length: total }, (_, i) => `Test_${i + 1}`),
    }
  },

  async implement(
    sessionId: string,
    className: string,
    mode: 'STUB' | 'CLAUDE',
  ): Promise<ImplementResult> {
    void sessionId
    await delay(mode === 'CLAUDE' ? 2000 : 400)

    const implCode: Record<string, string> = {
      OrderCalculationService: `namespace VBGone.Generated;

public class OrderCalculationService : IOrderCalculationService
{
    private const double TaxRate = 0.0825;
    private const double Tier1Discount = 0.10;
    private const double Tier2Discount = 0.15;
    private const double Tier3Discount = 0.20;

    public double CalculateDiscount(double unitPrice, int quantity)
    {
        var subtotal = unitPrice * quantity;
        return subtotal switch
        {
            > 1000 => Tier3Discount,
            > 500 => Tier2Discount,
            > 100 => Tier1Discount,
            _ => 0
        };
    }

    public double CalculateShipping(int quantity) => quantity switch
    {
        <= 0 => 0,
        <= 5 => 5.99,
        <= 20 => 9.99,
        _ => 14.99
    };

    public double CalculateTotal(double unitPrice, int quantity)
    {
        var subtotal = unitPrice * quantity;
        var discount = CalculateDiscount(unitPrice, quantity);
        var shipping = CalculateShipping(quantity);
        var afterDiscount = subtotal - (subtotal * discount) + shipping;
        return afterDiscount + (afterDiscount * TaxRate);
    }
}`,
      OrderValidator: `namespace VBGone.Generated;

public class OrderValidator : IOrderValidator
{
    public string ValidateOrder(string name, string amount, string quantity)
    {
        var errors = "";
        if (string.IsNullOrEmpty(name)) errors += "Name required. ";
        if (!double.TryParse(amount, out var a)) errors += "Amount must be numeric. ";
        else if (a <= 0) errors += "Amount must be positive. ";
        if (!int.TryParse(quantity, out var q)) errors += "Quantity must be numeric. ";
        else if (q <= 0) errors += "Quantity must be positive. ";
        return errors;
    }

    public string GetDiscountTier(double subtotal) => subtotal switch
    {
        <= 100 => "NONE",
        <= 500 => "BRONZE",
        <= 1000 => "SILVER",
        _ => "GOLD"
    };
}`,
      RefundService: `namespace VBGone.Generated;

public class RefundService : IRefundService
{
    private const int MaxRefunds = 3;

    public bool ProcessRefund(int orderId, string reason)
    {
        if (orderId <= 0 || string.IsNullOrEmpty(reason)) return false;
        // Delegates to IOrderRepository and INotificationService
        return true;
    }
}`,
      OrderProcessor: `namespace VBGone.Generated;

public class OrderProcessor : IOrderProcessor
{
    private readonly IOrderCalculationService _calc;
    private readonly IOrderValidator _validator;

    public OrderProcessor(IOrderCalculationService calc, IOrderValidator validator)
    {
        _calc = calc;
        _validator = validator;
    }

    public void SubmitOrder(string customerName, double unitPrice, int quantity)
    {
        var errors = _validator.ValidateOrder(customerName, unitPrice.ToString(), quantity.ToString());
        if (!string.IsNullOrEmpty(errors)) throw new ArgumentException(errors);
        var total = _calc.CalculateTotal(unitPrice, quantity);
        // Persist order via IOrderRepository
    }
}`,
    }

    const code =
      mode === 'CLAUDE'
        ? (implCode[className] ??
          `namespace VBGone.Generated;\n\npublic class ${className} : I${className}\n{\n    public int Add(int a, int b) => a + b;\n    public int Subtract(int a, int b) => a - b;\n    public int Multiply(int a, int b) => a * b;\n    public double Divide(int a, int b)\n    {\n        if (b == 0) throw new DivideByZeroException();\n        return (double)a / b;\n    }\n    public int Modulus(int a, int b) => a % b;\n}`)
        : `namespace VBGone.Generated;\n\npublic class ${className} : I${className}\n{\n    public int Add(int a, int b) => throw new NotImplementedException();\n    public int Subtract(int a, int b) => throw new NotImplementedException();\n    public int Multiply(int a, int b) => throw new NotImplementedException();\n    public double Divide(int a, int b) => throw new NotImplementedException();\n    public int Modulus(int a, int b) => throw new NotImplementedException();\n}`
    return { sessionId: MOCK_SESSION_ID, className, code, mode }
  },

  async buildAfterImplement(sessionId: string, mode: 'STUB' | 'CLAUDE'): Promise<BuildResult> {
    void sessionId
    await delay(1500)
    const total = lastMockTestCount
    return {
      sessionId: MOCK_SESSION_ID,
      buildStatus: mode === 'CLAUDE' ? 'GREEN' : 'RED',
      total,
      passed: mode === 'CLAUDE' ? total : 0,
      failed: mode === 'CLAUDE' ? 0 : total,
      errors: [],
      failedTests:
        mode === 'CLAUDE' ? [] : Array.from({ length: total }, (_, i) => `Test_${i + 1}`),
    }
  },

  async retryImplement(
    sessionId: string,
    className: string,
    _failingTests: string[],
  ): Promise<ImplementResult> {
    void sessionId
    await delay(2000)
    return {
      sessionId: MOCK_SESSION_ID,
      className,
      code: `namespace VBGone.Generated;\n\npublic class ${className} : I${className}\n{\n    // retry implementation\n}`,
      mode: 'CLAUDE',
    }
  },

  async raisePR(
    sessionId: string,
    repoOwner: string,
    repoName: string,
    branchName: string,
  ): Promise<PullRequestResult> {
    void sessionId
    await delay(1000)
    const classes = mockMigratedClasses.length > 0 ? mockMigratedClasses : ['Form1']
    const filesCommitted = classes.flatMap((cls) => [
      `${cls}/I${cls}.cs`,
      `${cls}/${cls}.cs`,
      `${cls}.Tests/${cls}Tests.cs`,
    ])
    return {
      sessionId: MOCK_SESSION_ID,
      prUrl: `https://github.com/${repoOwner}/${repoName}/pull/1`,
      branchName,
      filesCommitted,
    }
  },

  async uploadProject(_file: File): Promise<ProjectAnalysis> {
    await delay(2000)
    return {
      sessionId: MOCK_SESSION_ID,
      classes: [
        {
          name: 'ValidationHelper',
          methods: ['IsNullOrEmpty', 'IsValidEmail', 'IsInRange'],
          dependencies: [],
          complexity: 'LOW',
        },
        {
          name: 'StringHelper',
          methods: ['Capitalize', 'TruncateWithEllipsis', 'RemoveWhitespace', 'CountWords'],
          dependencies: [],
          complexity: 'LOW',
        },
        {
          name: 'DateHelper',
          methods: ['IsWeekday', 'GetBusinessDaysBetween', 'FormatFriendly'],
          dependencies: ['ValidationHelper'],
          complexity: 'MEDIUM',
        },
        {
          name: 'Calculator',
          methods: ['Add', 'Subtract', 'Multiply', 'Divide', 'Power', 'CalculateCompound'],
          dependencies: ['StringHelper', 'DateHelper'],
          complexity: 'HIGH',
        },
      ],
      suggestedMigrationOrder: ['ValidationHelper', 'StringHelper', 'DateHelper', 'Calculator'],
      dependencyGraph: {
        ValidationHelper: [],
        StringHelper: [],
        DateHelper: ['ValidationHelper'],
        Calculator: ['StringHelper', 'DateHelper'],
      },
      summary:
        'Four classes found across the project. ValidationHelper and StringHelper are leaf nodes with no dependencies. DateHelper depends on ValidationHelper. Calculator depends on StringHelper and DateHelper. Recommended migration order starts with the two independent helpers.',
    }
  },

  async fetchCost(_sessionId: string): Promise<CostResult> {
    return { sessionId: MOCK_SESSION_ID, steps: [], totalCost: 0 }
  },
}

/* ── Real API calls ── */

const realApi = {
  async analyse(filename: string, content: string): Promise<AnalysisResult> {
    const { data } = await api.post<AnalysisResult>('/analyse', { filename, content })
    return data
  },

  async generateInterface(sessionId: string, className: string): Promise<InterfaceResult> {
    const { data } = await api.post<InterfaceResult>('/interface', { sessionId, className })
    return data
  },

  async generateTests(sessionId: string, className: string): Promise<TestsResult> {
    const { data } = await api.post<TestsResult>('/tests', { sessionId, className })
    return data
  },

  async generateStub(sessionId: string, className: string): Promise<StubResult> {
    const { data } = await api.post<StubResult>('/stub', { sessionId, className })
    return data
  },

  async build(sessionId: string): Promise<BuildResult> {
    const { data } = await api.post<BuildResult>('/build', { sessionId })
    return data
  },

  async implement(
    sessionId: string,
    className: string,
    mode: 'STUB' | 'CLAUDE',
  ): Promise<ImplementResult> {
    const { data } = await api.post<ImplementResult>('/implement', { sessionId, className, mode })
    return data
  },

  async buildAfterImplement(sessionId: string, _mode: 'STUB' | 'CLAUDE'): Promise<BuildResult> {
    const { data } = await api.post<BuildResult>('/build', { sessionId })
    return data
  },

  async retryImplement(
    sessionId: string,
    className: string,
    failingTests: string[],
  ): Promise<ImplementResult> {
    const { data } = await api.post<ImplementResult>('/retry-implement', {
      sessionId,
      className,
      failingTests,
    })
    return data
  },

  async raisePR(
    sessionId: string,
    repoOwner: string,
    repoName: string,
    branchName: string,
  ): Promise<PullRequestResult> {
    const { data } = await api.post<PullRequestResult>('/pr', {
      sessionId,
      repoOwner,
      repoName,
      branchName,
    })
    return data
  },

  async uploadProject(file: File): Promise<ProjectAnalysis> {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await api.post<ProjectAnalysis>('/upload-project', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  async fetchCost(sessionId: string): Promise<CostResult> {
    const { data } = await api.get<CostResult>(`/cost/${sessionId}`)
    return data
  },
}

/* ── Export the active implementation ── */

const active = USE_MOCKS ? mockApi : realApi

export const analyse = active.analyse
export const generateInterface = active.generateInterface
export const generateTests = active.generateTests
export const generateStub = active.generateStub
export const build = active.build
export const implement = active.implement
export const buildAfterImplement = active.buildAfterImplement
export const retryImplement = active.retryImplement
export const raisePR = active.raisePR
export const uploadProject = active.uploadProject
export const fetchCost = active.fetchCost

/* Export the axios instance for when we wire to real backend */
export { api }

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

export { DEMO_VB_CONTENT, DEMO_FILENAME, DEMO_COMPLEX_CONTENT, DEMO_COMPLEX_FILENAME }
