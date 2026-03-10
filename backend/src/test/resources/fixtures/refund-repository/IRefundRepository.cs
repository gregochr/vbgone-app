public interface IRefundRepository
{
    bool ProcessRefund(int orderId, string reason);
    double CalculateTotal(double amount, int quantity);
    string ValidateOrder(string name, string amount, string quantity);
    string GetDiscountTier(double subtotal);
}