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
      <MonthlySummary />
    </section>
  );
}
