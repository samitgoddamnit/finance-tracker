import { useEffect, useState } from "react";
import Transaction from "./Transaction";
import "./TransactionList.css";

export default function TransactionList({
  selectedTransaction,
  onSelect,
  refreshKey,
  categoryLabels,
}) {
  const [transactions, setTransactions] = useState([]);

  useEffect(() => {
    fetch("/transactions")
      .then((data) => data.json())
      .then((data) => {
        setTransactions(data);
      });
  }, [refreshKey]);

  return (
    <ul className="transaction-list" aria-label="Transactions">
      {transactions.map((transaction) => (
        <Transaction
          key={transaction.transactionID}
          transaction={transaction}
          isSelected={
            selectedTransaction?.transactionID === transaction.transactionID
          }
          onSelect={onSelect}
          categoryLabel={categoryLabels[transaction.category]}
        />
      ))}
    </ul>
  );
}
