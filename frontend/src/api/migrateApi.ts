import axios from 'axios'

const api = axios.create({ baseURL: '/api/migrate' })

/* ── Types ── */

export interface ClassInfo {
  name: string
  methods: string[]
  dependencies: string[]
  complexity: 'LOW' | 'MEDIUM' | 'HIGH'
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
  buildStatus: 'RED' | 'GREEN'
  total: number
  passed: number
  failed: number
  errors: string[]
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

export async function analyse(filename: string, content: string): Promise<AnalysisResult> {
  void filename
  void content
  await delay(1200)
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
      },
    ],
    suggestedMigrationOrder: ['Form1'],
    summary:
      'One WinForms class found with 6 event handlers — sum, difference, product, quotient, clear, exit. No dependencies. Good candidate for migration.',
  }
}

export async function generateInterface(
  sessionId: string,
  className: string,
): Promise<InterfaceResult> {
  void sessionId
  await delay(800)
  return {
    sessionId: MOCK_SESSION_ID,
    className,
    interfaceName: `I${className}`,
    code: `namespace VBGone.Generated;

public interface I${className}
{
    int Add(int a, int b);
    int Subtract(int a, int b);
    int Multiply(int a, int b);
    double Divide(int a, int b);
    int Modulus(int a, int b);
}`,
  }
}

export async function generateTests(sessionId: string, className: string): Promise<TestsResult> {
  void sessionId
  await delay(1000)
  return {
    sessionId: MOCK_SESSION_ID,
    className,
    testClassName: `${className}Tests`,
    code: `using NUnit.Framework;

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
}`,
    testCount: 30,
  }
}

export async function generateStub(sessionId: string, className: string): Promise<StubResult> {
  void sessionId
  await delay(600)
  return {
    sessionId: MOCK_SESSION_ID,
    className,
    code: `namespace VBGone.Generated;

public class ${className} : I${className}
{
    public int Add(int a, int b) => throw new NotImplementedException();
    public int Subtract(int a, int b) => throw new NotImplementedException();
    public int Multiply(int a, int b) => throw new NotImplementedException();
    public double Divide(int a, int b) => throw new NotImplementedException();
    public int Modulus(int a, int b) => throw new NotImplementedException();
}`,
  }
}

export async function build(sessionId: string): Promise<BuildResult> {
  void sessionId
  await delay(1500)
  return {
    sessionId: MOCK_SESSION_ID,
    buildStatus: 'RED',
    total: 30,
    passed: 0,
    failed: 30,
    errors: [],
  }
}

export async function implement(
  sessionId: string,
  className: string,
  mode: 'STUB' | 'CLAUDE',
): Promise<ImplementResult> {
  void sessionId
  await delay(mode === 'CLAUDE' ? 2000 : 400)
  const code =
    mode === 'CLAUDE'
      ? `namespace VBGone.Generated;

public class ${className} : I${className}
{
    public int Add(int a, int b) => a + b;
    public int Subtract(int a, int b) => a - b;
    public int Multiply(int a, int b) => a * b;
    public double Divide(int a, int b)
    {
        if (b == 0) throw new DivideByZeroException();
        return (double)a / b;
    }
    public int Modulus(int a, int b) => a % b;
}`
      : `namespace VBGone.Generated;

public class ${className} : I${className}
{
    public int Add(int a, int b) => throw new NotImplementedException();
    public int Subtract(int a, int b) => throw new NotImplementedException();
    public int Multiply(int a, int b) => throw new NotImplementedException();
    public double Divide(int a, int b) => throw new NotImplementedException();
    public int Modulus(int a, int b) => throw new NotImplementedException();
}`
  return { sessionId: MOCK_SESSION_ID, className, code, mode }
}

export async function buildAfterImplement(
  sessionId: string,
  mode: 'STUB' | 'CLAUDE',
): Promise<BuildResult> {
  void sessionId
  await delay(1500)
  return {
    sessionId: MOCK_SESSION_ID,
    buildStatus: mode === 'CLAUDE' ? 'GREEN' : 'RED',
    total: 30,
    passed: mode === 'CLAUDE' ? 30 : 0,
    failed: mode === 'CLAUDE' ? 0 : 30,
    errors: [],
  }
}

export async function raisePR(
  sessionId: string,
  repoOwner: string,
  repoName: string,
  branchName: string,
): Promise<PullRequestResult> {
  void sessionId
  await delay(1000)
  return {
    sessionId: MOCK_SESSION_ID,
    prUrl: `https://github.com/${repoOwner}/${repoName}/pull/1`,
    branchName,
    filesCommitted: ['Form1/IForm1.cs', 'Form1/Form1.cs', 'Form1.Tests/Form1Tests.cs'],
  }
}

/* Export the axios instance for when we wire to real backend */
export { api }

export { DEMO_VB_CONTENT, DEMO_FILENAME }
