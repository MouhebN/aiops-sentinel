import { RequireAuth, RequireGuest, RequireRole } from 'auth/RouteGuards';
import PageLoader from 'components/loading/PageLoader';
import Splash from 'components/loading/Splash';
import AuthLayout from 'layouts/auth-layout';
import { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import paths, { rootPaths } from './path';

/* ---------------- Lazy loads various components ------------------------- */
const App = lazy(() => import('App'));
const MainLayout = lazy(() => import('layouts/main-layout'));
const LoginPage = lazy(() => import('pages/authentication/login'));
const SignUpPage = lazy(() => import('pages/authentication/register'));
const ForgotPasswordPage = lazy(() => import('pages/authentication/forgot-password'));
const PasswordResetPage = lazy(() => import('pages/authentication/reset-password'));
const CategoriesPage = lazy(() => import('pages/categories'));
const OrdersPage = lazy(() => import('pages/orders'));
const Dashboard = lazy(() => import('pages/dashboard/index'));
const ProductsPage = lazy(() => import('pages/products'));
const CustomersPage = lazy(() => import('pages/customers'));
const ReportsPage = lazy(() => import('pages/reports'));
const AuditLogsPage = lazy(() => import('pages/audit-logs'));
const UsersPage = lazy(() => import('pages/users'));
const MaintenancePage = lazy(() => import('pages/maintenance'));
const CouponsPage = lazy(() => import('pages/coupons'));
const InboxPage = lazy(() => import('pages/inbox'));
const NotFoundPage = lazy(() => import('pages/not-found'));
const ComponentsPage = lazy(() => import('pages/components'));
const TopologyPage = lazy(() => import('pages/topology'));
const ComponentDetailPage = lazy(() => import('pages/components/detail'));
const DevicesPage = lazy(() => import('pages/devices'));
const EventsPage = lazy(() => import('pages/events'));
const SyslogSourcesPage = lazy(() => import('pages/syslog-sources'));
const NetFlowPage = lazy(() => import('pages/netflow'));
const AlertsPage = lazy(() => import('pages/alerts'));
const IncidentsPage = lazy(() => import('pages/incidents'));
const IncidentDetailPage = lazy(() => import('pages/incidents/detail'));
const AiAnalysisPage = lazy(() => import('pages/ai-analysis'));
/* -------------------------------------------------------------------------- */

/**
 * @Defines the routes for the application using React Router.
 */
export const routes = [
  {
    element: (
      <Suspense fallback={<Splash />}>
        <App />
      </Suspense>
    ),
    children: [
      {
        element: <RequireAuth />,
        children: [
          {
            path: paths.default,
            element: (
              <Suspense fallback={<PageLoader />}>
                <MainLayout />
              </Suspense>
            ),
            children: [
              {
                index: true,
                element: <Dashboard />,
              },
              {
                path: paths.components,
                element: <ComponentsPage />,
              },
              {
                path: paths.topology,
                element: <TopologyPage />,
              },
              {
                path: paths.componentDetail,
                element: <ComponentDetailPage />,
              },
              {
                path: paths.devices,
                element: <DevicesPage />,
              },
              {
                path: paths.events,
                element: <EventsPage />,
              },
              {
                element: <RequireRole roles={['ADMIN']} />,
                children: [
                  {
                    path: paths.syslogSources,
                    element: <SyslogSourcesPage />,
                  },
                ],
              },
              {
                element: <RequireRole roles={['ADMIN', 'OPERATOR', 'VIEWER']} />,
                children: [
                  {
                    path: paths.netflow,
                    element: <NetFlowPage />,
                  },
                ],
              },
              {
                path: paths.alerts,
                element: <AlertsPage />,
              },
              {
                path: paths.incidents,
                element: <IncidentsPage />,
              },
              {
                path: paths.incidentDetail,
                element: <IncidentDetailPage />,
              },
              {
                element: <RequireRole roles={['ADMIN', 'OPERATOR']} />,
                children: [
                  {
                    path: paths.aiAnalysis,
                    element: <AiAnalysisPage />,
                  },
                ],
              },
              {
                path: paths.categories,
                element: <CategoriesPage />,
              },
              {
                path: paths.products,
                element: <ProductsPage />,
              },
              {
                path: paths.customers,
                element: <CustomersPage />,
              },
              {
                path: paths.orders,
                element: <OrdersPage />,
              },
              {
                path: paths.reports,
                element: <ReportsPage />,
              },
              {
                element: <RequireRole roles={['ADMIN']} />,
                children: [
                  {
                    path: paths.users,
                    element: <UsersPage />,
                  },
                  {
                    path: paths.auditLogs,
                    element: <AuditLogsPage />,
                  },
                  {
                    path: paths.maintenance,
                    element: <MaintenancePage />,
                  },
                ],
              },
              {
                path: paths.coupons,
                element: <CouponsPage />,
              },
              {
                path: paths.inbox,
                element: <InboxPage />,
              },
            ],
          },
        ],
      },
      {
        path: rootPaths.authRoot,
        element: <RequireGuest />,
        children: [
          {
            element: <AuthLayout />,
            children: [
              {
                path: paths.login,
                element: <LoginPage />,
              },
              {
                path: paths.signup,
                element: <SignUpPage />,
              },
              {
                path: paths.forgotPassword,
                element: <ForgotPasswordPage />,
              },
              {
                path: paths.resetPassword,
                element: <PasswordResetPage />,
              },
            ],
          },
        ],
      },
      {
        path: rootPaths.errorRoot,
        children: [
          {
            path: paths.notFound,
            element: <NotFoundPage />,
          },
        ],
      },
      {
        path: '*',
        element: <Navigate to={paths.notFound} replace />,
      },
    ],
  },
];

const router = createBrowserRouter(routes);

export default router;
