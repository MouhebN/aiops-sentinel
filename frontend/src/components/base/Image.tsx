import { Box } from '@mui/material';
import { BoxProps, SxProps } from '@mui/system';
import { ImgHTMLAttributes } from 'react';

interface ImageProps extends BoxProps {
  src: ImgHTMLAttributes<HTMLImageElement>['src'];
  alt: string;
  sx?: SxProps;
}

const Image = ({ src, alt, ...rest }: ImageProps) => {
  return <Box component="img" src={src} alt={alt} {...rest} />;
};

export default Image;
