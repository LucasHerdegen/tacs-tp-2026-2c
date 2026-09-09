import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { MainLayout } from './layouts/MainLayout';
import { Home } from './features/dashboard/views/Home';
import { ActivitySearch } from './features/activities/views/ActivitySearch';
import { ActivityDetail } from './features/activities/views/ActivityDetail';

function App() {
  return (
    <BrowserRouter>
      <MainLayout>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/activities" element={<ActivitySearch />} />
          <Route path="/activities/:id" element={<ActivityDetail />} />
        </Routes>
      </MainLayout>
    </BrowserRouter>
  )
}

export default App
