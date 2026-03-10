using NUnit.Framework;

[TestFixture]
public class OrderRepositoryTests
{
    private IOrderRepository _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderRepository();
    }

    // ── CalculateTotal ──────────────────────────────────────────────────────────

    [Test]
    public void CalculateTotal_TypicalInputs_ReturnsCorrectTotal()
    {
        // a=10, q=5 → subtotal=50, disc=0, ship=5.99
        // tot before tax = 55.99, tax = 55.99*0.0825 = 4.619175
        // total = 60.609175
        double result = _sut.CalculateTotal(10.0, 5);
        Assert.That(result, Is.EqualTo(60.609175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly100_NoDiscount()
    {
        // a=10, q=10 → subtotal=100, disc=0 (not >100), ship=9.99
        // tot before tax = 109.99, tax=9.074175, total=119.064175
        double result = _sut.CalculateTotal(10.0, 10);
        Assert.That(result, Is.EqualTo(119.064175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver100_AppliesBronzeDiscount()
    {
        // a=101, q=1 → subtotal=101, disc=0.1, ship=5.99
        // discounted = 101 - 10.1 = 90.9, + 5.99 = 96.89
        // tax = 96.89 * 0.0825 = 7.993425, total = 104.883425
        double result = _sut.CalculateTotal(101.0, 1);
        Assert.That(result, Is.EqualTo(104.883425).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly500_AppliesBronzeDiscount()
    {
        // a=100, q=5 → subtotal=500, disc=0.1 (not >500), ship=5.99
        // discounted = 500 - 50 = 450, + 5.99 = 455.99
        // tax = 455.99 * 0.0825 = 37.619175, total = 493.609175
        double result = _sut.CalculateTotal(100.0, 5);
        Assert.That(result, Is.EqualTo(493.609175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver500_AppliesSilverDiscount()
    {
        // a=501, q=1 → subtotal=501, disc=0.15, ship=5.99
        // discounted = 501 - 75.15 = 425.85, + 5.99 = 431.84
        // tax = 431.84 * 0.0825 = 35.6268, total = 467.4668
        double result = _sut.CalculateTotal(501.0, 1);
        Assert.That(result, Is.EqualTo(467.4668).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly1000_AppliesSilverDiscount()
    {
        // a=200, q=5 → subtotal=1000, disc=0.15 (not >1000), ship=5.99
        // discounted = 1000 - 150 = 850, + 5.99 = 855.99
        // tax = 855.99 * 0.0825 = 70.619175, total = 926.609175
        double result = _sut.CalculateTotal(200.0, 5);
        Assert.That(result, Is.EqualTo(926.609175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver1000_AppliesGoldDiscount()
    {
        // a=1001, q=1 → subtotal=1001, disc=0.2, ship=5.99
        // discounted = 1001 - 200.2 = 800.8, + 5.99 = 806.79
        // tax = 806.79 * 0.0825 = 66.560175, total = 873.350175
        double result = _sut.CalculateTotal(1001.0, 1);
        Assert.That(result, Is.EqualTo(873.350175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_LargeOrder_AppliesGoldDiscountAndHighShipping()
    {
        // a=100, q=25 → subtotal=2500, disc=0.2, ship=14.99
        // discounted = 2500 - 500 = 2000, + 14.99 = 2014.99
        // tax = 2014.99 * 0.0825 = 166.236675, total = 2181.226675
        double result = _sut.CalculateTotal(100.0, 25);
        Assert.That(result, Is.EqualTo(2181.226675).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityExactly5_UsesLowestShipping()
    {
        // a=5, q=5 → subtotal=25, disc=0, ship=5.99
        // tot before tax = 30.99, tax=2.556675, total=33.546675
        double result = _sut.CalculateTotal(5.0, 5);
        Assert.That(result, Is.EqualTo(33.546675).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityExactly6_UsesMediumShipping()
    {
        // a=5, q=6 → subtotal=30, disc=0, ship=9.99
        // tot before tax = 39.99, tax=3.299175, total=43.289175
        double result = _sut.CalculateTotal(5.0, 6);
        Assert.That(result, Is.EqualTo(43.289175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityExactly20_UsesMediumShipping()
    {
        // a=5, q=20 → subtotal=100, disc=0 (not >100), ship=9.99
        // tot before tax = 109.99, tax=9.074175, total=119.064175
        double result = _sut.CalculateTotal(5.0, 20);
        Assert.That(result, Is.EqualTo(119.064175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityExactly21_UsesHighestShipping()
    {
        // a=5, q=21 → subtotal=105, disc=0.1, ship=14.99
        // discounted = 105 - 10.5 = 94.5, + 14.99 = 109.49
        // tax = 109.49 * 0.0825 = 9.032925, total = 118.522925
        double result = _sut.CalculateTotal(5.0, 21);
        Assert.That(result, Is.EqualTo(118.522925).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroAmount_ReturnsShippingPlusTaxOnly()
    {
        // a=0, q=5 → subtotal=0, disc=0 (a not >0), ship=5.99
        // tot before tax = 5.99, tax=0.494175, total=6.484175
        double result = _sut.CalculateTotal(0.0, 5);
        Assert.That(result, Is.EqualTo(6.484175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroQuantity_ReturnsZero()
    {
        // a=10, q=0 → subtotal=0, disc=0, ship=0 (q not >0)
        // tot=0, tax=0, total=0
        double result = _sut.CalculateTotal(10.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroAmountAndZeroQuantity_ReturnsZero()
    {
        double result = _sut.CalculateTotal(0.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeAmount_NoDiscountApplied()
    {
        // a=-10, q=5 → a not >0, disc=0, ship=5.99
        // subtotal = -50, tot before tax = -50 + 5.99 = -44.01
        // tax = -44.01 * 0.0825 = -3.630825, total = -47.640825
        double result = _sut.CalculateTotal(-10.0, 5);
        Assert.That(result, Is.EqualTo(-47.640825).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeQuantity_NoShippingApplied()
    {
        // a=10, q=-1 → q not >0 for shipping, disc block also q not >0
        // subtotal = 10*-1 = -10, disc=0, ship=0
        // tot = -10, tax = -0.825, total = -10.825
        double result = _sut.CalculateTotal(10.0, -1);
        Assert.That(result, Is.EqualTo(-10.825).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_VeryLargeValues_DoesNotOverflow()
    {
        // a=1000000, q=1000 → subtotal=1,000,000,000, disc=0.2, ship=14.99
        // discounted = 800,000,000 + 14.99 = 800,000,014.99
        // tax = 800,000,014.99 * 0.0825 = 66,000,001.236675
        // total = 866,000,016.226675
        double result = _sut.CalculateTotal(1000000.0, 1000);
        Assert.That(result, Is.EqualTo(866000016.226675).Within(1.0));
    }

    [Test]
    public void CalculateTotal_FractionalAmount_CalculatesCorrectly()
    {
        // a=1.5, q=3 → subtotal=4.5, disc=0, ship=5.99
        // tot before tax = 10.49, tax=0.865425, total=11.355425
        double result = _sut.CalculateTotal(1.5, 3);
        Assert.That(result, Is.EqualTo(11.355425).Within(0.0001));
    }

    // ── ValidateOrder ───────────────────────────────────────────────────────────

    [Test]
    public void ValidateOrder_AllValidInputs_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("John", "10.00", "5");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_EmptyName_ReturnsNameError()
    {
        string result = _sut.ValidateOrder("", "10.00", "5");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_NullName_ReturnsNameError()
    {
        string result = _sut.ValidateOrder(null, "10.00", "5");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_NonNumericAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", "abc", "5");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_ReturnsAmountPositiveError()
    {
        string result = _sut.ValidateOrder("John", "0", "5");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_NegativeAmount_ReturnsAmountPositiveError()
    {
        string result = _sut.ValidateOrder("John", "-5.00", "5");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_NonNumericQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("John", "10.00", "xyz");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_ZeroQuantity_ReturnsQuantityPositiveError()
    {
        string result = _sut.ValidateOrder("John", "10.00", "0");
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_NegativeQuantity_ReturnsQuantityPositiveError()
    {
        string result = _sut.ValidateOrder("John", "10.00", "-3");
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_AllFieldsInvalid_ReturnsAllErrors()
    {
        string result = _sut.ValidateOrder("", "abc", "xyz");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be numeric."));
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_NameEmptyAndAmountZeroAndQuantityZero_ReturnsAllThreeErrors()
    {
        string result = _sut.ValidateOrder("", "0", "0");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be positive."));
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_EmptyAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", "", "5");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("John", "10.00", "");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_WhitespaceAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", "   ", "5");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_WhitespaceQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("John", "10.00", "   ");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_ValidDecimalAmount_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("Alice", "99.99", "1");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_AmountIsOnlyWhitespace_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", " ", "1");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_SpecialCharactersInName_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("O'Brien-Smith", "10.00", "2");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_ValidInputsDoNotContainAnyErrorKeyword()
    {
        string result = _sut.ValidateOrder("Bob", "25.50", "3");
        Assert.That(result, Does.Not.Contain("required"));
        Assert.That(result, Does.Not.Contain("numeric"));
        Assert.That(result, Does.Not.Contain("positive"));
    }

    // ── GetDiscountTier ─────────────────────────────────────────────────────────

    [Test]
    public void GetDiscountTier_SubtotalZero_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(0.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_NegativeSubtotal_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(-100.0);
        Assert.That(result, Is.EqualTo("NONE"));
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
    public void GetDiscountTier_SubtotalJustOver100_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(100.01);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalExactly500_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(500.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustBelow500_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(499.99);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustOver500_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(500.01);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalExactly1000_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(1000.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustBelow1000_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(999.99);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustOver1000_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(1000.01);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_VeryLargeSubtotal_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(999999999.0);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf200_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(200.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf750_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(750.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf2000_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(2000.0);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf1_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(1.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf50_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(50.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    // ── CalculateTotal integration with discount tiers ──────────────────────────

    [Test]
    public void CalculateTotal_SubtotalMatchingBronzeTier_DiscountRateIs10Percent()
    {
        // a=200, q=1 → subtotal=200 > 100, disc=0.1
        // discounted = 200 - 20 = 180, ship=5.99, before tax=185.99
        // tax = 185.99 * 0.0825 = 15.344175, total = 201.334175
        double result = _sut.CalculateTotal(200.0, 1);
        Assert.That(result, Is.EqualTo(201.334175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalMatchingSilverTier_DiscountRateIs15Percent()
    {
        // a=600, q=1 → subtotal=600 > 500, disc=0.15
        // discounted = 600 - 90 = 510, ship=5.99, before tax=515.99
        // tax = 515.99 * 0.0825 = 42.569175, total = 558.559175
        double result = _sut.CalculateTotal(600.0, 1);
        Assert.That(result, Is.EqualTo(558.559175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalMatchingGoldTier_DiscountRateIs20Percent()
    {
        // a=1100, q=1 → subtotal=1100 > 1000, disc=0.2
        // discounted = 1100 - 220 = 880, ship=5.99, before tax=885.99
        // tax = 885.99 * 0.0825 = 73.094175, total = 959.084175
        double result = _sut.CalculateTotal(1100.0, 1);
        Assert.That(result, Is.EqualTo(959.084175).Within(0.0001));
    }

    // ── ProcessRefund ───────────────────────────────────────────────────────────

    [Test]
    public void ProcessRefund_InvalidOrderId_ReturnsFalse()
    {
        bool result = _sut.ProcessRefund(-1, "test reason");
        Assert.That(result, Is.False);
    }

    [Test]
    public void ProcessRefund_ZeroOrderId_ReturnsFalse()
    {
        bool result = _sut.ProcessRefund(0, "test reason");
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
    public void ProcessRefund_NonExistentOrder_ReturnsFalse()
    {
        bool result = _sut.ProcessRefund(int.MaxValue, "valid reason");
        Assert.That(result, Is.False);
    }
}