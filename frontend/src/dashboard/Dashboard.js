import DashboardBox from "./DashboardBox";
import MonthlySummary from "./MonthlySummary";

const money = new Intl.NumberFormat("en-GB", {
  style: "currency",
  currency: "GBP",
});

export default function Dashboard() {
  const thisMonth = new Date().toLocaleDateString("en-GB", {
    month: "long",
    year: "numeric",
  });

  return (
    <section className="dashboard">
      <h1>Dashboard</h1>
      <div className="dashboard-boxes">
        <DashboardBox
          title={`Spent in ${thisMonth}`}
          endpoint="/dashboard/spent-this-month"
          formatValue={(value) => money.format(value)}
        />
      </div>
      <MonthlySummary />
    </section>
  );
}
