import { SvgIcon, SvgIconProps } from '@mui/material';

const ProductUpdateIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon {...props}>
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M12 2a7 7 0 014.001 12.744L16 22l-4-3-4 3v-7.255A7 7 0 0112 2zm2 14h-4v2l2-1 2 1v-2zM12 4a5 5 0 100 10 5 5 0 000-10z"
        clipRule="evenodd"
      ></path>
    </SvgIcon>
  );
};

export default ProductUpdateIcon;
