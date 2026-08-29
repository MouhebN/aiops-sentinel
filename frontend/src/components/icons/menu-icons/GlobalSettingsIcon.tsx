import { SvgIcon, SvgIconProps } from '@mui/material';

const GlobalSettingsIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon {...props}>
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M13.5 2L16 5l4 .5 2 3-2 3.5 2 3.5-2 3-4 .5-2.5 3h-3L8 19l-4-.5-2-3L4 12 2 8.5l2-3L8 5l2.5-3h3zm-.938 1.999h-1.125L9.031 6.887l-3.875.483-.806 1.211L6.304 12 4.35 15.418l.806 1.211 3.875.484L11.436 20h1.127l2.406-2.886 3.874-.484.806-1.211L17.696 12l1.953-3.419-.806-1.211-3.874-.483-2.407-2.888zM12 8a4 4 0 110 8 4 4 0 010-8zm0 2a2 2 0 100 4 2 2 0 000-4z"
        clipRule="evenodd"
      ></path>
    </SvgIcon>
  );
};

export default GlobalSettingsIcon;
