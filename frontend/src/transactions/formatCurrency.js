export default function formatCurrency(amount, currency) {
  const numericAmount = Number(amount);
  const formattedAmount = Number.isFinite(numericAmount)
    ? numericAmount.toFixed(2)
    : "0.00";

  return `${currency || ""} ${formattedAmount}`.trim();
}
