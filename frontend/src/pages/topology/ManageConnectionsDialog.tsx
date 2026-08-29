import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { DataGrid, GridColDef } from '@mui/x-data-grid';
import {
  aiopsApi,
  ComponentRelation,
  ComponentRelationType,
  MonitoredComponent,
} from 'api/aiopsApi';
import IconActionButton from 'components/aiops/IconActionButton';
import { useEffect, useMemo, useState } from 'react';

const RELATION_TYPES: ComponentRelationType[] = [
  'CONNECTED_TO',
  'ROUTES_TO',
  'DEPENDS_ON',
  'PROTECTS',
];

interface ManageConnectionsDialogProps {
  open: boolean;
  onClose: () => void;
  onChanged: () => void;
}

const ManageConnectionsDialog = ({ open, onClose, onChanged }: ManageConnectionsDialogProps) => {
  const [relations, setRelations] = useState<ComponentRelation[]>([]);
  const [components, setComponents] = useState<MonitoredComponent[]>([]);
  const [sourceComponentId, setSourceComponentId] = useState<number | ''>('');
  const [targetComponentId, setTargetComponentId] = useState<number | ''>('');
  const [relationType, setRelationType] = useState<ComponentRelationType>('CONNECTED_TO');
  const [label, setLabel] = useState('');
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    const [nextRelations, nextComponents] = await Promise.all([
      aiopsApi.getComponentRelations(),
      aiopsApi.getComponents(),
    ]);
    setRelations(nextRelations);
    setComponents(nextComponents);
  };

  useEffect(() => {
    if (open) {
      void load();
    }
  }, [open]);

  const columns: GridColDef<ComponentRelation>[] = useMemo(
    () => [
      { field: 'sourceName', headerName: 'From', flex: 1 },
      { field: 'targetName', headerName: 'To', flex: 1 },
      { field: 'relationType', headerName: 'Type', width: 150 },
      { field: 'label', headerName: 'Label', flex: 1 },
      {
        field: 'actions',
        headerName: '',
        width: 80,
        sortable: false,
        renderCell: (params) => (
          <IconActionButton
            icon="mdi:delete"
            label="Remove"
            onClick={async () => {
              await aiopsApi.deleteComponentRelation(params.row.id);
              await load();
              onChanged();
            }}
          />
        ),
      },
    ],
    [onChanged],
  );

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Manage connections</DialogTitle>
      <DialogContent>
        <Stack spacing={2} mt={1}>
          <Typography variant="body2" color="text.secondary">
            Draw the infrastructure map by connecting monitored components. This does not change
            packet direction or monitoring checks.
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <FormControl fullWidth size="small">
              <InputLabel>From</InputLabel>
              <Select
                label="From"
                value={sourceComponentId}
                onChange={(event) => setSourceComponentId(Number(event.target.value))}
              >
                {components.map((component) => (
                  <MenuItem key={component.id} value={component.id}>
                    {component.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl fullWidth size="small">
              <InputLabel>To</InputLabel>
              <Select
                label="To"
                value={targetComponentId}
                onChange={(event) => setTargetComponentId(Number(event.target.value))}
              >
                {components.map((component) => (
                  <MenuItem key={component.id} value={component.id}>
                    {component.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl fullWidth size="small">
              <InputLabel>Type</InputLabel>
              <Select
                label="Type"
                value={relationType}
                onChange={(event) => setRelationType(event.target.value as ComponentRelationType)}
              >
                {RELATION_TYPES.map((type) => (
                  <MenuItem key={type} value={type}>
                    {type.replace(/_/g, ' ')}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              size="small"
              label="Label"
              value={label}
              onChange={(event) => setLabel(event.target.value)}
            />
          </Stack>
          {error ? (
            <Typography color="error" variant="body2">
              {error}
            </Typography>
          ) : null}
          <DataGrid
            autoHeight
            rows={relations}
            columns={columns}
            disableRowSelectionOnClick
            hideFooter={relations.length < 8}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          onClick={async () => {
            if (sourceComponentId === '' || targetComponentId === '') {
              setError('Choose both components.');
              return;
            }
            try {
              setError(null);
              await aiopsApi.createComponentRelation({
                sourceComponentId,
                targetComponentId,
                relationType,
                label: label.trim() || undefined,
              });
              setLabel('');
              await load();
              onChanged();
            } catch (caught) {
              setError(caught instanceof Error ? caught.message : 'Could not save connection');
            }
          }}
        >
          Add connection
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ManageConnectionsDialog;
