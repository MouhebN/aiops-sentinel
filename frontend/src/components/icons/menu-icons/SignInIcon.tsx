import { SvgIcon, SvgIconProps } from '@mui/material';

const SignInIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon
      {...props}
      style={{
        verticalAlign: 'middle',
        fill: 'none',
        overflow: 'hidden',
      }}
      viewBox="-1 0 24 24"
    >
      <path
        d="M20 18L17 18M17 18L14 18M17 18V15M17 18V21M11 21H4C4 17.134 7.13401 14 11 14C11.695 14 12.3663 14.1013 13 14.2899M15 7C15 9.20914 13.2091 11 11 11C8.79086 11 7 9.20914 7 7C7 4.79086 8.79086 3 11 3C13.2091 3 15 4.79086 15 7Z"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </SvgIcon>
  );
};

export default SignInIcon;
