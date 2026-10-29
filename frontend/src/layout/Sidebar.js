import { NavLink } from "react-router-dom";
import "./Sidebar.css";

export default function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">Samit Finance Tracker!</div>
      <nav aria-label="Main navigation">
        <NavLink
          exact
          to="/dashboard"
          activeClassName="sidebar-link-active"
          className="sidebar-link"
        >
          Dashboard
        </NavLink>
        <NavLink
          to="/transactions"
          activeClassName="sidebar-link-active"
          className="sidebar-link"
        >
          Transactions
        </NavLink>
      </nav>
    </aside>
  );
}
