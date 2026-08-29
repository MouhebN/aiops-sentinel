import { DeviceType } from 'types/aiops';

export const deviceTypeIcon = (type: DeviceType | string) => {
  switch (type) {
    case 'FIREWALL':
      return 'mdi:firewall';
    case 'ROUTER':
      return 'mdi:router-network';
    case 'SWITCH':
      return 'mdi:lan';
    case 'SERVER':
      return 'mdi:server';
    case 'ATM':
      return 'mdi:atm';
    case 'IP_CAMERA':
      return 'mdi:cctv';
    case 'APPLICATION':
      return 'mdi:application-outline';
    case 'UPS':
      return 'mdi:battery-charging';
    case 'DATABASE':
      return 'mdi:database';
    default:
      return 'mdi:devices';
  }
};
