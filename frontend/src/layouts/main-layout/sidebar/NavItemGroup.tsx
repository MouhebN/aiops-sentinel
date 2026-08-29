import { ListSubheader } from '@mui/material';

interface NavItemGroupProp {
  subheader?: string;
}
const NavItemGroup = ({ subheader }: NavItemGroupProp) => {
  return <ListSubheader>{subheader}</ListSubheader>;
};

export default NavItemGroup;
