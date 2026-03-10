public interface IOrderNotificationService
{
    double CalculateTotal(double amount, int quantity);
    bool ProcessRefund(int orderId, string reason);
    string ValidateOrder(string name, string amount, string quantity);
    string GetDiscountTier(double subtotal);
}