import { SvgIcon, SvgIconProps } from '@mui/material';

const ReportsIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon {...props}>
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M18.786 19c.67 0 1.214.448 1.214 1s-.544 1-1.214 1H4.214C3.544 21 3 20.552 3 20s.544-1 1.214-1h14.572zM18 3a2 2 0 012 2v12H3v-4a2 2 0 012-2h3V9a2 2 0 012-2h3V5a2 2 0 012-2h3zM8 13H5v2h3v-2zm5-4h-3v6h3V9zm5-4h-3v10h3V5z"
        clipRule="evenodd"
      ></path>
    </SvgIcon>
  );
};

export default ReportsIcon;
