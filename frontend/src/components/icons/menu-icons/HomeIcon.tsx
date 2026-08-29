import { SvgIcon, SvgIconProps } from '@mui/material';

function HomeIcon(props: SvgIconProps) {
  return (
    <SvgIcon {...props}>
      <path
        fillRule="evenodd"
        d="M13.329 3.516c.058.052.114.107.166.166l6 6.75A2 2 0 0120 11.76V19a2 2 0 01-2 2h-3.998H10 6a2 2 0 01-2-2v-7.24a2 2 0 01.505-1.328l6-6.75a2 2 0 012.824-.166zM11 19h2v-4h-2v4zm4 0v-5a1 1 0 00-1-1h-4a1 1 0 00-1 1v5H6v-7.24l6-6.75 6 6.75V19h-3z"
        clipRule="evenodd"
        fill="currentColor"
      />
    </SvgIcon>
  );
}

export default HomeIcon;
