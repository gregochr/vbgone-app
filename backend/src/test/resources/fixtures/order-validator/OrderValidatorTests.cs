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

    // ValidateOrder — Name validation

    [Test]
    public void ValidateOrder_EmptyName_ReturnsNameRequiredError()
    {
        var result = _sut.ValidateOrder("", "10.00", "2");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_NullName_ReturnsNameRequiredError()
    {
        var result = _sut.ValidateOrder(null, "10.00", "2");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_WhitespaceName_ReturnsNameRequiredError()
    {
        var result = _sut.ValidateOrder("   ", "10.00", "2");
        Assert.That(result, Does.Contain("Name required."));
    }

    [Test]
    public void ValidateOrder_ValidName_DoesNotReturnNameError()
    {
        var result = _sut.ValidateOrder("John Smith", "10.00", "2");
        Assert.That(result, Does.Not.Contain("Name required."));
    }

    // ValidateOrder — Amount validation

    [Test]
    public void ValidateOrder_NonNumericAmount_ReturnsAmountNumericError()
    {
        var result = _sut.ValidateOrder("John", "abc", "2");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyAmount_ReturnsAmountNumericError()
    {
        var result = _sut.ValidateOrder("John", "", "2");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_NullAmount_ReturnsAmountNumericError()
    {
        var result = _sut.ValidateOrder("John", null, "2");
        Assert.That(result, Does.Contain("Amount must be numeric."));
    }

    [Test]
    public void ValidateOrder_ZeroAmount_ReturnsAmountPositiveError()
    {
        var result = _sut.ValidateOrder("John", "0", "2");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_NegativeAmount_ReturnsAmountPositiveError()
    {
        var result = _sut.ValidateOrder("John", "-5.00", "2");
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_ValidAmount_DoesNotReturnAmountError()
    {
        var result = _sut.ValidateOrder("John", "10.00", "2");
        Assert.That(result, Does.Not.Contain("Amount must be numeric."));
        Assert.That(result, Does.Not.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_AmountOfOneDecimalPlace_IsValid()
    {
        var result = _sut.ValidateOrder("John", "9.9", "2");
        Assert.That(result, Does.Not.Contain("Amount must be numeric."));
        Assert.That(result, Does.Not.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_VerySmallPositiveAmount_IsValid()
    {
        var result = _sut.ValidateOrder("John", "0.01", "2");
        Assert.That(result, Does.Not.Contain("Amount must be positive."));
    }

    // ValidateOrder — Quantity validation

    [Test]
    public void ValidateOrder_NonNumericQuantity_ReturnsQuantityNumericError()
    {
        var result = _sut.ValidateOrder("John", "10.00", "xyz");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_EmptyQuantity_ReturnsQuantityNumericError()
    {
        var result = _sut.ValidateOrder("John", "10.00", "");
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_NullQuantity_ReturnsQuantityNumericError()
    {
        var result = _sut.ValidateOrder("John", "10.00", null);
        Assert.That(result, Does.Contain("Quantity must be numeric."));
    }

    [Test]
    public void ValidateOrder_ZeroQuantity_ReturnsQuantityPositiveError()
    {
        var result = _sut.ValidateOrder("John", "10.00", "0");
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_NegativeQuantity_ReturnsQuantityPositiveError()
    {
        var result = _sut.ValidateOrder("John", "10.00", "-1");
        Assert.That(result, Does.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_ValidQuantity_DoesNotReturnQuantityError()
    {
        var result = _sut.ValidateOrder("John", "10.00", "2");
        Assert.That(result, Does.Not.Contain("Quantity must be numeric."));
        Assert.That(result, Does.Not.Contain("Quantity must be positive."));
    }

    [Test]
    public void ValidateOrder_QuantityOfOne_IsValid()
    {
        var result = _sut.ValidateOrder("John", "10.00", "1");
        Assert.That(result, Does.Not.Contain("Quantity must be positive."));
    }

    // ValidateOrder — Multiple errors

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
        var result = _sut.ValidateOrder("", "0", "2");
        Assert.That(result, Does.Contain("Name required."));
        Assert.That(result, Does.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_AllValid_ReturnsEmptyString()
    {
        var result = _sut.ValidateOrder("John", "10.00", "2");
        Assert.That(result, Is.EqualTo(""));
    }

    [Test]
    public void ValidateOrder_NonNumericAmountDoesNotAlsoReturnPositiveError()
    {
        var result = _sut.ValidateOrder("John", "abc", "2");
        Assert.That(result, Does.Not.Contain("Amount must be positive."));
    }

    [Test]
    public void ValidateOrder_NonNumericQuantityDoesNotAlsoReturnPositiveError()
    {
        var result = _sut.ValidateOrder("John", "10.00", "abc");
        Assert.That(result, Does.Not.Contain("Quantity must be positive."));
    }

    // GetDiscountTier — boundary and happy path

    [Test]
    public void GetDiscountTier_SubtotalOfZero_ReturnsNone()
    {
        var result = _sut.GetDiscountTier(0.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_NegativeSubtotal_ReturnsNone()
    {
        var result = _sut.GetDiscountTier(-100.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOfExactlyOne_ReturnsNone()
    {
        var result = _sut.GetDiscountTier(1.0);
        Assert.That(result, Is.EqualTo("NONE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf100_ReturnsNone()
    {
        var result = _sut.GetDiscountTier(100.0);
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
        var result = _sut.GetDiscountTier(250.0);
        Assert.That(result, Is.EqualTo("BRONZE"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf500_ReturnsBronze()
    {
        var result = _sut.GetDiscountTier(500.0);
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
        var result = _sut.GetDiscountTier(750.0);
        Assert.That(result, Is.EqualTo("SILVER"));
    }

    [Test]
    public void GetDiscountTier_SubtotalOf1000_ReturnsSilver()
    {
        var result = _sut.GetDiscountTier(1000.0);
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
        var result = _sut.GetDiscountTier(5000.0);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    [Test]
    public void GetDiscountTier_VeryLargeSubtotal_ReturnsGold()
    {
        var result = _sut.GetDiscountTier(double.MaxValue);
        Assert.That(result, Is.EqualTo("GOLD"));
    }

    // CalculateTotal — discount tiers

    [Test]
    public void CalculateTotal_ZeroAmountZeroQuantity_ReturnsZero()
    {
        var result = _sut.CalculateTotal(0.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroAmount_ReturnsOnlyShippingAndTax()
    {
        // q=1, ship=5.99, no discount since a=0, tax=5.99*0.0825
        var expectedShip = 5.99;
        var expectedTax = expectedShip * 0.0825;
        var expected = expectedShip + expectedTax;
        var result = _sut.CalculateTotal(0.0, 1);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_ZeroQuantity_ReturnsZero()
    {
        var result = _sut.CalculateTotal(50.0, 0);
        Assert.That(result, Is.EqualTo(0.0).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalAtOrBelow100_NoDiscount_SmallShipping()
    {
        // a=10, q=5 => subtotal=50, disc=0, ship=5.99
        // tot = 50 + 5.99 = 55.99, tax = 55.99*0.0825
        double a = 10.0, subtotal = 50.0, ship = 5.99;
        int q = 5;
        double tot = subtotal + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly100_NoDiscount()
    {
        // a=20, q=5 => subtotal=100, disc=0, ship=5.99
        double a = 20.0, subtotal = 100.0, ship = 5.99;
        int q = 5;
        double tot = subtotal + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalAbove100UpTo500_BronzeDiscount10Percent()
    {
        // a=25, q=6 => subtotal=150, disc=0.10, ship=9.99
        double a = 25.0, subtotal = 150.0, disc = 0.10, ship = 9.99;
        int q = 6;
        double tot = subtotal - (subtotal * disc) + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly500_BronzeDiscount10Percent()
    {
        // a=25, q=20 => subtotal=500, disc=0.10, ship=9.99
        double a = 25.0, subtotal = 500.0, disc = 0.10, ship = 9.99;
        int q = 20;
        double tot = subtotal - (subtotal * disc) + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalAbove500UpTo1000_SilverDiscount15Percent()
    {
        // a=30, q=20 => subtotal=600, disc=0.15, ship=9.99
        double a = 30.0, subtotal = 600.0, disc = 0.15, ship = 9.99;
        int q = 20;
        double tot = subtotal - (subtotal * disc) + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalExactly1000_SilverDiscount15Percent()
    {
        // a=50, q=20 => subtotal=1000, disc=0.15, ship=9.99
        double a = 50.0, subtotal = 1000.0, disc = 0.15, ship = 9.99;
        int q = 20;
        double tot = subtotal - (subtotal * disc) + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_SubtotalAbove1000_GoldDiscount20Percent()
    {
        // a=55, q=21 => subtotal=1155, disc=0.20, ship=14.99
        double a = 55.0, subtotal = 1155.0, disc = 0.20, ship = 14.99;
        int q = 21;
        double tot = subtotal - (subtotal * disc) + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // CalculateTotal — shipping tiers

    [Test]
    public void CalculateTotal_QuantityOf1_UsesSmallShipping5_99()
    {
        // a=1, q=1, subtotal=1 (no discount), ship=5.99
        double a = 1.0, subtotal = 1.0, ship = 5.99;
        int q = 1;
        double tot = subtotal + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOf5_UsesSmallShipping5_99()
    {
        // a=1, q=5, subtotal=5 (no discount), ship=5.99
        double a = 1.0, subtotal = 5.0, ship = 5.99;
        int q = 5;
        double tot = subtotal + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOf6_UsesMediumShipping9_99()
    {
        // a=1, q=6, subtotal=6 (no discount), ship=9.99
        double a = 1.0, subtotal = 6.0, ship = 9.99;
        int q = 6;
        double tot = subtotal + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOf20_UsesMediumShipping9_99()
    {
        // a=1, q=20, subtotal=20 (no discount), ship=9.99
        double a = 1.0, subtotal = 20.0, ship = 9.99;
        int q = 20;
        double tot = subtotal + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOf21_UsesLargeShipping14_99()
    {
        // a=1, q=21, subtotal=21 (no discount), ship=14.99
        double a = 1.0, subtotal = 21.0, ship = 14.99;
        int q = 21;
        double tot = subtotal + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_QuantityOf100_UsesLargeShipping14_99()
    {
        // a=1, q=100, subtotal=100 (no discount boundary), ship=14.99
        double a = 1.0, subtotal = 100.0, ship = 14.99;
        int q = 100;
        double tot = subtotal + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // CalculateTotal — negative inputs

    [Test]
    public void CalculateTotal_NegativeAmount_NoDiscountApplied()
    {
        // a<0 means outer If a>0 is false, disc stays 0, ship still calculated
        // a=-10, q=1, ship=5.99, subtotal=-10, tot=-10+5.99=-4.01, tax=-4.01*0.0825
        double a = -10.0;
        int q = 1;
        double ship = 5.99;
        double subtotal = a * q;
        double tot = subtotal + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_NegativeQuantity_ReturnsZeroShippingAndNoDiscount()
    {
        // q<=0 means no shipping, q<=0 outer check means no discount
        // a=10, q=-1 => subtotal=-10, ship=0, disc=0, tot=-10, tax=-10*0.0825
        double a = 10.0;
        int q = -1;
        double subtotal = a * q;
        double ship = 0.0;
        double tot = subtotal + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    // CalculateTotal — tax is always applied

    [Test]
    public void CalculateTotal_ResultIncludesTaxAt8Point25Percent()
    {
        // a=10, q=1, subtotal=10, ship=5.99, disc=0
        // tot=15.99, tax=15.99*0.0825=1.31918, final=17.30918
        double a = 10.0;
        int q = 1;
        double subtotal = 10.0, ship = 5.99;
        double tot = subtotal + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }

    [Test]
    public void CalculateTotal_LargeOrderGoldDiscountAndLargeShipping()
    {
        // a=100, q=50 => subtotal=5000, disc=0.20, ship=14.99
        double a = 100.0, subtotal = 5000.0, disc = 0.20, ship = 14.99;
        int q = 50;
        double tot = subtotal - (subtotal * disc) + ship;
        double tax = tot * 0.0825;
        double expected = tot + tax;
        var result = _sut.CalculateTotal(a, q);
        Assert.That(result, Is.EqualTo(expected).Within(0.0001));
    }
}