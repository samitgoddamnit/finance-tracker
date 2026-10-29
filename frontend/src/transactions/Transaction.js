import "./Transaction.css";
import formatCurrency from "./formatCurrency";

export default function Transaction({
  transaction,
  isSelected,
  onSelect,
  categoryLabel,
}) {
  const uncategorised =
    !transaction.category || transaction.category === "UNCATEGORISED";

  return (
    <li className={`transaction${isSelected ? " transaction-selected" : ""}`}>
      <button
        type="button"
        className="transaction-button"
        onClick={() => onSelect(transaction)}
        aria-pressed={isSelected}
      >
        <span className="transaction-main">
          <strong>{transaction.merchant || "Unknown merchant"}</strong>
          <small>{transaction.description || "No description"}</small>
          <span
            className={`transaction-category${uncategorised ? " transaction-category-none" : ""}`}
          >
            {categoryLabel || "Uncategorised"}
          </span>
        </span>
        <span className="transaction-summary">
          <strong>
            {formatCurrency(transaction.amount, transaction.currency)}
          </strong>
          <small>{transaction.time || transaction.source || "Payment"}</small>
        </span>
      </button>
    </li>
  );
}
