import { Redirect, Route, Switch } from "react-router-dom";
import Dashboard from "../dashboard/Dashboard";
import TransactionsPage from "../transactions/TransactionsPage";

export default function AppRoutes() {
  return (
    <Switch>
      <Route path="/dashboard" component={Dashboard} />
      <Route path="/transactions" component={TransactionsPage} />
      <Redirect to="/dashboard" />
    </Switch>
  );
}
