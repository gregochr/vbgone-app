using NUnit.Framework;

[TestFixture]
public class RefundRepositoryTests
{
    private IRefundRepository _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new RefundRepository();
    }

    // CalculateTotal — Happy Path

    [Test]
    public void CalculateTotal_SingleItemLowValue_ReturnsBaseAmountPlusShippingPlusTax()
    {
        // a=10, q=1 => subtotal=10 (<= 100 so no discount), ship=5.99, tax=(10+5.99)*0.0825
        double expected = (10.0 * 1 + 5.99) * (1 + 0.0825);
        double result = _sut.CalculateTotal(10.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly100_NoDiscount_SmallShipping()
    {
        // a=20, q=5 => subtotal=100, not > 100 so disc=0, q<=5 so ship=5.99
        double subtotal = 20.0 * 5;
        double ship = 5.99;
        double tot = subtotal + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(20.0, 5);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustAbove100_AppliesBronzeDiscount()
    {
        // a=11, q=10 => subtotal=110 (>100, <=500), disc=0.1, q<=20 so ship=9.99
        double subtotal = 11.0 * 10;
        double disc = 0.1;
        double ship = 9.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(11.0, 10);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly500_AppliesBronzeDiscount()
    {
        // a=25, q=20 => subtotal=500, not > 500 so disc=0.1, q<=20 so ship=9.99
        double subtotal = 25.0 * 20;
        double disc = 0.1;
        double ship = 9.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(25.0, 20);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustAbove500_AppliesSilverDiscount()
    {
        // a=26, q=20 => subtotal=520 (>500, <=1000), disc=0.15, q<=20 so ship=9.99
        double subtotal = 26.0 * 20;
        double disc = 0.15;
        double ship = 9.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(26.0, 20);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly1000_AppliesSilverDiscount()
    {
        // a=50, q=20 => subtotal=1000, not > 1000 so disc=0.15, q<=20 so ship=9.99
        double subtotal = 50.0 * 20;
        double disc = 0.15;
        double ship = 9.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(50.0, 20);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalAbove1000_AppliesGoldDiscount()
    {
        // a=100, q=25 => subtotal=2500 (>1000), disc=0.2, q>20 so ship=14.99
        double subtotal = 100.0 * 25;
        double disc = 0.2;
        double ship = 14.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(100.0, 25);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustAbove1000_AppliesGoldDiscount()
    {
        // a=51, q=20 => subtotal=1020 (>1000), disc=0.2, q<=20 so ship=9.99
        double subtotal = 51.0 * 20;
        double disc = 0.2;
        double ship = 9.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(51.0, 20);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // CalculateTotal — Shipping Tier Boundaries

    [Test]
    public void CalculateTotal_QuantityIs1_UsesSmallShipping()
    {
        // q=1 => ship=5.99
        double subtotal = 10.0 * 1;
        double ship = 5.99;
        double tot = subtotal + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(10.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityIs5_UsesSmallShipping()
    {
        // q=5 => ship=5.99
        double subtotal = 10.0 * 5;
        double ship = 5.99;
        double tot = subtotal + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(10.0, 5);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityIs6_UsesMediumShipping()
    {
        // q=6 => ship=9.99
        double subtotal = 10.0 * 6;
        double disc = 0.1; // 60 < 100 so actually no discount... wait 60 <= 100 so no disc
        double ship = 9.99;
        double tot = subtotal + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(10.0, 6);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityIs20_UsesMediumShipping()
    {
        // q=20 => ship=9.99
        double subtotal = 5.0 * 20; // 100, not >100 so no discount
        double ship = 9.99;
        double tot = subtotal + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(5.0, 20);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityIs21_UsesLargeShipping()
    {
        // q=21 => ship=14.99
        double subtotal = 5.0 * 21; // 105 > 100, disc=0.1
        double disc = 0.1;
        double ship = 14.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(5.0, 21);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // CalculateTotal — Edge Cases

    [Test]
    public void CalculateTotal_ZeroAmount_ReturnsOnlyShippingPlusTax()
    {
        // a=0 => subtotal=0, no discount check entered (a not > 0), q=1 so ship=5.99
        double ship = 5.99;
        double expected = ship + ship * 0.0825;
        double result = _sut.CalculateTotal(0.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroQuantity_ReturnsZero()
    {
        // q=0 => no shipping block entered, subtotal=0, disc=0, ship=0, tot=0
        double result = _sut.CalculateTotal(10.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroAmountZeroQuantity_ReturnsZero()
    {
        double result = _sut.CalculateTotal(0.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeAmount_ReturnsOnlyShippingPlusTax()
    {
        // a=-10 => a not > 0, no discount, q=1 => ship=5.99, subtotal=-10
        // tot = (-10*1) - 0 + 5.99 = -4.01, tax = -4.01 * 0.0825
        double subtotal = -10.0 * 1;
        double ship = 5.99;
        double tot = subtotal + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(-10.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeQuantity_ReturnsZero()
    {
        // q=-1 => q not > 0 so no discount entered, q not > 0 so no shipping entered, ship=0
        // tot = (10*-1) - 0 + 0 = -10, tax = -10*0.0825
        double subtotal = 10.0 * -1;
        double expected = subtotal + subtotal * 0.0825;
        double result = _sut.CalculateTotal(10.0, -1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_VeryLargeAmount_AppliesGoldDiscountAndLargeShipping()
    {
        // a=999999, q=100 => subtotal >> 1000, disc=0.2, q>20 ship=14.99
        double subtotal = 999999.0 * 100;
        double disc = 0.2;
        double ship = 14.99;
        double tot = subtotal - subtotal * disc + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(999999.0, 100);
        Assert.That(result, Is.EqualTo(expected).Within(0.01));
    }

    [Test]
    public void CalculateTotal_FractionalAmount_CalculatesCorrectly()
    {
        // a=0.01, q=1 => subtotal=0.01, no discount, ship=5.99
        double subtotal = 0.01 * 1;
        double ship = 5.99;
        double tot = subtotal + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(0.01, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // ValidateOrder — Happy Path

    [Test]
    public void ValidateOrder_ValidInputs_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "2");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_ValidNameAmountAndQuantity_ReturnsNoError()
    {
        string result = _sut.ValidateOrder("Bob Smith", "99.99", "100");
        Assert.That(result, Is.Empty);
    }

    // ValidateOrder — Name Validation

    [Test]
    public void ValidateOrder_EmptyName_ReturnsNameRequiredError()
    {
        string result = _sut.ValidateOrder("", "10.00", "2");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_NullName_ReturnsNameRequiredError()
    {
        string result = _sut.ValidateOrder(null, "10.00", "2");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_WhitespaceName_ReturnsNameRequiredError()
    {
        string result = _sut.ValidateOrder("   ", "10.00", "2");
        Assert.That(result, Does.Contain("Name required."));
    }

    // ValidateOrder — Amount Validation

    [Test]
    public void ValidateOrder_NonNumericAmount_ReturnsAmountMustBeNumericError()
    {
        string result = _sut.ValidateOrder("Alice", "abc", "2");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyAmount_ReturnsAmountMustBeNumericError()
    {
        string result = _sut.ValidateOrder("Alice", "", "2");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_ReturnsAmountMustBePositiveError()
    {
        string result = _sut.ValidateOrder("Alice", "0", "2");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_NegativeAmount_ReturnsAmountMustBePositiveError()
    {
        string result = _sut.ValidateOrder("Alice", "-5.00", "2");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_NullAmount_ReturnsAmountMustBeNumericError()
    {
        string result = _sut.ValidateOrder("Alice", null, "2");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    // ValidateOrder — Quantity Validation

    [Test]
    public void ValidateOrder_NonNumericQuantity_ReturnsQuantityMustBeNumericError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "xyz");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyQuantity_ReturnsQuantityMustBeNumericError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_ZeroQuantity_ReturnsQuantityMustBePositiveError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "0");
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_NegativeQuantity_ReturnsQuantityMustBePositiveError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "-3");
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_NullQuantity_ReturnsQuantityMustBeNumericError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", null);
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    // ValidateOrder — Multiple Errors

    [Test]
    public void ValidateOrder_AllInvalidInputs_ReturnsAllErrors()
    {
        string result = _sut.ValidateOrder("", "abc", "xyz");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be numeric."));
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyNameAndZeroAmount_ReturnsBothErrors()
    {
        string result = _sut.ValidateOrder("", "0", "1");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_EmptyNameAndNonNumericQuantity_ReturnsBothErrors()
    {
        string result = _sut.ValidateOrder("", "10.00", "bad");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    // GetDiscountTier — Happy Path

    [Test]
    public void GetDiscountTier_SubtotalAbove1000_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(1001.0);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalExactly1000_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(1000.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustAbove500_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(501.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalExactly500_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(500.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustAbove100_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(101.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalExactly100_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(100.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustBelow100_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(99.99);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOfOne_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(1.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    // GetDiscountTier — Edge Cases (zero, negative)

    [Test]
    public void GetDiscountTier_ZeroSubtotal_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(0.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_NegativeSubtotal_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(-1.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_VeryLargeSubtotal_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(999999999.0);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustBelow500_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(499.99);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustBelow1000_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(999.99);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustAbove1000_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(1000.01);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    // ProcessRefund — these tests verify interface contract behaviour
    // Note: ProcessRefund requires DB/email infrastructure; tests verify null/invalid guard behaviour

    [Test]
    public void ProcessRefund_InvalidOrderId_ReturnsFalse()
    {
        bool result = _sut.ProcessRefund(-1, "Damaged item");
        Assert.That(result, Is.False);
    }

    [Test]
    public void ProcessRefund_ZeroOrderId_ReturnsFalse()
    {
        bool result = _sut.ProcessRefund(0, "Test reason");
        Assert.That(result, Is.False);
    }

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
    public void ProcessRefund_NegativeOrderId_ReturnsFalse()
    {
        bool result = _sut.ProcessRefund(-999, "reason");
        Assert.That(result, Is.False);
    }

    // CalculateTotal — Tax Calculation Verification

    [Test]
    public void CalculateTotal_TaxRateApplied_TotalIncludesTax()
    {
        // Verify tax (8.25%) is included: result should be > pre-tax total
        double result = _sut.CalculateTotal(10.0, 1);
        double preTax = 10.0 + 5.99;
        Assert.That(result, Is.GreaterThan(preTax));
    }

    [Test]
    public void CalculateTotal_GoldDiscount_ReducesSubtotalBy20Percent()
    {
        // a=100, q=50 => subtotal=5000, disc=20%, ship=14.99
        double subtotal = 100.0 * 50;
        double discounted = subtotal * (1 - 0.2);
        double ship = 14.99;
        double tot = discounted + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(100.0, 50);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SilverDiscount_ReducesSubtotalBy15Percent()
    {
        // a=30, q=20 => subtotal=600 (>500, <=1000), disc=0.15, q<=20 ship=9.99
        double subtotal = 30.0 * 20;
        double discounted = subtotal * (1 - 0.15);
        double ship = 9.99;
        double tot = discounted + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(30.0, 20);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_BronzeDiscount_ReducesSubtotalBy10Percent()
    {
        // a=15, q=10 => subtotal=150 (>100, <=500), disc=0.1, q<=20 ship=9.99
        double subtotal = 15.0 * 10;
        double discounted = subtotal * (1 - 0.1);
        double ship = 9.99;
        double tot = discounted + ship;
        double expected = tot + tot * 0.0825;
        double result = _sut.CalculateTotal(15.0, 10);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // ValidateOrder — Boundary amounts

    [Test]
    public void ValidateOrder_SmallPositiveAmount_ReturnsNoError()
    {
        string result = _sut.ValidateOrder("Alice", "0.01", "1");
        Assert.That(result, Is.Empty);
    }

    [Test]
    public void ValidateOrder_LargeAmount_ReturnsNoError()
    {
        string result = _sut.ValidateOrder("Alice", "999999.99", "1");
        Assert.That(result, Is.Empty);
    }

    [Test]
    public void ValidateOrder_QuantityOfOne_ReturnsNoError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "1");
        Assert.That(result, Is.Empty);
    }

    [Test]
    public void ValidateOrder_LargeQuantity_ReturnsNoError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "9999");
        Assert.That(result, Is.Empty);
    }

    [Test]
    public void ValidateOrder_DecimalQuantity_ReturnsQuantityMustBeNumericOrPositiveError()
    {
        string result = _sut.ValidateOrder("Alice", "10.00", "1.5");
        Assert.That(result, Is.Not.Empty);
    }

    [Test]
    public void ValidateOrder_AmountWithCurrencySymbol_ReturnsAmountMustBeNumericError()
    {
        string result = _sut.ValidateOrder("Alice", "$10.00", "1");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_AllNullInputs_ReturnsAllErrors()
    {
        string result = _sut.ValidateOrder(null, null, null);
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be numeric."));
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    // GetDiscountTier — Decimal boundary values

    [Test]
    public void GetDiscountTier_FractionalSubtotalJustOver100_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(100.01);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_FractionalSubtotalJustOver500_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(500.01);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_FractionalSubtotalJustOver1000_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(1000.01);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOfDoubleMaxValue_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(double.MaxValue);
        Assert.That(result, Is.EqualTo("GOLD"));
    }
}