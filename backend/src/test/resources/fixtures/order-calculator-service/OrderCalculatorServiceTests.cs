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

    // =====================================================================
    // CalculateTotal — Happy Path
    // =====================================================================

    [Test]
    public void CalculateTotal_SingleItemLowValue_ReturnsBaseAmountPlusShippingPlusTax()
    {
        // a=10, q=1 => subtotal=10, no discount, ship=5.99
        // tot before tax = 10 + 5.99 = 15.99
        // tax = 15.99 * 0.0825 = 1.319175
        // total = 17.309175
        double result = _sut.CalculateTotal(10.0, 1);
        Assert.That(result, Is.EqualTo(17.309175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly100_NoDiscount_SmallShipping()
    {
        // a=20, q=5 => subtotal=100, not >100 so no discount, q<=5 so ship=5.99
        // tot before tax = 100 + 5.99 = 105.99
        // tax = 105.99 * 0.0825 = 8.744175
        // total = 114.734175
        double result = _sut.CalculateTotal(20.0, 5);
        Assert.That(result, Is.EqualTo(114.734175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver100_BronzeDiscount_MediumShipping()
    {
        // a=11, q=10 => subtotal=110, >100 but <=500 => disc=0.10, q<=20 so ship=9.99
        // discounted = 110 - 11 = 99
        // tot before tax = 99 + 9.99 = 108.99
        // tax = 108.99 * 0.0825 = 8.991675
        // total = 117.981675
        double result = _sut.CalculateTotal(11.0, 10);
        Assert.That(result, Is.EqualTo(117.981675).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly500_BronzeDiscount()
    {
        // a=25, q=20 => subtotal=500, >100 but <=500 => disc=0.10, q<=20 so ship=9.99
        // discounted = 500 - 50 = 450
        // tot before tax = 450 + 9.99 = 459.99
        // tax = 459.99 * 0.0825 = 37.949175
        // total = 497.939175
        double result = _sut.CalculateTotal(25.0, 20);
        Assert.That(result, Is.EqualTo(497.939175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver500_SilverDiscount_LargeShipping()
    {
        // a=26, q=20 => subtotal=520, >500 but <=1000 => disc=0.15, q<=20 so ship=9.99
        // discounted = 520 - 78 = 442
        // tot before tax = 442 + 9.99 = 451.99
        // tax = 451.99 * 0.0825 = 37.289175
        // total = 489.279175
        double result = _sut.CalculateTotal(26.0, 20);
        Assert.That(result, Is.EqualTo(489.279175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly1000_SilverDiscount()
    {
        // a=50, q=20 => subtotal=1000, >500 but <=1000 => disc=0.15, q<=20 so ship=9.99
        // discounted = 1000 - 150 = 850
        // tot before tax = 850 + 9.99 = 859.99
        // tax = 859.99 * 0.0825 = 70.949175
        // total = 930.939175
        double result = _sut.CalculateTotal(50.0, 20);
        Assert.That(result, Is.EqualTo(930.939175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalOver1000_GoldDiscount_LargeShipping()
    {
        // a=100, q=21 => subtotal=2100, >1000 => disc=0.20, q>20 so ship=14.99
        // discounted = 2100 - 420 = 1680
        // tot before tax = 1680 + 14.99 = 1694.99
        // tax = 1694.99 * 0.0825 = 139.836675
        // total = 1834.826675
        double result = _sut.CalculateTotal(100.0, 21);
        Assert.That(result, Is.EqualTo(1834.826675).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityExactly5_SmallShipping()
    {
        // q=5 => ship=5.99
        // a=5, q=5 => subtotal=25, no discount
        // tot before tax = 25 + 5.99 = 30.99
        // tax = 30.99 * 0.0825 = 2.556675
        // total = 33.546675
        double result = _sut.CalculateTotal(5.0, 5);
        Assert.That(result, Is.EqualTo(33.546675).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityExactly6_MediumShipping()
    {
        // q=6 => ship=9.99
        // a=5, q=6 => subtotal=30, no discount
        // tot before tax = 30 + 9.99 = 39.99
        // tax = 39.99 * 0.0825 = 3.299175
        // total = 43.289175
        double result = _sut.CalculateTotal(5.0, 6);
        Assert.That(result, Is.EqualTo(43.289175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityExactly20_MediumShipping()
    {
        // q=20 => ship=9.99
        // a=1, q=20 => subtotal=20, no discount
        // tot before tax = 20 + 9.99 = 29.99
        // tax = 29.99 * 0.0825 = 2.474175
        // total = 32.464175
        double result = _sut.CalculateTotal(1.0, 20);
        Assert.That(result, Is.EqualTo(32.464175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityExactly21_LargeShipping()
    {
        // q=21 => ship=14.99
        // a=1, q=21 => subtotal=21, no discount
        // tot before tax = 21 + 14.99 = 35.99
        // tax = 35.99 * 0.0825 = 2.969175
        // total = 38.959175
        double result = _sut.CalculateTotal(1.0, 21);
        Assert.That(result, Is.EqualTo(38.959175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_LargeQuantity_LargeShipping_GoldDiscount()
    {
        // a=50, q=100 => subtotal=5000, >1000 => disc=0.20, q>20 so ship=14.99
        // discounted = 5000 - 1000 = 4000
        // tot before tax = 4000 + 14.99 = 4014.99
        // tax = 4014.99 * 0.0825 = 331.236675
        // total = 4346.226675
        double result = _sut.CalculateTotal(50.0, 100);
        Assert.That(result, Is.EqualTo(4346.226675).Within(0.0001));
    }

    // =====================================================================
    // CalculateTotal — Edge Cases
    // =====================================================================

    [Test]
    public void CalculateTotal_ZeroAmount_ReturnsZeroNoShipping()
    {
        // a=0 => no discount block entered, q block still runs => ship applies
        // a=0, q=1 => subtotal=0, disc=0, ship=5.99
        // tot before tax = 0 + 5.99 = 5.99
        // tax = 5.99 * 0.0825 = 0.494175
        // total = 6.484175
        double result = _sut.CalculateTotal(0.0, 1);
        Assert.That(result, Is.EqualTo(6.484175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroQuantity_ReturnsZeroWithNoShipping()
    {
        // q=0 => shipping block not entered, discount block not entered (inner q>0 check)
        // tot before tax = 0 + 0 = 0
        // tax = 0, total = 0
        double result = _sut.CalculateTotal(10.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_BothZero_ReturnsZero()
    {
        double result = _sut.CalculateTotal(0.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeAmount_NoDiscountNoShippingCalc()
    {
        // a=-10, q=5 => a>0 is false so disc=0
        // q>0 so ship=5.99, subtotal=-50
        // tot before tax = -50 + 5.99 = -44.01
        // tax = -44.01 * 0.0825 = -3.630825
        // total = -47.640825
        double result = _sut.CalculateTotal(-10.0, 5);
        Assert.That(result, Is.EqualTo(-47.640825).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeQuantity_NoShippingNoDiscount()
    {
        // q=-1 => q>0 is false for both shipping and discount inner check
        // subtotal = a * q = 10 * -1 = -10, disc=0, ship=0
        // tot before tax = -10
        // tax = -10 * 0.0825 = -0.825
        // total = -10.825
        double result = _sut.CalculateTotal(10.0, -1);
        Assert.That(result, Is.EqualTo(-10.825).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactlyAt101_TriggersBronzeDiscount()
    {
        // a=101, q=1 => subtotal=101, >100 but <=500 => disc=0.10, q<=5 ship=5.99
        // discounted = 101 - 10.1 = 90.9
        // tot before tax = 90.9 + 5.99 = 96.89
        // tax = 96.89 * 0.0825 = 7.993425
        // total = 104.883425
        double result = _sut.CalculateTotal(101.0, 1);
        Assert.That(result, Is.EqualTo(104.883425).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactlyAt501_TriggersSilverDiscount()
    {
        // a=501, q=1 => subtotal=501, >500 but <=1000 => disc=0.15, q<=5 ship=5.99
        // discounted = 501 - 75.15 = 425.85
        // tot before tax = 425.85 + 5.99 = 431.84
        // tax = 431.84 * 0.0825 = 35.6268
        // total = 467.4668
        double result = _sut.CalculateTotal(501.0, 1);
        Assert.That(result, Is.EqualTo(467.4668).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactlyAt1001_TriggersGoldDiscount()
    {
        // a=1001, q=1 => subtotal=1001, >1000 => disc=0.20, q<=5 ship=5.99
        // discounted = 1001 - 200.2 = 800.8
        // tot before tax = 800.8 + 5.99 = 806.79
        // tax = 806.79 * 0.0825 = 66.560175
        // total = 873.350175
        double result = _sut.CalculateTotal(1001.0, 1);
        Assert.That(result, Is.EqualTo(873.350175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_VeryLargeAmount_GoldDiscount_LargeShipping()
    {
        // a=9999.99, q=50 => subtotal=499999.5, >1000 => disc=0.20, q>20 so ship=14.99
        // discounted = 499999.5 - 99999.9 = 399999.6
        // tot before tax = 399999.6 + 14.99 = 400014.59
        // tax = 400014.59 * 0.0825 = 33001.203675
        // total = 433015.793675
        double result = _sut.CalculateTotal(9999.99, 50);
        Assert.That(result, Is.EqualTo(433015.793675).Within(0.01));
    }

    [Test]
    public void CalculateTotal_FractionalAmount_CorrectlyComputed()
    {
        // a=0.99, q=3 => subtotal=2.97, no discount, q<=5 so ship=5.99
        // tot before tax = 2.97 + 5.99 = 8.96
        // tax = 8.96 * 0.0825 = 0.7392
        // total = 9.6992
        double result = _sut.CalculateTotal(0.99, 3);
        Assert.That(result, Is.EqualTo(9.6992).Within(0.0001));
    }

    // =====================================================================
    // ValidateOrder — Happy Path
    // =====================================================================

    [Test]
    public void ValidateOrder_AllValidInputs_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("John Smith", "19.99", "3");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_ValidNameAmountAndQuantity_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("Alice", "100", "10");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_ValidLargeValues_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("Bob", "9999.99", "999");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_ValidDecimalAmount_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("Carol", "0.01", "1");
        Assert.That(result, Is.EqualTo(""));
    }

    // =====================================================================
    // ValidateOrder — Error Conditions
    // =====================================================================

    [Test]
    public void ValidateOrder_EmptyName_ReturnsNameRequiredError()
    {
        string result = _sut.ValidateOrder("", "19.99", "3");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_NullName_ReturnsNameRequiredError()
    {
        string result = _sut.ValidateOrder(null, "19.99", "3");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_NonNumericAmount_ReturnsAmountMustBeNumericError()
    {
        string result = _sut.ValidateOrder("John", "abc", "3");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_ReturnsAmountMustBePositiveError()
    {
        string result = _sut.ValidateOrder("John", "0", "3");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_NegativeAmount_ReturnsAmountMustBePositiveError()
    {
        string result = _sut.ValidateOrder("John", "-5", "3");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_NonNumericQuantity_ReturnsQuantityMustBeNumericError()
    {
        string result = _sut.ValidateOrder("John", "19.99", "xyz");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_ZeroQuantity_ReturnsQuantityMustBePositiveError()
    {
        string result = _sut.ValidateOrder("John", "19.99", "0");
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_NegativeQuantity_ReturnsQuantityMustBePositiveError()
    {
        string result = _sut.ValidateOrder("John", "19.99", "-1");
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_EmptyNameAndNonNumericAmount_ReturnsBothErrors()
    {
        string result = _sut.ValidateOrder("", "abc", "3");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be numeric."));
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
    public void ValidateOrder_EmptyNameZeroAmountZeroQuantity_ReturnsAllErrors()
    {
        string result = _sut.ValidateOrder("", "0", "0");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be positive."));
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_EmptyAmount_ReturnsAmountMustBeNumericError()
    {
        string result = _sut.ValidateOrder("John", "", "3");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyQuantity_ReturnsQuantityMustBeNumericError()
    {
        string result = _sut.ValidateOrder("John", "19.99", "");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_NullAmount_ReturnsAmountMustBeNumericError()
    {
        string result = _sut.ValidateOrder("John", null, "3");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_NullQuantity_ReturnsQuantityMustBeNumericError()
    {
        string result = _sut.ValidateOrder("John", "19.99", null);
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_WhitespaceOnlyName_ReturnsNameRequiredError()
    {
        string result = _sut.ValidateOrder("   ", "19.99", "3");
        Assert.That(result, Does.Contain("Name required."));
    }

    // =====================================================================
    // GetDiscountTier — Happy Path
    // =====================================================================

    [Test]
    public void GetDiscountTier_SubtotalOf101_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(101.0);
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
    public void GetDiscountTier_VeryLargeSubtotal_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(999999.99);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf250_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(250.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf750_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(750.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf5000_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(5000.0);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    // =====================================================================
    // GetDiscountTier — Edge Cases / No Discount
    // =====================================================================

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
    public void GetDiscountTier_SubtotalOf100_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(100.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf1_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(1.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf99Point99_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(99.99);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf100Point01_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(100.01);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf500Point01_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(500.01);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf1000Point01_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(1000.01);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    // =====================================================================
    // CalculateTotal — Tax Verification
    // =====================================================================

    [Test]
    public void CalculateTotal_TaxIsAppliedAt8Point25Percent()
    {
        // a=10, q=1 => subtotal=10, no disc, ship=5.99
        // preTax = 15.99, tax = 15.99 * 0.0825
        // The total should be preTax * 1.0825
        double preTax = 10.0 + 5.99;
        double expected = preTax * 1.0825;
        double result = _sut.CalculateTotal(10.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_GoldDiscountCorrectlyReducesSubtotalBy20Percent()
    {
        // a=200, q=10 => subtotal=2000, >1000 => disc=0.20
        // discounted subtotal = 2000 * 0.80 = 1600
        // q<=20 so ship=9.99
        // preTax = 1609.99, total = 1609.99 * 1.0825
        double preTax = (200.0 * 10 * 0.80) + 9.99;
        double expected = preTax * 1.0825;
        double result = _sut.CalculateTotal(200.0, 10);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SilverDiscountCorrectlyReducesSubtotalBy15Percent()
    {
        // a=60, q=10 => subtotal=600, >500 but <=1000 => disc=0.15
        // discounted subtotal = 600 * 0.85 = 510
        // q<=20 so ship=9.99
        double preTax = (60.0 * 10 * 0.85) + 9.99;
        double expected = preTax * 1.0825;
        double result = _sut.CalculateTotal(60.0, 10);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_BronzeDiscountCorrectlyReducesSubtotalBy10Percent()
    {
        // a=15, q=10 => subtotal=150, >100 but <=500 => disc=0.10
        // discounted subtotal = 150 * 0.90 = 135
        // q<=20 so ship=9.99
        double preTax = (15.0 * 10 * 0.90) + 9.99;
        double expected = preTax * 1.0825;
        double result = _sut.CalculateTotal(15.0, 10);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // =====================================================================
    // CalculateTotal — Shipping Boundary Verification
    // =====================================================================

    [Test]
    public void CalculateTotal_QuantityOne_UsesSmallShipping5Point99()
    {
        // Base: a=1, q=1 => subtotal=1, no disc, ship must be 5.99
        // preTax = 6.99, total = 6.99 * 1.0825
        double expected = (1.0 + 5.99) * 1.0825;
        double result = _sut.CalculateTotal(1.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantitySix_UsesMediumShipping9Point99()
    {
        // a=1, q=6 => subtotal=6, no disc, ship must be 9.99
        double expected = (6.0 + 9.99) * 1.0825;
        double result = _sut.CalculateTotal(1.0, 6);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityTwentyOne_UsesLargeShipping14Point99()
    {
        // a=1, q=21 => subtotal=21, no disc, ship must be 14.99
        double expected = (21.0 + 14.99) * 1.0825;
        double result = _sut.CalculateTotal(1.0, 21);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }
}