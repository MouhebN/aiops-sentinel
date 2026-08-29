import { SvgIcon, SvgIconProps } from '@mui/material';

const ForgotPasswordIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon
      {...props}
      viewBox="0 0 24 24"
      style={{
        verticalAlign: 'middle',
        fill: 'none',
        overflow: 'hidden',
      }}
    >
      <path
        d="M21 8.5V6C21 4.89543 20.1046 4 19 4H5C3.89543 4 3 4.89543 3 6V11C3 12.1046 3.89543 13 5 13H10.875M19 14V12C19 10.8954 18.1046 10 17 10C15.8954 10 15 10.8954 15 12V14M14 20H20C20.5523 20 21 19.5523 21 19V15C21 14.4477 20.5523 14 20 14H14C13.4477 14 13 14.4477 13 15V19C13 19.5523 13.4477 20 14 20Z"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx={7.5} cy={8.5} r={1.5} fill="currentColor" />
      <circle cx={12} cy={8.5} r={1.5} fill="currentColor" />
    </SvgIcon>
  );
};

export default ForgotPasswordIcon;
