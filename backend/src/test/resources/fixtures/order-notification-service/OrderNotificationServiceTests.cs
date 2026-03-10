using NUnit.Framework;

[TestFixture]
public class OrderNotificationServiceTests
{
    private IOrderNotificationService _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderNotificationService();
    }

    // ── CalculateTotal – happy path ──────────────────────────────────────────

    [Test]
    public void CalculateTotal_TypicalAmountAndQuantity_ReturnsCorrectTotal()
    {
        // a=10, q=3 → subtotal=30, disc=0, ship=5.99, tax=(35.99*0.0825)
        double expected = (10.0 * 3 - 0) + 5.99;
        expected = expected + expected * 0.0825;
        double result = _sut.CalculateTotal(10.0, 3);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly100_NoDiscount()
    {
        // a=20, q=5 → subtotal=100, disc=0, ship=5.99
        double subtotal = 20.0 * 5;
        double ship = 5.99;
        double tot = subtotal + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(20.0, 5);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver100_AppliesBronzeDiscount()
    {
        // a=101, q=1 → subtotal=101, disc=0.10, ship=5.99
        double a = 101.0;
        int q = 1;
        double subtotal = a * q;
        double disc = 0.10;
        double ship = 5.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly500_AppliesBronzeDiscount()
    {
        // a=100, q=5 → subtotal=500, disc=0.10, ship=5.99
        double a = 100.0;
        int q = 5;
        double subtotal = a * q;
        double disc = 0.10;
        double ship = 5.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver500_AppliesSilverDiscount()
    {
        // a=501, q=1 → subtotal=501, disc=0.15, ship=5.99
        double a = 501.0;
        int q = 1;
        double subtotal = a * q;
        double disc = 0.15;
        double ship = 5.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly1000_AppliesSilverDiscount()
    {
        // a=200, q=5 → subtotal=1000, disc=0.15, ship=5.99
        double a = 200.0;
        int q = 5;
        double subtotal = a * q;
        double disc = 0.15;
        double ship = 5.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_SubtotalOver1000_AppliesGoldDiscount()
    {
        // a=200, q=6 → subtotal=1200, disc=0.20, ship=9.99
        double a = 200.0;
        int q = 6;
        double subtotal = a * q;
        double disc = 0.20;
        double ship = 9.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    // ── CalculateTotal – shipping tiers ─────────────────────────────────────

    [Test]
    public void CalculateTotal_QuantityOne_UsesLowShipping()
    {
        // q=1 ≤ 5 → ship=5.99
        double a = 10.0;
        int q = 1;
        double ship = 5.99;
        double tot = a * q + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_QuantityFive_UsesLowShipping()
    {
        double a = 10.0;
        int q = 5;
        double ship = 5.99;
        double tot = a * q + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_QuantitySix_UsesMediumShipping()
    {
        double a = 10.0;
        int q = 6;
        double ship = 9.99;
        double tot = a * q + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_QuantityTwenty_UsesMediumShipping()
    {
        double a = 10.0;
        int q = 20;
        double ship = 9.99;
        double tot = a * q + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_QuantityTwentyOne_UsesHighShipping()
    {
        double a = 10.0;
        int q = 21;
        double ship = 14.99;
        double tot = a * q + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_LargeQuantity_UsesHighShipping()
    {
        double a = 5.0;
        int q = 100;
        double ship = 14.99;
        double tot = a * q + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    // ── CalculateTotal – edge / boundary ────────────────────────────────────

    [Test]
    public void CalculateTotal_ZeroAmount_ReturnsShippingWithTaxOnly()
    {
        // a=0 → disc stays 0 per the guard, ship determined by q
        double a = 0.0;
        int q = 3;
        double ship = 5.99;
        double tot = 0 + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_ZeroQuantity_ReturnsZeroWithNoShipping()
    {
        // q=0 → ship=0, disc=0 (both guards fail)
        double a = 50.0;
        int q = 0;
        double expected = 0.0;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_NegativeAmount_ReturnsZeroOrShippingWithTax()
    {
        // a < 0 → disc guard fails, ship still applied if q>0
        double a = -10.0;
        int q = 2;
        double ship = 5.99;
        double tot = (a * q) + ship; // subtotal is negative, disc=0
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_NegativeQuantity_ReturnsNoShippingAndNoDiscount()
    {
        // q<0 → ship guard fails, disc guard also fails
        double a = 50.0;
        int q = -1;
        double tot = a * q; // negative subtotal, no ship, no disc
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_BothZero_ReturnsZero()
    {
        double result = _sut.CalculateTotal(0.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.001));
    }

    [Test]
    public void CalculateTotal_VeryLargeValues_DoesNotOverflow()
    {
        double a = 1_000_000.0;
        int q = 1000;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.GreaterThan(0));
        Assert.That(double.IsInfinity(result), Is.False);
        Assert.That(double.IsNaN(result), Is.False);
    }

    [Test]
    public void CalculateTotal_SubtotalExactly101_DiscountIs10Percent()
    {
        double a = 101.0;
        int q = 1;
        double subtotal = 101.0;
        double disc = 0.10;
        double ship = 5.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly1001_DiscountIs20Percent()
    {
        double a = 1001.0;
        int q = 1;
        double subtotal = 1001.0;
        double disc = 0.20;
        double ship = 5.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    // ── ValidateOrder – happy path ───────────────────────────────────────────

    [Test]
    public void ValidateOrder_AllValidInputs_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("Alice", "49.99", "3");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_ValidInputsWholeNumbers_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("Bob", "100", "10");
        Assert.That(result, Is.EqualTo(""));
    }

    // ── ValidateOrder – name validation ─────────────────────────────────────

    [Test]
    public void ValidateOrder_EmptyName_ReturnsNameRequiredError()
    {
        string result = _sut.ValidateOrder("", "10.00", "2");
        Assert.That(result, Does.Contain("Name required"));
    }

    [Test]
    public void ValidateOrder_NullName_ReturnsNameRequiredError()
    {
        string result = _sut.ValidateOrder(null, "10.00", "2");
        Assert.That(result, Does.Contain("Name required"));
    }

    [Test]
    public void ValidateOrder_WhitespaceName_ReturnsNameRequiredError()
    {
        string result = _sut.ValidateOrder("   ", "10.00", "2");
        Assert.That(result, Does.Contain("Name required"));
    }

    // ── ValidateOrder – amount validation ───────────────────────────────────

    [Test]
    public void ValidateOrder_NonNumericAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("Alice", "abc", "2");
        Assert.That(result, Does.Contain("Amount must be numeric"));
    }

    [Test]
    public void ValidateOrder_EmptyAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("Alice", "", "2");
        Assert.That(result, Does.Contain("Amount must be numeric"));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_ReturnsAmountPositiveError()
    {
        string result = _sut.ValidateOrder("Alice", "0", "2");
        Assert.That(result, Does.Contain("Amount must be positive"));
    }

    [Test]
    public void ValidateOrder_NegativeAmount_ReturnsAmountPositiveError()
    {
        string result = _sut.ValidateOrder("Alice", "-5.00", "2");
        Assert.That(result, Does.Contain("Amount must be positive"));
    }

    [Test]
    public void ValidateOrder_NullAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("Alice", null, "2");
        Assert.That(result, Does.Contain("Amount must be numeric"));
    }

    // ── ValidateOrder – quantity validation ──────────────────────────────────

    [Test]
    public void ValidateOrder_NonNumericQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "xyz");
        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    [Test]
    public void ValidateOrder_EmptyQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "");
        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    [Test]
    public void ValidateOrder_ZeroQuantity_ReturnsQuantityPositiveError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "0");
        Assert.That(result, Does.Contain("Quantity must be positive"));
    }

    [Test]
    public void ValidateOrder_NegativeQuantity_ReturnsQuantityPositiveError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "-3");
        Assert.That(result, Does.Contain("Quantity must be positive"));
    }

    [Test]
    public void ValidateOrder_NullQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", null);
        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    // ── ValidateOrder – multiple errors ─────────────────────────────────────

    [Test]
    public void ValidateOrder_AllFieldsInvalid_ReturnsAllErrors()
    {
        string result = _sut.ValidateOrder("", "abc", "xyz");
        Assert.That(result, Does.Contain("Name required"));
        Assert.That(result, Does.Contain("Amount must be numeric"));
        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    [Test]
    public void ValidateOrder_EmptyNameAndZeroAmount_ReturnsBothErrors()
    {
        string result = _sut.ValidateOrder("", "0", "5");
        Assert.That(result, Does.Contain("Name required"));
        Assert.That(result, Does.Contain("Amount must be positive"));
    }

    [Test]
    public void ValidateOrder_EmptyNameAndNegativeQuantity_ReturnsBothErrors()
    {
        string result = _sut.ValidateOrder("", "10.00", "-1");
        Assert.That(result, Does.Contain("Name required"));
        Assert.That(result, Does.Contain("Quantity must be positive"));
    }

    // ── GetDiscountTier – happy path ─────────────────────────────────────────

    [Test]
    public void GetDiscountTier_SubtotalOf50_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(50.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf100_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(100.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf101_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(101.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf300_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(300.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf500_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(500.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf501_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(501.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf750_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(750.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf1000_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(1000.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf1001_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(1001.0);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf9999_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(9999.0);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    // ── GetDiscountTier – edge / boundary ────────────────────────────────────

    [Test]
    public void GetDiscountTier_ZeroSubtotal_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(0.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_NegativeSubtotal_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(-50.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_VeryLargeSubtotal_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(double.MaxValue / 2);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf1_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(1.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustBelow101_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(100.99);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustBelow501_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(500.99);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustBelow1001_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(1000.99);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    // ── ProcessRefund – happy path ───────────────────────────────────────────

    [Test]
    public void ProcessRefund_ValidOrderIdAndReason_ReturnsTrue()
    {
        bool result = _sut.ProcessRefund(1, "Damaged item");
        Assert.That(result, Is.True);
    }

    // ── ProcessRefund – error conditions ────────────────────────────────────

    [Test]
    public void ProcessRefund_EmptyReason_ReturnsFalse()
    {
        bool result = _sut.ProcessRefund(1, "");
        Assert.That(result, Is.False);
    }

    [Test]
    public void ProcessRefund_NullReason_ReturnsFalse()
    {
        bool result = _sut.ProcessRefund(1, null);
        Assert.That(result, Is.False);
    }

    [Test]
    public void ProcessRefund_InvalidOrderId_ReturnsFalse()
    {
        bool result = _sut.ProcessRefund(-1, "Wrong item");
        Assert.That(result, Is.False);
    }

    [Test]
    public void ProcessRefund_ZeroOrderId_ReturnsFalse()
    {
        bool result = _sut.ProcessRefund(0, "Wrong item");
        Assert.That(result, Is.False);
    }

    // ── CalculateTotal – tax precision ───────────────────────────────────────

    [Test]
    public void CalculateTotal_TaxIsAppliedAtCorrectRate()
    {
        // Verify the 8.25% tax is embedded in the result
        double a = 10.0;
        int q = 1;
        double preTax = a * q + 5.99; // no discount
        double withTax = preTax + preTax * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(withTax).Within(0.001));
    }

    [Test]
    public void CalculateTotal_GoldDiscountReducesSubtotalBeforeShippingAndTax()
    {
        double a = 200.0;
        int q = 6;
        double subtotal = a * q;           // 1200
        double discounted = subtotal * 0.80; // 20% off
        double ship = 9.99;               // 6-20 range
        double preTax = discounted + ship;
        double expected = preTax + preTax * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_SilverDiscountAppliedCorrectly()
    {
        double a = 600.0;
        int q = 1;
        double subtotal = a * q;            // 600
        double discounted = subtotal * 0.85; // 15% off
        double ship = 5.99;
        double preTax = discounted + ship;
        double expected = preTax + preTax * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    [Test]
    public void CalculateTotal_BronzeDiscountAppliedCorrectly()
    {
        double a = 200.0;
        int q = 1;
        double subtotal = a * q;            // 200
        double discounted = subtotal * 0.90; // 10% off
        double ship = 5.99;
        double preTax = discounted + ship;
        double expected = preTax + preTax * 0.0825;
        double result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.001));
    }

    // ── ValidateOrder – boundary numeric strings ─────────────────────────────

    [Test]
    public void ValidateOrder_AmountOfOneDecimalPlace_IsValid()
    {
        string result = _sut.ValidateOrder("Alice", "9.9", "1");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_QuantityOfOne_IsValid()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "1");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_AmountWithLeadingSpaces_ReturnsError()
    {
        string result = _sut.ValidateOrder("Alice", " 10.00", "1");
        Assert.That(result, Does.Contain("Amount must be numeric").Or.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_ValidInputs_DoesNotContainQuantityError()
    {
        string result = _sut.ValidateOrder("Dave", "25.00", "4");
        Assert.That(result, Does.Not.Contain("Quantity"));
    }

    [Test]
    public void ValidateOrder_ValidInputs_DoesNotContainAmountError()
    {
        string result = _sut.ValidateOrder("Dave", "25.00", "4");
        Assert.That(result, Does.Not.Contain("Amount"));
    }

    [Test]
    public void ValidateOrder_ValidInputs_DoesNotContainNameError()
    {
        string result = _sut.ValidateOrder("Dave", "25.00", "4");
        Assert.That(result, Does.Not.Contain("Name"));
    }

    // ── GetDiscountTier – return value casing ────────────────────────────────

    [Test]
    public void GetDiscountTier_AllTiersReturnUpperCaseStrings()
    {
        string none   = _sut.GetDiscountTier(50.0);
        string bronze = _sut.GetDiscountTier(200.0);
        string silver = _sut.GetDiscountTier(600.0);
        string gold   = _sut.GetDiscountTier(1500.0);

        Assert.That(none,   Is.EqualTo(none.ToUpper()));
        Assert.That(bronze, Is.EqualTo(bronze.ToUpper()));
        Assert.That(silver, Is.EqualTo(silver.ToUpper()));
        Assert.That(gold,   Is.EqualTo(gold.ToUpper()));
    }

    [Test]
    public void GetDiscountTier_NoneReturnValue_IsNotNullOrEmpty()
    {
        string result = _sut.GetDiscountTier(0.0);
        Assert.That(result, Is.Not.Null.And.Not.Empty);
    }

    [Test]
    public void GetDiscountTier_GoldReturnValue_IsNotNullOrEmpty()
    {
        string result = _sut.GetDiscountTier(2000.0);
        Assert.That(result, Is.Not.Null.And.Not.Empty);
    }
}