import "./App.css";
import { BrowserRouter } from "react-router-dom";
import AppLayout from "./layout/AppLayout";
import AppRoutes from "./routes/AppRoutes";

export default function App() {
  return (
    <BrowserRouter>
      <AppLayout>
        <AppRoutes />
      </AppLayout>
    </BrowserRouter>
  );
}
