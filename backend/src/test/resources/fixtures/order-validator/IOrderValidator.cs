public interface IOrderValidator
{
    string ValidateOrder(string name, string amount, string quantity);
    double CalculateTotal(double amount, int quantity);
    bool ProcessRefund(int orderId, string reason);
    string GetDiscountTier(double subtotal);
}