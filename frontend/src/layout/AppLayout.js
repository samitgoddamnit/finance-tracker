import { useLocation } from "react-router-dom";
import Sidebar from "./Sidebar";
import "./AppLayout.css";

export default function AppLayout({ children }) {
  const location = useLocation();
  //anything that would go on every page in the tool, should be rendered here
  return (
    <div className="app-layout">
      <Sidebar />
      <main className="app-content" key={location.pathname}>
        {children}
      </main>
    </div>
  );
}
