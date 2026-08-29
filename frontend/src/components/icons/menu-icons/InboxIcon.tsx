import { SvgIcon, SvgIconProps } from '@mui/material';

const InboxIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon {...props}>
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M19 3a2 2 0 012 2v12a2 2 0 01-2 2H9l-6 3V5a2 2 0 012-2h14zm0 2H5v13.468L8.446 17H19V5zm-8 7v2H7v-2h4zm6-4v2H7V8h10z"
        clipRule="evenodd"
      ></path>
    </SvgIcon>
  );
};

export default InboxIcon;
