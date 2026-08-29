import { SvgIcon, SvgIconProps } from '@mui/material';

const CheckboxIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon
      xmlns="http://www.w3.org/2000/svg"
      width="20"
      height="20"
      fill="none"
      viewBox="0 0 20 20"
      {...props}
    >
      <rect width="19" height="19" x="0.5" y="0.5" fill="#fff" stroke="#D7DBEC" rx="3.5"></rect>
    </SvgIcon>
  );
};

export default CheckboxIcon;
