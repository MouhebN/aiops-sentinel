import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import {
  aiopsApi,
  ComponentNetworkInterface,
  NetworkInterfaceRole,
  SaveComponentNetworkInterfacePayload,
} from 'api/aiopsApi';
import IconActionButton from 'components/aiops/IconActionButton';
import { useState } from 'react';

export const NETWORK_INTERFACE_ROLES: NetworkInterfaceRole[] = [
  'MANAGEMENT',
  'LAN',
  'WAN',
  'SERVICE',
  'OTHER',
];

interface InterfaceFormState {
  name: string;
  ipAddress: string;
  role: NetworkInterfaceRole;
  primary: boolean;
}

const emptyForm: InterfaceFormState = {
  name: '',
  ipAddress: '',
  role: 'LAN',
  primary: false,
};

const toForm = (item: ComponentNetworkInterface): InterfaceFormState => ({
  name: item.name,
  ipAddress: item.ipAddress,
  role: item.role,
  primary: item.primary,
});

const toPayload = (form: InterfaceFormState): SaveComponentNetworkInterfacePayload => ({
  name: form.name.trim(),
  ipAddress: form.ipAddress.trim(),
  role: form.role,
  primary: form.primary,
});

interface NetworkInterfacesSectionProps {
  componentId: number;
  interfaces: ComponentNetworkInterface[];
  canManage: boolean;
  onChanged: () => Promise<void> | void;
}

const NetworkInterfacesSection = ({
  componentId,
  interfaces,
  canManage,
  onChanged,
}: NetworkInterfacesSectionProps) => {
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ComponentNetworkInterface | null>(null);
  const [form, setForm] = useState<InterfaceFormState>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<ComponentNetworkInterface | null>(null);
  const [deleting, setDeleting] = useState(false);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setFormError(null);
    setDialogOpen(true);
  };

  const openEdit = (item: ComponentNetworkInterface) => {
    setEditing(item);
    setForm(toForm(item));
    setFormError(null);
    setDialogOpen(true);
  };

  const handleSave = async () => {
    if (!form.name.trim()) {
      setFormError('Interface name is required.');
      return;
    }
    if (!form.ipAddress.trim()) {
      setFormError('IP address is required.');
      return;
    }

    try {
      setSaving(true);
      setFormError(null);
      const payload = toPayload(form);
      if (editing) {
        await aiopsApi.updateComponentInterface(componentId, editing.id, payload);
      } else {
        await aiopsApi.createComponentInterface(componentId, payload);
      }
      setDialogOpen(false);
      await onChanged();
    } catch (requestError) {
      setFormError(
        requestError instanceof Error ? requestError.message : 'Could not save interface',
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    try {
      setDeleting(true);
      await aiopsApi.deleteComponentInterface(componentId, deleteTarget.id);
      setDeleteTarget(null);
      await onChanged();
    } catch (requestError) {
      setFormError(
        requestError instanceof Error ? requestError.message : 'Could not delete interface',
      );
      setDeleteTarget(null);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Stack spacing={1.5}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
        <Stack spacing={0.25}>
          <Typography variant="h6">Network Interfaces</Typography>
          <Typography variant="body2" color="text.secondary">
            Extra IPs used by incidents, topology, and AI. Monitoring checks still use the primary
            IP.
          </Typography>
        </Stack>
        {canManage && (
          <IconActionButton
            icon="mdi:plus"
            label="Add interface"
            color="primary"
            onClick={openCreate}
          />
        )}
      </Stack>

      {interfaces.length === 0 ? (
        <Typography color="text.secondary">No extra network interfaces configured.</Typography>
      ) : (
        <Stack spacing={1}>
          {interfaces.map((item) => (
            <Stack
              key={item.id}
              direction={{ xs: 'column', sm: 'row' }}
              justifyContent="space-between"
              alignItems={{ sm: 'center' }}
              spacing={1}
              sx={{
                p: 1.25,
                borderRadius: 1.5,
                border: '1px solid',
                borderColor: 'divider',
              }}
            >
              <Stack spacing={0.25} sx={{ minWidth: 0 }}>
                <Typography fontWeight={700}>{item.name}</Typography>
                <Typography sx={{ overflowWrap: 'anywhere' }}>{item.ipAddress}</Typography>
                <Stack direction="row" spacing={0.75} flexWrap="wrap">
                  <Chip size="small" variant="outlined" label={item.role} />
                  {item.primary && <Chip size="small" color="primary" label="Primary" />}
                </Stack>
              </Stack>
              {canManage && (
                <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                  <IconActionButton
                    icon="mdi:pencil-outline"
                    label="Edit interface"
                    onClick={() => openEdit(item)}
                  />
                  <IconActionButton
                    icon="mdi:trash-can-outline"
                    label="Delete interface"
                    color="error"
                    onClick={() => setDeleteTarget(item)}
                  />
                </Stack>
              )}
            </Stack>
          ))}
        </Stack>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>{editing ? 'Edit interface' : 'Add interface'}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={1.5} sx={{ pt: 0.5 }}>
            {formError && <Alert severity="error">{formError}</Alert>}
            <TextField
              label="Name"
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
              fullWidth
              required
              size="small"
            />
            <TextField
              label="IP address"
              value={form.ipAddress}
              onChange={(event) => setForm({ ...form, ipAddress: event.target.value })}
              fullWidth
              required
              size="small"
            />
            <TextField
              select
              label="Role"
              value={form.role}
              onChange={(event) =>
                setForm({ ...form, role: event.target.value as NetworkInterfaceRole })
              }
              fullWidth
              size="small"
            >
              {NETWORK_INTERFACE_ROLES.map((role) => (
                <MenuItem key={role} value={role}>
                  {role}
                </MenuItem>
              ))}
            </TextField>
            <FormControlLabel
              control={
                <Switch
                  checked={form.primary}
                  onChange={(event) => setForm({ ...form, primary: event.target.checked })}
                />
              }
              label="Primary interface flag"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={saving} variant="contained">
            {saving ? 'Saving...' : 'Save'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)}>
        <DialogTitle>Delete interface?</DialogTitle>
        <DialogContent>
          <Typography>
            Remove {deleteTarget?.name} ({deleteTarget?.ipAddress})? The component and its metrics
            stay unchanged.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)} disabled={deleting}>
            Cancel
          </Button>
          <Button onClick={handleDelete} color="error" disabled={deleting} variant="contained">
            {deleting ? 'Deleting...' : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
};

export default NetworkInterfacesSection;
