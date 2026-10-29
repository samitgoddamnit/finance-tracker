import { useEffect, useState } from "react";

export default function DashboardBox({
  title,
  endpoint,
  formatValue = (value) => value,
}) {
  const [value, setValue] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadValue() {
      try {
        const response = await fetch(endpoint);

        if (!response.ok) {
          throw new Error(`Unable to load ${title.toLowerCase()}`);
        }

        setValue(await response.json());
      } catch (requestError) {
        setError(requestError.message);
      }
    }

    loadValue();
  }, [endpoint, title]);

  return (
    <article className="dashboard-box">
      <h2>{title}</h2>
      {error ? (
        <p role="alert">{error}</p>
      ) : (
        <p>{value === null ? "Loading..." : formatValue(value)}</p>
      )}
    </article>
  );
}
