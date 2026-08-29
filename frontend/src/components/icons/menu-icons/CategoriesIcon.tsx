import { SvgIcon, SvgIconProps } from '@mui/material';

const CategoriesIcon = (props: SvgIconProps) => {
  return (
    <SvgIcon {...props}>
      <path
        fill="currentColor"
        fillRule="evenodd"
        d="M9.5 2L12 5h8a2 2 0 012 2v12a2 2 0 01-2 2H4a2 2 0 01-2-2V4a2 2 0 012-2h5.5zm1.563 5l-2.5-3H4v15h16V7h-8.937z"
        clipRule="evenodd"
      ></path>
    </SvgIcon>
  );
};

export default CategoriesIcon;
