import { UserRole } from 'types/aiops';

export const canManageComponents = (role?: UserRole) => role === 'ADMIN';

export const canManageTopology = (role?: UserRole) => role === 'ADMIN' || role === 'OPERATOR';

export const canManageThresholds = (role?: UserRole) => role === 'ADMIN';

export const canRunManualChecks = (role?: UserRole) => role === 'ADMIN' || role === 'OPERATOR';

export const canAcknowledgeIncidents = (role?: UserRole) => role === 'ADMIN' || role === 'OPERATOR';

export const canResolveIncidents = (role?: UserRole) => role === 'ADMIN' || role === 'OPERATOR';

export const canUseAiAnalysis = (role?: UserRole) => role === 'ADMIN' || role === 'OPERATOR';

export const canUploadPacketCaptures = (role?: UserRole) => role === 'ADMIN' || role === 'OPERATOR';

export const canManageSyslogSources = (role?: UserRole) => role === 'ADMIN';

export const canManageNetFlow = (role?: UserRole) => role === 'ADMIN' || role === 'OPERATOR';

export const canRebuildIncidents = (role?: UserRole) => role === 'ADMIN';

export const canSaveReports = (role?: UserRole) => role === 'ADMIN' || role === 'OPERATOR';

export const canManageMaintenance = (role?: UserRole) => role === 'ADMIN';
