public interface IOrderCalculatorService
{
    double CalculateTotal(double amount, int quantity);
    bool ProcessRefund(int orderId, string reason);
    string ValidateOrder(string name, string amount, string quantity);
    string GetDiscountTier(double subtotal);
}