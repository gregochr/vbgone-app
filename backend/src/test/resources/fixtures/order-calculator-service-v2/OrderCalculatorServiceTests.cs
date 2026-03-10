using NUnit.Framework;

[TestFixture]
public class OrderCalculatorServiceTests
{
    private IOrderCalculatorService _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderCalculatorService();
    }

    // ── CalculateTotal ──────────────────────────────────────────────────────────

    // Happy path: small order, no discount, cheapest shipping tier
    [Test]
    public void CalculateTotal_SmallOrder_NoDiscount_LowShipping()
    {
        // a=10, q=2 → subtotal=20 (≤100 → disc=0), ship=5.99, pre-tax=25.99, tax=25.99*0.0825≈2.144, total≈28.134
        double result = _sut.CalculateTotal(10.0, 2);
        double expected = (10.0 * 2 + 5.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Subtotal exactly at boundary 100 — no discount applies (> 100 required)
    [Test]
    public void CalculateTotal_SubtotalExactly100_NoDiscount()
    {
        // a=20, q=5 → subtotal=100, not >100 → disc=0, ship=5.99
        double result = _sut.CalculateTotal(20.0, 5);
        double expected = (100.0 + 5.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Subtotal just above 100 → BRONZE discount (10%)
    [Test]
    public void CalculateTotal_SubtotalJustAbove100_BronzeDiscount()
    {
        // a=101, q=1 → subtotal=101 >100 but ≤500 → disc=0.10, ship=5.99
        double subtotal = 101.0 * 1;
        double discounted = subtotal - subtotal * 0.10;
        double expected = (discounted + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(101.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Subtotal exactly 500 → still BRONZE (> 500 required for SILVER)
    [Test]
    public void CalculateTotal_SubtotalExactly500_BronzeDiscount()
    {
        // a=100, q=5 → subtotal=500, >100 but NOT >500 → disc=0.10, ship=5.99
        double subtotal = 500.0;
        double discounted = subtotal - subtotal * 0.10;
        double expected = (discounted + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(100.0, 5);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Subtotal just above 500 → SILVER discount (15%)
    [Test]
    public void CalculateTotal_SubtotalJustAbove500_SilverDiscount()
    {
        // a=501, q=1 → subtotal=501 >500 but ≤1000 → disc=0.15, ship=5.99
        double subtotal = 501.0;
        double discounted = subtotal - subtotal * 0.15;
        double expected = (discounted + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(501.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Subtotal exactly 1000 → SILVER (> 1000 required for GOLD)
    [Test]
    public void CalculateTotal_SubtotalExactly1000_SilverDiscount()
    {
        // a=200, q=5 → subtotal=1000, >500 but NOT >1000 → disc=0.15, ship=5.99
        double subtotal = 1000.0;
        double discounted = subtotal - subtotal * 0.15;
        double expected = (discounted + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(200.0, 5);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Subtotal just above 1000 → GOLD discount (20%)
    [Test]
    public void CalculateTotal_SubtotalJustAbove1000_GoldDiscount()
    {
        // a=1001, q=1 → subtotal=1001 >1000 → disc=0.20, ship=5.99
        double subtotal = 1001.0;
        double discounted = subtotal - subtotal * 0.20;
        double expected = (discounted + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(1001.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Large subtotal deep in GOLD tier
    [Test]
    public void CalculateTotal_LargeOrder_GoldDiscount_HighShipping()
    {
        // a=100, q=25 → subtotal=2500 >1000 → disc=0.20, ship=14.99 (q>20)
        double subtotal = 2500.0;
        double discounted = subtotal - subtotal * 0.20;
        double expected = (discounted + 14.99) * 1.0825;
        double result = _sut.CalculateTotal(100.0, 25);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Shipping tier boundary: q=5 → low tier (s1=5.99)
    [Test]
    public void CalculateTotal_QuantityExactly5_LowShipping()
    {
        // a=1, q=5 → subtotal=5 ≤100 → disc=0, ship=5.99
        double expected = (5.0 + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(1.0, 5);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Shipping tier boundary: q=6 → mid tier (s2=9.99)
    [Test]
    public void CalculateTotal_QuantityExactly6_MidShipping()
    {
        // a=1, q=6 → subtotal=6 ≤100 → disc=0, ship=9.99
        double expected = (6.0 + 9.99) * 1.0825;
        double result = _sut.CalculateTotal(1.0, 6);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Shipping tier boundary: q=20 → mid tier (s2=9.99)
    [Test]
    public void CalculateTotal_QuantityExactly20_MidShipping()
    {
        // a=1, q=20 → subtotal=20 ≤100 → disc=0, ship=9.99
        double expected = (20.0 + 9.99) * 1.0825;
        double result = _sut.CalculateTotal(1.0, 20);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Shipping tier boundary: q=21 → high tier (s3=14.99)
    [Test]
    public void CalculateTotal_QuantityExactly21_HighShipping()
    {
        // a=1, q=21 → subtotal=21 ≤100 → disc=0, ship=14.99
        double expected = (21.0 + 14.99) * 1.0825;
        double result = _sut.CalculateTotal(1.0, 21);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Edge case: quantity=1, minimal order
    [Test]
    public void CalculateTotal_QuantityOne_SmallAmount()
    {
        // a=0.01, q=1 → subtotal=0.01 ≤100 → disc=0, ship=5.99
        double expected = (0.01 + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(0.01, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Edge case: amount=0, no discount, no subtotal — shipping still applies when q>0 per logic
    [Test]
    public void CalculateTotal_AmountZero_ShippingStillApplies()
    {
        // a=0 → outer If a>0 is false → disc stays 0, but q check for shipping is independent
        // q=1 ≤5 → ship=5.99, tot=(0*1)-0+5.99=5.99, tx=5.99*0.0825, total=5.99*1.0825
        double expected = (0.0 + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(0.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Edge case: quantity=0 — no shipping, no discount
    [Test]
    public void CalculateTotal_QuantityZero_NoShippingNoDiscount()
    {
        // q=0 → shipping block skipped (q>0 is false), discount block skipped too → tot=0, tx=0
        double result = _sut.CalculateTotal(50.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }

    // Edge case: negative amount — outer If a>0 false, no discount; shipping depends only on q
    [Test]
    public void CalculateTotal_NegativeAmount_NoDiscount()
    {
        // a=-10, q=1 → a>0 is false → disc=0, ship=5.99
        // tot=(-10*1)-0+5.99 = -4.01, tx=-4.01*0.0825, total=-4.01*1.0825
        double expected = (-10.0 * 1 + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(-10.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Edge case: negative quantity — shipping block requires q>0, so no shipping; discount requires q>0 too
    [Test]
    public void CalculateTotal_NegativeQuantity_NoShippingNoDiscount()
    {
        // q=-5 → q>0 false for both blocks → disc=0, ship=0
        // tot=(50*-5)-0+0=-250, tx=-250*0.0825, total=-250*1.0825
        double expected = (50.0 * -5) * 1.0825;
        double result = _sut.CalculateTotal(50.0, -5);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Very large values — stress test
    [Test]
    public void CalculateTotal_VeryLargeValues_GoldDiscount_HighShipping()
    {
        // a=9999.99, q=100 → subtotal=999999 >1000 → disc=0.20, ship=14.99
        double subtotal = 9999.99 * 100;
        double discounted = subtotal * 0.80;
        double expected = (discounted + 14.99) * 1.0825;
        double result = _sut.CalculateTotal(9999.99, 100);
        Assert.That(result, Is.EqualTo(expected).Within(0.01));
    }

    // Fractional amount that pushes subtotal just over 100
    [Test]
    public void CalculateTotal_FractionalAmount_SubtotalJustOver100()
    {
        // a=33.67, q=3 → subtotal=101.01 >100 ≤500 → disc=0.10, ship=5.99
        double subtotal = 33.67 * 3;
        double discounted = subtotal - subtotal * 0.10;
        double expected = (discounted + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(33.67, 3);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Confirm tax rate is applied (sanity check on 8.25% tax)
    [Test]
    public void CalculateTotal_VerifyTaxRateApplied()
    {
        // a=10, q=1, subtotal=10 ≤100 → disc=0, ship=5.99, pre-tax=15.99
        // tax = 15.99 * 0.0825 = 1.319175, total = 17.309175
        double result = _sut.CalculateTotal(10.0, 1);
        double preTax = 10.0 + 5.99;
        double tax = preTax * 0.0825;
        Assert.That(result, Is.EqualTo(preTax + tax).Within(0.0001));
    }

    // ── ValidateOrder ───────────────────────────────────────────────────────────

    // Happy path: all valid inputs
    [Test]
    public void ValidateOrder_AllValid_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("Alice", "99.99", "3");
        Assert.That(result, Is.EqualTo(""));
    }

    // Empty customer name
    [Test]
    public void ValidateOrder_EmptyName_ReturnsNameError()
    {
        string result = _sut.ValidateOrder("", "10.00", "1");
        Assert.That(result, Does.Contain("Name required"));
    }

    // Non-numeric amount
    [Test]
    public void ValidateOrder_NonNumericAmount_ReturnsAmountError()
    {
        string result = _sut.ValidateOrder("Bob", "abc", "1");
        Assert.That(result, Does.Contain("Amount must be numeric"));
    }

    // Amount is zero
    [Test]
    public void ValidateOrder_ZeroAmount_ReturnsPositiveAmountError()
    {
        string result = _sut.ValidateOrder("Bob", "0", "1");
        Assert.That(result, Does.Contain("Amount must be positive"));
    }

    // Negative amount
    [Test]
    public void ValidateOrder_NegativeAmount_ReturnsPositiveAmountError()
    {
        string result = _sut.ValidateOrder("Bob", "-5.00", "1");
        Assert.That(result, Does.Contain("Amount must be positive"));
    }

    // Non-numeric quantity
    [Test]
    public void ValidateOrder_NonNumericQuantity_ReturnsQuantityError()
    {
        string result = _sut.ValidateOrder("Bob", "10.00", "xyz");
        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    // Quantity is zero
    [Test]
    public void ValidateOrder_ZeroQuantity_ReturnsPositiveQuantityError()
    {
        string result = _sut.ValidateOrder("Bob", "10.00", "0");
        Assert.That(result, Does.Contain("Quantity must be positive"));
    }

    // Negative quantity
    [Test]
    public void ValidateOrder_NegativeQuantity_ReturnsPositiveQuantityError()
    {
        string result = _sut.ValidateOrder("Bob", "10.00", "-1");
        Assert.That(result, Does.Contain("Quantity must be positive"));
    }

    // All fields invalid — all errors concatenated
    [Test]
    public void ValidateOrder_AllInvalid_ReturnsAllErrors()
    {
        string result = _sut.ValidateOrder("", "abc", "xyz");
        Assert.That(result, Does.Contain("Name required"));
        Assert.That(result, Does.Contain("Amount must be numeric"));
        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    // Name and amount valid but quantity invalid
    [Test]
    public void ValidateOrder_NameAndAmountValidQuantityInvalid_ReturnsQuantityErrorOnly()
    {
        string result = _sut.ValidateOrder("Carol", "25.00", "0");
        Assert.That(result, Does.Not.Contain("Name required"));
        Assert.That(result, Does.Not.Contain("Amount"));
        Assert.That(result, Does.Contain("Quantity must be positive"));
    }

    // Name missing and amount invalid
    [Test]
    public void ValidateOrder_NameMissingAmountInvalid_ReturnsBothErrors()
    {
        string result = _sut.ValidateOrder("", "-1", "2");
        Assert.That(result, Does.Contain("Name required"));
        Assert.That(result, Does.Contain("Amount must be positive"));
    }

    // Whitespace-only name (should be treated as non-empty per VB.NET string comparison)
    [Test]
    public void ValidateOrder_WhitespaceName_DoesNotTriggerNameError()
    {
        // VB.NET: n = "" check — a space is not equal to "", so no name error
        string result = _sut.ValidateOrder("   ", "10.00", "1");
        Assert.That(result, Does.Not.Contain("Name required"));
    }

    // Amount as decimal string with trailing zeros
    [Test]
    public void ValidateOrder_AmountWithTrailingZeros_IsValid()
    {
        string result = _sut.ValidateOrder("Dave", "10.00", "2");
        Assert.That(result, Is.EqualTo(""));
    }

    // Quantity as large numeric string
    [Test]
    public void ValidateOrder_LargeQuantityString_IsValid()
    {
        string result = _sut.ValidateOrder("Eve", "5.00", "1000");
        Assert.That(result, Is.EqualTo(""));
    }

    // ── GetDiscountTier ─────────────────────────────────────────────────────────

    // Subtotal zero → NONE
    [Test]
    public void GetDiscountTier_SubtotalZero_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(0.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    // Subtotal negative → NONE
    [Test]
    public void GetDiscountTier_NegativeSubtotal_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(-50.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    // Subtotal exactly 100 → NONE (≤100 goes to NoDiscount)
    [Test]
    public void GetDiscountTier_SubtotalExactly100_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(100.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    // Subtotal just above 100 → BRONZE
    [Test]
    public void GetDiscountTier_SubtotalJustAbove100_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(100.01);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    // Subtotal exactly 500 → BRONZE (≤500 → Tier1)
    [Test]
    public void GetDiscountTier_SubtotalExactly500_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(500.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    // Subtotal just above 500 → SILVER
    [Test]
    public void GetDiscountTier_SubtotalJustAbove500_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(500.01);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    // Subtotal exactly 1000 → SILVER (≤1000 → Tier2)
    [Test]
    public void GetDiscountTier_SubtotalExactly1000_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(1000.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    // Subtotal just above 1000 → GOLD
    [Test]
    public void GetDiscountTier_SubtotalJustAbove1000_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(1000.01);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    // Subtotal very large → GOLD
    [Test]
    public void GetDiscountTier_VeryLargeSubtotal_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(999999.99);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    // Subtotal 50 → NONE (between 0 and 100)
    [Test]
    public void GetDiscountTier_Subtotal50_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(50.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    // Subtotal 750 → SILVER (between 500 and 1000)
    [Test]
    public void GetDiscountTier_Subtotal750_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(750.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    // Subtotal 250 → BRONZE (between 100 and 500)
    [Test]
    public void GetDiscountTier_Subtotal250_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(250.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    // Subtotal 2000 → GOLD
    [Test]
    public void GetDiscountTier_Subtotal2000_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(2000.0);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    // Verify return values are exact strings (case-sensitive)
    [Test]
    public void GetDiscountTier_ReturnValues_AreUpperCase()
    {
        Assert.That(_sut.GetDiscountTier(50.0), Is.EqualTo("NONE"));
        Assert.That(_sut.GetDiscountTier(200.0), Is.EqualTo("BRONZE"));
        Assert.That(_sut.GetDiscountTier(600.0), Is.EqualTo("SILVER"));
        Assert.That(_sut.GetDiscountTier(1500.0), Is.EqualTo("GOLD"));
    }

    // ── CalculateTotal + GetDiscountTier consistency ────────────────────────────

    // Cross-check: CalculateTotal with subtotal in BRONZE range uses 10% discount
    [Test]
    public void CalculateTotal_BronzeRange_DiscountConsistentWithGetDiscountTier()
    {
        double a = 50.0;
        int q = 4;
        // subtotal=200 → BRONZE per GetDiscountTier
        string tier = _sut.GetDiscountTier(a * q);
        Assert.That(tier, Is.EqualTo("BRONZE"));

        double subtotal = a * q;
        double expected = (subtotal - subtotal * 0.10 + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // Cross-check: CalculateTotal with subtotal in GOLD range uses 20% discount
    [Test]
    public void CalculateTotal_GoldRange_DiscountConsistentWithGetDiscountTier()
    {
        double a = 200.0;
        int q = 6;
        // subtotal=1200 → GOLD per GetDiscountTier
        string tier = _sut.GetDiscountTier(a * q);
        Assert.That(tier, Is.EqualTo("GOLD"));

        double subtotal = a * q;
        double expected = (subtotal - subtotal * 0.20 + 9.99) * 1.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }
}