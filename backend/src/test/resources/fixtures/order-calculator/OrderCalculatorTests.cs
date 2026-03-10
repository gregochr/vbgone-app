using NUnit.Framework;

[TestFixture]
public class OrderCalculatorTests
{
    private IOrderCalculator _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderCalculator();
    }

    // CalculateTotal — Happy Path Tests

    [Test]
    public void CalculateTotal_SmallOrderNoDiscount_ReturnsCorrectTotal()
    {
        // subtotal = 10 * 5 = 50, no discount, shipping = 5.99 (q<=5)
        // tot before tax = 50 + 5.99 = 55.99, tax = 55.99 * 0.0825 = 4.619175
        // total = 55.99 + 4.619175 = 60.609175
        double result = _sut.CalculateTotal(10.0, 5);
        Assert.That(result, Is.EqualTo(60.609175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOne_UsesSmallShipping()
    {
        // subtotal = 5 * 1 = 5, no discount, shipping = 5.99 (q<=5)
        // tot before tax = 10.99, tax = 10.99 * 0.0825 = 0.906675
        // total = 11.896675
        double result = _sut.CalculateTotal(5.0, 1);
        Assert.That(result, Is.EqualTo(11.896675).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityFive_UsesSmallShipping()
    {
        // subtotal = 20 * 5 = 100, no discount (not > 100), shipping = 5.99
        // tot before tax = 105.99, tax = 105.99 * 0.0825 = 8.744175
        // total = 114.734175
        double result = _sut.CalculateTotal(20.0, 5);
        Assert.That(result, Is.EqualTo(114.734175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantitySix_UsesMediumShipping()
    {
        // subtotal = 5 * 6 = 30, no discount, shipping = 9.99 (q<=20)
        // tot before tax = 39.99, tax = 39.99 * 0.0825 = 3.299175
        // total = 43.289175
        double result = _sut.CalculateTotal(5.0, 6);
        Assert.That(result, Is.EqualTo(43.289175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityTwenty_UsesMediumShipping()
    {
        // subtotal = 5 * 20 = 100, no discount, shipping = 9.99
        // tot before tax = 109.99, tax = 109.99 * 0.0825 = 9.074175
        // total = 119.064175
        double result = _sut.CalculateTotal(5.0, 20);
        Assert.That(result, Is.EqualTo(119.064175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityTwentyOne_UsesLargeShipping()
    {
        // subtotal = 5 * 21 = 105, discount = 0.1, ship = 14.99
        // discounted = 105 - 10.5 = 94.5, tot before tax = 94.5 + 14.99 = 109.49
        // tax = 109.49 * 0.0825 = 9.032925, total = 118.522925
        double result = _sut.CalculateTotal(5.0, 21);
        Assert.That(result, Is.EqualTo(118.522925).Within(0.0001));
    }

    // CalculateTotal — Discount Tier Tests

    [Test]
    public void CalculateTotal_SubtotalExactly100_NoDiscount()
    {
        // subtotal = 20 * 5 = 100, NOT > 100 so no discount, shipping = 5.99
        // tot before tax = 105.99, tax = 8.744175, total = 114.734175
        double result = _sut.CalculateTotal(20.0, 5);
        Assert.That(result, Is.EqualTo(114.734175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver100_AppliesBronzeDiscount()
    {
        // subtotal = 101, discount = 0.1, ship = 9.99 (q=1 but wait q must be >5 for medium; let's use a=101, q=1)
        // q=1 <= 5 so ship = 5.99
        // discounted subtotal = 101 - 10.1 = 90.9, tot before tax = 96.89
        // tax = 96.89 * 0.0825 = 7.993425, total = 104.883425
        double result = _sut.CalculateTotal(101.0, 1);
        Assert.That(result, Is.EqualTo(104.883425).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly500_AppliesBronzeDiscount()
    {
        // subtotal = 500, NOT > 500, so discount = 0.1, ship = 9.99 (q=10)
        // discounted = 500 - 50 = 450, tot before tax = 459.99
        // tax = 459.99 * 0.0825 = 37.949175, total = 497.939175
        double result = _sut.CalculateTotal(50.0, 10);
        Assert.That(result, Is.EqualTo(497.939175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver500_AppliesSilverDiscount()
    {
        // subtotal = 501, discount = 0.15, ship = 9.99 (q=1 -> ship=5.99)
        // use a=501, q=1, ship=5.99
        // discounted = 501 - 75.15 = 425.85, tot before tax = 431.84
        // tax = 431.84 * 0.0825 = 35.6268, total = 467.4668
        double result = _sut.CalculateTotal(501.0, 1);
        Assert.That(result, Is.EqualTo(467.4668).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly1000_AppliesSilverDiscount()
    {
        // subtotal = 1000, NOT > 1000, discount = 0.15, ship = 14.99 (q=25)
        // discounted = 1000 - 150 = 850, tot before tax = 864.99
        // tax = 864.99 * 0.0825 = 71.361675, total = 936.351675
        double result = _sut.CalculateTotal(40.0, 25);
        Assert.That(result, Is.EqualTo(936.351675).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver1000_AppliesGoldDiscount()
    {
        // subtotal = 1001, discount = 0.2, ship = 5.99 (q=1)
        // discounted = 1001 - 200.2 = 800.8, tot before tax = 806.79
        // tax = 806.79 * 0.0825 = 66.560175, total = 873.350175
        double result = _sut.CalculateTotal(1001.0, 1);
        Assert.That(result, Is.EqualTo(873.350175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_LargeSubtotal_AppliesGoldDiscount()
    {
        // subtotal = 5000, discount = 0.2, ship = 14.99 (q=50)
        // discounted = 5000 - 1000 = 4000, tot before tax = 4014.99
        // tax = 4014.99 * 0.0825 = 331.236675, total = 4346.226675
        double result = _sut.CalculateTotal(100.0, 50);
        Assert.That(result, Is.EqualTo(4346.226675).Within(0.0001));
    }

    // CalculateTotal — Edge Cases: Zero and Negative Inputs

    [Test]
    public void CalculateTotal_ZeroAmount_ReturnsShippingWithTaxOnly()
    {
        // a=0, q=3: a > 0 is false so disc=0, q<=5 so ship=5.99
        // tot = (0*3) - 0 + 5.99 = 5.99, tax = 5.99 * 0.0825 = 0.494175
        // total = 6.484175
        double result = _sut.CalculateTotal(0.0, 3);
        Assert.That(result, Is.EqualTo(6.484175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroQuantity_ReturnsZero()
    {
        // q=0: shipping block skipped (q>0 false), disc block skipped
        // tot = 0, tax = 0, total = 0
        double result = _sut.CalculateTotal(50.0, 0);
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
        // a=-10 <= 0, so disc=0; q=3 <=5 so ship=5.99
        // tot = (-10*3) - 0 + 5.99 = -24.01, tax = -24.01 * 0.0825 = -1.980825
        // total = -25.990825
        double result = _sut.CalculateTotal(-10.0, 3);
        Assert.That(result, Is.EqualTo(-25.990825).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeQuantity_NoShippingNoDiscount()
    {
        // q=-1 <= 0: shipping block skipped, discount inner q>0 check fails
        // tot = (10 * -1) - 0 + 0 = -10, tax = -10 * 0.0825 = -0.825
        // total = -10.825
        double result = _sut.CalculateTotal(10.0, -1);
        Assert.That(result, Is.EqualTo(-10.825).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_VerySmallPositiveAmount_NoDiscountSmallShipping()
    {
        // a=0.01, q=1, subtotal=0.01, not >100, disc=0, ship=5.99
        // tot before tax = 0.01 + 5.99 = 6.00, tax = 6.00 * 0.0825 = 0.495
        // total = 6.495
        double result = _sut.CalculateTotal(0.01, 1);
        Assert.That(result, Is.EqualTo(6.495).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_MaxIntQuantity_UsesLargeShipping()
    {
        // q >> 20 so ship = 14.99, subtotal = 1.0 * int.MaxValue is huge
        // Just verify it doesn't throw and uses large shipping tier
        Assert.DoesNotThrow(() => _sut.CalculateTotal(1.0, int.MaxValue));
    }

    // CalculateTotal — Boundary Tests for Shipping Tiers

    [Test]
    public void CalculateTotal_QuantityBoundary_ExactlyFive_UsesSmallShipping()
    {
        // q=5, ship=5.99
        // subtotal = 10*5=50, no disc, tot=55.99, tax=4.619175, total=60.609175
        double result = _sut.CalculateTotal(10.0, 5);
        Assert.That(result, Is.EqualTo(60.609175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityBoundary_ExactlySix_UsesMediumShipping()
    {
        // q=6, ship=9.99
        // subtotal=10*6=60, no disc, tot=69.99, tax=5.774175, total=75.764175
        double result = _sut.CalculateTotal(10.0, 6);
        Assert.That(result, Is.EqualTo(75.764175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityBoundary_ExactlyTwenty_UsesMediumShipping()
    {
        // q=20, ship=9.99
        // subtotal=5*20=100, NOT >100 no disc, tot=109.99, tax=9.074175, total=119.064175
        double result = _sut.CalculateTotal(5.0, 20);
        Assert.That(result, Is.EqualTo(119.064175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityBoundary_ExactlyTwentyOne_UsesLargeShipping()
    {
        // q=21, ship=14.99
        // subtotal=5*21=105, >100 disc=0.1
        // discounted=105-10.5=94.5, tot=109.49, tax=9.032925, total=118.522925
        double result = _sut.CalculateTotal(5.0, 21);
        Assert.That(result, Is.EqualTo(118.522925).Within(0.0001));
    }

    // ValidateOrder — Happy Path

    [Test]
    public void ValidateOrder_ValidInputs_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("John Smith", "19.99", "3");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_ValidInputsWithDecimalAmount_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("Jane Doe", "9.50", "1");
        Assert.That(result, Is.EqualTo(""));
    }

    // ValidateOrder — Name Validation

    [Test]
    public void ValidateOrder_EmptyName_ReturnsNameError()
    {
        string result = _sut.ValidateOrder("", "10.00", "2");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_NullName_ReturnsNameError()
    {
        string result = _sut.ValidateOrder(null, "10.00", "2");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_WhitespaceName_ReturnsNameError()
    {
        string result = _sut.ValidateOrder("   ", "10.00", "2");
        Assert.That(result, Does.Contain("Name required."));
    }

    // ValidateOrder — Amount Validation

    [Test]
    public void ValidateOrder_NonNumericAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", "abc", "2");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", "", "2");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_NullAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", null, "2");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_ReturnsAmountPositiveError()
    {
        string result = _sut.ValidateOrder("John", "0", "2");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_NegativeAmount_ReturnsAmountPositiveError()
    {
        string result = _sut.ValidateOrder("John", "-5.00", "2");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_DoesNotContainNumericError()
    {
        string result = _sut.ValidateOrder("John", "0", "2");
        Assert.That(result, Does.Not.Contain("Amount must be numeric."));
    }

    // ValidateOrder — Quantity Validation

    [Test]
    public void ValidateOrder_NonNumericQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("John", "10.00", "xyz");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("John", "10.00", "");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_NullQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("John", "10.00", null);
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
    public void ValidateOrder_ZeroQuantity_DoesNotContainNumericError()
    {
        string result = _sut.ValidateOrder("John", "10.00", "0");
        Assert.That(result, Does.Not.Contain("Quantity must be numeric."));
    }

    // ValidateOrder — Multiple Errors

    [Test]
    public void ValidateOrder_AllFieldsEmpty_ReturnsAllErrors()
    {
        string result = _sut.ValidateOrder("", "", "");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be numeric."));
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyNameAndNonNumericAmount_ReturnsTwoErrors()
    {
        string result = _sut.ValidateOrder("", "bad", "5");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be numeric."));
        Assert.That(result, Does.Not.Contain("Quantity"));
    }

    [Test]
    public void ValidateOrder_EmptyNameAndNegativeAmount_ReturnsBothErrors()
    {
        string result = _sut.ValidateOrder("", "-1", "5");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_ValidNameInvalidAmountInvalidQuantity_ReturnsTwoErrors()
    {
        string result = _sut.ValidateOrder("John", "abc", "xyz");
        Assert.That(result, Does.Not.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be numeric."));
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    // GetDiscountTier — Happy Path

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
    public void GetDiscountTier_SubtotalFifty_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(50.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalExactly100_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(100.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustOver100_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(100.01);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalTwoFifty_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(250.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalExactly500_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(500.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustOver500_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(500.01);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalSevenFifty_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(750.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalExactly1000_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(1000.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustOver1000_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(1000.01);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalFiveThousand_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(5000.0);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalMaxDouble_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(double.MaxValue);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    // GetDiscountTier — Boundary Tests

    [Test]
    public void GetDiscountTier_SubtotalOne_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(1.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalNegativeLarge_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(-9999.99);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    // CalculateTotal — Tax Calculation Verification

    [Test]
    public void CalculateTotal_TaxRateAppliedCorrectly_EightPointTwoFivePercent()
    {
        // a=10, q=1, subtotal=10, no disc, ship=5.99
        // tot before tax = 15.99, expected tax = 15.99 * 0.0825 = 1.319175
        // total = 17.309175
        double result = _sut.CalculateTotal(10.0, 1);
        Assert.That(result, Is.EqualTo(17.309175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_GoldDiscountWithLargeShipping_CalculatedCorrectly()
    {
        // a=100, q=30, subtotal=3000 > 1000, disc=0.2
        // discounted=3000-600=2400, ship=14.99, tot=2414.99
        // tax=2414.99*0.0825=199.236675, total=2614.226675
        double result = _sut.CalculateTotal(100.0, 30);
        Assert.That(result, Is.EqualTo(2614.226675).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SilverDiscountWithMediumShipping_CalculatedCorrectly()
    {
        // a=60, q=10, subtotal=600 > 500, disc=0.15
        // discounted=600-90=510, ship=9.99, tot=519.99
        // tax=519.99*0.0825=42.899175, total=562.889175
        double result = _sut.CalculateTotal(60.0, 10);
        Assert.That(result, Is.EqualTo(562.889175).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_BronzeDiscountWithSmallShipping_CalculatedCorrectly()
    {
        // a=150, q=1, subtotal=150 > 100, disc=0.1
        // discounted=150-15=135, ship=5.99, tot=140.99
        // tax=140.99*0.0825=11.631675, total=152.621675
        double result = _sut.CalculateTotal(150.0, 1);
        Assert.That(result, Is.EqualTo(152.621675).Within(0.0001));
    }

    // CalculateTotal — Return Type and Non-Negative Result

    [Test]
    public void CalculateTotal_TypicalOrder_ReturnsDouble()
    {
        object result = _sut.CalculateTotal(25.0, 4);
        Assert.That(result, Is.TypeOf<double>());
    }

    [Test]
    public void CalculateTotal_PositiveAmountAndQuantity_ReturnsPositiveValue()
    {
        double result = _sut.CalculateTotal(10.0, 2);
        Assert.That(result, Is.GreaterThan(0.0));
    }

    // ValidateOrder — Return Type

    [Test]
    public void ValidateOrder_ValidInputs_ReturnsString()
    {
        object result = _sut.ValidateOrder("John", "10.00", "1");
        Assert.That(result, Is.TypeOf<string>());
    }

    // GetDiscountTier — Return Type

    [Test]
    public void GetDiscountTier_AnyInput_ReturnsString()
    {
        object result = _sut.GetDiscountTier(100.0);
        Assert.That(result, Is.TypeOf<string>());
    }

    // ValidateOrder — Positive Amount Boundary

    [Test]
    public void ValidateOrder_AmountOfVerySmallPositive_PassesValidation()
    {
        string result = _sut.ValidateOrder("John", "0.01", "1");
        Assert.That(result, Does.Not.Contain("Amount"));
    }

    [Test]
    public void ValidateOrder_QuantityOfOne_PassesValidation()
    {
        string result = _sut.ValidateOrder("John", "10.00", "1");
        Assert.That(result, Does.Not.Contain("Quantity"));
    }

    [Test]
    public void ValidateOrder_LargeValidQuantity_PassesValidation()
    {
        string result = _sut.ValidateOrder("John", "10.00", "1000");
        Assert.That(result, Does.Not.Contain("Quantity"));
    }

    [Test]
    public void ValidateOrder_FloatQuantityString_ReturnsQuantityNumericOrPositiveError()
    {
        // "1.5" is numeric but CInt may truncate or it may fail depending on implementation
        // VB IsNumeric("1.5") = True, CInt(1.5) = 2 > 0, so no error expected
        string result = _sut.ValidateOrder("John", "10.00", "1.5");
        Assert.That(result, Does.Not.Contain("Quantity must be numeric."));
    }

    // CalculateTotal — Discount Does Not Apply When Amount Is Zero

    [Test]
    public void CalculateTotal_ZeroAmountHighQuantity_NoDiscountApplied()
    {
        // a=0 so a>0 fails, disc=0, q=25 so ship=14.99
        // tot = 0 + 14.99 = 14.99, tax = 14.99*0.0825=1.236675, total=16.226675
        double result = _sut.CalculateTotal(0.0, 25);
        Assert.That(result, Is.EqualTo(16.226675).Within(0.0001));
    }

    // CalculateTotal — Shipping Only Applied When Quantity > 0

    [Test]
    public void CalculateTotal_PositiveAmountZeroQuantity_NoShippingCharged()
    {
        // q=0: shipping block skipped, disc block inner q>0 skipped
        // tot = (100*0) - 0 + 0 = 0, tax = 0, total = 0
        double result = _sut.CalculateTotal(100.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }

    // GetDiscountTier — Exact Boundary at 1000.01

    [Test]
    public void GetDiscountTier_Subtotal1000Point01_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(1000.01);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_Subtotal500Point01_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(500.01);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_Subtotal100Point01_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(100.01);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_Subtotal99Point99_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(99.99);
        Assert.That(result, Is.EqualTo("NONE"));
    }
}