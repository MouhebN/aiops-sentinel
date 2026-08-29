/* eslint-disable @typescript-eslint/no-explicit-any */
import { SvgIconProps } from '@mui/material';
import HomeIcon from 'components/icons/menu-icons/HomeIcon';
import InboxIcon from 'components/icons/menu-icons/InboxIcon';
import OrderIcon from 'components/icons/menu-icons/OrderIcon';
import ProductsIcon from 'components/icons/menu-icons/ProductsIcon';
import ReportsIcon from 'components/icons/menu-icons/ReportsIcon';
import { UserRole } from 'types/aiops';
import { uniqueId } from 'lodash';

export interface IMenuitems {
  [x: string]: any;
  id?: string;
  navlabel?: boolean;
  subheader?: string;
  title?: string;
  icon?: (props: SvgIconProps) => JSX.Element;
  href?: string;
  children?: IMenuitems[];
  chip?: string;
  chipColor?: string | any;
  variant?: string | any;
  available?: boolean;
  disabled?: boolean;
  roles?: UserRole[];
  level?: number;
  onClick?: React.MouseEvent<HTMLButtonElement, MouseEvent>;
}

const Menuitems: IMenuitems[] = [
  {
    navlabel: true,
    subheader: 'Supervision',
  },
  {
    id: uniqueId(),
    title: 'Overview',
    icon: HomeIcon,
    href: '/',
    available: true,
  },
  {
    id: uniqueId(),
    title: 'Components',
    icon: ProductsIcon,
    href: '/components',
    available: true,
  },
  {
    id: uniqueId(),
    title: 'Infrastructure map',
    icon: ReportsIcon,
    href: '/topology',
    available: true,
  },
  {
    id: uniqueId(),
    title: 'Devices',
    icon: ProductsIcon,
    href: '/devices',
    available: true,
  },
  {
    id: uniqueId(),
    title: 'Events & Logs',
    icon: ReportsIcon,
    href: '/events',
    available: true,
  },
  {
    id: uniqueId(),
    title: 'Syslog Sources',
    icon: ReportsIcon,
    href: '/syslog-sources',
    available: true,
    roles: ['ADMIN'],
  },
  {
    id: uniqueId(),
    title: 'NetFlow',
    icon: ReportsIcon,
    href: '/netflow',
    available: true,
    roles: ['ADMIN', 'OPERATOR', 'VIEWER'],
  },
  {
    id: uniqueId(),
    title: 'Alerts',
    icon: InboxIcon,
    href: '/alerts',
    available: true,
  },
  {
    id: uniqueId(),
    title: 'Incidents',
    icon: InboxIcon,
    href: '/incidents',
    available: true,
  },
  {
    navlabel: true,
    subheader: 'Intelligence',
  },
  {
    id: uniqueId(),
    title: 'AI Analysis',
    icon: OrderIcon,
    href: '/ai-analysis',
    available: true,
    roles: ['ADMIN', 'OPERATOR'],
  },
  {
    id: uniqueId(),
    title: 'Diagnostic Reports',
    icon: ReportsIcon,
    href: '/reports',
    available: true,
  },
  {
    id: uniqueId(),
    title: 'Users',
    icon: ReportsIcon,
    href: '/users',
    available: true,
    roles: ['ADMIN'],
  },
  {
    id: uniqueId(),
    title: 'Audit Logs',
    icon: ReportsIcon,
    href: '/audit-logs',
    available: true,
    roles: ['ADMIN'],
  },
  {
    id: uniqueId(),
    title: 'Maintenance',
    icon: ReportsIcon,
    href: '/maintenance',
    available: true,
    roles: ['ADMIN'],
  },
];

export default Menuitems;
