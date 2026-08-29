import { SvgIcon, SvgIconProps } from '@mui/material';

const CheckboxCheckedIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon
      xmlns="http://www.w3.org/2000/svg"
      width="20"
      height="20"
      fill="none"
      viewBox="0 0 20 20"
      {...props}
    >
      <rect width="20" height="20" fill="#1E5EFF" rx="4"></rect>
      <path
        fill="#fff"
        fillRule="evenodd"
        d="M13.293 7.293a1 1 0 011.497 1.32l-.083.094-5 5a1 1 0 01-1.32.083l-.094-.083-3-3a1 1 0 011.32-1.497l.094.083L9 11.585l4.293-4.292z"
        clipRule="evenodd"
      ></path>
    </SvgIcon>
  );
};

export default CheckboxCheckedIcon;
