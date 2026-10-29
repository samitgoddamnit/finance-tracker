import { useEffect, useState } from "react";
import formatCurrency from "./formatCurrency";
import "./NeedsCategory.css";

// Same grouping the backend uses for merchant memory: cleaned merchant name, and
// whether money came in or went out. Choosing a category for one transaction in a
// group makes the backend apply it to the rest, so each group needs one choice.
function merchantKey(transaction) {
  const name = transaction.cleanMerchant || transaction.merchant || "Unknown";
  return `${name}|${Number(transaction.amount) > 0 ? "IN" : "OUT"}`;
}

function formatDate(time) {
  return time
    ? new Date(time).toLocaleDateString("en-GB", {
        day: "numeric",
        month: "short",
        year: "numeric",
      })
    : "";
}

function groupByMerchant(transactions) {
  const groups = new Map();
  for (const transaction of transactions) {
    const key = merchantKey(transaction);
    if (!groups.has(key)) {
      groups.set(key, []);
    }
    groups.get(key).push(transaction);
  }
  // biggest amounts first: those matter most for the dashboard
  return [...groups.entries()]
    .map(([key, items]) => ({
      key,
      items,
      total: items.reduce((sum, t) => sum + Number(t.amount), 0),
    }))
    .sort((a, b) => Math.abs(b.total) - Math.abs(a.total));
}

export default function NeedsCategory({ categories, refreshKey, onCategorised }) {
  const [transactions, setTransactions] = useState([]);
  const [saving, setSaving] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetch("/transactions")
      .then((response) => response.json())
      .then((all) =>
        setTransactions(
          all.filter((t) => !t.category || t.category === "UNCATEGORISED"),
        ),
      );
  }, [refreshKey]);

  const choices = categories.filter((c) => c.name !== "UNCATEGORISED");
  const groups = groupByMerchant(transactions);

  const categorise = async (group, category) => {
    setError(null);
    setSaving(group.key);
    try {
      const response = await fetch(
        `/transactions/${group.items[0].transactionID}/category`,
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ category }),
        },
      );
      if (!response.ok) {
        setError((await response.text()) || "Couldn't save the category");
        return;
      }
      onCategorised();
    } finally {
      setSaving(null);
    }
  };

  if (transactions.length === 0) {
    return null;
  }

  return (
    <section className="needs-category" aria-labelledby="needs-category-title">
      <h2 id="needs-category-title">
        Needs a category{" "}
        <span className="needs-category-count">
          {transactions.length} transaction{transactions.length === 1 ? "" : "s"}
        </span>
      </h2>
      <p className="needs-category-note">
        Nothing could place these. Pick a category and every transaction from
        that merchant follows; the ML model learns from your choice too.
      </p>
      {error && (
        <p className="transactions-upload-error" role="alert">
          {error}
        </p>
      )}
      <ul className="needs-category-list">
        {groups.map((group) => {
          const first = group.items[0];
          const detail = first.reference || first.location;
          const times = group.items.map((t) => t.time).filter(Boolean).sort();
          return (
            <li key={group.key} className="needs-category-item">
              <div className="needs-category-main">
                <strong>{first.merchant || "Unknown merchant"}</strong>
                <small>
                  {group.items.length > 1
                    ? `${group.items.length} transactions, ${formatDate(times[0])} – ${formatDate(times.at(-1))}`
                    : formatDate(first.time)}
                  {detail ? ` · ${detail}` : ""}
                </small>
              </div>
              <strong className="needs-category-amount">
                {formatCurrency(group.total, first.currency)}
              </strong>
              <select
                className="needs-category-select"
                aria-label={`Category for ${first.merchant || "this merchant"}`}
                value=""
                disabled={saving === group.key}
                onChange={(event) => categorise(group, event.target.value)}
              >
                <option value="" disabled>
                  {saving === group.key ? "Saving…" : "Choose a category"}
                </option>
                {choices.map((category) => (
                  <option key={category.name} value={category.name}>
                    {category.label}
                  </option>
                ))}
              </select>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
