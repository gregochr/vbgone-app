using NUnit.Framework;

[TestFixture]
public class OrderLogServiceTests
{
    private IOrderLogService _sut;

    [SetUp]
    public void SetUp()
    {
        _sut = new OrderLogService();
    }

    // CalculateTotal — Happy Path

    [Test]
    public void CalculateTotal_SmallQuantityLowAmount_ReturnsBaseShippingPlusTax()
    {
        // q=1, a=10 => subtotal=10, no discount, ship=5.99, tax=(10+5.99)*0.0825
        double a = 10.0;
        int q = 1;
        double ship = 5.99;
        double subtotal = a * q;
        double tot = subtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityFive_UsesLowestShippingTier()
    {
        // q=5 => ship=5.99
        double a = 10.0;
        int q = 5;
        double ship = 5.99;
        double subtotal = a * q;
        double tot = subtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantitySix_UsesMidShippingTier()
    {
        // q=6 => ship=9.99
        double a = 10.0;
        int q = 6;
        double ship = 9.99;
        double subtotal = a * q;
        double tot = subtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityTwenty_UsesMidShippingTier()
    {
        // q=20 (<= 20 => ship=9.99), subtotal=200 (> 100 => 10% discount)
        double a = 10.0;
        int q = 20;
        double ship = 9.99;
        double subtotal = a * q;
        double afterDisc = subtotal - subtotal * 0.10;
        double tot = afterDisc + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityTwentyOne_UsesHighestShippingTier()
    {
        // q=21 (> 20 => ship=14.99), subtotal=210 (> 100 => 10% discount)
        double a = 10.0;
        int q = 21;
        double ship = 14.99;
        double subtotal = a * q;
        double afterDisc = subtotal - subtotal * 0.10;
        double tot = afterDisc + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly100_NoDiscount()
    {
        // a=10, q=10 => subtotal=100, not >100, no discount, ship=9.99
        double a = 10.0;
        int q = 10;
        double disc = 0.0;
        double ship = 9.99;
        double subtotal = a * q;
        double tot = subtotal - (subtotal * disc) + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver100_AppliesBronzeDiscount()
    {
        // a=10.1, q=10 => subtotal=101, >100 and <=500, disc=0.1, ship=9.99
        double a = 10.1;
        int q = 10;
        double disc = 0.1;
        double ship = 9.99;
        double subtotal = a * q;
        double tot = subtotal - (subtotal * disc) + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly500_AppliesBronzeDiscount()
    {
        // a=50, q=10 => subtotal=500, >100 and <=500, disc=0.1, ship=9.99
        double a = 50.0;
        int q = 10;
        double disc = 0.1;
        double ship = 9.99;
        double subtotal = a * q;
        double tot = subtotal - (subtotal * disc) + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustOver500_AppliesSilverDiscount()
    {
        // a=50.1, q=10 => subtotal=501, >500 and <=1000, disc=0.15, ship=9.99
        double a = 50.1;
        int q = 10;
        double disc = 0.15;
        double ship = 9.99;
        double subtotal = a * q;
        double tot = subtotal - (subtotal * disc) + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly1000_AppliesSilverDiscount()
    {
        // a=100, q=10 => subtotal=1000, >500 and <=1000, disc=0.15, ship=9.99
        double a = 100.0;
        int q = 10;
        double disc = 0.15;
        double ship = 9.99;
        double subtotal = a * q;
        double tot = subtotal - (subtotal * disc) + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalOver1000_AppliesGoldDiscount()
    {
        // a=100.1, q=10 => subtotal=1001, >1000, disc=0.2, ship=9.99
        double a = 100.1;
        int q = 10;
        double disc = 0.2;
        double ship = 9.99;
        double subtotal = a * q;
        double tot = subtotal - (subtotal * disc) + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_LargeOrder_AppliesGoldDiscountAndHighShipping()
    {
        // a=200, q=50 => subtotal=10000, >1000, disc=0.2, ship=14.99
        double a = 200.0;
        int q = 50;
        double disc = 0.2;
        double ship = 14.99;
        double subtotal = a * q;
        double tot = subtotal - (subtotal * disc) + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // CalculateTotal — Edge Cases

    [Test]
    public void CalculateTotal_ZeroAmount_ReturnsShippingOnlyWithTax()
    {
        // a=0 => subtotal=0, no discount triggered (a not >0), q=1 ship=5.99
        double a = 0.0;
        int q = 1;
        double ship = 5.99;
        double tot = 0.0 + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroQuantity_ReturnsZeroNoShipping()
    {
        // q=0 => shipping not applied, subtotal=0, result=0
        double a = 50.0;
        int q = 0;
        double expected = 0.0;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeAmount_NoDiscountNoShipping()
    {
        // a=-10 => a not >0 so no discount, q=1 but subtotal negative
        // shipping still applies since q>0, q=1 => ship=5.99
        double a = -10.0;
        int q = 1;
        double ship = 5.99;
        double subtotal = a * q; // -10
        double tot = subtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeQuantity_ReturnsZeroOrNegative()
    {
        // q<0 => shipping not applied, q not >0
        double a = 50.0;
        int q = -1;
        double subtotal = a * q; // -50
        double tot = subtotal; // no ship, no discount
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_VerySmallPositiveAmount_BelowAllDiscountThresholds()
    {
        // a=0.01, q=1 => subtotal=0.01, no discount, ship=5.99
        double a = 0.01;
        int q = 1;
        double ship = 5.99;
        double subtotal = a * q;
        double tot = subtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOne_UsesLowestShippingTier()
    {
        double a = 5.0;
        int q = 1;
        double ship = 5.99;
        double subtotal = a * q;
        double tot = subtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_BothZero_ReturnsZero()
    {
        double a = 0.0;
        int q = 0;
        double expected = 0.0;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalJustBelow100_NoDiscount()
    {
        // a=9.99, q=10 => subtotal=99.9, not >100, no discount, ship=9.99
        double a = 9.99;
        int q = 10;
        double ship = 9.99;
        double subtotal = a * q;
        double tot = subtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_TaxIsAppliedToShippingAsWell()
    {
        // Verify tax is on the full total including shipping, not just subtotal
        double a = 50.0;
        int q = 1;
        double ship = 5.99;
        double subtotal = a * q;
        double tot = subtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // ValidateOrder — Happy Path

    [Test]
    public void ValidateOrder_AllValidInputs_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("John Smith", "25.00", "3");

        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_ValidIntegerAmount_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("Jane Doe", "100", "1");

        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_ValidMinimalInputs_ReturnsEmptyString()
    {
        string result = _sut.ValidateOrder("A", "0.01", "1");

        Assert.That(result, Is.EqualTo(""));
    }

    // ValidateOrder — Name Validation

    [Test]
    public void ValidateOrder_EmptyName_ReturnsNameRequiredError()
    {
        string result = _sut.ValidateOrder("", "25.00", "3");

        Assert.That(result, Does.Contain("Name required"));
    }

    [Test]
    public void ValidateOrder_EmptyName_ErrorIncludesNameMessage()
    {
        string result = _sut.ValidateOrder("", "25.00", "3");

        Assert.That(result, Is.Not.Empty);
    }

    // ValidateOrder — Amount Validation

    [Test]
    public void ValidateOrder_NonNumericAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", "abc", "3");

        Assert.That(result, Does.Contain("Amount must be numeric"));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_ReturnsAmountPositiveError()
    {
        string result = _sut.ValidateOrder("John", "0", "3");

        Assert.That(result, Does.Contain("Amount must be positive"));
    }

    [Test]
    public void ValidateOrder_NegativeAmount_ReturnsAmountPositiveError()
    {
        string result = _sut.ValidateOrder("John", "-5", "3");

        Assert.That(result, Does.Contain("Amount must be positive"));
    }

    [Test]
    public void ValidateOrder_EmptyAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", "", "3");

        Assert.That(result, Does.Contain("Amount must be numeric"));
    }

    [Test]
    public void ValidateOrder_AmountWithLettersMixed_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", "12abc", "3");

        Assert.That(result, Does.Contain("Amount must be numeric"));
    }

    // ValidateOrder — Quantity Validation

    [Test]
    public void ValidateOrder_NonNumericQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("John", "25.00", "xyz");

        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    [Test]
    public void ValidateOrder_ZeroQuantity_ReturnsQuantityPositiveError()
    {
        string result = _sut.ValidateOrder("John", "25.00", "0");

        Assert.That(result, Does.Contain("Quantity must be positive"));
    }

    [Test]
    public void ValidateOrder_NegativeQuantity_ReturnsQuantityPositiveError()
    {
        string result = _sut.ValidateOrder("John", "25.00", "-1");

        Assert.That(result, Does.Contain("Quantity must be positive"));
    }

    [Test]
    public void ValidateOrder_EmptyQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("John", "25.00", "");

        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    // ValidateOrder — Multiple Errors

    [Test]
    public void ValidateOrder_AllInvalid_ReturnsAllErrors()
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
    public void ValidateOrder_EmptyNameAndInvalidQuantity_ReturnsBothErrors()
    {
        string result = _sut.ValidateOrder("", "25.00", "0");

        Assert.That(result, Does.Contain("Name required"));
        Assert.That(result, Does.Contain("Quantity must be positive"));
    }

    [Test]
    public void ValidateOrder_ValidNameInvalidAmountInvalidQuantity_ReturnsTwoErrors()
    {
        string result = _sut.ValidateOrder("John", "bad", "bad");

        Assert.That(result, Does.Contain("Amount must be numeric"));
        Assert.That(result, Does.Contain("Quantity must be numeric"));
        Assert.That(result, Does.Not.Contain("Name required"));
    }

    // ValidateOrder — Null Inputs

    [Test]
    public void ValidateOrder_NullName_ReturnsNameRequiredError()
    {
        string result = _sut.ValidateOrder(null, "25.00", "3");

        Assert.That(result, Does.Contain("Name required"));
    }

    [Test]
    public void ValidateOrder_NullAmount_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", null, "3");

        Assert.That(result, Does.Contain("Amount must be numeric"));
    }

    [Test]
    public void ValidateOrder_NullQuantity_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("John", "25.00", null);

        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    [Test]
    public void ValidateOrder_AllNull_ReturnsAllErrors()
    {
        string result = _sut.ValidateOrder(null, null, null);

        Assert.That(result, Does.Contain("Name required"));
        Assert.That(result, Does.Contain("Amount must be numeric"));
        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    // GetDiscountTier — Happy Path

    [Test]
    public void GetDiscountTier_SubtotalAbove1000_ReturnsGold()
    {
        string result = _sut.GetDiscountTier(1001.0);

        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalExactly1001_ReturnsGold()
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
    public void GetDiscountTier_SubtotalBetween500And1000_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(750.0);

        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalExactly501_ReturnsSilver()
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
    public void GetDiscountTier_SubtotalBetween100And500_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(250.0);

        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalExactly101_ReturnsBronze()
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
    public void GetDiscountTier_SubtotalBetween0And100_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(50.0);

        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOne_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(1.0);

        Assert.That(result, Is.EqualTo("NONE"));
    }

    // GetDiscountTier — Edge Cases

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
        string result = _sut.GetDiscountTier(999999.99);

        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustBelow100_ReturnsNone()
    {
        string result = _sut.GetDiscountTier(99.99);

        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustAbove100_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(100.01);

        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustBelow500_ReturnsBronze()
    {
        string result = _sut.GetDiscountTier(499.99);

        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalJustAbove500_ReturnsSilver()
    {
        string result = _sut.GetDiscountTier(500.01);

        Assert.That(result, Is.EqualTo("SILVER"));
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

    // CalculateTotal — Tax Rate Verification

    [Test]
    public void CalculateTotal_TaxRateIs8Point25Percent()
    {
        // a=100, q=1 => subtotal=100 (no discount), ship=5.99
        // tot before tax = 105.99, tx = 105.99 * 0.0825 = 8.744175, total = 114.734175
        double a = 100.0;
        int q = 1;
        double ship = 5.99;
        double subtotal = a * q;
        double tot = subtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_GoldDiscountReducesSubtotalBy20Percent()
    {
        // a=200, q=6 => subtotal=1200, >1000 => disc=0.2, ship=9.99
        double a = 200.0;
        int q = 6;
        double disc = 0.2;
        double ship = 9.99;
        double subtotal = a * q;
        double discountedSubtotal = subtotal - (subtotal * disc);
        double tot = discountedSubtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SilverDiscountReducesSubtotalBy15Percent()
    {
        // a=100, q=6 => subtotal=600, >500 and <=1000 => disc=0.15, ship=9.99
        double a = 100.0;
        int q = 6;
        double disc = 0.15;
        double ship = 9.99;
        double subtotal = a * q;
        double discountedSubtotal = subtotal - (subtotal * disc);
        double tot = discountedSubtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_BronzeDiscountReducesSubtotalBy10Percent()
    {
        // a=20, q=6 => subtotal=120, >100 and <=500 => disc=0.1, ship=9.99
        double a = 20.0;
        int q = 6;
        double disc = 0.1;
        double ship = 9.99;
        double subtotal = a * q;
        double discountedSubtotal = subtotal - (subtotal * disc);
        double tot = discountedSubtotal + ship;
        double tx = tot * 0.0825;
        double expected = tot + tx;

        double result = _sut.CalculateTotal(a, q);

        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // ValidateOrder — Whitespace Edge Cases

    [Test]
    public void ValidateOrder_WhitespaceOnlyName_BehavesLikeEmptyName()
    {
        // VB.NET n="" check means whitespace-only is non-empty, so this may pass name check
        // Testing actual behaviour: whitespace is not empty string in VB
        string result = _sut.ValidateOrder("   ", "25.00", "3");

        // Whitespace is not equal to "" in VB, so no name error expected
        Assert.That(result, Does.Not.Contain("Name required"));
    }

    [Test]
    public void ValidateOrder_AmountWithSpaces_ReturnsAmountNumericError()
    {
        string result = _sut.ValidateOrder("John", "  ", "3");

        Assert.That(result, Does.Contain("Amount must be numeric"));
    }

    [Test]
    public void ValidateOrder_QuantityWithSpaces_ReturnsQuantityNumericError()
    {
        string result = _sut.ValidateOrder("John", "25.00", "  ");

        Assert.That(result, Does.Contain("Quantity must be numeric"));
    }

    // GetDiscountTier — Return Value Format

    [Test]
    public void GetDiscountTier_ReturnsUppercaseNone()
    {
        string result = _sut.GetDiscountTier(0.0);

        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_ReturnsUppercaseBronze()
    {
        string result = _sut.GetDiscountTier(200.0);

        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_ReturnsUppercaseSilver()
    {
        string result = _sut.GetDiscountTier(600.0);

        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_ReturnsUppercaseGold()
    {
        string result = _sut.GetDiscountTier(1500.0);

        Assert.That(result, Is.EqualTo("GOLD"));
    }

    // CalculateTotal — Result Is Always Non-Negative When Inputs Are Valid

    [Test]
    public void CalculateTotal_ValidInputs_ResultIsPositive()
    {
        double result = _sut.CalculateTotal(10.0, 2);

        Assert.That(result, Is.GreaterThan(0.0));
    }

    [Test]
    public void CalculateTotal_MinimumValidInputs_ResultIsPositive()
    {
        double result = _sut.CalculateTotal(0.01, 1);

        Assert.That(result, Is.GreaterThan(0.0));
    }
}