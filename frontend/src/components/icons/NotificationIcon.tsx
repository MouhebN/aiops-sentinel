import { SvgIcon, SvgIconProps } from '@mui/material';

const NotificationIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon
      xmlns="http://www.w3.org/2000/svg"
      width="24"
      height="24"
      fill="none"
      viewBox="0 0 24 24"
      {...props}
    >
      <path
        fill="#7E84A3"
        fillRule="evenodd"
        d="M13 3c2.282.464 4 2.581 4 5v4a2 2 0 012 2v5h-5a2 2 0 11-4 0H5v-5a2 2 0 012-2V8c0-2.419 1.718-4.537 4-5h2zm-1 2a3 3 0 00-2.995 2.824L9 8v4a2 2 0 01-2 2v3h10v-3a2 2 0 01-2-2V8a3 3 0 00-3-3z"
        clipRule="evenodd"
      ></path>
    </SvgIcon>
  );
};

export default NotificationIcon;
