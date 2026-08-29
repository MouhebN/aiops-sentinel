import { SvgIcon, SvgIconProps } from '@mui/material';

const DollarIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon {...props}>
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M12 3a1 1 0 011 1v.09c2.194.401 3.89 2.095 3.995 4.201L17 8.5h-2c0-1.066-.822-2.009-1.999-2.357v4.947C15.265 11.505 17 13.298 17 15.5s-1.735 3.995-4 4.41V20a1 1 0 11-2 0v-.09c-2.193-.4-3.89-2.095-3.995-4.201L7 15.5h2c0 1.067.823 2.01 2 2.358V12.91c-2.265-.414-4-2.207-4-4.41 0-2.203 1.735-3.996 4-4.41V4a1 1 0 011-1zm1.001 10.143v4.714C14.178 17.51 15 16.567 15 15.5c0-1.066-.822-2.009-1.999-2.357zM9 8.5c0 1.067.823 2.01 2 2.357V6.143C9.823 6.49 9 7.433 9 8.5z"
        clipRule="evenodd"
      ></path>
    </SvgIcon>
  );
};

export default DollarIcon;
