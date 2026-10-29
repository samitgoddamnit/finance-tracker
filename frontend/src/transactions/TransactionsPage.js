import { useEffect, useState, useRef } from "react";

import TransactionList from "./TransactionList";
import NeedsCategory from "./NeedsCategory";
import formatCurrency from "./formatCurrency";
import "./TransactionsPage.css";

function describeCategorySource(transaction) {
  switch (transaction.categorySource) {
    case "USER":
      return "Set by you";
    case "MERCHANT":
      return "Same merchant as one you categorised";
    case "RULE":
      return "Matched a merchant rule";
    case "ML":
      return `ML guess, ${Math.round(transaction.categoryConfidence * 100)}% sure`;
    default:
      return "Nothing could place this yet";
  }
}

export default function TransactionsPage() {
  const [selectedTransaction, setSelectedTransaction] = useState(null);
  const [uploadStatus, setUploadStatus] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [categories, setCategories] = useState([]);
  const [categoryError, setCategoryError] = useState(null);
  const inputRef = useRef(null);

  useEffect(() => {
    fetch("/categories")
      .then((response) => response.json())
      .then(setCategories);
  }, []);

  const categoryLabels = Object.fromEntries(
    categories.map((category) => [category.name, category.label]),
  );

  const handleCategoryChange = async (event) => {
    setCategoryError(null);
    const response = await fetch(
      `/transactions/${selectedTransaction.transactionID}/category`,
      {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ category: event.target.value }),
      },
    );
    if (!response.ok) {
      setCategoryError((await response.text()) || "Couldn't save the category");
      return;
    }
    setSelectedTransaction(await response.json());
    refreshAfterCategorising();
  };

  // Other transactions from the same merchant change straight away, so reload now.
  // The ML service then retrains and re-predicts in the background, so reload again
  // a few seconds later to pick up its new guesses.
  const refreshAfterCategorising = () => {
    setRefreshKey((key) => key + 1);
    setTimeout(() => setRefreshKey((key) => key + 1), 4000);
  };

  const handleFileChange = async (event) => {
    const file = event.target.files[0];
    // clear the input so picking the same file again still fires onChange
    event.target.value = "";
    if (!file) {
      return;
    }

    setUploadStatus({ state: "uploading", message: `Importing ${file.name}...` });

    const formData = new FormData();
    formData.append("file", file);

    try {
      const response = await fetch("/transactions/upload", {
        method: "POST",
        body: formData,
      });
      if (!response.ok) {
        const message = await response.text();
        setUploadStatus({
          state: "error",
          message: message || "Upload failed",
        });
        return;
      }
      const { imported, duplicatesSkipped } = await response.json();
      const skippedNote =
        duplicatesSkipped > 0 ? ` (${duplicatesSkipped} were already there)` : "";
      setUploadStatus({
        state: "success",
        message: `Imported ${imported.length} new transactions from ${file.name}${skippedNote}`,
      });
      // transactions are saved through kafka, so give the consumer a moment before reloading
      setTimeout(() => setRefreshKey((key) => key + 1), 1000);
    } catch (error) {
      setUploadStatus({
        state: "error",
        message: "Couldn't reach the server",
      });
    }
  };

  return (
    <section className="transactions-page">
      <header className="transactions-header">
        <p className="transactions-eyebrow">Activity</p>
        <h1>Transactions</h1>
        <p className="transactions-intro">
          Select a payment to inspect its details.
        </p>
        <div className="transactions-upload">
          <input
            type="file"
            accept="application/pdf"
            ref={inputRef}
            hidden
            onChange={handleFileChange}
          />
          <button
            type="button"
            className="transactions-upload-button"
            disabled={uploadStatus?.state === "uploading"}
            onClick={() => inputRef.current?.click()}
          >
            Upload HSBC statement (PDF)
          </button>
          {uploadStatus && (
            <p
              className={`transactions-upload-status transactions-upload-${uploadStatus.state}`}
              role="status"
            >
              {uploadStatus.message}
            </p>
          )}
        </div>
      </header>

      <NeedsCategory
        categories={categories}
        refreshKey={refreshKey}
        onCategorised={refreshAfterCategorising}
      />

      <div className="transactions-workspace">
        <article className="transaction-details" aria-live="polite">
          {selectedTransaction ? (
            <>
              <p className="transaction-details-label">Payment details</p>
              <h2>{selectedTransaction.merchant || "Unknown merchant"}</h2>
              <p className="transaction-details-amount">
                {formatCurrency(
                  selectedTransaction.amount,
                  selectedTransaction.currency,
                )}
              </p>
              <div className="transaction-details-divider" />
              <dl>
                <div>
                  <dt>
                    <label htmlFor="transaction-category">Category</label>
                  </dt>
                  <dd>
                    <select
                      id="transaction-category"
                      className="transaction-category-select"
                      value={selectedTransaction.category || "UNCATEGORISED"}
                      onChange={handleCategoryChange}
                    >
                      {categories.map((category) => (
                        <option key={category.name} value={category.name}>
                          {category.label}
                        </option>
                      ))}
                    </select>
                    <small className="transaction-category-source">
                      {describeCategorySource(selectedTransaction)}
                    </small>
                    {categoryError && (
                      <small className="transactions-upload-error" role="alert">
                        {categoryError}
                      </small>
                    )}
                  </dd>
                </div>
                <div>
                  <dt>Description</dt>
                  <dd>{selectedTransaction.description || "No description"}</dd>
                </div>
                {selectedTransaction.location && (
                  <div>
                    <dt>Location</dt>
                    <dd>{selectedTransaction.location}</dd>
                  </div>
                )}
                {selectedTransaction.reference && (
                  <div>
                    <dt>Reference</dt>
                    <dd>{selectedTransaction.reference}</dd>
                  </div>
                )}
                <div>
                  <dt>Source</dt>
                  <dd>{selectedTransaction.source || "Unknown"}</dd>
                </div>
                <div>
                  <dt>Date and time</dt>
                  <dd>{selectedTransaction.time || "Unknown"}</dd>
                </div>
              </dl>
            </>
          ) : (
            <div className="transaction-details-empty">
              <span aria-hidden="true">↗</span>
            </div>
          )}
        </article>

        <TransactionList
          selectedTransaction={selectedTransaction}
          onSelect={setSelectedTransaction}
          refreshKey={refreshKey}
          categoryLabels={categoryLabels}
        />
      </div>
    </section>
  );
}
