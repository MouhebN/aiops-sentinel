import {
  Alert,
  Box,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Grid,
  MenuItem,
  Paper,
  Stack,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import { DataGrid, GridColDef, GridPaginationModel } from '@mui/x-data-grid';
import { aiopsApi, CreateUserPayload, ManagedUser, UpdateUserPayload } from 'api/aiopsApi';
import ApiState from 'components/aiops/ApiState';
import FilterCard from 'components/aiops/FilterCard';
import IconActionButton from 'components/aiops/IconActionButton';
import PageHeader from 'components/common/PageHeader';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { UserRole } from 'types/aiops';

const USER_ROLES: UserRole[] = ['ADMIN', 'OPERATOR', 'VIEWER'];

interface UserFormState {
  fullName: string;
  email: string;
  password: string;
  role: UserRole;
  enabled: boolean;
}

const emptyForm: UserFormState = {
  fullName: '',
  email: '',
  password: '',
  role: 'VIEWER',
  enabled: true,
};

const UsersPage = () => {
  const [users, setUsers] = useState<ManagedUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [searchFilter, setSearchFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState<UserRole | ''>('');
  const [enabledFilter, setEnabledFilter] = useState<'true' | 'false' | ''>('');
  const [paginationModel, setPaginationModel] = useState<GridPaginationModel>({
    page: 0,
    pageSize: 20,
  });
  const [rowCount, setRowCount] = useState(0);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<ManagedUser | null>(null);
  const [passwordUser, setPasswordUser] = useState<ManagedUser | null>(null);
  const [form, setForm] = useState<UserFormState>(emptyForm);
  const [newPassword, setNewPassword] = useState('');

  const queryParams = useMemo(
    () => ({
      page: paginationModel.page,
      size: paginationModel.pageSize,
      search: searchFilter.trim() || undefined,
      role: roleFilter || undefined,
      enabled: enabledFilter || undefined,
    }),
    [enabledFilter, paginationModel.page, paginationModel.pageSize, roleFilter, searchFilter],
  );

  const refresh = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await aiopsApi.getUsers(queryParams);
      setUsers(response.content);
      setRowCount(response.totalElements);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Unknown users error');
    } finally {
      setLoading(false);
    }
  }, [queryParams]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const openCreateDialog = () => {
    setEditingUser(null);
    setForm(emptyForm);
    setFormError(null);
    setDialogOpen(true);
  };

  const openEditDialog = (user: ManagedUser) => {
    setEditingUser(user);
    setForm({
      fullName: user.fullName,
      email: user.email,
      password: '',
      role: user.role,
      enabled: user.enabled,
    });
    setFormError(null);
    setDialogOpen(true);
  };

  const openPasswordDialog = (user: ManagedUser) => {
    setPasswordUser(user);
    setNewPassword('');
    setPasswordError(null);
    setPasswordDialogOpen(true);
  };

  const validateForm = () => {
    if (!form.fullName.trim()) {
      return 'Full name is required.';
    }
    if (!editingUser && !form.email.trim()) {
      return 'Email is required.';
    }
    if (!editingUser && form.password.trim().length < 8) {
      return 'Password must be at least 8 characters.';
    }
    return null;
  };

  const handleSave = async () => {
    const validationError = validateForm();
    if (validationError) {
      setFormError(validationError);
      return;
    }

    try {
      setSaving(true);
      setFormError(null);
      if (editingUser) {
        const payload: UpdateUserPayload = {
          fullName: form.fullName.trim(),
          role: form.role,
          enabled: form.enabled,
        };
        await aiopsApi.updateUser(editingUser.id, payload);
      } else {
        const payload: CreateUserPayload = {
          fullName: form.fullName.trim(),
          email: form.email.trim(),
          password: form.password,
          role: form.role,
          enabled: form.enabled,
        };
        await aiopsApi.createUser(payload);
      }
      setDialogOpen(false);
      await refresh();
    } catch (requestError) {
      setFormError(requestError instanceof Error ? requestError.message : 'Unknown save error');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleEnabled = async (user: ManagedUser) => {
    try {
      setError(null);
      if (user.enabled) {
        await aiopsApi.disableUser(user.id);
      } else {
        await aiopsApi.enableUser(user.id);
      }
      await refresh();
    } catch (requestError) {
      setError(
        requestError instanceof Error ? requestError.message : 'Unknown enable/disable error',
      );
    }
  };

  const handleResetPassword = async () => {
    if (!passwordUser) {
      return;
    }
    if (newPassword.trim().length < 8) {
      setPasswordError('Password must be at least 8 characters.');
      return;
    }

    try {
      setSaving(true);
      setPasswordError(null);
      await aiopsApi.resetUserPassword(passwordUser.id, newPassword);
      setPasswordDialogOpen(false);
      await refresh();
    } catch (requestError) {
      setPasswordError(
        requestError instanceof Error ? requestError.message : 'Unknown password reset error',
      );
    } finally {
      setSaving(false);
    }
  };

  const resetFilters = () => {
    setSearchFilter('');
    setRoleFilter('');
    setEnabledFilter('');
    setPaginationModel((current) => ({ ...current, page: 0 }));
  };

  const columns = useMemo<GridColDef<ManagedUser>[]>(
    () => [
      { field: 'fullName', headerName: 'Full Name', minWidth: 200, flex: 1 },
      { field: 'email', headerName: 'Email', minWidth: 220, flex: 1.1 },
      {
        field: 'role',
        headerName: 'Role',
        minWidth: 120,
        flex: 0.5,
        renderCell: (params) => <Chip label={params.row.role} size="small" variant="outlined" />,
      },
      {
        field: 'enabled',
        headerName: 'Status',
        minWidth: 120,
        flex: 0.55,
        renderCell: (params) => (
          <Chip
            size="small"
            label={params.row.enabled ? 'Enabled' : 'Disabled'}
            color={params.row.enabled ? 'success' : 'default'}
          />
        ),
      },
      {
        field: 'updatedAt',
        headerName: 'Updated',
        minWidth: 170,
        flex: 0.75,
        valueFormatter: (value) => dayjs(value as string).format('DD/MM/YYYY HH:mm'),
      },
      {
        field: 'actions',
        headerName: 'Actions',
        minWidth: 160,
        flex: 0.75,
        sortable: false,
        renderCell: (params) => (
          <Stack direction="row" spacing={1}>
            <IconActionButton
              icon="mdi:pencil-outline"
              label="Edit user"
              onClick={() => openEditDialog(params.row)}
            />
            <IconActionButton
              icon={params.row.enabled ? 'mdi:account-off-outline' : 'mdi:account-check-outline'}
              label={params.row.enabled ? 'Disable user' : 'Enable user'}
              color={params.row.enabled ? 'warning' : 'success'}
              onClick={() => handleToggleEnabled(params.row)}
            />
            <IconActionButton
              icon="mdi:key-variant"
              label="Reset password"
              onClick={() => openPasswordDialog(params.row)}
            />
          </Stack>
        ),
      },
    ],
    [],
  );

  return (
    <Box sx={{ pb: 2 }}>
      <PageHeader
        actions={
          <Stack direction="row" spacing={1}>
            <IconActionButton icon="mdi:refresh" label="Refresh users" onClick={refresh} />
            <IconActionButton
              icon="mdi:plus"
              label="Add user"
              color="primary"
              onClick={openCreateDialog}
            />
          </Stack>
        }
      >
        Users
      </PageHeader>

      <Stack spacing={3} mt={3}>
        <ApiState loading={loading} error={error} onRetry={refresh} />
        {error && <Alert severity="error">{error}</Alert>}

        <FilterCard title="User Filters">
          <Grid container spacing={2}>
            <Grid item xs={12} md={4}>
              <TextField
                fullWidth
                size="small"
                label="Search name or email"
                value={searchFilter}
                onChange={(event) => {
                  setSearchFilter(event.target.value);
                  setPaginationModel((current) => ({ ...current, page: 0 }));
                }}
              />
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                select
                fullWidth
                size="small"
                label="Role"
                value={roleFilter}
                onChange={(event) => {
                  setRoleFilter(event.target.value as UserRole | '');
                  setPaginationModel((current) => ({ ...current, page: 0 }));
                }}
              >
                <MenuItem value="">All roles</MenuItem>
                {USER_ROLES.map((role) => (
                  <MenuItem key={role} value={role}>
                    {role}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                select
                fullWidth
                size="small"
                label="Status"
                value={enabledFilter}
                onChange={(event) => {
                  setEnabledFilter(event.target.value as 'true' | 'false' | '');
                  setPaginationModel((current) => ({ ...current, page: 0 }));
                }}
              >
                <MenuItem value="">All statuses</MenuItem>
                <MenuItem value="true">Enabled</MenuItem>
                <MenuItem value="false">Disabled</MenuItem>
              </TextField>
            </Grid>
            <Grid item xs={12} md={2}>
              <Stack
                direction="row"
                spacing={1}
                justifyContent={{ xs: 'flex-start', md: 'flex-end' }}
              >
                <IconActionButton
                  icon="mdi:filter-off-outline"
                  label="Reset filters"
                  onClick={resetFilters}
                />
              </Stack>
            </Grid>
          </Grid>
        </FilterCard>

        <Paper
          sx={{ p: 2, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}
          elevation={0}
        >
          <Typography variant="h5" mb={2}>
            Platform Users
          </Typography>
          <DataGrid
            rows={users}
            columns={columns}
            loading={loading}
            autoHeight
            disableRowSelectionOnClick
            paginationMode="server"
            rowCount={rowCount}
            pageSizeOptions={[10, 20, 50]}
            paginationModel={paginationModel}
            onPaginationModelChange={setPaginationModel}
          />
        </Paper>
      </Stack>

      <Dialog
        open={dialogOpen}
        onClose={() => !saving && setDialogOpen(false)}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle>{editingUser ? 'Edit User' : 'Create User'}</DialogTitle>
        <DialogContent dividers sx={{ pt: 2 }}>
          <Stack spacing={2}>
            {formError && <Alert severity="error">{formError}</Alert>}
            <TextField
              fullWidth
              size="small"
              label="Full name"
              value={form.fullName}
              onChange={(event) =>
                setForm((current) => ({ ...current, fullName: event.target.value }))
              }
            />
            <TextField
              fullWidth
              size="small"
              label="Email"
              value={form.email}
              onChange={(event) =>
                setForm((current) => ({ ...current, email: event.target.value }))
              }
              disabled={Boolean(editingUser)}
            />
            {!editingUser && (
              <TextField
                fullWidth
                size="small"
                type="password"
                label="Password"
                value={form.password}
                onChange={(event) =>
                  setForm((current) => ({ ...current, password: event.target.value }))
                }
              />
            )}
            <Grid container spacing={2} alignItems="center">
              <Grid item xs={12} sm={6}>
                <Stack spacing={0.75} sx={{ maxWidth: { xs: '100%', sm: 240 } }}>
                  <Typography variant="body2" color="text.secondary" fontWeight={500}>
                    Role
                  </Typography>
                  <ToggleButtonGroup
                    exclusive
                    size="small"
                    value={form.role}
                    onChange={(_, nextRole: UserRole | null) => {
                      if (nextRole) {
                        setForm((current) => ({ ...current, role: nextRole }));
                      }
                    }}
                    sx={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
                      '& .MuiToggleButton-root': {
                        px: 1,
                        py: 0.75,
                        textTransform: 'none',
                        fontSize: 12,
                        borderRadius: 1,
                      },
                    }}
                  >
                    {USER_ROLES.map((role) => (
                      <ToggleButton key={role} value={role}>
                        {role}
                      </ToggleButton>
                    ))}
                  </ToggleButtonGroup>
                </Stack>
              </Grid>
              <Grid item xs={12} sm={6}>
                <Stack
                  justifyContent="center"
                  alignItems={{ xs: 'flex-start', sm: 'flex-end' }}
                  sx={{ height: '100%', minHeight: 40 }}
                >
                  <FormControlLabel
                    control={
                      <Switch
                        checked={form.enabled}
                        onChange={(event) =>
                          setForm((current) => ({ ...current, enabled: event.target.checked }))
                        }
                      />
                    }
                    label="Enabled"
                    sx={{ mr: 0 }}
                  />
                </Stack>
              </Grid>
            </Grid>
          </Stack>
        </DialogContent>
        <DialogActions>
          <IconActionButton icon="mdi:close" label="Cancel" onClick={() => setDialogOpen(false)} />
          <IconActionButton
            icon={saving ? 'mdi:loading' : 'mdi:content-save-outline'}
            label={editingUser ? 'Save user' : 'Create user'}
            color="primary"
            disabled={saving}
            onClick={handleSave}
          />
        </DialogActions>
      </Dialog>

      <Dialog
        open={passwordDialogOpen}
        onClose={() => !saving && setPasswordDialogOpen(false)}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle>Reset Password</DialogTitle>
        <DialogContent dividers sx={{ pt: 2 }}>
          <Stack spacing={2}>
            {passwordError && <Alert severity="error">{passwordError}</Alert>}
            <Typography color="text.secondary">
              {passwordUser ? `Set a new password for ${passwordUser.email}.` : ''}
            </Typography>
            <TextField
              fullWidth
              size="small"
              type="password"
              label="New password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <IconActionButton
            icon="mdi:close"
            label="Cancel"
            onClick={() => setPasswordDialogOpen(false)}
          />
          <IconActionButton
            icon={saving ? 'mdi:loading' : 'mdi:key-variant'}
            label="Reset password"
            color="primary"
            disabled={saving}
            onClick={handleResetPassword}
          />
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default UsersPage;
