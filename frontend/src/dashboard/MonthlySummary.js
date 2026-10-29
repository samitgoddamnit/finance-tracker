import { useEffect, useState } from "react";
import "./MonthlySummary.css";

const money = new Intl.NumberFormat("en-GB", {
  style: "currency",
  currency: "GBP",
});

// months are "YYYY-MM" strings, which also sort correctly as text
function currentMonth() {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}`;
}

function addMonths(month, count) {
  const [year, monthNumber] = month.split("-").map(Number);
  const date = new Date(year, monthNumber - 1 + count);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

// "2026-08" -> "August 2026"
function formatMonth(month) {
  const [year, monthNumber] = month.split("-").map(Number);
  return new Date(year, monthNumber - 1).toLocaleDateString("en-GB", {
    month: "long",
    year: "numeric",
  });
}

// one month's rows from the summary, biggest first, Uncategorised last
function rowsFor(rows, column) {
  if (column < 0) {
    return [];
  }
  return rows
    .map((row) => ({ ...row, amount: Number(row.amounts[column]) }))
    .filter((row) => row.amount !== 0)
    .sort(
      (a, b) =>
        (a.category === "UNCATEGORISED") - (b.category === "UNCATEGORISED") ||
        b.amount - a.amount,
    );
}

function Section({ title, rows, total }) {
  return (
    <tbody>
      <tr className="monthly-summary-section">
        <th scope="rowgroup" colSpan={3}>
          {title}
        </th>
      </tr>
      {rows.length === 0 ? (
        <tr>
          <td colSpan={3} className="monthly-summary-none">
            None
          </td>
        </tr>
      ) : (
        rows.map((row) => (
          <tr key={row.category}>
            <th scope="row">{row.label}</th>
            <td className="text-end">{money.format(row.amount)}</td>
            <td className="text-end monthly-summary-share">
              {total > 0 ? `${Math.round((row.amount / total) * 100)}%` : "–"}
            </td>
          </tr>
        ))
      )}
      <tr className="monthly-summary-subtotal">
        <th scope="row">Total {title.toLowerCase()}</th>
        <td className="text-end">{money.format(total)}</td>
        <td />
      </tr>
    </tbody>
  );
}

export default function MonthlySummary() {
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState("");
  const [month, setMonth] = useState(currentMonth);

  useEffect(() => {
    fetch("/dashboard/monthly-summary")
      .then((response) => {
        if (!response.ok) {
          throw new Error("Unable to load the monthly summary");
        }
        return response.json();
      })
      .then(setSummary)
      .catch((requestError) => setError(requestError.message));
  }, []);

  if (error) {
    return <p role="alert">{error}</p>;
  }
  if (!summary) {
    return <p>Loading...</p>;
  }

  // you can step back to the earliest month with data, and forward to this month
  const earliest = [summary.months[0], currentMonth()].filter(Boolean).sort()[0];
  const latest = [summary.months.at(-1), currentMonth()].filter(Boolean).sort().at(-1);

  const column = summary.months.indexOf(month);
  const inRows = rowsFor(summary.moneyIn, column);
  const outRows = rowsFor(summary.moneyOut, column);
  const totalIn = column < 0 ? 0 : Number(summary.inTotals[column]);
  const totalOut = column < 0 ? 0 : Number(summary.outTotals[column]);
  const net = totalIn - totalOut;

  return (
    <section className="monthly-summary">
      <div className="monthly-summary-header">
        <h2>Money in and out</h2>
        <div className="monthly-summary-nav">
          <button
            type="button"
            className="monthly-summary-step"
            onClick={() => setMonth(addMonths(month, -1))}
            disabled={month <= earliest}
            aria-label="Previous month"
          >
            ‹
          </button>
          <span className="monthly-summary-month" aria-live="polite">
            {formatMonth(month)}
          </span>
          <button
            type="button"
            className="monthly-summary-step"
            onClick={() => setMonth(addMonths(month, 1))}
            disabled={month >= latest}
            aria-label="Next month"
          >
            ›
          </button>
        </div>
      </div>
      <p className="monthly-summary-note">
        Refunds are taken off the category they refund rather than counted as
        money in.
      </p>

      {inRows.length === 0 && outRows.length === 0 ? (
        <p className="monthly-summary-empty">
          Nothing recorded for {formatMonth(month)}. Upload a statement covering
          it on the Transactions page.
        </p>
      ) : (
        <div className="table-responsive">
          <table className="table table-sm monthly-summary-table">
            <thead>
              <tr>
                <th scope="col">Category</th>
                <th scope="col" className="text-end">
                  Amount
                </th>
                <th scope="col" className="text-end">
                  Share
                </th>
              </tr>
            </thead>
            <Section title="Money in" rows={inRows} total={totalIn} />
            <Section title="Money out" rows={outRows} total={totalOut} />
            <tfoot>
              <tr>
                <th scope="row">Net</th>
                <td
                  className={`text-end ${net < 0 ? "monthly-summary-negative" : "monthly-summary-positive"}`}
                >
                  {net > 0 ? "+" : ""}
                  {money.format(net)}
                </td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </section>
  );
}
