import { SvgIcon, SvgIconProps } from '@mui/material';

const PersonalSettingsIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon {...props}>
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M12 12a8 8 0 017.996 7.75L20 20v1H4v-1a8 8 0 018-8zm0 2a6.002 6.002 0 00-5.851 4.667l-.048.23-.017.103h11.831l-.016-.102a6.003 6.003 0 00-5.425-4.88l-.25-.014L12 14zm0-11a4 4 0 110 8 4 4 0 010-8zm0 2a2 2 0 100 4 2 2 0 000-4z"
        clipRule="evenodd"
      ></path>
    </SvgIcon>
  );
};

export default PersonalSettingsIcon;
