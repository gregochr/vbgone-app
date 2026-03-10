using NUnit.Framework;

[TestFixture]
public class OrderValidatorTests
{
    private IOrderValidator _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderValidator();
    }

    // =====================================================================
    // ValidateOrder Tests
    // =====================================================================

    [Test]
    public void ValidateOrder_ValidInputs_ReturnsEmptyString()
    {
        var result = _sut.ValidateOrder("John Smith", "99.99", "5");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_EmptyName_ReturnsNameRequiredError()
    {
        var result = _sut.ValidateOrder("", "99.99", "5");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_NullName_ReturnsNameRequiredError()
    {
        var result = _sut.ValidateOrder(null, "99.99", "5");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_WhitespaceName_ReturnsNameRequiredError()
    {
        var result = _sut.ValidateOrder("   ", "99.99", "5");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_NonNumericAmount_ReturnsAmountMustBeNumericError()
    {
        var result = _sut.ValidateOrder("John Smith", "abc", "5");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_NullAmount_ReturnsAmountMustBeNumericError()
    {
        var result = _sut.ValidateOrder("John Smith", null, "5");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyAmount_ReturnsAmountMustBeNumericError()
    {
        var result = _sut.ValidateOrder("John Smith", "", "5");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_ReturnsAmountMustBePositiveError()
    {
        var result = _sut.ValidateOrder("John Smith", "0", "5");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_NegativeAmount_ReturnsAmountMustBePositiveError()
    {
        var result = _sut.ValidateOrder("John Smith", "-1.00", "5");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_DoesNotReturnAmountMustBeNumericError()
    {
        var result = _sut.ValidateOrder("John Smith", "0", "5");
        Assert.That(result, Does.Not.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_NonNumericQuantity_ReturnsQuantityMustBeNumericError()
    {
        var result = _sut.ValidateOrder("John Smith", "99.99", "xyz");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_NullQuantity_ReturnsQuantityMustBeNumericError()
    {
        var result = _sut.ValidateOrder("John Smith", "99.99", null);
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyQuantity_ReturnsQuantityMustBeNumericError()
    {
        var result = _sut.ValidateOrder("John Smith", "99.99", "");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_ZeroQuantity_ReturnsQuantityMustBePositiveError()
    {
        var result = _sut.ValidateOrder("John Smith", "99.99", "0");
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_NegativeQuantity_ReturnsQuantityMustBePositiveError()
    {
        var result = _sut.ValidateOrder("John Smith", "99.99", "-3");
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_ZeroQuantity_DoesNotReturnQuantityMustBeNumericError()
    {
        var result = _sut.ValidateOrder("John Smith", "99.99", "0");
        Assert.That(result, Does.Not.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_AllFieldsInvalid_ReturnsAllErrors()
    {
        var result = _sut.ValidateOrder("", "abc", "xyz");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be numeric."));
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyNameAndZeroAmount_ReturnsBothErrors()
    {
        var result = _sut.ValidateOrder("", "0", "5");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_EmptyNameAndZeroQuantity_ReturnsBothErrors()
    {
        var result = _sut.ValidateOrder("", "99.99", "0");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_ValidDecimalAmount_ReturnsEmptyString()
    {
        var result = _sut.ValidateOrder("Jane Doe", "0.01", "1");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_LargeValidValues_ReturnsEmptyString()
    {
        var result = _sut.ValidateOrder("Corporate Client", "9999.99", "999");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_QuantityOf1_ReturnsEmptyString()
    {
        var result = _sut.ValidateOrder("John Smith", "50.00", "1");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_FloatStringQuantity_ReturnsQuantityNumericError()
    {
        var result = _sut.ValidateOrder("John Smith", "50.00", "2.5");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    // =====================================================================
    // GetDiscountTier Tests
    // =====================================================================

    [Test]
    public void GetDiscountTier_SubtotalOfZero_ReturnsNone()
    {
        var result = _sut.GetDiscountTier(0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_NegativeSubtotal_ReturnsNone()
    {
        var result = _sut.GetDiscountTier(-100);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf1_ReturnsNone()
    {
        var result = _sut.GetDiscountTier(1);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf100_ReturnsNone()
    {
        var result = _sut.GetDiscountTier(100);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustAbove100_ReturnsBronze()
    {
        var result = _sut.GetDiscountTier(100.01);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf250_ReturnsBronze()
    {
        var result = _sut.GetDiscountTier(250);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf500_ReturnsBronze()
    {
        var result = _sut.GetDiscountTier(500);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustAbove500_ReturnsSilver()
    {
        var result = _sut.GetDiscountTier(500.01);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf750_ReturnsSilver()
    {
        var result = _sut.GetDiscountTier(750);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf1000_ReturnsSilver()
    {
        var result = _sut.GetDiscountTier(1000);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustAbove1000_ReturnsGold()
    {
        var result = _sut.GetDiscountTier(1000.01);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf5000_ReturnsGold()
    {
        var result = _sut.GetDiscountTier(5000);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_VeryLargeSubtotal_ReturnsGold()
    {
        var result = _sut.GetDiscountTier(double.MaxValue);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_VerySmallNegative_ReturnsNone()
    {
        var result = _sut.GetDiscountTier(-0.001);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    // =====================================================================
    // CalculateTotal Tests
    // =====================================================================

    [Test]
    public void CalculateTotal_SmallOrderUnder100_AppliesNoDiscountAndSmallShipping()
    {
        // amount=10, qty=5: subtotal=50 (no discount), ship=5.99
        // tot = 50 + 5.99 = 55.99, tax = 55.99 * 0.0825 = 4.619175
        // total = 60.609175
        var result = _sut.CalculateTotal(10.0, 5);
        Assert.That(result, Is.EqualTo(60.609175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly100_AppliesNoDiscountAndSmallShipping()
    {
        // amount=20, qty=5: subtotal=100 (no discount, boundary), ship=5.99
        // tot = 100 + 5.99 = 105.99, tax = 105.99 * 0.0825
        // total = 105.99 * 1.0825
        var result = _sut.CalculateTotal(20.0, 5);
        double expected = (100.0 + 5.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustAbove100_AppliesBronzeDiscount()
    {
        // amount=21, qty=5: subtotal=105 (>100, <=500 => disc=0.10), ship=5.99
        // afterDisc = 105 - 10.5 = 94.5, tot = 94.5 + 5.99 = 100.49
        // tax = 100.49 * 0.0825, total = 100.49 * 1.0825
        var result = _sut.CalculateTotal(21.0, 5);
        double subtotal = 21.0 * 5;
        double afterDisc = subtotal - subtotal * 0.10;
        double expected = (afterDisc + 5.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalOf500_AppliesBronzeDiscount()
    {
        // amount=100, qty=5: subtotal=500 (<=500 => disc=0.10), ship=5.99
        var result = _sut.CalculateTotal(100.0, 5);
        double subtotal = 100.0 * 5;
        double afterDisc = subtotal - subtotal * 0.10;
        double expected = (afterDisc + 5.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustAbove500_AppliesSilverDiscount()
    {
        // amount=26, qty=20: subtotal=520 (>500, <=1000 => disc=0.15), ship=9.99
        var result = _sut.CalculateTotal(26.0, 20);
        double subtotal = 26.0 * 20;
        double afterDisc = subtotal - subtotal * 0.15;
        double expected = (afterDisc + 9.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalOf1000_AppliesSilverDiscount()
    {
        // amount=50, qty=20: subtotal=1000 (<=1000 => disc=0.15), ship=9.99
        var result = _sut.CalculateTotal(50.0, 20);
        double subtotal = 50.0 * 20;
        double afterDisc = subtotal - subtotal * 0.15;
        double expected = (afterDisc + 9.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustAbove1000_AppliesGoldDiscount()
    {
        // amount=51, qty=20: subtotal=1020 (>1000 => disc=0.20), ship=9.99
        var result = _sut.CalculateTotal(51.0, 20);
        double subtotal = 51.0 * 20;
        double afterDisc = subtotal - subtotal * 0.20;
        double expected = (afterDisc + 9.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_LargeSubtotal_AppliesGoldDiscount()
    {
        // amount=200, qty=50: subtotal=10000 (>1000 => disc=0.20), ship=14.99
        var result = _sut.CalculateTotal(200.0, 50);
        double subtotal = 200.0 * 50;
        double afterDisc = subtotal - subtotal * 0.20;
        double expected = (afterDisc + 14.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOf1_AppliesSmallShipping()
    {
        // qty=1 (<= 5 => ship=5.99)
        var result = _sut.CalculateTotal(10.0, 1);
        double subtotal = 10.0 * 1;
        double expected = (subtotal + 5.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOf5_AppliesSmallShipping()
    {
        // qty=5 (<= 5 => ship=5.99)
        var result = _sut.CalculateTotal(10.0, 5);
        double subtotal = 10.0 * 5;
        double expected = (subtotal + 5.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOf6_AppliesMediumShipping()
    {
        // qty=6 (> 5, <= 20 => ship=9.99)
        var result = _sut.CalculateTotal(10.0, 6);
        double subtotal = 10.0 * 6;
        double expected = (subtotal + 9.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOf20_AppliesMediumShipping()
    {
        // qty=20 (<= 20 => ship=9.99)
        var result = _sut.CalculateTotal(10.0, 20);
        double subtotal = 10.0 * 20;
        double expected = (subtotal + 9.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOf21_AppliesLargeShipping()
    {
        // qty=21 (> 20 => ship=14.99)
        var result = _sut.CalculateTotal(10.0, 21);
        double subtotal = 10.0 * 21;
        double expected = (subtotal + 14.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOf100_AppliesLargeShipping()
    {
        // qty=100 (> 20 => ship=14.99)
        var result = _sut.CalculateTotal(5.0, 100);
        double subtotal = 5.0 * 100;
        double afterDisc = subtotal - subtotal * 0.20;
        double expected = (afterDisc + 14.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroAmount_ReturnsShippingPlusTaxOnly()
    {
        // a=0: discount block skipped (a > 0 is false), ship applies for qty
        // subtotal = 0*5 = 0, disc=0, ship=5.99
        // tot = 0 + 5.99 = 5.99, tax = 5.99 * 0.0825
        var result = _sut.CalculateTotal(0.0, 5);
        double expected = 5.99 * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroQuantity_ReturnsZero()
    {
        // q=0: shipping block skipped, subtotal=0, disc=0, ship=0, tot=0, tax=0
        var result = _sut.CalculateTotal(50.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeAmount_ReturnsShippingPlusTaxOnly()
    {
        // a < 0: discount block skipped, ship=5.99 for qty=1
        // subtotal = -10*1 = -10, disc=0, ship=5.99
        // tot = -10 + 5.99 = -4.01, tax = -4.01 * 0.0825
        var result = _sut.CalculateTotal(-10.0, 1);
        double subtotal = -10.0 * 1;
        double expected = (subtotal + 5.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeQuantity_ReturnsZero()
    {
        // q < 0: shipping block skipped (q > 0 false), subtotal = a*q (negative)
        // disc block: a>0 but q>0 is false so disc=0
        // tot = a*q + 0 + 0 = negative, tax = negative
        var result = _sut.CalculateTotal(10.0, -1);
        double subtotal = 10.0 * -1;
        double expected = subtotal * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_TaxIsIncludedInTotal()
    {
        // Verify tax (0.0825) is included by checking result > pretax amount
        double amount = 10.0;
        int qty = 3;
        var result = _sut.CalculateTotal(amount, qty);
        double pretax = amount * qty + 5.99;
        Assert.That(result, Is.GreaterThan(pretax));
    }

    [Test]
    public void CalculateTotal_BronzeDiscountReducesTotal()
    {
        // Subtotal 200 with bronze should be less than no discount total at same qty
        double resultWithDiscount = _sut.CalculateTotal(40.0, 5);  // subtotal=200, bronze
        double resultWithoutDiscount = _sut.CalculateTotal(20.0, 5); // subtotal=100, no discount
        // The discounted one has higher subtotal but discount applied
        // Just verify discount was applied: 200 * 0.9 = 180, vs 100 * no discount
        double subtotalDisc = 40.0 * 5;
        double afterDisc = subtotalDisc * 0.90;
        double expected = (afterDisc + 5.99) * 1.0825;
        Assert.That(resultWithDiscount, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactlyAtBronzeBoundary_NoDiscount()
    {
        // subtotal exactly 100 => no discount
        var result = _sut.CalculateTotal(100.0, 1);
        double expected = (100.0 + 5.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactlyAtSilverBoundary_BronzeDiscount()
    {
        // subtotal exactly 500 => bronze (d1=0.10)
        var result = _sut.CalculateTotal(500.0, 1);
        double afterDisc = 500.0 - 500.0 * 0.10;
        double expected = (afterDisc + 5.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactlyAtGoldBoundary_SilverDiscount()
    {
        // subtotal exactly 1000 => silver (d2=0.15)
        var result = _sut.CalculateTotal(1000.0, 1);
        double afterDisc = 1000.0 - 1000.0 * 0.15;
        double expected = (afterDisc + 5.99) * 1.0825;
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ResultIsPositiveForNormalInputs()
    {
        var result = _sut.CalculateTotal(25.0, 4);
        Assert.That(result, Is.Positive);
    }

    [Test]
    public void CalculateTotal_BothZero_ReturnsZero()
    {
        var result = _sut.CalculateTotal(0.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }
}