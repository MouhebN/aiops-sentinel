export const rootPaths = {
  root: '/',
  pagesRoot: '/',
  authRoot: '/authentication',
  errorRoot: '/error',
};

/**
 * Object containing various paths used in the application.
 */
const paths = {
  default: `${rootPaths.root}`,
  components: `${rootPaths.pagesRoot}components`,
  topology: `${rootPaths.pagesRoot}topology`,
  componentDetail: `${rootPaths.pagesRoot}components/:id`,
  devices: `${rootPaths.pagesRoot}devices`,
  events: `${rootPaths.pagesRoot}events`,
  syslogSources: `${rootPaths.pagesRoot}syslog-sources`,
  netflow: `${rootPaths.pagesRoot}netflow`,
  alerts: `${rootPaths.pagesRoot}alerts`,
  incidents: `${rootPaths.pagesRoot}incidents`,
  incidentDetail: `${rootPaths.pagesRoot}incidents/:id`,
  aiAnalysis: `${rootPaths.pagesRoot}ai-analysis`,
  categories: `${rootPaths.pagesRoot}categories`,
  products: `${rootPaths.pagesRoot}products`,
  customers: `${rootPaths.pagesRoot}customers`,
  orders: `${rootPaths.pagesRoot}orders`,
  reports: `${rootPaths.pagesRoot}reports`,
  auditLogs: `${rootPaths.pagesRoot}audit-logs`,
  users: `${rootPaths.pagesRoot}users`,
  maintenance: `${rootPaths.pagesRoot}maintenance`,
  coupons: `${rootPaths.pagesRoot}coupons`,
  inbox: `${rootPaths.pagesRoot}inbox`,
  login: `${rootPaths.authRoot}/login`,
  signup: `${rootPaths.authRoot}/sign-up`,
  forgotPassword: `${rootPaths.authRoot}/forgot-password`,
  resetPassword: `${rootPaths.authRoot}/reset-password`,
  notFound: `${rootPaths.errorRoot}/404`,
};

export default paths;
