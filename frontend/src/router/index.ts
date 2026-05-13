import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../pages/HomePage.vue'
import InspirationPage from '../pages/InspirationPage.vue'
import AssistantPage from '../pages/AssistantPage.vue'
import DealsPage from '../pages/DealsPage.vue'
import EnterprisePage from '../pages/EnterprisePage.vue'
import AdminDashboardPage from '../pages/AdminDashboardPage.vue'
import GuideDetailPage from '../pages/GuideDetailPage.vue'
import GuidesPage from '../pages/GuidesPage.vue'
import TripsPage from '../pages/TripsPage.vue'
import PersonalizedTripPage from '../pages/PersonalizedTripPage.vue'
import PreferencesPage from '../pages/PreferencesPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomePage },
    { path: '/inspiration', name: 'inspiration', component: InspirationPage },
    { path: '/assistant', name: 'assistant', component: AssistantPage },
    { path: '/personalized', name: 'personalized', component: PersonalizedTripPage },
    { path: '/deals', name: 'deals', component: DealsPage },
    { path: '/guides', name: 'guides', component: GuidesPage },
    { path: '/trips', name: 'trips', component: TripsPage },
    { path: '/preferences', name: 'preferences', component: PreferencesPage },
    { path: '/enterprise', name: 'enterprise', component: EnterprisePage },
    { path: '/guide', name: 'guide-detail', component: GuideDetailPage },
    { path: '/admin', name: 'admin', component: AdminDashboardPage },
  ],
})

export default router
