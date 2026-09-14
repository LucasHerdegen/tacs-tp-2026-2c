import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { MainLayout } from './layouts/MainLayout';
import { Home } from './features/dashboard/views/Home';
import { ActivitySearch } from './features/activities/views/ActivitySearch';
import { ActivityDetail } from './features/activities/views/ActivityDetail';
import { MyActivities } from "./features/activities/views/MyActivities";
import { WeatherConfig } from './features/activities/views/ActivityWeatherConfig';
import { CreateActivity } from './features/activities/views/ActivityCreate';
import { AuthProvider } from './features/auth/AuthProvider';
import { GuestOnly, RequireAuth } from './features/auth/components/AuthRoute';
import { Login } from './features/auth/views/Login';
import { Register } from './features/auth/views/Register';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <MainLayout>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route element={<GuestOnly />}>
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
            </Route>
            <Route element={<RequireAuth />}>
              <Route path="/activities" element={<ActivitySearch />} />
              <Route path="/my-activities" element={<MyActivities />} />
              <Route path="/activities/new" element={<CreateActivity />} />
              <Route path="/activities/:id" element={<ActivityDetail />} />
              <Route path="/activities/:id/weather-config" element={<WeatherConfig />} />
            </Route>
          </Routes>
        </MainLayout>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
